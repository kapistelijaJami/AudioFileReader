package audiofilereader;

import filereader.FileReader;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;

//TODO: more formats, maybe move my AudioInputStream implementation from MusicData constructor to here, since it can convert to 16bit and to mono if over 2 channels etc.
//JAAD AAC decoder and mp4 demultiplexer https://jaadec.sourceforge.net/usage.php
//You can create a temp file with ffmpeg (or maybe get it with pipe/inputStream?) so it could basically play any file, even video. This if others fail, because it's not fast.
public class AudioFileReader extends FileReader {
	private MusicData musicData;
	private boolean dataRead = false;
	
	public MusicData read(File file) {
		musicData = new MusicData();
		dataRead = false;
		
		Optional<String> ext = getExtension(file.getPath());
		
		if (!ext.isPresent()) {
			return null;
		}
		
		switch (ext.get().toLowerCase()) {
			case "wav":
				readWav(file);
				break;
			case "mp3":
				readMp3(file);
				break;
			default:
				return null;
		}
		
		return musicData;
	}
	
	public void readWav(File file) {
		try {
			readerHEAD = 0;
			byte[] bytes = Files.readAllBytes(file.toPath());
			
			print("length: " + bytes.length);
			
			String start = readChars(bytes, readerHEAD, 4);
			print("start: " + start);
			
			int size = readInt(bytes, readerHEAD, ByteOrder.LITTLE_ENDIAN);
			print("size: " + size);
			
			String fileType = readChars(bytes, readerHEAD, 4);
			print("file type: " + fileType);
			
			
			while (!dataRead) {
				readChunk(bytes);
			}
		} catch (IOException e) {
			
		}
	}
	
	public void readChunk(byte[] bytes) {
		String formatChunkMarker = readChars(bytes, readerHEAD, 4);
		print("format chunk marker: " + formatChunkMarker);
		
		if (formatChunkMarker.equals("fmt ")) {
			readFMTChunk(bytes);
		} else if (formatChunkMarker.equals("data")) {
			readData(bytes);
		} else {
			int length = readInt(bytes, readerHEAD, ByteOrder.LITTLE_ENDIAN);
			print("length: " + length);
			print("Extra info: " + readChars(bytes, readerHEAD, length));
		}
	}
	
	private void readFMTChunk(byte[] bytes) {
		int formatDataLength = readInt(bytes, readerHEAD, ByteOrder.LITTLE_ENDIAN);
		print("format data length: " + formatDataLength);
		
		short typeFormat = readShort(bytes, readerHEAD, ByteOrder.LITTLE_ENDIAN); //0 = unknown, 1 = PCM/uncompressed, 2 = Microsoft ADPCM, [...], 80 = MPEG etc...
		print("type format: " + typeFormat);
		
		musicData.numberOfChannels = readShort(bytes, readerHEAD, ByteOrder.LITTLE_ENDIAN);
		print("number of channels: " + musicData.numberOfChannels);
		
		musicData.sampleRate = readInt(bytes, readerHEAD, ByteOrder.LITTLE_ENDIAN);
		print("sample rate: " + musicData.sampleRate);
		
		musicData.avgBytesPerSecond = readInt(bytes, readerHEAD, ByteOrder.LITTLE_ENDIAN); //(Sample Rate * BitsPerSample * Channels) / 8, if bytesPerSecond * 8 / 1000 you get kb/s
		print("avg bytes per second: " + musicData.avgBytesPerSecond);
		
		musicData.bytesPerFrame = readShort(bytes, readerHEAD, ByteOrder.LITTLE_ENDIAN); //(BitsPerSample * Channels) / 8, number means: 1 = 8 bit mono, 2 = 8 bit stereo/16 bit mono, 4 = 16 bit stereo
		print("bytes per frame: " + musicData.bytesPerFrame);
		
		musicData.bitsPerSample = readShort(bytes, readerHEAD, ByteOrder.LITTLE_ENDIAN);
		print("bits per sample: " + musicData.bitsPerSample);
		
		int extraLength = formatDataLength - 16;
		if (extraLength == 0) {
			return;
		}
		print("extra: " + readChars(bytes, readerHEAD, extraLength));
	}
	
	public void readData(byte[] bytes) {
		musicData.dataLength = readInt(bytes, readerHEAD, ByteOrder.LITTLE_ENDIAN);
		print("data length: " + musicData.dataLength);
		
		musicData.dataBytes = Arrays.copyOfRange(bytes, readerHEAD, readerHEAD + (int) musicData.dataLength);
		
		musicData.setSamples(musicData.convertByteDataToSamples(), false);
		dataRead = true;
	}
	
	//https://www.programmersought.com/article/13025807723/
	//and http://mpgedit.org/mpgedit/mpeg_format/mpeghdr.htm
	//didnt really explain it all, maybe this: https://www.diva-portal.org/smash/get/diva2:830195/FULLTEXT01.pdf
	//kesken
	public void readMp3(File file) {
		try {
			readerHEAD = 0;
			byte[] bytes = Files.readAllBytes(file.toPath());
			
			musicData.filename = file.getName();
			System.out.println("File name: " + musicData.filename);
			
			print("length: " + bytes.length);
			
			String start = readChars(bytes, readerHEAD, 3);
			print("start: " + start);
			
			if (start.equals("ID3")) {
				readID3LabelHeader(bytes);
			}
		} catch (IOException e) {
			
		}
	}
	
	public void readID3LabelHeader(byte[] bytes) {
		byte versionNumber = readByte(bytes, readerHEAD);
		print("version number: " + versionNumber);
		
		byte minorVersionNumber = readByte(bytes, readerHEAD);
		print("minor version number: " + minorVersionNumber);
		
		byte flagByte = readByte(bytes, readerHEAD);
		print("flag byte: " + flagByte);
		
		int size = readMP3TAGSizeInt(bytes, readerHEAD);
		print("size: " + size);
		
		String frame = readChars(bytes, readerHEAD, 4);
		print("frame: " + frame);
		
		int frameContentSize = (int) readBytesAsLong(bytes, readerHEAD, 4, ByteOrder.BIG_ENDIAN);
		print("frame content size: " + frameContentSize);
		
		Short markFrame = readShort(bytes, readerHEAD, ByteOrder.BIG_ENDIAN);
		print("mark frame: " + markFrame);
		
		String content = readChars(bytes, readerHEAD, frameContentSize);
		print("content: " + content);
	}
	
	private int readMP3TAGSizeInt(byte[] bytes, int offset) {
		int size = (bytes[offset + 0] & 0x7F) * 0x200000 + (bytes[offset + 1] & 0x7F) * 0x400 + (bytes[offset + 2] & 0x7F) * 0x80 + (bytes[offset + 3] & 0x7F);
		readerHEAD += 4;
		
		return size;
	}
	
	@Override
	public void resetReader() {
		super.resetReader();
		
		musicData = null;
		dataRead = false;
	}
}
