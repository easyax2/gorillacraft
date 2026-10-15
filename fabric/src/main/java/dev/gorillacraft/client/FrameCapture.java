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
import java.nio.ByteOrder;
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
 */
final class FrameCapture {
	/** Where sky pixels are put, in blocks from the camera. */
	private static final float SKY_DISTANCE = 600f;

	private final LinkServer link;
	private final ClientConfig cfg;

	private GpuBuffer colorBuffer;
	private float[] points = new float[0];
	private int gridW, gridH;
	private int bufferWidth;
	private int bufferHeight;
	private boolean busy;
	private boolean failed;
	private int frameId;
	private static final int RAY_THREADS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 4));
	private final java.util.concurrent.ExecutorService rayThread = java.util.concurrent.Executors.newFixedThreadPool(RAY_THREADS + 1, r -> {
		Thread t = new Thread(r, "GorillaCraft overlay rays");
		t.setDaemon(true);
		return t;
	});
	private long rayNanos;
	private int rayFrames;

	FrameCapture(LinkServer link, ClientConfig cfg) {
		this.link = link;
		this.cfg = cfg;
	}

	/** @return whether a frame was started (false: still busy with the last one, or nothing to grab). */
	boolean capture(Minecraft mc, int eye) {
		if (busy || failed || link.pendingBytes() > 2L << 20) {
			return false; // one frame in flight at a time; skip frames rather than queue them up
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
		if (colorBuffer == null || w != bufferWidth || h != bufferHeight) {
			closeBuffers();
			int usage = GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ;
			colorBuffer = device.createBuffer(() -> "GorillaCraft frame color", usage, (long) w * h * 4);
			bufferWidth = w;
			bufferHeight = h;
		}

		// Snapshot the camera this frame was drawn with.
		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 pos = camera.position();
		Matrix4f proj = ((CameraAccess) camera).gorillacraft$projection().getMatrix(new Matrix4f());
		Matrix4f view = camera.getViewRotationMatrix(new Matrix4f());
		Matrix4f inverse = proj.mul(view, new Matrix4f()).invert();
		boolean zeroToOne = device.getDeviceInfo().isZZeroToOne();
		float maxDistance = mc.options.getEffectiveRenderDistance() * 16;

		// The rays run on their own thread (reading blocks only) while the GPU copies the picture;
		// the frame goes out once both are done.
		busy = true;
		java.util.concurrent.CompletableFuture<Void> rays = java.util.concurrent.CompletableFuture.runAsync(() -> {
			long t0 = System.nanoTime();
			castRays(level, pos, inverse, zeroToOne, w, h, maxDistance);
			rayNanos += System.nanoTime() - t0;
			if (++rayFrames == 300) {
				GorillaCraft.LOG.info("Overlay: {}x{} frames, {}x{} depth rays take {} ms", w, h, gridW, gridH, rayNanos / rayFrames / 1_000_000);
				rayNanos = 0;
				rayFrames = 0;
			}
		}, rayThread);
		try {
			device.createCommandEncoder().copyTextureToBuffer(color, colorBuffer, 0,
					() -> rays.whenComplete((v, e) -> mc.execute(() -> {
						if (e != null) {
							GorillaCraft.LOG.error("Overlay rays failed", e);
							busy = false;
						} else {
							finish(w, h, pos, eye);
						}
					})), 0);
		} catch (RuntimeException e) {
			GorillaCraft.LOG.error("Can't read Minecraft's frame back for the overlay", e);
			failed = true;
			busy = false;
			return false;
		}
		return true;
	}

	private void finish(int w, int h, Vec3 cam, int eye) {
		try (GpuBufferSlice.MappedView cv = colorBuffer.map(true, false)) {
			ByteBuffer colors = cv.data();

			// Picture: downscaled to at most overlayWidth, rows top to bottom, RGB.
			int step = Math.max(1, (w + cfg.overlayWidth - 1) / cfg.overlayWidth);
			int iw = w / step;
			int ih = h / step;
			FrameWriter out = new FrameWriter(Proto.MC_FRAME, iw * ih * 3 + points.length * 4 + 64);
			out.i32(++frameId).u8(eye).f64(cam.x).f64(cam.y).f64(cam.z).i32(iw).i32(ih);
			byte[] row = new byte[iw * 3];
			for (int y = 0; y < ih; y++) {
				int src = h - 1 - y * step; // GPU rows start at the bottom
				for (int x = 0; x < iw; x++) {
					int o = (src * w + x * step) * 4;
					row[x * 3] = colors.get(o);
					row[x * 3 + 1] = colors.get(o + 1);
					row[x * 3 + 2] = colors.get(o + 2);
				}
				out.bytes(row);
			}

			out.i32(gridW).i32(gridH);
			for (float f : points) {
				out.f32(f);
			}
			link.send(out.finish());
		} catch (RuntimeException e) {
			GorillaCraft.LOG.error("Reading Minecraft's frame back failed", e);
			failed = true;
		} finally {
			busy = false;
		}
	}

	void reset() {
		failed = false;
	}

	/**
	 * One ray per grid point, from the camera through that spot of the picture, against the
	 * blocks' visual shapes and fluids. Points are relative to the camera, Minecraft axes; misses
	 * (sky) go far away.
	 */
	private void castRays(ClientLevel level, Vec3 cam, Matrix4f inverse, boolean zeroToOne, int w, int h, float maxDistance) {
		int gw = Math.max(2, cfg.overlayGrid);
		int gh = Math.max(2, Math.round(gw * (float) h / w));
		if (points.length != gw * gh * 3) {
			points = new float[gw * gh * 3];
		}
		gridW = gw;
		gridH = gh;
		float ndcZ = zeroToOne ? 0.5f : 0f;
		// Rows are split between a few threads; they only read blocks.
		var parts = new java.util.ArrayList<java.util.concurrent.Future<?>>();
		for (int t = 0; t < RAY_THREADS; t++) {
			int from = gh * t / RAY_THREADS, to = gh * (t + 1) / RAY_THREADS;
			Runnable rows = () -> castRows(level, cam, inverse, ndcZ, gw, gh, from, to, maxDistance);
			if (t == RAY_THREADS - 1) {
				rows.run();
			} else {
				parts.add(rayThread.submit(rows));
			}
		}
		for (var f : parts) {
			try {
				f.get();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void castRows(ClientLevel level, Vec3 cam, Matrix4f inverse, float ndcZ, int gw, int gh, int fromRow, int toRow, float maxDistance) {
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
				Vec3 end = cam.add(dx * maxDistance, dy * maxDistance, dz * maxDistance);
				BlockHitResult hit = level.clip(new ClipContext(cam, end, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, context));
				double dist = hit.getType() == HitResult.Type.MISS ? SKY_DISTANCE : hit.getLocation().distanceTo(cam);
				points[o++] = (float) (dx * dist);
				points[o++] = (float) (dy * dist);
				points[o++] = (float) (dz * dist);
			}
		}
	}

	private void closeBuffers() {
		if (colorBuffer != null) {
			colorBuffer.close();
			colorBuffer = null;
		}
	}
}
