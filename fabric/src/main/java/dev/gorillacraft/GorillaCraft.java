package dev.gorillacraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (client + server) half. Its only job is the "this player is a gorilla" flag: a player
 * whose movement comes from Gorilla Tag ignores Minecraft collision, so the server must not
 * rubber-band them for walking through a block edge the gorilla is hugging.
 */
public class GorillaCraft implements ModInitializer {
	public static final String MOD_ID = "gorillacraft";
	public static final Logger LOG = LoggerFactory.getLogger("GorillaCraft");

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(GorillaPresencePayload.TYPE, GorillaPresencePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GorillaPosePayload.TYPE, GorillaPosePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GorillaPresencePayload.TYPE, (payload, context) -> {
			GorillaPlayers.set(context.player().getUUID(), payload.active());
			if (!payload.active()) {
				GorillaAvatars.remove(context.player().getUUID());
				context.player().setInvisible(false);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(GorillaPosePayload.TYPE, (payload, context) -> GorillaAvatars.update(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			GorillaPlayers.set(handler.player.getUUID(), false);
			GorillaAvatars.remove(handler.player.getUUID());
		});
		// Avatar parts are never kept: discard any that come back from disk.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (GorillaAvatars.isLeftover(entity)) {
				entity.discard();
			}
		});
	}
}
