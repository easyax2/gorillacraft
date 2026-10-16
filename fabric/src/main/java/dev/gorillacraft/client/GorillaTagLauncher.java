package dev.gorillacraft.client;

import dev.gorillacraft.GorillaCraft;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * Starts Gorilla Tag when Minecraft starts. Gorilla Tag runs without VR of its own (the plugin
 * sees -gorillacraft and turns VR off), in a small window: it only moves the gorilla and gives
 * Minecraft its model. Waits a few seconds first, in case a Gorilla Tag that's already running
 * connects by itself.
 */
final class GorillaTagLauncher {
	static final String APP_ID = "1533390";
	private static final String[] GAME_ARGS = {"-gorillacraft", "-screen-width", "640", "-screen-height", "360", "-screen-fullscreen", "0"};
	private static final long WAIT_NANOS = 6_000_000_000L;

	private final long startAt = System.nanoTime() + WAIT_NANOS;
	private boolean done;

	void tick(Minecraft mc, GorillaSession session, boolean linkUp) {
		if (done || System.nanoTime() < startAt) {
			return;
		}
		done = true;
		if (!linkUp) {
			GorillaCraft.LOG.warn("GorillaCraft's port is taken (another Minecraft with GorillaCraft is running?): not starting Gorilla Tag");
			return;
		}
		if (session.connected()) {
			GorillaCraft.LOG.info("Gorilla Tag is already running");
			return;
		}
		launch();
	}

	static void launch() {
		try {
			List<String> cmd = new ArrayList<>();
			if (Util.getPlatform() == Util.OS.WINDOWS) {
				String steam = windowsSteam();
				if (steam == null) {
					openUrl();
					return;
				}
				cmd.add(steam);
			} else {
				cmd.add("steam");
			}
			cmd.add("-applaunch");
			cmd.add(APP_ID);
			cmd.addAll(List.of(GAME_ARGS));
			new ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			GorillaCraft.LOG.info("Starting Gorilla Tag: {}", String.join(" ", cmd));
		} catch (Exception e) {
			GorillaCraft.LOG.warn("Couldn't run Steam directly ({}), asking the desktop to open Gorilla Tag", e.toString());
			openUrl();
		}
	}

	/** steam://run/<app>//<args>/ through the desktop's URL handler (Steam must be installed). */
	private static void openUrl() {
		String url = "steam://run/" + APP_ID + "//" + String.join(" ", GAME_ARGS) + "/";
		List<String> cmd = Util.getPlatform() == Util.OS.WINDOWS ? List.of("cmd", "/c", "start", "", url) : List.of("xdg-open", url);
		try {
			new ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			GorillaCraft.LOG.info("Starting Gorilla Tag: {}", url);
		} catch (Exception e) {
			GorillaCraft.LOG.error("Couldn't start Gorilla Tag; start it from Steam yourself", e);
		}
	}

	private static String windowsSteam() {
		List<String> tries = new ArrayList<>();
		try {
			Process p = new ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamExe").start();
			String out = new String(p.getInputStream().readAllBytes());
			int i = out.indexOf("REG_SZ");
			if (i >= 0) {
				tries.add(out.substring(i + 6).trim().replace('/', File.separatorChar));
			}
		} catch (Exception ignored) {
		}
		tries.add("C:\\Program Files (x86)\\Steam\\steam.exe");
		tries.add("C:\\Program Files\\Steam\\steam.exe");
		for (String t : tries) {
			if (!t.isEmpty() && Files.exists(Path.of(t))) {
				return t;
			}
		}
		return null;
	}
}
