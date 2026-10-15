package dev.gorillacraft.link;

import dev.gorillacraft.GorillaCraft;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Frames to Gorilla Tag through shared memory, the way SkyCraft hands its overlay to Skyrim: a
 * lock-free triple buffer per picture stream, filled with one memcpy straight from the mapped GPU
 * readback (rows stay bottom-up, which is also the order Unity loads textures in).
 *
 * SkyCraft uses a named Windows mapping; here Minecraft is often a Linux program while Gorilla
 * Tag runs under Proton, so the memory is a file both sides map: in /dev/shm on Linux (RAM), in
 * the temp folder on Windows.
 *
 * Layout (little-endian), mirrored in gorillatag/src/SharedFrames.cs:
 *   0x0000 header: u32 magic "GCFB", u32 version, u32 buffers, u32 slots, u64 slotBytes, u64 dataOffset
 *   0x0100 + b*0x100: buffer b: u32 state (bits 0-1 middle slot, bit 2 dirty), u32 pad, u64 published,
 *                     then 3 slot headers of 0x40 at +0x40: u32 width, height, gridW, gridH, flags,
 *                     eye, u64 frameId, f64 camX, camY, camZ
 *   dataOffset + (b*3 + s)*slotBytes: pixels (width*height*4 RGBA, bottom-up), then grid points
 *                     (gridW*gridH*3 f32, rows top-down, relative to the camera, Minecraft axes)
 * Writer: fills its private back slot, then back = xchg(state, back | DIRTY) & 3.
 * Reader: if state & DIRTY: front = xchg(state, front) & 3, then reads slot front.
 */
public final class SharedFrames implements AutoCloseable {
	public static final int MAGIC = 0x42464347; // "GCFB"
	public static final int VERSION = 1;
	public static final int BUFFERS = 4; // left eye, right eye, centre, GUI panel
	public static final int GUI = 3;
	public static final int SLOTS = 3;
	public static final int MAX_WIDTH = 2560;
	public static final int MAX_HEIGHT = 1600;
	public static final int MAX_GRID_POINTS = 320 * 200;
	public static final int DIRTY = 4;
	public static final int FLAG_BOTTOM_UP = 1;

	private static final long PIXEL_BYTES = (long) MAX_WIDTH * MAX_HEIGHT * 4;
	private static final long SLOT_BYTES = align(PIXEL_BYTES + (long) MAX_GRID_POINTS * 12, 4096);
	private static final long DATA_OFFSET = 0x2000;
	private static final long SIZE = DATA_OFFSET + SLOT_BYTES * BUFFERS * SLOTS;
	private static final VarHandle INT = ValueLayout.JAVA_INT.varHandle();

	private final Path path;
	private final Arena arena;
	private final MemorySegment shm;
	private final int[] back = new int[BUFFERS];

	private SharedFrames(Path path, Arena arena, MemorySegment shm) {
		this.path = path;
		this.arena = arena;
		this.shm = shm;
		for (int b = 0; b < BUFFERS; b++) {
			back[b] = 0;
			shm.set(ValueLayout.JAVA_INT, ctl(b), 1); // middle = slot 1; the reader starts with slot 2
		}
	}

	/** Creates the shared file for this link port. */
	public static SharedFrames create(int port) throws IOException {
		Path dir = Files.isDirectory(Path.of("/dev/shm")) ? Path.of("/dev/shm") : Path.of(System.getProperty("java.io.tmpdir"));
		Path path = dir.resolve("gorillacraft-" + port + ".frames");
		Arena arena = Arena.ofShared();
		try (FileChannel ch = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
			ch.truncate(0);
			ch.write(java.nio.ByteBuffer.wrap(new byte[1]), SIZE - 1); // sparse: only pages that get written use memory
			MemorySegment shm = ch.map(FileChannel.MapMode.READ_WRITE, 0, SIZE, arena);
			shm.set(ValueLayout.JAVA_INT, 0, MAGIC);
			shm.set(ValueLayout.JAVA_INT, 4, VERSION);
			shm.set(ValueLayout.JAVA_INT, 8, BUFFERS);
			shm.set(ValueLayout.JAVA_INT, 12, SLOTS);
			shm.set(ValueLayout.JAVA_LONG, 16, SLOT_BYTES);
			shm.set(ValueLayout.JAVA_LONG, 24, DATA_OFFSET);
			GorillaCraft.LOG.info("Frames go through shared memory: {} ({} MiB, sparse)", path, SIZE >> 20);
			return new SharedFrames(path, arena, shm);
		}
	}

	public Path path() {
		return path;
	}

	public long size() {
		return SIZE;
	}

	/** Whether a picture of this size fits a slot. */
	public static boolean fits(int width, int height, int gridPoints) {
		return width <= MAX_WIDTH && height <= MAX_HEIGHT && gridPoints <= MAX_GRID_POINTS;
	}

	/**
	 * Publishes one picture (and optionally its depth grid) to buffer {@code b}.
	 *
	 * @param pixels   the mapped GPU readback, width*height*4 bytes, bottom-up
	 * @param points   grid points (gridW*gridH*3), or null
	 */
	public void publish(int b, int eye, long frameId, double camX, double camY, double camZ, int width, int height, MemorySegment pixels,
			int gridW, int gridH, float[] points) {
		int slot = back[b];
		long data = DATA_OFFSET + (b * SLOTS + slot) * SLOT_BYTES;
		long bytes = (long) width * height * 4;
		if (bytes > 0) {
			MemorySegment.copy(pixels, 0, shm, data, bytes);
		}
		if (points != null) {
			MemorySegment.copy(MemorySegment.ofArray(points), 0, shm, data + PIXEL_BYTES, (long) gridW * gridH * 12);
		}
		long hdr = ctl(b) + 0x40 + slot * 0x40L;
		shm.set(ValueLayout.JAVA_INT, hdr, width);
		shm.set(ValueLayout.JAVA_INT, hdr + 4, height);
		shm.set(ValueLayout.JAVA_INT, hdr + 8, points != null ? gridW : 0);
		shm.set(ValueLayout.JAVA_INT, hdr + 12, points != null ? gridH : 0);
		shm.set(ValueLayout.JAVA_INT, hdr + 16, FLAG_BOTTOM_UP);
		shm.set(ValueLayout.JAVA_INT, hdr + 20, eye);
		shm.set(ValueLayout.JAVA_LONG, hdr + 24, frameId);
		shm.set(ValueLayout.JAVA_DOUBLE, hdr + 32, camX);
		shm.set(ValueLayout.JAVA_DOUBLE, hdr + 40, camY);
		shm.set(ValueLayout.JAVA_DOUBLE, hdr + 48, camZ);
		int old = (int) INT.getAndSet(shm, ctl(b), slot | DIRTY);
		back[b] = old & 3;
		shm.set(ValueLayout.JAVA_LONG, ctl(b) + 8, shm.get(ValueLayout.JAVA_LONG, ctl(b) + 8) + 1);
	}

	private static long ctl(int b) {
		return 0x100L + b * 0x100L;
	}

	private static long align(long v, long a) {
		return (v + a - 1) / a * a;
	}

	@Override
	public void close() {
		arena.close();
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
		}
	}
}
