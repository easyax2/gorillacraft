package dev.gorillacraft.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.gorillacraft.GorillaCraft;
import dev.gorillacraft.link.FrameWriter;
import dev.gorillacraft.link.LinkServer;
import dev.gorillacraft.link.Proto;
import dev.gorillacraft.mixin.client.CameraAccess;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Grabs Minecraft's picture of the world for Gorilla Tag's overlay, plus where each part of it
 * is in 3D. The picture is copied off the GPU asynchronously, the same way screenshots are taken.
 * The 3D part is a grid of rays cast from the camera through the picture into Minecraft's blocks
 * (reading the GPU depth buffer back doesn't work on 26.3's renderer). Gorilla Tag stretches the
 * picture over those points, so it sits in the world with real depth instead of being a flat
 * screen.
 *
 * Two frames can be on their way at once (one being copied/ray-cast while the other is sent),
 * so the frame rate isn't limited by the round trip.
 */
final class FrameCapture {
	/** Where sky pixels are put, in blocks from the camera (inside Gorilla Tag's far plane). */
	private static final float SKY_DISTANCE = 300f;
	private static final int SLOTS = 2;
	private static final int RAY_THREADS = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));

	private final LinkServer link;
	private final ClientConfig cfg;
	private final java.util.function.Supplier<dev.gorillacraft.link.SharedFrames> shared;
	private final Slot[] slots = new Slot[SLOTS];
	private final ExecutorService rayPool = Executors.newFixedThreadPool(RAY_THREADS + SLOTS, r -> {
		Thread t = new Thread(r, "GorillaCraft overlay rays");
		t.setDaemon(true);
		return t;
	});
	private boolean failed;
	private int frameId;
	private long rayNanos;
	private int rayFrames;

	/** One frame on its way: its GPU buffer and its grid of points. */
	private static final class Slot {
		GpuBuffer buffer;
		int width, height;
		float[] points = new float[0];
		int gridW, gridH;
		boolean busy;
	}

	FrameCapture(LinkServer link, ClientConfig cfg, java.util.function.Supplier<dev.gorillacraft.link.SharedFrames> shared) {
		this.link = link;
		this.cfg = cfg;
		this.shared = shared;
		for (int i = 0; i < SLOTS; i++) {
			slots[i] = new Slot();
		}
	}

	/** @return whether a frame was started (false: both slots busy, link backed up, or nothing to grab). */
	boolean capture(Minecraft mc, int eye) {
		if (failed || shared.get() == null && link.pendingBytes() > 24L << 20) {
			return false;
		}
		Slot slot = null;
		for (Slot s : slots) {
			if (!s.busy) {
				slot = s;
				break;
			}
		}
		if (slot == null) {
			return false;
		}
		RenderTarget target = mc.gameRenderer.mainRenderTarget();
		GpuTexture color = target.getColorTexture();
		ClientLevel level = mc.level;
		if (color == null || level == null) {
			return false;
		}
		int w = color.getWidth(0);
		int h = color.getHeight(0);
		GpuDevice device = RenderSystem.getDevice();
		if (slot.buffer == null || w != slot.width || h != slot.height) {
			if (slot.buffer != null) {
				slot.buffer.close();
			}
			slot.buffer = device.createBuffer(() -> "GorillaCraft frame color", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long) w * h * 4);
			slot.width = w;
			slot.height = h;
		}

		// Snapshot the camera this frame was drawn with.
		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 pos = camera.position();
		Matrix4f proj = ((CameraAccess) camera).gorillacraft$projection().getMatrix(new Matrix4f());
		Matrix4f view = camera.getViewRotationMatrix(new Matrix4f());
		Matrix4f inverse = proj.mul(view, new Matrix4f()).invert();
		boolean zeroToOne = device.getDeviceInfo().isZZeroToOne();
		float maxDistance = mc.options.getEffectiveRenderDistance() * 16;

		// Rays on worker threads (reading blocks only) while the GPU copies the picture; the frame
		// goes out once both are done.
		Slot s = slot;
		s.busy = true;
		CompletableFuture<Void> rays = CompletableFuture.runAsync(() -> {
			long t0 = System.nanoTime();
			castRays(s, level, pos, inverse, zeroToOne, w, h, maxDistance);
			synchronized (this) {
				rayNanos += System.nanoTime() - t0;
				if (++rayFrames == 600) {
					GorillaCraft.LOG.info("Overlay: {}x{} frames, {}x{} depth rays take {} ms on {} threads", w, h, s.gridW, s.gridH,
							rayNanos / rayFrames / 1_000_000, RAY_THREADS);
					rayNanos = 0;
					rayFrames = 0;
				}
			}
		}, rayPool);
		try {
			device.createCommandEncoder().copyTextureToBuffer(color, s.buffer, 0,
					() -> rays.whenComplete((v, e) -> mc.execute(() -> {
						if (e != null) {
							GorillaCraft.LOG.error("Overlay rays failed", e);
							s.busy = false;
						} else {
							finish(s, pos, eye);
						}
					})), 0);
		} catch (RuntimeException e) {
			GorillaCraft.LOG.error("Can't read Minecraft's frame back for the overlay", e);
			failed = true;
			s.busy = false;
			return false;
		}
		return true;
	}

	private void finish(Slot s, Vec3 cam, int eye) {
		try (GpuBufferSlice.MappedView view = s.buffer.map(true, false)) {
			int w = s.width, h = s.height;
			var shm = shared.get();
			if (shm != null && dev.gorillacraft.link.SharedFrames.fits(w, h, s.gridW * s.gridH)) {
				// SkyCraft's way: one copy straight into shared memory, rows as the GPU left them.
				shm.publish(Math.min(eye, 2), eye, ++frameId, cam.x, cam.y, cam.z, w, h,
						java.lang.foreign.MemorySegment.ofBuffer(view.data()), s.gridW, s.gridH, s.points);
				return;
			}
			int step = Math.max(1, (w + cfg.overlayWidth - 1) / cfg.overlayWidth);
			int iw = w / step, ih = h / step;
			FrameWriter out = new FrameWriter(Proto.MC_FRAME, iw * ih * 4 + s.points.length * 4 + 64);
			out.i32(++frameId).u8(eye).f64(cam.x).f64(cam.y).f64(cam.z).i32(iw).i32(ih);
			writeRows(out, view.data(), w, h, step);
			out.i32(s.gridW).i32(s.gridH);
			for (float f : s.points) {
				out.f32(f);
			}
			link.send(out.finish());
		} catch (RuntimeException e) {
			GorillaCraft.LOG.error("Reading Minecraft's frame back failed", e);
			failed = true;
		} finally {
			s.busy = false;
		}
	}

	/** RGBA rows, top to bottom (the GPU's start at the bottom), downscaled by {@code step}. */
	static void writeRows(FrameWriter out, ByteBuffer pixels, int w, int h, int step) {
		int iw = w / step, ih = h / step;
		byte[] row = new byte[iw * 4];
		for (int y = 0; y < ih; y++) {
			int src = (h - 1 - y * step) * w * 4;
			if (step == 1) {
				pixels.get(src, row, 0, row.length);
			} else {
				for (int x = 0; x < iw; x++) {
					pixels.get(src + x * step * 4, row, x * 4, 4);
				}
			}
			out.bytes(row);
		}
	}

	/**
	 * One ray per grid point, from the camera through that spot of the picture, against the
	 * blocks' visual shapes and fluids. Points are relative to the camera, Minecraft axes; misses
	 * (sky) go far away.
	 */
	private void castRays(Slot s, ClientLevel level, Vec3 cam, Matrix4f inverse, boolean zeroToOne, int w, int h, float maxDistance) {
		int gw = Math.max(2, cfg.overlayGrid);
		int gh = Math.max(2, Math.round(gw * (float) h / w));
		if (s.points.length != gw * gh * 3) {
			s.points = new float[gw * gh * 3];
		}
		s.gridW = gw;
		s.gridH = gh;
		float ndcZ = zeroToOne ? 0.5f : 0f;
		float reach = Math.min(maxDistance, SKY_DISTANCE);
		// Rows are split between threads; they only read blocks.
		List<Future<?>> parts = new ArrayList<>();
		for (int t = 0; t < RAY_THREADS; t++) {
			int from = gh * t / RAY_THREADS, to = gh * (t + 1) / RAY_THREADS;
			Runnable rows = () -> castRows(s.points, level, cam, inverse, ndcZ, gw, gh, from, to, reach);
			if (t == RAY_THREADS - 1) {
				rows.run();
			} else {
				parts.add(rayPool.submit(rows));
			}
		}
		for (Future<?> f : parts) {
			try {
				f.get();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static void castRows(float[] points, ClientLevel level, Vec3 cam, Matrix4f inverse, float ndcZ, int gw, int gh, int fromRow, int toRow,
			float reach) {
		Vector4f p = new Vector4f();
		CollisionContext context = CollisionContext.empty();
		int o = fromRow * gw * 3;
		for (int gy = fromRow; gy < toRow; gy++) {
			float ndcY = 1f - 2f * gy / (gh - 1); // top row first
			for (int gx = 0; gx < gw; gx++) {
				float ndcX = 2f * gx / (gw - 1) - 1f;
				inverse.transform(p.set(ndcX, ndcY, ndcZ, 1f));
				double dx = p.x / p.w, dy = p.y / p.w, dz = p.z / p.w;
				double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
				dx /= len;
				dy /= len;
				dz /= len;
				Vec3 end = cam.add(dx * reach, dy * reach, dz * reach);
				BlockHitResult hit = level.clip(new ClipContext(cam, end, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, context));
				double dist = hit.getType() == HitResult.Type.MISS ? SKY_DISTANCE : hit.getLocation().distanceTo(cam);
				points[o++] = (float) (dx * dist);
				points[o++] = (float) (dy * dist);
				points[o++] = (float) (dz * dist);
			}
		}
	}

	void reset() {
		failed = false;
	}
}
