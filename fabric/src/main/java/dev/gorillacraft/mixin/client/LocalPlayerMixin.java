package dev.gorillacraft.mixin.client;

import dev.gorillacraft.client.GorillaCraftClient;
import dev.gorillacraft.client.GorillaSession;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
	/** Put the player where the gorilla is, before tick() sends the position to the server. */
	@Inject(method = "tick", at = @At("HEAD"))
	private void gorillacraft$applyPose(CallbackInfo ci) {
		GorillaSession session = GorillaCraftClient.session();
		if (session != null) {
			session.applyPose((LocalPlayer) (Object) this);
		}
	}

	/** Gorilla Tag does the moving: no Minecraft input, gravity or collision while it drives. */
	@Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
	private void gorillacraft$skipMovement(CallbackInfo ci) {
		GorillaSession session = GorillaCraftClient.session();
		if (session != null && session.driving()) {
			ci.cancel();
		}
	}
}
