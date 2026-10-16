package dev.gorillacraft.mixin;

import dev.gorillacraft.GorillaPlayers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
}
