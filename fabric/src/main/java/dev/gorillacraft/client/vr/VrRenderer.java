package dev.gorillacraft.client.vr;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import dev.gorillacraft.GorillaCraft;
import dev.gorillacraft.client.ClientConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Draws Minecraft for the headset. Each frame:
 *
 * 1. Minecraft extracts the world once, with the camera at the middle of your head and a culling
 *    frustum wide enough for both eyes (CameraMixin).
 * 2. GameRenderer.render() runs once per eye into an eye-sized target, with that eye's position,
 *    rotation and (asymmetric) field of view, and no GUI. Each result is copied into that eye's
 *    swapchain image.
 * 3. The GUI alone (screens, HUD) is drawn into Minecraft's normal target on a transparent
 *    background and becomes a panel floating in front of you while a screen is open, or the
 *    hotbar on your left wrist while playing.
 *
 * Where you are in Minecraft: {@link #trackingToWorld} maps the headset's tracking space to
 * Minecraft's world. While Gorilla Tag drives the gorilla it is Gorilla Tag's rig (the gorilla's
 * physics move it); otherwise it keeps your head on the Minecraft player.
 */
public final class VrRenderer {
	public enum Pass {
		NONE, EYE, GUI
	}

	private static VrRenderer instance;

	private final XrRuntime xr;
	private final GlBlit blit = new GlBlit();
	private final VrControls controls;
	private TextureTarget eyeTarget, eyeHud;
	private Pass pass = Pass.NONE;
	private boolean eyesDrawnLastFrame;

	/** Tracking space (metres, OpenXR axes) → Minecraft world (blocks). */
	private final Matrix4d trackingToWorld = new Matrix4d();
	private final Quaterniond worldRot = new Quaterniond();
	/** Set by the session while Gorilla Tag drives: Gorilla Tag's rig in Minecraft coordinates. */
	private final Matrix4d gorillaRig = new Matrix4d();
	private boolean haveGorillaRig;

	public static VrRenderer get() {
		return instance;
	}

	public static void init(ClientConfig cfg) {
		if (cfg.vr) {
			instance = new VrRenderer(cfg);
		}
	}

	private VrRenderer(ClientConfig cfg) {
		xr = new XrRuntime((float) cfg.vrResolution);
		controls = new VrControls(this, xr);
	}

	public XrRuntime xr() {
		return xr;
	}

	/** True while this frame goes to the headset (the headset is on someone's head). */
	public boolean active() {
		return xr.shouldRender();
	}

	/** True between the runtime's begin and end of frame: VrRenderer.render must run (and end it). */
	public boolean inFrame() {
		return xr.frameBegun();
	}

	private int eyeIndex;
	private Vec3 extractionPos = Vec3.ZERO;

	/**
	 * During an eye pass: where the world was extracted from (the middle of the head). Minecraft's
	 * section grid, compiling and visibility use this for both eyes, so the grid can't move
	 * between the eyes (they're 6 cm apart and can sit on two sides of a section boundary).
	 */
	public Vec3 extractionPos() {
		return extractionPos;
	}

	/** True while the right eye is drawn (after the left, which did the shared work). */
	public boolean secondEye() {
		return pass == Pass.EYE && eyeIndex == 1;
	}

	/** True during the eye passes (no GUI, no first-person hand). */
	public boolean eyePass() {
		return pass == Pass.EYE;
	}

	private final Vector3f eyeOffset = new Vector3f();

	/** During an eye pass: the extraction camera (head middle) minus this eye, world axes. */
	public Vector3f eyeOffset() {
		return eyeOffset;
	}

	// Frame timing, logged every 10 seconds.
	private long statsStart = System.nanoTime(), waitNanos, renderNanos;
	private int statsFrames;

	public VrControls controls() {
		return controls;
	}

	/** Gorilla Tag's rig (tracking → Minecraft), from each pose; null when Gorilla Tag isn't driving. */
	public void setGorillaRig(double[] m) {
		if (m == null) {
			haveGorillaRig = false;
			return;
		}
		gorillaRig.set(m[0], m[4], m[8], 0, m[1], m[5], m[9], 0, m[2], m[6], m[10], 0, m[3], m[7], m[11], 1);
		haveGorillaRig = true;
	}

	// ---------------------------------------------------------------- frame

	private Boolean openGl;
	private Boolean savedVsync;

	/** Start of Minecraft's frame (before the camera moves). */
	public void beginFrame(Minecraft mc) {
		if (openGl == null) {
			String backend = RenderSystem.getDevice().getDeviceInfo().backendName();
			openGl = backend.toLowerCase(java.util.Locale.ROOT).contains("opengl");
			if (!openGl) {
				GorillaCraft.LOG.warn("VR needs Minecraft's OpenGL renderer, not {} (Options → Video Settings → Graphics API)", backend);
			}
		}
		if (!openGl) {
			return;
		}
		long t0 = System.nanoTime();
		xr.beginFrame();
		waitNanos += System.nanoTime() - t0;
		// Gorilla Tag's newest rig and bones, straight after the wait: as fresh as they get.
		var gorillaSession = dev.gorillacraft.client.GorillaCraftClient.session();
		if (gorillaSession != null) {
			gorillaSession.frame(mc);
		}
		// The headset sets the pace: vsync to the monitor would hold every frame back.
		if (xr.sessionRunning()) {
			mc.options.pauseOnLostFocus = false; // you're in the headset, not at the window
		}
		if (xr.sessionRunning() && savedVsync == null) {
			savedVsync = mc.options.enableVsync().get();
			mc.options.enableVsync().set(false);
		} else if (!xr.sessionRunning() && savedVsync != null) {
			mc.options.enableVsync().set(savedVsync);
			savedVsync = null;
		}
		if (!xr.frameBegun()) {
			return;
		}
		updateOrigin(mc);
		controls.update(mc);
	}

	private void updateOrigin(Minecraft mc) {
		var session = dev.gorillacraft.client.GorillaCraftClient.session();
		if (haveGorillaRig && session != null && session.driving()) {
			trackingToWorld.set(gorillaRig);
		} else {
			// No gorilla: your head stays on the Minecraft player (no turning, real height above its feet).
			Vector3f head = headTracking(new Vector3f());
			Vec3 feet = mc.player != null ? mc.player.position() : Vec3.ZERO;
			double floor = xr.stageSpace() ? feet.y : feet.y + (mc.player != null ? mc.player.getEyeHeight() : 1.62);
			trackingToWorld.translation(feet.x - head.x, floor, feet.z - head.z);
		}
		trackingToWorld.getNormalizedRotation(worldRot);
	}

	/** Middle of the two eyes, tracking space. */
	public Vector3f headTracking(Vector3f dest) {
		return dest.set(xr.view(0).pos).add(xr.view(1).pos).mul(0.5f);
	}

	public Vector3d toWorld(Vector3f tracking, Vector3d dest) {
		return trackingToWorld.transformPosition(dest.set(tracking.x, tracking.y, tracking.z));
	}

	public Quaternionf toWorld(Quaternionf tracking, Quaternionf dest) {
		Quaterniond q = new Quaterniond(worldRot).mul(tracking.x, tracking.y, tracking.z, tracking.w);
		return dest.set((float) q.x, (float) q.y, (float) q.z, (float) q.w);
	}

	public double worldScale() {
		return trackingToWorld.getScale(new Vector3d()).x;
	}

	/** Where the camera is for Minecraft's extraction and sounds: middle of the head. */
	public Vec3 headWorld() {
		Vector3d p = toWorld(headTracking(new Vector3f()), new Vector3d());
		return new Vec3(p.x, p.y, p.z);
	}

	public Quaternionf headWorldRotation() {
		return toWorld(xr.view(0).rot, new Quaternionf());
	}

	/**
	 * A frustum that holds both eyes' views, from the middle of the head, for culling during
	 * extraction (the eye passes reuse what was extracted).
	 */
	public Frustum cullFrustum(float depthFar, boolean zZeroToOne) {
		XrRuntime.View l = xr.view(0), r = xr.view(1);
		float margin = (float) Math.toRadians(4);
		float left = Math.min(l.left, r.left) - margin, right = Math.max(l.right, r.right) + margin;
		float up = Math.max(l.up, r.up) + margin, down = Math.min(l.down, r.down) - margin;
		float n = 0.05f;
		Matrix4f proj = new Matrix4f().setFrustum((float) Math.tan(left) * n, (float) Math.tan(right) * n, (float) Math.tan(down) * n,
				(float) Math.tan(up) * n, n, depthFar, zZeroToOne);
		Matrix4f view = new Matrix4f().rotation(headWorldRotation().conjugate());
		Frustum f = new Frustum(view, proj);
		Vec3 head = headWorld();
		f.prepare(head.x, head.y, head.z);
		return f;
	}

	/**
	 * Replaces GameRenderer.render() for a VR frame: both eyes, then the GUI, then hands
	 * everything to the runtime.
	 */
	public void render(Minecraft mc, GameRenderer gameRenderer) {
		long t0 = System.nanoTime();
		try {
			renderFrame(mc, gameRenderer);
		} finally {
			renderNanos += System.nanoTime() - t0;
			statsFrames++;
			long now = System.nanoTime();
			if (now - statsStart > 10_000_000_000L) {
				double s = (now - statsStart) / 1e9;
				var session = dev.gorillacraft.client.GorillaCraftClient.session();
				GorillaCraft.LOG.info("VR: {} fps, drawing {} ms/frame, waiting for the headset {} ms/frame, Gorilla Tag {} poses/s",
						Math.round(statsFrames / s), String.format("%.1f", renderNanos / 1e6 / statsFrames),
						String.format("%.1f", waitNanos / 1e6 / statsFrames), session != null ? Math.round(session.takePoseCount() / s) : 0);
				statsStart = now;
				statsFrames = 0;
				waitNanos = renderNanos = 0;
			}
		}
	}

	private void renderFrame(Minecraft mc, GameRenderer gameRenderer) {
		GameRenderState state = gameRenderer.gameRenderState();
		state.framerateLimit = 260; // the runtime paces frames (xrWaitFrame), not Minecraft's limiter
		if (!xr.shouldRender()) {
			// Nobody's wearing the headset: plain Minecraft on the monitor (and its chunks keep building).
			eyesDrawnLastFrame = false;
			gameRenderer.render();
			xr.endFrame(false, List.of());
			return;
		}
		boolean eyes = state.shouldRenderLevel;
		VrGameRenderer vgr = (VrGameRenderer) gameRenderer;
		try {
			if (eyes) {
				renderEyes(gameRenderer, vgr, state);
			}
			pass = Pass.GUI;
			vgr.gorillacraft$renderGuiOnly(!eyes);
		} finally {
			pass = Pass.NONE;
		}
		debugDump("gui", gameRenderer.mainRenderTarget());
		dumpFrame++;
		List<XrRuntime.Quad> quads = new ArrayList<>();
		copyGui(mc, gameRenderer, quads);
		eyesDrawnLastFrame = eyes;
		xr.endFrame(eyes, quads);
	}

	private void renderEyes(GameRenderer gameRenderer, VrGameRenderer vgr, GameRenderState state) {
		XrRuntime.Swapchain sc0 = xr.eye(0);
		ensureEyeTargets(sc0.width, sc0.height);
		RenderTarget main = gameRenderer.mainRenderTarget();
		RenderTarget hud = vgr.gorillacraft$hud3DTarget();
		int winW = state.windowRenderState.width, winH = state.windowRenderState.height;
		CameraRenderState cam = state.levelRenderState.cameraRenderState;
		Vec3 headPos = cam.pos;
		extractionPos = headPos;
		Quaternionf headRot = new Quaternionf(cam.orientation);
		Matrix4f headView = new Matrix4f(cam.viewRotationMatrix);
		Matrix4f headProj = new Matrix4f(cam.projectionMatrix);
		vgr.gorillacraft$setTargets(eyeTarget, eyeHud);
		state.windowRenderState.width = eyeTarget.width;
		state.windowRenderState.height = eyeTarget.height;
		pass = Pass.EYE;
		try {
			boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
			for (int e = 0; e < 2; e++) {
				eyeIndex = e;
				XrRuntime.View v = xr.view(e);
				Vector3d p = toWorld(v.pos, new Vector3d());
				cam.pos = new Vec3(p.x, p.y, p.z);
				eyeOffset.set((float) (headPos.x - p.x), (float) (headPos.y - p.y), (float) (headPos.z - p.z));
				toWorld(v.rot, cam.orientation);
				cam.viewRotationMatrix.rotation(new Quaternionf(cam.orientation).conjugate());
				// Reversed depth like Minecraft's own projection: near and far swapped.
				float n = cam.depthFar, f = 0.05f;
				cam.projectionMatrix.setFrustum((float) Math.tan(v.left) * n, (float) Math.tan(v.right) * n, (float) Math.tan(v.down) * n,
						(float) Math.tan(v.up) * n, n, f, zZeroToOne);
				gameRenderer.render();
				debugDump("eye" + e, eyeTarget);
				XrRuntime.Swapchain sc = xr.eye(e);
				int img = xr.acquire(sc);
				int src = ((GlTexture) eyeTarget.getColorTexture()).glId();
				blit.blit(src, 0, 0, eyeTarget.width, eyeTarget.height, img, 0, 0, sc.width, sc.height, false);
				xr.release(sc);
			}
		} finally {
			vgr.gorillacraft$setTargets(main, hud);
			state.windowRenderState.width = winW;
			state.windowRenderState.height = winH;
			cam.pos = headPos;
			cam.orientation.set(headRot);
			cam.viewRotationMatrix.set(headView);
			cam.projectionMatrix.set(headProj);
		}
	}

	private static final String DUMP = System.getenv("GORILLACRAFT_VR_DUMP");
	private int dumpFrame;

	/** Debugging: GORILLACRAFT_VR_DUMP=N saves both eyes and the GUI as PNGs on frame N (in the game folder). */
	private void debugDump(String name, RenderTarget target) {
		if (DUMP == null || dumpFrame != Integer.parseInt(DUMP)) {
			return;
		}
		int w = target.width, h = target.height;
		java.nio.ByteBuffer buf = org.lwjgl.system.MemoryUtil.memAlloc(w * h * 4);
		try {
			GL45C_getTextureImage(((GlTexture) target.getColorTexture()).glId(), buf);
			try (com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(w, h, false)) {
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int i = ((h - 1 - y) * w + x) * 4;
						int r = buf.get(i) & 255, g = buf.get(i + 1) & 255, b = buf.get(i + 2) & 255, a = buf.get(i + 3) & 255;
						img.setPixel(x, y, a << 24 | r << 16 | g << 8 | b);
					}
				}
				java.nio.file.Path out = Minecraft.getInstance().gameDirectory.toPath().resolve("vr-" + name + ".png");
				img.writeToFile(out);
				GorillaCraft.LOG.info("VR debug: wrote {}", out);
			}
		} catch (Exception ex) {
			GorillaCraft.LOG.error("VR debug dump failed", ex);
		} finally {
			org.lwjgl.system.MemoryUtil.memFree(buf);
		}
	}

	private static void GL45C_getTextureImage(int tex, java.nio.ByteBuffer buf) {
		org.lwjgl.opengl.GL45C.glGetTextureImage(tex, 0, org.lwjgl.opengl.GL11C.GL_RGBA, org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE, buf);
	}

	private void ensureEyeTargets(int w, int h) {
		if (eyeTarget == null || eyeTarget.width != w || eyeTarget.height != h) {
			if (eyeTarget != null) {
				eyeTarget.destroyBuffers();
				eyeHud.destroyBuffers();
			}
			eyeTarget = new TextureTarget("gorillacraft_eye", w, h, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
			eyeHud = new TextureTarget("gorillacraft_eye_hud", w, h, null, GpuFormat.D32_FLOAT);
			GorillaCraft.LOG.info("VR eye targets: {}x{}", w, h);
		}
	}

	/** The GUI (drawn into Minecraft's main target on transparent black) onto the panel or the wrist. */
	private void copyGui(Minecraft mc, GameRenderer gameRenderer, List<XrRuntime.Quad> quads) {
		RenderTarget main = gameRenderer.mainRenderTarget();
		int src = ((GlTexture) main.getColorTexture()).glId();
		int w = main.width, h = main.height;
		boolean screen = mc.gui.screen() != null || mc.level == null || mc.player == null;
		if (screen) {
			XrRuntime.Swapchain gui = xr.guiSized(w, h);
			int img = xr.acquire(gui);
			blit.blit(src, 0, 0, w, h, img, 0, 0, w, h, false);
			xr.release(gui);
			quads.addAll(controls.panelQuads(gui, (float) h / w));
		} else if (!mc.gui.hud.isHidden()) {
			// The hotbar (and hearts, food, XP above it): the bottom middle of the screen.
			int scale = Math.max(1, mc.getWindow().getGuiScale());
			int cw = Math.min(w, 200 * scale), ch = Math.min(h, 60 * scale);
			int x0 = (w - cw) / 2;
			XrRuntime.Swapchain hud = xr.hud();
			int img = xr.acquire(hud);
			GlBlit.clear(img);
			// Keep the crop's shape inside the hud image (letterboxed).
			float sx = (float) hud.width / cw, sy = (float) hud.height / ch, s = Math.min(sx, sy);
			int dw = Math.round(cw * s), dh = Math.round(ch * s);
			int dx = (hud.width - dw) / 2, dy = (hud.height - dh) / 2;
			blit.blit(src, x0, 0, x0 + cw, ch, img, dx, dy, dx + dw, dy + dh, true);
			xr.release(hud);
			quads.add(controls.wristQuad(hud));
		}
	}

	/** What the desktop window shows: the left eye while in VR. */
	public GpuTextureView mirror(GpuTextureView normal) {
		return eyesDrawnLastFrame && eyeTarget != null ? eyeTarget.getColorTextureView() : normal;
	}
}
