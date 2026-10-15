package dev.gorillacraft.mixin.client;

import dev.gorillacraft.client.GorillaCraftClient;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every "re-render this section" request (block changes, light updates, chunk loads) ends here. */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
	@Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
	private void gorillacraft$sectionDirty(int x, int y, int z, boolean playerChanged, CallbackInfo ci) {
		GorillaCraftClient.sectionDirty(x, y, z);
	}
}
