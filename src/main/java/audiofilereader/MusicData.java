package audiofilereader;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import stopwatchtimerrender.Settings;

public class MusicData {
	public int numberOfChannels;
	public int sampleRate;
	public int avgBytesPerSecond;
	public short bytesPerFrame;
	public short bitsPerSample;
	public int dataLength;
	public byte[] dataBytes;
	private short[] samples;
	
	public short[] samplesLeft;
	public short[] samplesRight;
	
	/**
	 * Creates a MusicData object with 16bit values and given sample rate and number of channels.
	 * @param sampleRate How many samples per second (per channel)
	 * @param nbrOrChannels
	 * @return 
	 */
	public static MusicData createMusicData(int sampleRate, int nbrOrChannels) {
		MusicData musicData = new MusicData();
		
		musicData.numberOfChannels = nbrOrChannels;
		musicData.sampleRate = sampleRate;
		musicData.bitsPerSample = 16;
		musicData.avgBytesPerSecond = (musicData.sampleRate * musicData.bitsPerSample * musicData.numberOfChannels) / 8;
		musicData.bytesPerFrame = (short) ((musicData.bitsPerSample * musicData.numberOfChannels) / 8);
		
		return musicData;
	}
	
	public short[] getSamples() {
		return samples;
	}
	
	public short[] getSamplesChannel(boolean left) {
		return getSamplesChannel(left, 0, (int) getFrameCount());
	}
	
	public short[] getSamplesChannel(boolean left, int startFrame, int sampleCount) {
		short[] s = new short[sampleCount];
		
		int start = startFrame * numberOfChannels + (!left && numberOfChannels == 2 ? 1 : 0);
		int count = 0;
		for (int i = start; count < sampleCount; i += numberOfChannels) {
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
	
	public void setSamples(short[] samples) {
		this.samples = samples;
		if (dataBytes == null || dataBytes.length == 0) {
			turnSamplesToByteData();
		}
		separateSamplesToChannels();
	}
	
	public void setSamplesDuplicateChannel(short[] samples) {
		this.samples = duplicateChannel(samples);
		if (dataBytes == null || dataBytes.length == 0) {
			turnSamplesToByteData();
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
		samplesLeft = new short[samples.length / 2];
		samplesRight = new short[samples.length / 2];
		
		for (int i = 0; i < samples.length; i++) {
			short val = samples[i];
			if (i % numberOfChannels == 0) {
				samplesLeft[counter] = val;
			} else {
				samplesRight[counter] = val;
				counter++;
			}
			if (numberOfChannels == 1) {
				counter++;
			}
		}
		
		Duration dur = Duration.ofMillis(getDurationMillis());
		
		Settings s = new Settings("hh:mm:ss.lll");
		System.out.println("Duration: " + s.format(dur));
	}
	
	private void turnSamplesToByteData() {
		dataBytes = new byte[samples.length * 2];
		
		for (int i = 0; i < samples.length; i++) {
			int sample = samples[i];
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
		Duration dur = Duration.of(micros, ChronoUnit.MICROS);
		String start = dur.toHours() > 0 ? "hh:": "";
		
		Settings s = new Settings(start + "mm:ss.lll");
		return s.format(dur);
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
	
	public long secondsToByteNumber(long seconds) {
		return microsToByteNumber((long) (seconds * 1e6));
	}
	
	public int getFrameCount() {
		return dataBytes.length / bytesPerFrame;
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
