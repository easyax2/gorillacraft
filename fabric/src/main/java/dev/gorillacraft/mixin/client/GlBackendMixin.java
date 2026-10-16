package dev.gorillacraft.mixin.client;

import com.mojang.renderpearl.backend.opengl.GlBackend;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OpenXR on Linux takes OpenGL through EGL. SDL already uses EGL on Wayland; on X11 it would
 * pick GLX, so ask for EGL before it loads OpenGL.
 */
@Mixin(GlBackend.class)
abstract class GlBackendMixin {
	@Inject(method = "loadLibrary", at = @At("HEAD"))
	private void gorillacraft$egl(CallbackInfo ci) {
		if (Platform.get() == Platform.LINUX && System.getenv("GORILLACRAFT_NO_EGL") == null) {
			SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_FORCE_EGL, "1");
		}
	}
}
