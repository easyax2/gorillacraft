package dev.gorillacraft.mixin.client;

import dev.gorillacraft.client.GorillaCraftClient;
import dev.gorillacraft.client.GorillaSession;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In overlay mode Minecraft draws what the gorilla sees: every frame the camera goes to Gorilla
 * Tag's latest pose (not the player's tick-interpolated position), at one eye or the other in
 * turn for per-eye frames, with a wide, fixed field of view that covers some head turning.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void gorillacraft$headPose(DeltaTracker deltaTracker, CallbackInfo ci) {
		GorillaSession session = GorillaCraftClient.session();
		if (session != null && session.overlayActive()) {
			setPosition(session.cameraX(), session.cameraY(), session.cameraZ());
			setRotation(session.poseYaw(), session.posePitch());
		}
	}

	@Inject(method = "calculateFov", at = @At("HEAD"), cancellable = true)
	private void gorillacraft$wideFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		GorillaSession session = GorillaCraftClient.session();
		if (session != null && session.overlayActive()) {
			cir.setReturnValue(session.overlayFov());
		}
	}
}
