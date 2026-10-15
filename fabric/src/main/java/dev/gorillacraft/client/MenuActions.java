package dev.gorillacraft.client;

import dev.gorillacraft.GorillaCraft;
import dev.gorillacraft.link.FrameWriter;
import dev.gorillacraft.link.LinkServer;
import dev.gorillacraft.link.Proto;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * What Gorilla Tag's menu asks Minecraft to do: list the singleplayer worlds, open one, join a
 * server, or leave. Each goes through the same code Minecraft's own menus use. Switching from
 * one world to another first leaves the current one, then acts once Minecraft is back at the
 * title screen.
 */
final class MenuActions {
	private final LinkServer link;
	private final Consumer<String> status;

	private Runnable pending;
	private String pendingWhat;
	private boolean leaving;
	private Screen lastScreen;

	MenuActions(LinkServer link, Consumer<String> status) {
		this.link = link;
		this.status = status;
	}

	void listWorlds(Minecraft mc) {
		try {
			var levels = mc.getLevelSource();
			levels.loadLevelSummaries(levels.findLevelCandidates()).thenAcceptAsync(this::sendWorlds, mc);
		} catch (Exception e) {
			GorillaCraft.LOG.warn("Couldn't list worlds", e);
			sendWorlds(List.of());
		}
	}

	private void sendWorlds(List<LevelSummary> worlds) {
		List<LevelSummary> usable = worlds.stream()
				.filter(w -> !w.isDisabled() && !w.requiresManualConversion()) // locked = the one that's open now: keep it
				.sorted(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed())
				.limit(200)
				.toList();
		FrameWriter w = new FrameWriter(Proto.MC_WORLDS).i32(usable.size());
		for (LevelSummary s : usable) {
			w.str(s.getLevelId()).str(s.getLevelName());
		}
		link.send(w.finish());
	}

	/** Opens the world, or creates it (creative, cheats on) if there's none by that name. */
	void openWorld(Minecraft mc, String name) {
		String id = name.trim();
		if (id.isEmpty() || !mc.getLevelSource().isNewLevelIdAcceptable(id) && !mc.getLevelSource().levelExists(id)) {
			status.accept("Can't use that as a world name: " + id);
			return;
		}
		if (mc.getLevelSource().levelExists(id)) {
			schedule(mc, "Opening world " + id, () -> mc.createWorldOpenFlows().openWorld(id, () -> mc.gui.setScreen(new TitleScreen())));
		} else {
			schedule(mc, "Creating world " + id, () -> AutoWorld.create(mc, id, new TitleScreen()));
		}
	}

	void joinServer(Minecraft mc, String address) {
		String a = address.trim();
		if (!ServerAddress.isValidAddress(a)) {
			status.accept("Not a server address: " + a);
			return;
		}
		schedule(mc, "Joining " + a, () -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(a),
				new ServerData("GorillaCraft", a, ServerData.Type.OTHER), false, null));
	}

	void leave(Minecraft mc) {
		schedule(mc, "Leaving the world", null);
	}

	private void schedule(Minecraft mc, String what, Runnable action) {
		pending = action;
		pendingWhat = what;
		leaving = false;
		status.accept(what + "...");
		tick(mc);
	}

	void tick(Minecraft mc) {
		Screen screen = mc.gui.screen();
		if (screen != lastScreen) {
			lastScreen = screen;
			if (screen instanceof DisconnectedScreen) {
				status.accept("Minecraft: " + screen.getTitle().getString() + " (details in the Minecraft window)");
			}
		}
		if (pendingWhat == null) {
			return;
		}
		ClientLevel level = mc.level;
		if (level != null) {
			if (!leaving) {
				leaving = true;
				mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
			}
			return;
		}
		// Fully out of the old world (saved, disconnected, not mid-connection): do it.
		if (mc.getConnection() == null && mc.getSingleplayerServer() == null && !(screen instanceof ConnectScreen)) {
			Runnable action = pending;
			GorillaCraft.LOG.info("Menu: {}", pendingWhat);
			pending = null;
			pendingWhat = null;
			if (action != null) {
				action.run();
			} else if (!(screen instanceof TitleScreen)) {
				mc.gui.setScreen(new TitleScreen());
			}
		}
	}
}
