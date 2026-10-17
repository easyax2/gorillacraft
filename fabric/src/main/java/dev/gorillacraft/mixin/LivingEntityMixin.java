package dev.gorillacraft.mixin;

import dev.gorillacraft.GorillaPlayers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gorillas are made invisible only so other players see the gorilla instead of a Minecraft
 * player model. Mobs should still see them: hostile mobs hunt them like any player.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Redirect(method = "getVisibilityPercent", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInvisible()Z"))
	private boolean gorillacraft$mobsSeeGorillas(LivingEntity self) {
		if (self instanceof Player p && GorillaPlayers.isGorilla(p.getUUID())) {
			return false;
		}
		return self.isInvisible();
	}

	/**
	 * A gorilla's player box is put around Gorilla Tag's head, and Gorilla Tag keeps the head out
	 * of walls, but the box is wider than the gorilla's head: next to a wall part of it pokes in and
	 * Minecraft would suffocate you. Gorillas don't take wall damage.
	 */
	@Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
	private void gorillacraft$noWallDamage(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player p && GorillaPlayers.isGorilla(p.getUUID())) {
			cir.setReturnValue(false);
		}
	}
}
