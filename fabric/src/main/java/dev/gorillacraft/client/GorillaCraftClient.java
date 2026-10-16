package dev.gorillacraft.client;

import dev.gorillacraft.link.LinkServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class GorillaCraftClient implements ClientModInitializer {
	private static GorillaSession session;

	public static GorillaSession session() {
		return session;
	}

	/** Forwarded from LevelExtractorMixin. */
	public static void sectionDirty(int x, int y, int z) {
		SectionStreamer.markDirty(x, y, z);
	}

	@Override
	public void onInitializeClient() {
		ClientConfig cfg = ClientConfig.load();
		LinkServer link = new LinkServer(cfg.port);
		link.start();
		session = new GorillaSession(link, cfg);
		ClientTickEvents.END_CLIENT_TICK.register(session::tick);
		AutoWorld autoWorld = new AutoWorld();
		ClientTickEvents.END_CLIENT_TICK.register(autoWorld::tick);
		// Other gorillas as Gorilla Tag's real model.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.gorillacraft.GorillaBonesPayload.TYPE,
				(payload, context) -> GorillaMeshes.setPose(payload.owner(), payload.ref(), payload.color(), payload.bones()));
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(GorillaMeshes::render);
	}
}
