package dev.gorillacraft.mixin.client;

import dev.gorillacraft.client.GorillaCraftClient;
import dev.gorillacraft.client.GorillaSession;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After the world is drawn each frame, grab it for Gorilla Tag's overlay; and no hand in that picture. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void gorillacraft$noHand(CallbackInfo ci) {
		GorillaSession session = GorillaCraftClient.session();
		if (session != null && session.overlayActive()) {
			ci.cancel();
		}
	}

	/** After the whole frame (GUI included): the inventory/chest panel. */
	@Inject(method = "render", at = @At("TAIL"))
	private void gorillacraft$guiDone(CallbackInfo ci) {
		GorillaSession session = GorillaCraftClient.session();
		if (session != null) {
			session.onFrameFinished();
		}
	}

	/** Right after the world is drawn, before anything else (GUI, screens) reuses the depth buffer. */
	@Inject(method = "renderLevel", at = @At("TAIL"))
	private void gorillacraft$frameDone(CallbackInfo ci) {
		GorillaSession session = GorillaCraftClient.session();
		if (session != null) {
			session.onFrameRendered();
		}
	}
}
