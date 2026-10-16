package dev.gorillacraft.mixin.client;

import dev.gorillacraft.client.vr.VrRenderer;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
}
