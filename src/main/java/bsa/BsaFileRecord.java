package bsa;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BsaFileRecord {
	// hash
	String name;
	int offset;
	int size;
	boolean compressed;
	String nameWithPath;
	int nameLength;

	public BsaFileRecord() {
	}

	public BsaFileRecord(RandomAccessFile file, int offset, boolean defaultCompressed) throws IOException {
		byte[] bytes = new byte[8 + 4 + 4];
		file.seek(offset);
		file.read(bytes, 0, bytes.length);
		ByteBuffer recordBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		// hashcode to ignore
		recordBuffer.getLong();
		this.size = recordBuffer.getInt();
		if ((this.size & (1 << 30)) != 0) {
			this.compressed = !defaultCompressed;
			this.size ^= (1 << 30);
		} else {
			this.compressed = defaultCompressed;
		}
		this.offset = recordBuffer.getInt();
	}

	public void setNameLength(byte b) {
		this.nameLength = Byte.toUnsignedInt(b);
	}

	public int getOffset() {
		return offset;
	}

	public int getSize() {
		return size;
	}

	@Override
	public String toString() {
		return name;
	}
}
