package audiofilereader;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import processes.Execute;
import processes.StreamGobbler;
import processes.StreamGobblerText;
import timer.DurationFormat;

public class MusicData {
	public String filename;
	public String filePath;
	private int channels; //usually 2 for music
	public int sampleRate; //usually 44.1k
	public int bitsPerSample; //usually 16 (which is 2 bytes per sample) One sample per one channel.
	public int bytesPerFrame; //usually 4 for 2 channels. Frames contain samples from all channels.
	public int avgBytesPerSecond;
	public long dataLength; //full audiodata length in bytes. All samples from all channels interleaved.
	private byte[] dataBytes; //the actual audiodata for dataLength.
	private short[] samples; //dataBytes turned into shorts.
	
	private short[] samplesLeft; //samples separated to channels.
	private short[] samplesRight;
	
	public static double convertProgress;
	
	/**
	 * Creates a MusicData object with 16bit values and given sample rate and number of channels.
	 * @param sampleRate How many samples per second (per channel)
	 * @param nbrOfChannels
	 * @return 
	 */
	public static MusicData createMusicData(int sampleRate, int nbrOfChannels) {
		MusicData musicData = new MusicData();
		
		musicData.channels = nbrOfChannels;
		musicData.sampleRate = sampleRate;
		musicData.bitsPerSample = 16;
		musicData.bytesPerFrame = (musicData.bitsPerSample * musicData.channels) / 8;
		musicData.avgBytesPerSecond = (musicData.sampleRate * musicData.bitsPerSample * musicData.channels) / 8;
		
		return musicData;
	}
	
	public static MusicData createMusicDataByDataBytes(byte[] dataBytes, String filename, int sampleRate, int channels) {
		MusicData musicData = createMusicData(sampleRate, channels);
		
		musicData.filename = filename;
		musicData.dataLength = dataBytes.length;
		musicData.dataBytes = dataBytes;
		
		musicData.setSamples(musicData.convertByteDataToSamples(), false);
		
		return musicData;
	}
	
	/**
	 * Create MusicData object with the help of AudioInputStream and FFmpeg.
	 * AudioInputStream can probably read files more consistently than doing it manually.
	 * @param file
	 * @return 
	 */
	public static MusicData createMusicData(File file) {
		try {
			AudioInputStream ais = AudioSystem.getAudioInputStream(file);
			return new MusicData(ais, file.getName());
			
		} catch (IOException | UnsupportedAudioFileException e) {
			System.err.println("Couldn't read audio file: " + e.getMessage());
			
			try {
				System.out.println("Trying conversion.\n");
				convertProgress = 0;
				System.out.println(file.getAbsolutePath());
				
				if (!Execute.programExists("ffmpeg")) {
					throw new RuntimeException("No ffmpeg installed!");
				}
				
				int channels = 2;
				long fileSizeInMegabytes = file.length() / 1024 / 1024;
				if (fileSizeInMegabytes > 100) {
					channels = 1;
				}
				
				String durText = null;
				int sampleRate = 44100;
				if (Execute.programExists("ffprobe -v quiet")) {
					//finds the sample rate with ffprobe
					String result = Execute.executeCommandOut("ffprobe -v error -select_streams a:0 -of default=noprint_wrappers=1:nokey=1 -show_entries stream=sample_rate \"" + file.getAbsolutePath() + "\"").trim();
					sampleRate = Integer.parseInt(result);
					
					//finds the duration in seconds with ffprobe
					durText = Execute.executeCommandOut("ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \"" + file.getAbsolutePath() + "\"").trim();
				}
				
				Duration dur = (durText != null) ? Duration.ofSeconds((int) Double.parseDouble(durText)) : null;
				
				String command = "ffmpeg -i \"" + file.getAbsolutePath() + "\" -c:a pcm_s16le -ac " + channels + " -f s16le pipe:1";
				//String command = "ffmpeg -i \"" + file.getAbsolutePath() + "\" -c:a pcm_s16le -ac " + channels + " -f wav pipe:1"; //this does something different, doesn't give same amount of samples to both channels (gives more data), and writes wrong amount of bytes to buffer. Must have a header in data or smt.
				Process process = Execute.executeCommandGetProcess(command);
				
				
				
				
				new StreamGobblerText(process.getErrorStream(), StreamGobbler.Type.ERROR, false, text -> MusicData.updateConvertProgress(FFmpegProgress.getProgress(text, dur))).start();
				
				byte[] bytes = process.getInputStream().readAllBytes();
				
				process.waitFor();
				process.destroy();
				
				return createMusicDataByDataBytes(bytes, file.getName(), sampleRate, channels);
				
				
				//with the use of an extra file
				//Execute.execute("ffmpeg -i \"" + file.getAbsolutePath() + "\" -c:a pcm_s16le -ac 1 tempFile.wav", false);
				
				//AudioInputStream ais = AudioSystem.getAudioInputStream(new File("tempFile.wav"));
				/*AudioInputStream ais = AudioSystem.getAudioInputStream(fis);
				return new MusicData(ais, file.getName());*/
				
			} catch (IOException | InterruptedException | RuntimeException e2) {
				System.err.println("Couldn't read or convert the file");
				e2.printStackTrace();
			}
		}
		return null;
	}
	
	public static void updateConvertProgress(double percent) {
		if (percent < 0) {
			return;
		}
		convertProgress = percent;
	}
	
	public static MusicData createDefault() {
		return MusicData.createMusicData(44100, 2);
	}
	
	public MusicData() {}
	
	public MusicData(AudioInputStream ais, String filename) {
		try {
			AudioFormat format = ais.getFormat();
			
			//Convert the format to 16 bit and max 2 channels:
			AudioFormat toAudioFormat = new AudioFormat(format.getSampleRate(), 16, format.getChannels() > 2 ? 1 : format.getChannels(), true, false);
			if (!AudioSystem.isConversionSupported(toAudioFormat, format)) {
				throw new IllegalArgumentException("system cannot convert from " + format + " to " + toAudioFormat);
			}
			ais = AudioSystem.getAudioInputStream(toAudioFormat, ais);
			format = ais.getFormat();
			
			
			this.filename = filename;
			channels = format.getChannels();
			sampleRate = (int) format.getSampleRate();
			bitsPerSample = format.getSampleSizeInBits();
			bytesPerFrame = format.getFrameSize();
			avgBytesPerSecond = (sampleRate * bitsPerSample * channels) / 8;
			dataLength = ais.getFrameLength() * bytesPerFrame;
		
			dataBytes = ais.readAllBytes();
			setSamples(convertByteDataToSamples(), false);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public int getChannels() {
		return channels;
	}
	
	public void setChannels(int channels) {
		this.channels = channels;
	}
	
	public byte[] getDataBytes() {
		if (dataBytes == null) {
			return new byte[0];
		}
		return dataBytes;
	}
	
	public long getDataLength() {
		return dataLength;
	}
	
	public void setDataBytes(byte[] dataBytes) {
		this.dataBytes = dataBytes;
	}
	
	public short[] getSamples() {
		if (samples == null) {
			return new short[0];
		}
		return samples;
	}
	
	public short[] getSamplesLeft() {
		if (samplesLeft == null) {
			return new short[0];
		}
		return samplesLeft;
	}
	
	public short[] getSamplesRight() {
		if (channels == 1) {
			return getSamplesLeft();
		} else if (samplesRight == null) {
			return new short[0];
		}
		return samplesRight;
	}
	
	public short[] getSamplesByChannel(boolean left) {
		if (left) {
			return getSamplesLeft();
		}
		return getSamplesRight();
	}
	
	//Use only for small ranges.
	public short[] getSamplesByChannel(boolean left, int startFrame, int length) {
		short[] s = new short[length];
		
		int start = startFrame * channels + ((!left && channels == 2) ? 1 : 0);
		int count = 0;
		for (int i = start; count < length; i += channels) {
			if (i < 0) {
				continue;
			}
			if (i >= samples.length) {
				return s;
			}
			s[count] = samples[i];
			count++;
		}
		return s;
	}
	
	public final short[] convertByteDataToSamples() {
		int bytesPerSample = bitsPerSample / 8;
		short[] tempSamples = new short[(int) dataLength / bytesPerSample];
		
		int counter = 0;
		for (int i = 0; i < dataBytes.length; i += bytesPerSample, counter++) {
			ByteBuffer buf = ByteBuffer.wrap(dataBytes, i, bytesPerSample);
			buf.order(ByteOrder.LITTLE_ENDIAN);
			
			switch (bytesPerSample) {
				case 1:
					//8 bit
					tempSamples[counter] = (short) (buf.get() - 128);
					break;
				case 2:
					//16 bit
					tempSamples[counter] = buf.getShort();
					break;
				case 3:
					//24 bit, changed to use shorts, scaling to 16bit
					byte[] tmp = new byte[3];
					buf.get(tmp);
					int val = (tmp[0] << 16 | tmp[1] << 8 | tmp[2]);
					double max24 = 0x800000;
					int max16 = 0x8000;
					tempSamples[counter] = (short) ((val / max24) * max16);
					break;
			}
		}
		return tempSamples;
	}
	
	public void clearData() {
		dataLength = 0;
		dataBytes = null;
		samples = null;
		samplesLeft = null;
		samplesRight = null;
	}
	
	/**
	 * DuplicateMonoToStereo is used only when you want 2 channels, but only provide mono.
	 * You knew previously that you were going to get 2 channels, and already initialized musicData with 2 channels.
	 * This does not change number of channels automatically.
	 * Also if using duplicateMonoToStereo dataBytes should be null, and created in this method.
	 * If you already made MusicData with different audio, use clearData() to clear them. But preferably just create new MusicData object.
	 * @param samples
	 * @param duplicateMonoToStereo 
	 */
	public final void setSamples(short[] samples, boolean duplicateMonoToStereo) {
		if (duplicateMonoToStereo) {
			this.samples = duplicateChannel(samples);
		} else {
			this.samples = samples;
		}
		if (dataBytes == null || dataBytes.length == 0) {
			turnSamplesToByteData(); //we need dataBytes for audio playback, samples are used for other things.
		}
		separateSamplesToChannels();
	}
	
	private short[] duplicateChannel(short[] samples) {
		short[] allSamples = new short[samples.length * 2];
		
		for (int i = 0; i < samples.length; i++) {
			int idx = i * 2;
			allSamples[idx] = samples[i];
			allSamples[idx + 1] = samples[i];
		}
		
		return allSamples;
	}
	
	private void separateSamplesToChannels() {
		if (dataBytes == null || dataBytes.length == 0) {
			turnSamplesToByteData();
		}
		
		int counter = 0;
		samplesLeft = new short[samples.length / channels + 1];
		samplesRight = new short[samples.length / channels + 1];
		
		for (int i = 0; i < samples.length; i++) {
			short val = samples[i];
			if (i % channels == 0) {
				samplesLeft[counter] = val;
			} else {
				if (counter >= samplesRight.length) {
					break;
				}
				samplesRight[counter] = val;
				counter++;
			}
			
			if (channels == 1) {
				counter++;
			}
		}
		
		Duration dur = Duration.ofMillis(getDurationMillis());
		
		DurationFormat s = new DurationFormat("hh:mm:ss.lll");
		System.out.println("Duration: " + s.format(dur));
	}
	
	private void turnSamplesToByteData() {
		dataBytes = new byte[samples.length * 2]; //2 bytes per sample for 16bit
		
		for (int i = 0; i < samples.length; i++) {
			short sample = samples[i];
			//convert from short to 2 bytes
			dataBytes[2 * i] = (byte) (sample & 0xff);
			dataBytes[2 * i + 1] = (byte) (sample >> 8 & 0xff);
		}
		
		dataLength = dataBytes.length;
	}
	
	public long getDurationMillis() {
		return (long) (getFrameCount() / (double) sampleRate * 1000);
	}
	
	public long getDurationMicros() {
		return (long) (getFrameCount() / (double) sampleRate * 1000000);
	}
	
	public int bytesToSeconds(int bytes) {
		return (int) (bytesToMicros(bytes) / 1e6);
	}
	
	public long bytesToMicros(int bytes) {
		return frameToMicros(bytesToFrameNumber(bytes));
	}
	
	public int bytesToFrameNumber(int bytes) {
		return (int) (bytes / (double) bytesPerFrame);
	}
	
	public int frameToByteNumber(int frame) {
		return frame * bytesPerFrame;
	}
	
	public String bytesToDurationString(int bytes) {
		return microsToDurationString(bytesToMicros(bytes));
	}
	
	public String microsToDurationString(long micros) {
		Duration audioFullDuration = Duration.of(getDurationMicros(), ChronoUnit.MICROS);
		String format = (audioFullDuration.toHours() > 0 ? "hh:": "") + "mm:ss.lll";
		DurationFormat formatter = new DurationFormat(format);
		
		Duration dur = Duration.of(micros, ChronoUnit.MICROS);
		return formatter.format(dur);
	}
	
	public int microsToFrameNumber(long micros) {
		return (int) (sampleRate * micros / 1e6);
	}
	
	public int millisToFrameNumber(long millis) {
		return (int) (sampleRate * millis / 1000);
	}
	
	public long microsToByteNumber(long micros) {
		return microsToFrameNumber(micros) * bytesPerFrame;
	}
	
	public long millisToByteNumber(long millis) {
		return millisToFrameNumber(millis) * bytesPerFrame;
	}
	
	public long secondsToByteNumber(long seconds) {
		return microsToByteNumber((long) (seconds * 1e6));
	}
	
	public int getFrameCount() {
		return (int) dataLength / bytesPerFrame;
	}

	public long frameToMicros(long frame) {
		return (long) (frame / (double) sampleRate * 1e6);
	}

	public long frameToMillis(long frame) {
		return (long) (frame / (double) sampleRate * 1000);
	}
	
	public int getMaxValue() {
		return (int) Math.pow(2, bitsPerSample) / 2;
	}
}
