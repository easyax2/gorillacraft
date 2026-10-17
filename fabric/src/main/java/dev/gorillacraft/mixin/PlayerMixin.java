package dev.gorillacraft.mixin;

import dev.gorillacraft.GorillaPlayers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerMixin {
	/**
	 * Player.tick resets noPhysics to isSpectator() every tick. For a gorilla we set it back, so
	 * the server's "moved wrongly" check (skipped for noPhysics) accepts Gorilla Tag's position,
	 * and Gorilla Tag's falls never turn into Minecraft fall damage.
	 */
	@Inject(method = "tick", at = @At("TAIL"))
	private void gorillacraft$gorillaPhysics(CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		boolean gorilla = GorillaPlayers.isGorilla(self.getUUID());
		// Gorilla-sized box (AvatarMixin) while a gorilla, the normal one otherwise.
		if (gorilla != (self.getBbHeight() == GorillaPlayers.DIMENSIONS.height())) {
			self.refreshDimensions();
		}
		if (gorilla) {
			self.noPhysics = true;
			self.resetFallDistance();
			// Others see a gorilla (GorillaAvatars), not this player's own model.
			if (!self.level().isClientSide() && !self.isInvisible()) {
				self.setInvisible(true);
			}
		}
	}
}
