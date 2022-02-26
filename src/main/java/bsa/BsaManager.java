package bsa;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;

import init.Settings;

public class BsaManager {
	/**
	 * bsaFiles is a map of records from all BSA archives and their corresponding BSA archives.<br>
	 * This is to easily get a BSA archive against a certain record to extract that record from that file, as game information holds records instead of archive
	 * names.
	 */
	private static Map<String, BsaFile> bsaFiles;

	/**
	 * Although its name should be bsaFiles, this map is based on archive name and archive itself.
	 */
	private static Map<String, BsaFile> archives;

	public static void initializeAllBsaLoading() {
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".bsa");
			}
		};
		initializeFilteredBsaLoading(filter);
	}

	public static void initializeFilteredBsaLoading(FilenameFilter filter) {
		double start = System.nanoTime();
		if (bsaFiles == null)
			bsaFiles = new HashMap<>();
		if (archives == null)
			archives = new HashMap<>();
		File skyrimData = new File(Settings.SKYRIM_LOCATION);
		File[] bsaTextureFiles = skyrimData.listFiles(filter);
		Arrays.stream(bsaTextureFiles).forEach(file -> {
			try {
				BsaFile bsaFile = new BsaFile(file);
				archives.put(file.getName(), bsaFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		System.out.println("Bsa Load Time: " + ((System.nanoTime() - start) / 1000000000) + "s");
	}

	public static void loadTextures() {
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.startsWith("Skyrim") && name.contains("Texture") && name.endsWith(".bsa");
			}
		};
		BsaManager.initializeFilteredBsaLoading(filter);
	}

	public static void loadSounds() {
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.startsWith("Skyrim") && name.contains("Sounds") && name.endsWith(".bsa");
			}
		};
		BsaManager.initializeFilteredBsaLoading(filter);
	}

	public static byte[] getFileBytes(String filename) throws IOException, DataFormatException {
		if (bsaFiles == null)
			return null;
		BsaFile bsa = bsaFiles.get(filename);
		return bsa.load(filename);
	}

	/**
	 * @param filename
	 *            is name of the internal file in BSA archive.
	 * @return
	 */
	public static BsaFile getFile(String filename) {
		if (bsaFiles == null)
			return null;
		return bsaFiles.get(filename);
	}

	public static BsaFile getFileContaining(String... filename) {
		Set<String> nks = archives.keySet().stream().map(k -> k).collect(Collectors.toSet());
		List<String> filenameList = Arrays.asList(filename).stream().map(m -> m.toLowerCase()).collect(Collectors.toList());
		HashMap<String, Integer> matchRating = new HashMap<>(nks.size());
		nks.forEach(k -> {
			matchRating.put(k, 0);
			filenameList.forEach(s -> {
				if (k.toLowerCase().contains(s.toLowerCase())) {
					matchRating.replace(k, matchRating.get(k) + 1);
				}
			});
			System.out.println("Rating: " + k + " <" + matchRating.get(k) + ">");
		});
		if (matchRating.size() == 1 && matchRating.get(nks.stream().collect(Collectors.toList()).get(0)) > 0) {
			return archives.get(matchRating.keySet().stream().collect(Collectors.toList()).get(0));
		}
		Optional<Entry<String, Integer>> matched = matchRating.entrySet().stream().max(Comparator.comparing(Map.Entry::getValue));
		return bsaFiles.get(matched.get().getKey());
	}

	public static void saveBsaFileRecord(String nameWithPath, BsaFile bsaFile) {
		bsaFiles.put(nameWithPath, bsaFile);
	}

	public static Map<String, BsaFile> getAllBsaFileRecords() {
		return bsaFiles;
	}

	public static void saveToDrive(File out, String filename, BsaFile file) throws IOException, DataFormatException {
		byte[] b = getFileBytes(filename);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		FileOutputStream os = new FileOutputStream(out);
		bos.writeBytes(b);
		bos.writeTo(os);
		bos.close();
		os.close();
	}
}
