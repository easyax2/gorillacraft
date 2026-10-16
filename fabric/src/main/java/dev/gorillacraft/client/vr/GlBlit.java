package dev.gorillacraft.client.vr;

import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL30C.GL_LINEAR;
import static org.lwjgl.opengl.GL30C.GL_NEAREST;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.lwjgl.opengl.GL45C;

/**
 * Copies Minecraft's finished pictures into the runtime's swapchain images. Uses its own
 * framebuffers through direct state access, so none of Minecraft's GL bindings change. Minecraft's
 * OpenGL backend runs its commands right away, so a copy made after a pass sees the whole pass.
 */
final class GlBlit {
	private int readFbo, drawFbo;

	/** Copies (and scales) a source rectangle into a destination rectangle; both in GL texture coordinates (origin bottom left). */
	void blit(int src, int sx0, int sy0, int sx1, int sy1, int dst, int dx0, int dy0, int dx1, int dy1, boolean linear) {
		if (readFbo == 0) {
			readFbo = GL45C.glCreateFramebuffers();
			drawFbo = GL45C.glCreateFramebuffers();
		}
		GL45C.glNamedFramebufferTexture(readFbo, GL_COLOR_ATTACHMENT0, src, 0);
		GL45C.glNamedFramebufferTexture(drawFbo, GL_COLOR_ATTACHMENT0, dst, 0);
		GlStateManager._disableScissorTest(); // blits are clipped by the scissor; Minecraft's cached state stays right
		GL45C.glBlitNamedFramebuffer(readFbo, drawFbo, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, GL_COLOR_BUFFER_BIT, linear ? GL_LINEAR : GL_NEAREST);
		// Don't keep the swapchain image attached: the runtime owns it between frames.
		GL45C.glNamedFramebufferTexture(drawFbo, GL_COLOR_ATTACHMENT0, 0, 0);
	}

	/** Clears a texture to transparent black. */
	static void clear(int tex) {
		org.lwjgl.opengl.GL44C.glClearTexImage(tex, 0, org.lwjgl.opengl.GL11C.GL_RGBA, org.lwjgl.opengl.GL11C.GL_FLOAT, new float[4]);
	}
}
