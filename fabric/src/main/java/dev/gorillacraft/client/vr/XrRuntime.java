package dev.gorillacraft.client.vr;

import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

import dev.gorillacraft.GorillaCraft;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.KHROpenGLEnable;
import org.lwjgl.openxr.MNDXEGLEnable;
import org.lwjgl.openxr.XR;
import org.lwjgl.openxr.XrCompositionLayerProjection;
import org.lwjgl.openxr.XrCompositionLayerProjectionView;
import org.lwjgl.openxr.XrCompositionLayerQuad;
import org.lwjgl.openxr.XrEventDataBuffer;
import org.lwjgl.openxr.XrEventDataSessionStateChanged;
import org.lwjgl.openxr.XrExtensionProperties;
import org.lwjgl.openxr.XrFovf;
import org.lwjgl.openxr.XrFrameBeginInfo;
import org.lwjgl.openxr.XrFrameEndInfo;
import org.lwjgl.openxr.XrFrameState;
import org.lwjgl.openxr.XrFrameWaitInfo;
import org.lwjgl.openxr.XrGraphicsBindingEGLMNDX;
import org.lwjgl.openxr.XrGraphicsBindingOpenGLWin32KHR;
import org.lwjgl.openxr.XrGraphicsRequirementsOpenGLKHR;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrInstanceCreateInfo;
import org.lwjgl.openxr.XrPosef;
import org.lwjgl.openxr.XrReferenceSpaceCreateInfo;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.openxr.XrSessionBeginInfo;
import org.lwjgl.openxr.XrSessionCreateInfo;
import org.lwjgl.openxr.XrSpace;
import org.lwjgl.openxr.XrSwapchain;
import org.lwjgl.openxr.XrSwapchainCreateInfo;
import org.lwjgl.openxr.XrSwapchainImageAcquireInfo;
import org.lwjgl.openxr.XrSwapchainImageBaseHeader;
import org.lwjgl.openxr.XrSwapchainImageOpenGLKHR;
import org.lwjgl.openxr.XrSwapchainImageReleaseInfo;
import org.lwjgl.openxr.XrSwapchainImageWaitInfo;
import org.lwjgl.openxr.XrSystemGetInfo;
import org.lwjgl.openxr.XrView;
import org.lwjgl.openxr.XrViewConfigurationView;
import org.lwjgl.openxr.XrViewLocateInfo;
import org.lwjgl.openxr.XrViewState;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.Struct;

/**
 * Minecraft's own OpenXR session: Minecraft is the VR game. It talks to the headset's runtime
 * (WiVRn, SteamVR, Oculus...) through OpenXR with its OpenGL context, renders each eye into the
 * runtime's swapchain images and submits them with a GUI panel on top.
 *
 * Everything here runs on Minecraft's render thread. Without a runtime or headset, Minecraft
 * plays normally on the monitor and tries again every few seconds.
 */
public final class XrRuntime {
	public static final int LEFT = 0, RIGHT = 1;
	private static final long RETRY_NANOS = 3_000_000_000L;
	/** OpenGL formats: what Minecraft's targets hold is sRGB-encoded, so the runtime must read it as sRGB. */
	private static final long GL_SRGB8_ALPHA8 = 0x8C43, GL_RGBA8 = 0x8058;

	/** One eye's view for the frame being drawn, in tracking space (metres, OpenXR axes). */
	public static final class View {
		public final Vector3f pos = new Vector3f();
		public final Quaternionf rot = new Quaternionf();
		public float left, right, up, down;
	}

	public static final class Swapchain {
		XrSwapchain handle;
		public int width, height;
		int[] images = new int[0];
		long format;
	}

	private XrInstance instance;
	private long systemId;
	private XrSession session;
	private XrSpace space;
	private boolean stageSpace;
	private boolean running;
	private boolean focused;
	private long nextTry;
	private boolean gaveUp;
	private String lastProblem = "";
	private long swapchainFormat;
	private boolean loaderLoaded;
	private SharedLibrary egl;

	private final Swapchain[] eyes = new Swapchain[2];
	private Swapchain gui, hud, cursor;
	private XrInput input;

	private boolean frameBegun;
	private boolean shouldRender;
	private long displayTime;
	private final View[] views = {new View(), new View()};
	private boolean viewsValid;

	/** The user's eye-resolution multiplier (1 = the runtime's recommendation). */
	private final float resolutionScale;

	public XrRuntime(float resolutionScale) {
		this.resolutionScale = resolutionScale;
	}

	public boolean sessionRunning() {
		return running;
	}

	public boolean focused() {
		return focused;
	}

	public boolean frameBegun() {
		return frameBegun;
	}

	/** True when this frame's eyes should be drawn (the runtime wants a picture and knows where the eyes are). */
	public boolean shouldRender() {
		return frameBegun && (shouldRender && viewsValid || FORCE_RENDER);
	}

	/** Testing without wearing the headset: draw the eyes even when the runtime says nobody's looking. */
	private static final boolean FORCE_RENDER = System.getenv("GORILLACRAFT_VR_FORCE_RENDER") != null;

	public View view(int eye) {
		return views[eye];
	}

	public Swapchain eye(int eye) {
		return eyes[eye];
	}

	public Swapchain gui() {
		return gui;
	}

	public Swapchain hud() {
		return hud;
	}

	public XrInput input() {
		return input;
	}

	public long displayTime() {
		return displayTime;
	}

	public boolean stageSpace() {
		return stageSpace;
	}

	XrSpace space() {
		return space;
	}

	XrSession session() {
		return session;
	}

	XrInstance instance() {
		return instance;
	}

	/** Why VR isn't running, for the status line ("" while it runs). */
	public String problem() {
		return running ? "" : lastProblem;
	}

	// ---------------------------------------------------------------- start up

	private void tryStart() {
		long now = System.nanoTime();
		if (gaveUp || now < nextTry) {
			return;
		}
		nextTry = now + RETRY_NANOS;
		try {
			if (instance == null) {
				createInstance();
			}
			if (systemId == 0 && !findSystem()) {
				return;
			}
			createSession();
		} catch (XrFailure e) {
			problem(e.getMessage());
			destroySession();
			if (e.fatal) {
				gaveUp = true;
				destroyInstance();
			}
		} catch (Throwable e) {
			GorillaCraft.LOG.error("OpenXR failed to start", e);
			problem("OpenXR failed: " + e);
			destroySession();
			destroyInstance();
			gaveUp = true;
		}
	}

	private void problem(String text) {
		if (!text.equals(lastProblem)) {
			lastProblem = text;
			GorillaCraft.LOG.warn("VR: {}", text);
		}
	}

	private void loadLoader() {
		if (loaderLoaded) {
			return;
		}
		// The system's loader knows where the system's runtime is registered; ours is the fallback.
		String sys = null;
		if (Platform.get() == Platform.LINUX && System.getenv("GORILLACRAFT_BUNDLED_OPENXR") == null) {
			for (String p : List.of("/usr/lib/libopenxr_loader.so.1", "/usr/lib64/libopenxr_loader.so.1",
					"/usr/lib/x86_64-linux-gnu/libopenxr_loader.so.1")) {
				if (Files.exists(Path.of(p))) {
					sys = p;
					break;
				}
			}
		}
		try {
			// Before the XR class loads: otherwise it loads the default loader by itself.
			org.lwjgl.system.Configuration.OPENXR_EXPLICIT_INIT.set(true);
			if (sys != null) {
				XR.create(sys);
			} else {
				XR.create();
			}
		} catch (Throwable e) {
			throw new XrFailure("No OpenXR loader: " + e.getMessage(), true);
		}
		loaderLoaded = true;
	}

	private boolean linuxEgl() {
		return Platform.get() == Platform.LINUX;
	}

	private void createInstance() {
		loadLoader();
		try (MemoryStack stack = stackPush()) {
			IntBuffer n = stack.mallocInt(1);
			int r = xrEnumerateInstanceExtensionProperties((CharSequence) null, n, null);
			if (r != XR_SUCCESS) {
				throw new XrFailure("No OpenXR runtime (is WiVRn/SteamVR running?): " + r, false);
			}
			XrExtensionProperties.Buffer props = XrExtensionProperties.calloc(n.get(0), stack);
			for (int i = 0; i < props.capacity(); i++) {
				props.get(i).type$Default();
			}
			check(xrEnumerateInstanceExtensionProperties((CharSequence) null, n, props), "xrEnumerateInstanceExtensionProperties");
			Set<String> available = new HashSet<>();
			for (int i = 0; i < props.capacity(); i++) {
				available.add(props.get(i).extensionNameString());
			}
			List<String> want = linuxEgl()
					? List.of(KHROpenGLEnable.XR_KHR_OPENGL_ENABLE_EXTENSION_NAME, MNDXEGLEnable.XR_MNDX_EGL_ENABLE_EXTENSION_NAME)
					: List.of(KHROpenGLEnable.XR_KHR_OPENGL_ENABLE_EXTENSION_NAME);
			for (String ext : want) {
				if (!available.contains(ext)) {
					throw new XrFailure("The OpenXR runtime can't take OpenGL frames (" + ext + " missing)", true);
				}
			}
			PointerBuffer exts = stack.mallocPointer(want.size());
			for (String ext : want) {
				exts.put(stack.UTF8(ext));
			}
			exts.flip();
			XrInstanceCreateInfo ci = XrInstanceCreateInfo.calloc(stack).type$Default();
			ci.applicationInfo().applicationName(stack.UTF8("GorillaCraft")).applicationVersion(1).engineName(stack.UTF8("Minecraft"))
					.engineVersion(1).apiVersion(XR_API_VERSION_1_0);
			ci.enabledExtensionNames(exts);
			PointerBuffer pp = stack.mallocPointer(1);
			r = xrCreateInstance(ci, pp);
			if (r != XR_SUCCESS) {
				throw new XrFailure("No OpenXR runtime (is WiVRn/SteamVR running?): xrCreateInstance " + r, false);
			}
			instance = new XrInstance(pp.get(0), ci);
			GorillaCraft.LOG.info("OpenXR instance created");
		}
	}

	private boolean findSystem() {
		try (MemoryStack stack = stackPush()) {
			XrSystemGetInfo gi = XrSystemGetInfo.calloc(stack).type$Default().formFactor(XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY);
			LongBuffer id = stack.mallocLong(1);
			int r = xrGetSystem(instance, gi, id);
			if (r != XR_SUCCESS) {
				problem("Headset not connected (xrGetSystem " + r + ")");
				return false;
			}
			systemId = id.get(0);
			return true;
		}
	}

	private void createSession() {
		try (MemoryStack stack = stackPush()) {
			XrGraphicsRequirementsOpenGLKHR req = XrGraphicsRequirementsOpenGLKHR.calloc(stack).type$Default();
			check(KHROpenGLEnable.xrGetOpenGLGraphicsRequirementsKHR(instance, systemId, req), "xrGetOpenGLGraphicsRequirementsKHR");
			Struct<?> binding = linuxEgl() ? eglBinding(stack) : XrGraphicsBindingOpenGLWin32KHR.calloc(stack).type$Default()
					.hDC(org.lwjgl.opengl.WGL.wglGetCurrentDC()).hGLRC(org.lwjgl.opengl.WGL.wglGetCurrentContext((IntBuffer) null));
			XrSessionCreateInfo sci = XrSessionCreateInfo.calloc(stack).type$Default().next(binding.address()).systemId(systemId);
			PointerBuffer pp = stack.mallocPointer(1);
			check(xrCreateSession(instance, sci, pp), "xrCreateSession");
			session = new XrSession(pp.get(0), instance);

			space = createReferenceSpace(stack, XR_REFERENCE_SPACE_TYPE_STAGE);
			stageSpace = space != null;
			if (space == null) {
				space = createReferenceSpace(stack, XR_REFERENCE_SPACE_TYPE_LOCAL);
			}
			if (space == null) {
				throw new XrFailure("No tracking space", false);
			}

			swapchainFormat = pickFormat(stack);
			IntBuffer n = stack.mallocInt(1);
			check(xrEnumerateViewConfigurationViews(instance, systemId, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, n, null), "views");
			XrViewConfigurationView.Buffer vcv = XrViewConfigurationView.calloc(n.get(0), stack);
			for (int i = 0; i < vcv.capacity(); i++) {
				vcv.get(i).type$Default();
			}
			check(xrEnumerateViewConfigurationViews(instance, systemId, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, n, vcv), "views");
			for (int e = 0; e < 2; e++) {
				XrViewConfigurationView v = vcv.get(e);
				int w = Math.min(v.maxImageRectWidth(), Math.max(64, Math.round(v.recommendedImageRectWidth() * resolutionScale)));
				int h = Math.min(v.maxImageRectHeight(), Math.max(64, Math.round(v.recommendedImageRectHeight() * resolutionScale)));
				eyes[e] = createSwapchain(w, h, false);
			}
			hud = createSwapchain(640, 192, false);
			cursor = createSwapchain(16, 16, true);
			input = new XrInput(this);
			input.create();
			fillCursor();
			GorillaCraft.LOG.info("OpenXR session created: {}x{} per eye, {} space, format 0x{}", eyes[0].width, eyes[0].height,
					stageSpace ? "stage" : "local", Long.toHexString(swapchainFormat));
		}
	}

	/** The EGL context Minecraft's window uses (SDL on Wayland, or on X11 with EGL forced at start). */
	private Struct<?> eglBinding(MemoryStack stack) {
		if (egl == null) {
			egl = APIUtil.apiCreateLibrary("libEGL.so.1");
		}
		long getProcAddress = egl.getFunctionAddress("eglGetProcAddress");
		long getCurrentContext = egl.getFunctionAddress("eglGetCurrentContext");
		long display = SDLVideo.SDL_EGL_GetCurrentDisplay();
		long config = SDLVideo.SDL_EGL_GetCurrentConfig();
		long context = getCurrentContext != 0 ? JNI.callP(getCurrentContext) : 0;
		if (getProcAddress == 0 || display == 0 || context == 0) {
			throw new XrFailure("Minecraft's OpenGL isn't on EGL, which VR needs on Linux", true);
		}
		return XrGraphicsBindingEGLMNDX.calloc(stack).type$Default().getProcAddress(getProcAddress).display(display).config(config).context(context);
	}

	private XrSpace createReferenceSpace(MemoryStack stack, int type) {
		XrReferenceSpaceCreateInfo ci = XrReferenceSpaceCreateInfo.calloc(stack).type$Default().referenceSpaceType(type);
		ci.poseInReferenceSpace().orientation().w(1f);
		PointerBuffer pp = stack.mallocPointer(1);
		if (xrCreateReferenceSpace(session, ci, pp) != XR_SUCCESS) {
			return null;
		}
		return new XrSpace(pp.get(0), session);
	}

	private long pickFormat(MemoryStack stack) {
		IntBuffer n = stack.mallocInt(1);
		check(xrEnumerateSwapchainFormats(session, n, null), "formats");
		LongBuffer formats = stack.mallocLong(n.get(0));
		check(xrEnumerateSwapchainFormats(session, n, formats), "formats");
		long fallback = -1;
		for (int i = 0; i < n.get(0); i++) {
			long f = formats.get(i);
			if (f == GL_SRGB8_ALPHA8) {
				return f;
			}
			if (f == GL_RGBA8) {
				fallback = f;
			}
		}
		if (fallback < 0) {
			throw new XrFailure("The runtime has no 8-bit RGBA swapchains", true);
		}
		GorillaCraft.LOG.warn("No sRGB swapchains: VR colours will look washed out");
		return fallback;
	}

	Swapchain createSwapchain(int width, int height, boolean isStatic) {
		try (MemoryStack stack = stackPush()) {
			XrSwapchainCreateInfo ci = XrSwapchainCreateInfo.calloc(stack).type$Default()
					.createFlags(isStatic ? XR_SWAPCHAIN_CREATE_STATIC_IMAGE_BIT : 0)
					.usageFlags(XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_TRANSFER_DST_BIT)
					.format(swapchainFormat).sampleCount(1).width(width).height(height).faceCount(1).arraySize(1).mipCount(1);
			PointerBuffer pp = stack.mallocPointer(1);
			check(xrCreateSwapchain(session, ci, pp), "xrCreateSwapchain");
			Swapchain sc = new Swapchain();
			sc.handle = new XrSwapchain(pp.get(0), session);
			sc.width = width;
			sc.height = height;
			sc.format = swapchainFormat;
			IntBuffer n = stack.mallocInt(1);
			check(xrEnumerateSwapchainImages(sc.handle, n, null), "images");
			XrSwapchainImageOpenGLKHR.Buffer imgs = XrSwapchainImageOpenGLKHR.calloc(n.get(0), stack);
			for (int i = 0; i < imgs.capacity(); i++) {
				imgs.get(i).type$Default();
			}
			check(xrEnumerateSwapchainImages(sc.handle, n, XrSwapchainImageBaseHeader.create(imgs.address(), imgs.capacity())), "images");
			sc.images = new int[imgs.capacity()];
			for (int i = 0; i < sc.images.length; i++) {
				sc.images[i] = imgs.get(i).image();
			}
			return sc;
		}
	}

	void destroySwapchain(Swapchain sc) {
		if (sc != null && sc.handle != null) {
			xrDestroySwapchain(sc.handle);
			sc.handle = null;
		}
	}

	/** The GUI panel's swapchain, the size of Minecraft's window (made again when the window changes size). */
	public Swapchain guiSized(int width, int height) {
		if (gui == null || gui.width != width || gui.height != height) {
			destroySwapchain(gui);
			gui = createSwapchain(width, height, false);
		}
		return gui;
	}

	/** The laser pointer's dot: a static white square. */
	private void fillCursor() {
		int tex = acquire(cursor);
		org.lwjgl.opengl.GL44C.glClearTexImage(tex, 0, org.lwjgl.opengl.GL11C.GL_RGBA, org.lwjgl.opengl.GL11C.GL_FLOAT,
				new float[] {1f, 1f, 1f, 1f});
		release(cursor);
	}

	// ---------------------------------------------------------------- frames

	/**
	 * Start of Minecraft's frame: handles runtime events, then waits for the runtime's frame
	 * timing (this paces Minecraft to the headset's refresh rate) and finds the eyes for the time
	 * this frame will be shown.
	 */
	public void beginFrame() {
		frameBegun = false;
		if (session == null) {
			tryStart();
			if (session == null) {
				return;
			}
		}
		try {
			pollEvents();
			if (!running || session == null) {
				return;
			}
			try (MemoryStack stack = stackPush()) {
				XrFrameState fs = XrFrameState.calloc(stack).type$Default();
				check(xrWaitFrame(session, XrFrameWaitInfo.calloc(stack).type$Default(), fs), "xrWaitFrame");
				displayTime = fs.predictedDisplayTime();
				shouldRender = fs.shouldRender();
				check(xrBeginFrame(session, XrFrameBeginInfo.calloc(stack).type$Default()), "xrBeginFrame");
				frameBegun = true;
				locateViews(stack);
			}
			input.sync(displayTime, focused);
		} catch (XrFailure e) {
			problem(e.getMessage());
			frameBegun = false;
			destroySession();
		}
	}

	private void locateViews(MemoryStack stack) {
		XrViewState vs = XrViewState.calloc(stack).type$Default();
		XrView.Buffer xv = XrView.calloc(2, stack);
		for (int i = 0; i < 2; i++) {
			xv.get(i).type$Default();
		}
		IntBuffer n = stack.mallocInt(1);
		XrViewLocateInfo li = XrViewLocateInfo.calloc(stack).type$Default().viewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)
				.displayTime(displayTime).space(space);
		check(xrLocateViews(session, li, vs, n, xv), "xrLocateViews");
		long need = XR_VIEW_STATE_ORIENTATION_VALID_BIT | XR_VIEW_STATE_POSITION_VALID_BIT;
		viewsValid = (vs.viewStateFlags() & need) == need;
		for (int i = 0; i < 2; i++) {
			XrView v = xv.get(i);
			View out = views[i];
			XrPosef p = v.pose();
			out.pos.set(p.position$().x(), p.position$().y(), p.position$().z());
			out.rot.set(p.orientation().x(), p.orientation().y(), p.orientation().z(), p.orientation().w());
			XrFovf f = v.fov();
			out.left = f.angleLeft();
			out.right = f.angleRight();
			out.up = f.angleUp();
			out.down = f.angleDown();
		}
	}

	/** A quad layer to show this frame: a swapchain, where (tracking space, or a hand) and how big. */
	public record Quad(Swapchain swapchain, boolean onLeftHand, Vector3f pos, Quaternionf rot, float width, float height) {
	}

	/** Pointer dot quad, drawn at the laser's hit point. */
	public Quad cursorQuad(Vector3f pos, Quaternionf rot, float size) {
		return new Quad(cursor, false, pos, rot, size, size);
	}

	/** End of the frame: hands the eyes (if drawn) and the quads to the runtime. */
	public void endFrame(boolean eyesDrawn, List<Quad> quads) {
		if (!frameBegun) {
			return;
		}
		frameBegun = false;
		try (MemoryStack stack = stackPush()) {
			PointerBuffer layers = stack.mallocPointer(1 + quads.size());
			if (eyesDrawn) {
				XrCompositionLayerProjectionView.Buffer pv = XrCompositionLayerProjectionView.calloc(2, stack);
				for (int e = 0; e < 2; e++) {
					View v = views[e];
					XrCompositionLayerProjectionView p = pv.get(e).type$Default();
					p.pose().orientation().set(v.rot.x, v.rot.y, v.rot.z, v.rot.w);
					p.pose().position$().set(v.pos.x, v.pos.y, v.pos.z);
					p.fov().set(v.left, v.right, v.up, v.down);
					p.subImage().swapchain(eyes[e].handle).imageArrayIndex(0);
					p.subImage().imageRect().extent().set(eyes[e].width, eyes[e].height);
				}
				XrCompositionLayerProjection proj = XrCompositionLayerProjection.calloc(stack).type$Default().space(space).views(pv);
				layers.put(proj.address());
			}
			for (Quad q : quads) {
				XrCompositionLayerQuad l = XrCompositionLayerQuad.calloc(stack).type$Default()
						.layerFlags(XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT)
						.space(q.onLeftHand() ? input.gripSpace(LEFT) : space).eyeVisibility(XR_EYE_VISIBILITY_BOTH);
				l.subImage().swapchain(q.swapchain().handle).imageArrayIndex(0);
				l.subImage().imageRect().extent().set(q.swapchain().width, q.swapchain().height);
				l.pose().orientation().set(q.rot().x, q.rot().y, q.rot().z, q.rot().w);
				l.pose().position$().set(q.pos().x, q.pos().y, q.pos().z);
				l.size().set(q.width(), q.height());
				layers.put(l.address());
			}
			layers.flip();
			XrFrameEndInfo ei = XrFrameEndInfo.calloc(stack).type$Default().displayTime(displayTime)
					.environmentBlendMode(XR_ENVIRONMENT_BLEND_MODE_OPAQUE).layers(layers);
			check(xrEndFrame(session, ei), "xrEndFrame");
		} catch (XrFailure e) {
			problem(e.getMessage());
			destroySession();
		}
	}

	/** Acquires the swapchain's next image and waits until it can be drawn into; returns its GL texture. */
	public int acquire(Swapchain sc) {
		try (MemoryStack stack = stackPush()) {
			IntBuffer idx = stack.mallocInt(1);
			check(xrAcquireSwapchainImage(sc.handle, XrSwapchainImageAcquireInfo.calloc(stack).type$Default(), idx), "acquire");
			check(xrWaitSwapchainImage(sc.handle, XrSwapchainImageWaitInfo.calloc(stack).type$Default().timeout(XR_INFINITE_DURATION)), "wait");
			return sc.images[idx.get(0)];
		}
	}

	public void release(Swapchain sc) {
		try (MemoryStack stack = stackPush()) {
			check(xrReleaseSwapchainImage(sc.handle, XrSwapchainImageReleaseInfo.calloc(stack).type$Default()), "release");
		}
	}

	private void pollEvents() {
		try (MemoryStack stack = stackPush()) {
			XrEventDataBuffer ev = XrEventDataBuffer.calloc(stack).type$Default();
			while (instance != null && xrPollEvent(instance, ev) == XR_SUCCESS) {
				switch (ev.type()) {
					case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED -> onState(XrEventDataSessionStateChanged.create(ev.address()).state(), stack);
					case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING -> {
						problem("The OpenXR runtime is going away");
						destroySession();
						destroyInstance();
						return;
					}
					default -> {
					}
				}
				ev.type$Default();
			}
		}
	}

	private void onState(int state, MemoryStack stack) {
		focused = state == XR_SESSION_STATE_FOCUSED;
		GorillaCraft.LOG.info("VR session state {}", state);
		switch (state) {
			case XR_SESSION_STATE_READY -> {
				check(xrBeginSession(session, XrSessionBeginInfo.calloc(stack).type$Default()
						.primaryViewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)), "xrBeginSession");
				running = true;
				lastProblem = "";
				GorillaCraft.LOG.info("VR session running");
			}
			case XR_SESSION_STATE_STOPPING -> {
				xrEndSession(session);
				running = false;
				problem("VR paused by the headset");
			}
			case XR_SESSION_STATE_EXITING, XR_SESSION_STATE_LOSS_PENDING -> {
				problem("VR session ended; trying again");
				destroySession();
				systemId = 0;
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- shut down

	private void destroySession() {
		running = false;
		focused = false;
		frameBegun = false;
		if (input != null) {
			input.destroy();
			input = null;
		}
		for (int e = 0; e < 2; e++) {
			destroySwapchain(eyes[e]);
			eyes[e] = null;
		}
		destroySwapchain(gui);
		destroySwapchain(hud);
		destroySwapchain(cursor);
		gui = hud = cursor = null;
		if (space != null) {
			xrDestroySpace(space);
			space = null;
		}
		if (session != null) {
			xrDestroySession(session);
			session = null;
		}
		nextTry = System.nanoTime() + RETRY_NANOS;
	}

	private void destroyInstance() {
		if (instance != null) {
			xrDestroyInstance(instance);
			instance = null;
		}
		systemId = 0;
	}

	public void close() {
		destroySession();
		destroyInstance();
	}

	static void check(int result, String what) {
		if (result < 0) {
			throw new XrFailure(what + " failed: " + result, false);
		}
	}

	static final class XrFailure extends RuntimeException {
		final boolean fatal;

		XrFailure(String message, boolean fatal) {
			super(message);
			this.fatal = fatal;
		}
	}
}
