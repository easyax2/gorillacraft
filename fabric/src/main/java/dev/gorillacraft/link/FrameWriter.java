package dev.gorillacraft.link;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Growable little-endian buffer that produces one complete frame. */
public final class FrameWriter {
	private ByteBuffer buf;

	public FrameWriter(int type) {
		this(type, 256);
	}

	public FrameWriter(int type, int capacity) {
		buf = ByteBuffer.allocate(Math.max(capacity, 16)).order(ByteOrder.LITTLE_ENDIAN);
		buf.putInt(0); // length, patched in finish()
		buf.put((byte) type);
	}

	private void ensure(int more) {
		if (buf.remaining() < more) {
			int cap = Math.max(buf.capacity() * 2, buf.position() + more);
			ByteBuffer bigger = ByteBuffer.allocate(cap).order(ByteOrder.LITTLE_ENDIAN);
			buf.flip();
			bigger.put(buf);
			buf = bigger;
		}
	}

	public FrameWriter u8(int v) {
		ensure(1);
		buf.put((byte) v);
		return this;
	}

	public FrameWriter u16(int v) {
		ensure(2);
		buf.putShort((short) v);
		return this;
	}

	public FrameWriter i32(int v) {
		ensure(4);
		buf.putInt(v);
		return this;
	}

	public FrameWriter f32(float v) {
		ensure(4);
		buf.putFloat(v);
		return this;
	}

	public FrameWriter f64(double v) {
		ensure(8);
		buf.putDouble(v);
		return this;
	}

	public FrameWriter bytes(byte[] b) {
		ensure(b.length);
		buf.put(b);
		return this;
	}

	public FrameWriter str(String s) {
		byte[] b = s.getBytes(StandardCharsets.UTF_8);
		int len = Math.min(b.length, 0xFFFF);
		u16(len);
		ensure(len);
		buf.put(b, 0, len);
		return this;
	}

	/** Current write offset, for patching counts written before their contents. */
	public int position() {
		return buf.position();
	}

	public void patchI32(int at, int v) {
		buf.putInt(at, v);
	}

	public byte[] finish() {
		buf.putInt(0, buf.position() - 4);
		return Arrays.copyOf(buf.array(), buf.position());
	}
}
