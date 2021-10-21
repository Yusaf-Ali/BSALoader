package bsa;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;

import init.Settings;
import me.yusaf.Logger;

/**
 * Reads .bsa archives. Can load inner file from provided archive against a correct path. allFilenames public list is available for easier access to filenames
 * 
 * @author Yusaf Ali
 *
 */
public class BsaFile {
	public List<String> allFilenames = new ArrayList<>();
	private List<BsaFolderRecord> folders = new ArrayList<>();
	private Map<String, BsaFileRecord> files = new LinkedHashMap<>();
	public static Logger logger = Logger.getInstance(BsaFile.class);

	/**
	 * Key should be the texture path and value should be bsa archive name
	 */
	public static Map<String, BsaFile> lookupMap = new LinkedHashMap<>();

	private File location;
	private String bsaName;
	private int archiveFlags;
	private int version;

	/**
	 * Automatically reads header of provided file of .bsa extension
	 * 
	 * @param location
	 *            provided location
	 * @throws IOException
	 */
	public BsaFile(File location) throws IOException {
		this.setName(location.toString());
		this.location = location;

		int totalBytesRead = 0;
		RandomAccessFile accessFile = new RandomAccessFile(location, "r");
		byte[] headerBytes = new byte[4 * 9];
		ByteBuffer headerBuffer = ByteBuffer.allocate(4 * 9);
		accessFile.read(headerBytes);
		headerBuffer.put(headerBytes);
		headerBuffer.flip();
		headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
		logger.log("Total file size = " + accessFile.getChannel().size(), 1);

		try {
			int fileId = headerBuffer.getInt();
			totalBytesRead += 4;
			if (fileId != 0x415342) {
				throw new IOException("Supporting only BSA files");
			}
			logger.extra("FileId: " + fileId + " [" + totalBytesRead + "]");
			version = headerBuffer.getInt();
			totalBytesRead += 4;
			logger.extra("Version: " + version + " [" + totalBytesRead + "]");
			int folderOffset = headerBuffer.getInt();
			totalBytesRead += 4;
			logger.extra("FolderOffset: " + folderOffset + " [" + totalBytesRead + "]");
			archiveFlags = headerBuffer.getInt();
			totalBytesRead += 4;
			logger.extra("ArchiveFlags: " + archiveFlags + " [" + totalBytesRead + "]");
			int folderCount = headerBuffer.getInt();
			totalBytesRead += 4;
			logger.extra("FolderCount: " + folderCount + " [" + totalBytesRead + "]");
			int fileCount = headerBuffer.getInt();
			totalBytesRead += 4;
			logger.extra("File count: " + fileCount + " [" + totalBytesRead + "]");
			int totalFolderNameLength = headerBuffer.getInt();
			totalBytesRead += 4;
			logger.extra("FolderNameLength: " + totalFolderNameLength + " [" + totalBytesRead + "]");
			int totalFileNameLength = headerBuffer.getInt();
			totalBytesRead += 4;
			logger.extra("FileNameLength: " + totalFileNameLength + " [" + totalBytesRead + "]");
			int fileFlags = headerBuffer.getInt();
			totalBytesRead += 4;
			logger.extra("FileFlags: " + fileFlags + " [" + totalBytesRead + "]");
			FileFlagType fileCollectionType = FileFlagType.get(fileFlags);
			logger.extra("FileCollectionType: " + fileCollectionType.toString() + " [" + totalBytesRead + "]");

			boolean defaultCompressed = false;
			if ((archiveFlags & 0x4) != 0) {
				defaultCompressed = true;
				logger.extra("DefaultCompressed: " + defaultCompressed);
			}

			logger.extra("Processing Folder Record Blocks");
			byte[] folderList = new byte[folderCount * 24];
			accessFile.seek(totalBytesRead);
			accessFile.read(folderList, 0, folderCount * 24);
			ByteBuffer folderAllRecordBuffer = ByteBuffer.wrap(folderList).order(ByteOrder.LITTLE_ENDIAN);
			for (int i = 0; i < folderCount; i++) {
				BsaFolderRecord folder = new BsaFolderRecord(folderAllRecordBuffer);
				logger.extra("Folder " + i +
						" fileCount: " + Integer.toUnsignedString(folder.fileCount) +
						", padding: " + Integer.toUnsignedString(folder.padding) +
						", offset: " + Long.toUnsignedString(folder.offset)
						+ " [" + totalBytesRead + "]");
				totalBytesRead += 24;
				folders.add(folder);
			}

			logger.extra("Processing File Record Blocks");
			for (int i = 0; i < folderCount; i++) {
				BsaFolderRecord folder = folders.get(i);
				// Folder names are prepended with their length
				byte[] foldernameLengthByte = new byte[1];
				accessFile.seek(totalBytesRead);
				accessFile.read(foldernameLengthByte, 0, 1);
				totalBytesRead++;
				folder.setNameLength(foldernameLengthByte[0]);
				byte[] foldernameBytes = new byte[folder.nameLength];
				accessFile.seek(totalBytesRead);
				accessFile.read(foldernameBytes, 0, folder.nameLength);
				ByteBuffer foldernameBuffer = ByteBuffer.wrap(foldernameBytes).order(ByteOrder.LITTLE_ENDIAN);
				folder.name = readBString(foldernameBuffer);
				totalBytesRead += folder.nameLength;
				logger.extra("Currently processing FileRecords for " + folder.name + " [" + totalBytesRead + "]");

				for (int j = 0; j < folder.fileCount; j++) {
					BsaFileRecord fr = new BsaFileRecord(accessFile, totalBytesRead, defaultCompressed);
					logger.extra("File (" + i + ", " + j + ")" +
							" size: " + Integer.toUnsignedString(fr.size) +
							", compressed: " + fr.compressed +
							", offset: " + Long.toUnsignedString(fr.offset)
							+ " [" + totalBytesRead + "]");
					totalBytesRead += 16;
					folder.files.add(fr);
				}
			}

			// Following bit is necessary for Bethesda games to successfully read from archive
			if ((archiveFlags & 0x2) == 0x2) {
				logger.extra("Processing File Name Blocks");
				for (int i = 0; i < folderCount; i++) {
					BsaFolderRecord fold = folders.get(i);
					for (int j = 0; j < fold.fileCount; j++) {
						BsaFileRecord fr = fold.files.get(j);
						// Filenames are prepended by their length
						logger.extra("File Name Block # (" + i + ", " + j + "): name length = " + fr.nameLength);
						accessFile.seek(totalBytesRead);
						String str = readBString(accessFile);
						totalBytesRead += str.length() + 1; // Accounting for null character
						logger.extra("String found = " + str);
						fr.nameLength = str.length();
						// Actual filename
						fr.name = str;
						// Name of the file with the path and backslash is important for loading
						String nameWithPath = (fold.name + "\\" + fr.name).toLowerCase();
						fr.nameWithPath = nameWithPath;
						lookupMap.put(nameWithPath, this);
						logger.extra("Name: " + bsaName);
						logger.extra("NameWithPath: " + nameWithPath);
						files.put(nameWithPath, fr);
					}
				}
			}
		} finally {
			accessFile.close();
		}
		allFilenames.addAll(files.keySet());
	}

	/**
	 * Reads all characters starting from current position. If required to access some other location of file, use seek before calling this method
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 */
	private String readBString(RandomAccessFile file) throws IOException {
		StringBuilder sb = new StringBuilder();
		byte b;
		while ((b = file.readByte()) != 0) {
			sb.append((char) (b & 0xff));
		}
		return sb.toString();
	}

	private String readBString(ByteBuffer buffer) {
		StringBuilder sb = new StringBuilder();
		byte b;
		while ((b = buffer.get()) != 0) {
			sb.append((char) (b & 0xff));
		}
		return sb.toString();
	}

	private byte[] load(int off, int len, String fullname, boolean compressed) throws IOException, DataFormatException {
		byte[] totalFileBytes = null;
		byte[] filenameBytes = new byte[fullname.length() + 1];
		RandomAccessFile raf = new RandomAccessFile(location, "r");
		try {
			// Seek to file location and read file name
			raf.seek(off);
			raf.read(filenameBytes, 0, fullname.length() + 1);
			boolean namePrefix = true;
			if ((filenameBytes[0] & 0xff) == fullname.length()) {
				for (int i = 0; i < fullname.length(); i++) {
					if ((filenameBytes[i + 1] & 0xff) != fullname.charAt(i)) {
						namePrefix = false;
						break;
					}
				}
			} else {
				namePrefix = false;
			}

			// According to namePrefix found, seek the file by name.length
			if (namePrefix) {
				raf.seek(off + fullname.length() + 1);
			} else {
				raf.seek(off);
			}

			if (compressed) {
				int originalSize = raf.read() | (raf.read() << 8) | (raf.read() << 16) | (raf.read() << 24);
				logger.extra("Compressed Size: " + (len));
				logger.extra("Original Size: " + originalSize);
				// Subtract original size int (depecrated ulong) and fullname.length + null character if present
				int lengthWithoutSizeAndNameBytes = len - 4 - fullname.length() - 1;
				totalFileBytes = new byte[lengthWithoutSizeAndNameBytes];
				byte[] decompressedBytes = new byte[originalSize];
				// Seeked, read all bytes till byte array is filled
				raf.readFully(totalFileBytes);
				if (version == 104) {
					// Simple zip decompression
					Inflater inflater = new Inflater();
					inflater.setInput(totalFileBytes);
					inflater.inflate(decompressedBytes);
					inflater.end();
					totalFileBytes = decompressedBytes;
				} else if (version == 105) {
					// Lz4 decompression
					try {
						// Apache common compress technique
						FramedLZ4CompressorInputStream zIn = new FramedLZ4CompressorInputStream(new ByteArrayInputStream(totalFileBytes));
						ByteArrayOutputStream out = new ByteArrayOutputStream(decompressedBytes.length);
						int n = 0;
						byte[] buffer = new byte[6144]; // 4096 + 2048
						while ((-1 != (n = zIn.read(buffer)))) {
							out.write(buffer, 0, n);
						}
						decompressedBytes = out.toByteArray();
						out.close();
						zIn.close();
					} catch (Exception ex) {
						System.err.println("Failed decompression by apache common compress for file: " + fullname);
						ex.printStackTrace();
						// fallback
						System.err.println("Fallback to Yann Collet's lz4 cli!");
						decompressedBytes = processLz4File(totalFileBytes, originalSize, fullname);
					}
					// Creates a new file, the file is left to avoid recreation
					return decompressedBytes;
				}
			} else {
				raf.readFully(totalFileBytes);
			}
		} finally {
			raf.close();
		}
		return totalFileBytes;
	}

	/**
	 * Debug method, used for dumping bytes into a file
	 * 
	 * @param totalFileBytes
	 */
	public void saveToFile(byte[] totalFileBytes) {
		try {
			File f = new File("byte_literal_data.dat");
			if (!f.exists()) {
				ByteArrayOutputStream xo = new ByteArrayOutputStream();
				xo.write(totalFileBytes);
				FileOutputStream fos = new FileOutputStream(f);
				xo.writeTo(fos);
				fos.close();
				xo.close();
				if (f.exists()) {
					System.out.println("The file has been created: " + f.getAbsolutePath());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public byte[] load(String filename) throws IOException, DataFormatException {
		BsaFileRecord fileRecord = files.get(filename.toLowerCase());
		if (fileRecord == null) {
			logger.exception("File not in archive " + filename);
			return null;
		}
		return load(fileRecord.offset, fileRecord.size, fileRecord.nameWithPath, fileRecord.compressed);
	}

	public List<String> getFilenames() {
		return files.keySet().stream().collect(Collectors.toList());
	}

	public String getName() {
		return bsaName;
	}

	public void setName(String name) {
		this.bsaName = name;
	}

	private byte[] processLz4File(byte[] totalFileBytes, int originalSize, String fullname) throws IOException {
		File outputFile = new File(Settings.TEMP_LOCATION, fullname);
		if (!outputFile.exists()) {
			logger.extra("Using temp location: " + Settings.TEMP_LOCATION);
			String inputName = fullname.substring(0, fullname.lastIndexOf(".")) + ".compressed";
			File inputFile = new File(Settings.TEMP_LOCATION, inputName);
			inputFile.getParentFile().mkdirs();
			FileOutputStream os = new FileOutputStream(inputFile);
			os.write(totalFileBytes);
			os.flush();
			os.close();
			processLz4(inputFile, outputFile, originalSize);
			logger.extra("Remove compressed file data");
			inputFile.delete();
		}
		FileInputStream is = new FileInputStream(outputFile);
		byte[] bex = is.readAllBytes();
		is.close();
		return bex;
	}

	private void processLz4(File compressedDataFile, File uncompressedLocationFile, int originalSize) {
		ProcessBuilder pb = new ProcessBuilder();
		File lz4Exe = new File("lib/bin/lz4.exe");
		String input = "\"" + compressedDataFile.getAbsolutePath() + "\"";
		String output = "\"" + uncompressedLocationFile.getAbsolutePath() + "\"";
		String path2 = "\"\"" + lz4Exe.getAbsolutePath() + "\" -vd " + input + " " + output + "\"";
		try {
			logger.extra("Executing: ");
			logger.extra(pb.command().stream().collect(Collectors.joining(" ")));
			System.out.println(pb.command().stream().collect(Collectors.joining(" ")));
			pb.command("cmd", "/C", path2);
			Process p = pb.start();
			p.getErrorStream().transferTo(System.err);
			p.getInputStream().transferTo(System.out);
			p.waitFor();
		} catch (IOException e) {
			logger.exception(e);
		} catch (InterruptedException e) {
			logger.exception(e);
		}
	}
}