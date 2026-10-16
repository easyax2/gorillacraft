package dev.gorillacraft.mixin.client;

import dev.gorillacraft.client.vr.VrRenderer;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

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

	/** The section grid follows the middle of the head, not each eye. */
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;repositionCamera(Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
	private void gorillacraft$repositionAtHead(LevelRenderer self, CameraRenderState cam, Operation<Void> original) {
		atHead(cam, () -> original.call(self, cam));
	}

	/** Compiling happens once per frame (first eye), from the middle of the head. */
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;compileSections(Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
	private void gorillacraft$compileOnce(LevelRenderer self, CameraRenderState cam, Operation<Void> original) {
		VrRenderer vr = VrRenderer.get();
		if (vr == null || !vr.secondEye()) {
			atHead(cam, () -> original.call(self, cam));
		}
	}

	private static void atHead(CameraRenderState cam, Runnable r) {
		VrRenderer vr = VrRenderer.get();
		if (vr == null || !vr.eyePass()) {
			r.run();
			return;
		}
		Vec3 eye = cam.pos;
		net.minecraft.core.BlockPos eyeBlock = cam.blockPos;
		cam.pos = vr.extractionPos();
		cam.blockPos = net.minecraft.core.BlockPos.containing(cam.pos);
		try {
			r.run();
		} finally {
			cam.pos = eye;
			cam.blockPos = eyeBlock;
		}
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V"))
	private void gorillacraft$occlusionOnce(net.minecraft.client.renderer.SectionOcclusionGraph graph, CameraRenderState cam, int fov,
			net.minecraft.client.renderer.state.level.ChunkLoadingRenderState loading, Operation<Void> original) {
		VrRenderer vr = VrRenderer.get();
		if (vr == null || !vr.secondEye()) {
			atHead(cam, () -> original.call(graph, cam, fov, loading));
		}
	}
}
