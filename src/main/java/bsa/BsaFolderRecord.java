package bsa;

import java.nio.*;
import java.util.*;

public class BsaFolderRecord {
	// This is read
	// hash
	String name;
	Integer fileCount;
	Integer padding;
	Long offset;
	Integer nameLength;
	// Not Read
	List<BsaFileRecord> files = new ArrayList<BsaFileRecord>();

	public BsaFolderRecord() {
	}

	public BsaFolderRecord(ByteBuffer buffer) {
		// Ignorable hashcode
		buffer.getLong();
		// File count
		this.fileCount = buffer.getInt();
		// Padding
		this.padding = buffer.getInt();
		// Offset
		this.offset = buffer.getLong();
	}

	public void setNameLength(byte b) {
		this.nameLength = (int) b;
	}

	@Override
	public String toString() {
		return name;
	}
}
