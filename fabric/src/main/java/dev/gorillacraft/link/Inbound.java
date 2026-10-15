package dev.gorillacraft.link;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** One received frame. Read it in order with the typed getters. */
public final class Inbound {
	public final int type;
	public final int connection;
	private final ByteBuffer buf;

	Inbound(int type, byte[] payload, int connection) {
		this.type = type;
		this.connection = connection;
		this.buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
	}

	public int u8() {
		return buf.get() & 0xFF;
	}

	public int i32() {
		return buf.getInt();
	}

	public float f32() {
		return buf.getFloat();
	}

	public double f64() {
		return buf.getDouble();
	}

	public String str() {
		int len = buf.getShort() & 0xFFFF;
		byte[] b = new byte[len];
		buf.get(b);
		return new String(b, StandardCharsets.UTF_8);
	}
}
