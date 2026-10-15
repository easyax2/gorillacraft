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
	}
}
