package dev.gorillacraft.mixin;

import dev.gorillacraft.GorillaPlayers;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A gorilla's player box is gorilla-sized, around the head: Minecraft puts the player's eyes at
 * the gorilla's head, and a normal 1.8-block player hanging below that dips into lava, fire,
 * walls and cactus the gorilla is nowhere near.
 */
@Mixin(Avatar.class)
abstract class AvatarMixin {
	@Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
	private void gorillacraft$gorillaSize(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if ((Object) this instanceof Player p && GorillaPlayers.isGorilla(p.getUUID())) {
			cir.setReturnValue(GorillaPlayers.DIMENSIONS);
		}
	}
}
