package dev.gorillacraft.link;

/**
 * Wire protocol shared with the Gorilla Tag plugin (see protocol/PROTOCOL.md, mirrored in
 * gorillatag/src/Proto.cs). Frames are [u32 length][u8 type][payload], little-endian, where
 * length counts the type byte and the payload.
 */
public final class Proto {
	public static final int VERSION = 9;
	public static final int MAX_FRAME = 64 << 20;

	// Gorilla Tag → Minecraft
	public static final int GT_HELLO = 0x01;
	public static final int GT_POSE = 0x02;
	public static final int GT_MINE = 0x03;
	public static final int GT_USE = 0x04;
	public static final int GT_SLOT = 0x05;
	public static final int GT_MODEL = 0x0E;
	public static final int GT_BONES = 0x0F;

	// Minecraft → Gorilla Tag
	public static final int MC_WELCOME = 0x81;
	public static final int MC_WORLD = 0x82;
	public static final int MC_ATLAS = 0x83;
	public static final int MC_SECTION = 0x84;
	public static final int MC_SECTION_REMOVE = 0x85;
	public static final int MC_TELEPORT = 0x86;
	public static final int MC_HOTBAR = 0x87;
	public static final int MC_STATUS = 0x88;
	public static final int MC_TRACKING = 0x8E;

	/** GT_HELLO flags: what Gorilla Tag wants. */
	public static final int WANT_BLOCK_MESHES = 1;

	/** Block shape entry flag: the block's outline is the full unit cube. */
	public static final int SHAPE_FULL_CUBE = 0xFF;

	private Proto() {
	}
}
