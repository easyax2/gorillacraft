package dev.gorillacraft.client.vr;

import com.mojang.blaze3d.pipeline.RenderTarget;

/** What GameRendererMixin adds to GameRenderer for VR. */
public interface VrGameRenderer {
	/** Points the game renderer at other targets (the eye-sized ones), or back at its own. */
	void gorillacraft$setTargets(RenderTarget main, RenderTarget hud3d);

	RenderTarget gorillacraft$hud3DTarget();

	/** Just the GUI, into the main target on a transparent background (the world isn't drawn). */
	void gorillacraft$renderGuiOnly(boolean endFrame);
}
