package dev.gorillacraft.mixin.client;

import dev.gorillacraft.GorillaAvatars;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gorillas instead of players: a player who has a gorilla avatar isn't drawn (no Steve, no
 * floating held item; the avatar shows both), and your own avatar isn't drawn for you (Gorilla
 * Tag shows your own hands without Minecraft's delay).
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	@Unique
	private static final Set<UUID> gorillacraft$gorillas = ConcurrentHashMap.newKeySet();

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void gorillacraft$gorillas(E entity, Frustum frustum, double x, double y, double z, float partialTick,
			CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Display && entity.getCustomName() != null) {
			String name = entity.getCustomName().getString();
			if (name.startsWith(GorillaAvatars.NAME_PREFIX)) {
				try {
					UUID owner = UUID.fromString(name.substring(GorillaAvatars.NAME_PREFIX.length()));
					gorillacraft$gorillas.add(owner);
					var me = Minecraft.getInstance().player;
					boolean mine = me != null && me.getUUID().equals(owner);
					if (mine && System.getenv("GORILLACRAFT_SHOW_OWN_AVATAR") == null
							|| dev.gorillacraft.client.GorillaMeshes.drawsReal(owner) && !(entity instanceof Display.ItemDisplay)) {
						// Yours (VR draws your held item itself), or drawn here as the real Gorilla Tag
						// gorilla, which keeps the avatar's held item in its hand.
						cir.setReturnValue(false);
					}
				} catch (IllegalArgumentException ignored) {
				}
			}
		} else if (entity instanceof Player p && p.isInvisible() && gorillacraft$gorillas.contains(p.getUUID())) {
			cir.setReturnValue(false);
		}
	}
}
