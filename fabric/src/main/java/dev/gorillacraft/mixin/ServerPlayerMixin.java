package dev.gorillacraft.mixin;

import dev.gorillacraft.GorillaMotion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A gorilla's speed is how it really moves (Gorilla Tag moves it by position, reporting no motion). */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Inject(method = "getKnownSpeed", at = @At("HEAD"), cancellable = true)
	private void gorillacraft$gorillaSpeed(CallbackInfoReturnable<Vec3> cir) {
		Vec3 v = GorillaMotion.velocity((ServerPlayer) (Object) this);
		if (v != null) {
			cir.setReturnValue(v);
		}
	}
}
