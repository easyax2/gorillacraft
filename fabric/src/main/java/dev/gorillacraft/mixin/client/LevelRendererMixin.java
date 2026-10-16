package dev.gorillacraft.mixin.client;

import dev.gorillacraft.client.vr.VrRenderer;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

/**
 * In VR the level is drawn twice from one extraction (once per eye). Minecraft empties the
 * entity/block entity lists once they're submitted; keep them for the second eye (the next
 * extraction empties them anyway).
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	@Redirect(method = "submitFeatures", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V"))
	private void gorillacraft$keepForSecondEye(List<?> list) {
		VrRenderer vr = VrRenderer.get();
		if (vr == null || !vr.eyePass()) {
			list.clear();
		}
	}

	/** The second eye sees the same sections: compiling and visibility were done for the first. */
	@WrapWithCondition(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;compileSections(Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
	private boolean gorillacraft$compileOnce(net.minecraft.client.renderer.LevelRenderer self, net.minecraft.client.renderer.state.level.CameraRenderState cam) {
		VrRenderer vr = VrRenderer.get();
		return vr == null || !vr.secondEye();
	}

	@WrapWithCondition(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V"))
	private boolean gorillacraft$occlusionOnce(net.minecraft.client.renderer.SectionOcclusionGraph graph,
			net.minecraft.client.renderer.state.level.CameraRenderState cam, int fov, net.minecraft.client.renderer.state.level.ChunkLoadingRenderState loading) {
		VrRenderer vr = VrRenderer.get();
		return vr == null || !vr.secondEye();
	}
}
