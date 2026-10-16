package dev.gorillacraft.mixin.client;

import dev.gorillacraft.client.vr.VrRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In VR the camera is your head: the middle of your eyes, turned the way your head is (roll
 * included), with a culling frustum that holds both eyes. Minecraft extracts the world from
 * here; each eye's own view is set just before that eye is drawn (VrRenderer).
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	private Frustum cullFrustum;

	@Shadow
	@Final
	private Quaternionf rotation;

	@Shadow
	private float depthFar;

	@Shadow
	private boolean initialized;

	@Inject(method = "update", at = @At("TAIL"))
	private void gorillacraft$head(DeltaTracker deltaTracker, CallbackInfo ci) {
		VrRenderer vr = VrRenderer.get();
		if (vr == null || !vr.active() || !initialized) {
			return;
		}
		Quaternionf head = vr.headWorldRotation();
		Vector3f forward = head.transform(new Vector3f(0, 0, -1));
		float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
		float pitch = (float) Math.toDegrees(-Math.asin(Math.clamp(forward.y, -1f, 1f)));
		setRotation(yaw, pitch);
		rotation.set(head);
		setPosition(vr.headWorld());
		cullFrustum = vr.cullFrustum(depthFar,
				com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
	}
}
