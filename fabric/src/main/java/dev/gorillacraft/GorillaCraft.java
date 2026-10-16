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
		PayloadTypeRegistry.clientboundPlay().register(GorillaBonesPayload.TYPE, GorillaBonesPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GorillaPresencePayload.TYPE, (payload, context) -> {
			GorillaPlayers.set(context.player().getUUID(), payload.active());
			if (!payload.active()) {
				GorillaAvatars.remove(context.player().getUUID());
				context.player().setInvisible(false);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(GorillaPosePayload.TYPE, (payload, context) -> {
			GorillaAvatars.update(context.player(), payload);
			// The real Gorilla Tag gorilla for everyone else with GorillaCraft.
			if (payload.bones().length > 0 && GorillaPlayers.isGorilla(context.player().getUUID())) {
				var bones = new GorillaBonesPayload(context.player().getUUID(), payload.head(), payload.color(), payload.bones());
				for (var other : context.player().level().players()) {
					if (other != context.player() && ServerPlayNetworking.canSend(other, GorillaBonesPayload.TYPE)) {
						ServerPlayNetworking.send(other, bones);
					}
				}
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(GorillaMotion::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			GorillaMotion.forget(handler.player.getUUID());
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
