package dev.gorillacraft.mixin.client;

import dev.gorillacraft.client.vr.VrRenderer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Particles are stored relative to the camera they were extracted with (the middle of your
 * head in VR); each eye is a few centimetres off that, so move them to be relative to the eye
 * being drawn. Without this both eyes see every particle in the same place on their screen,
 * which puts it at the wrong depth.
 */
@Mixin(QuadParticleRenderState.class)
abstract class QuadParticleRenderStateMixin {
	@ModifyVariable(method = "renderRotatedQuad", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float gorillacraft$eyeX(float x) {
		VrRenderer vr = VrRenderer.get();
		return vr != null && vr.eyePass() ? x + vr.eyeOffset().x : x;
	}

	@ModifyVariable(method = "renderRotatedQuad", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float gorillacraft$eyeY(float y) {
		VrRenderer vr = VrRenderer.get();
		return vr != null && vr.eyePass() ? y + vr.eyeOffset().y : y;
	}

	@ModifyVariable(method = "renderRotatedQuad", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private float gorillacraft$eyeZ(float z) {
		VrRenderer vr = VrRenderer.get();
		return vr != null && vr.eyePass() ? z + vr.eyeOffset().z : z;
	}
}
