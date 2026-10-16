package dev.gorillacraft.client;

import dev.gorillacraft.GorillaCraft;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** config/gorillacraft.properties, written with defaults on first start. */
public final class ClientConfig {
	/** Loopback port Gorilla Tag connects to. */
	public int port = 47123;
	/** Horizontal streaming radius around the player, in chunks. */
	public int radius = 4;
	/** Vertical streaming radius around the player, in sections (16 blocks). */
	public int verticalRadius = 4;
	/** Sections meshed and sent per client tick at most. */
	public int sectionsPerTick = 6;
	/** Play in VR through OpenXR (WiVRn, SteamVR...) when a headset is there. */
	public boolean vr = true;
	/** Eye resolution, relative to what the headset's runtime recommends. */
	public double vrResolution = 1.0;
	/** Start Gorilla Tag (through Steam) when Minecraft starts and it isn't running yet. */
	public boolean launchGorillaTag = true;

	public static ClientConfig load() {
		ClientConfig c = new ClientConfig();
		Path file = FabricLoader.getInstance().getConfigDir().resolve("gorillacraft.properties");
		Properties p = new Properties();
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file)) {
				p.load(r);
			} catch (IOException e) {
				GorillaCraft.LOG.warn("Couldn't read {}, using defaults", file, e);
			}
		}
		c.port = intProp(p, "port", c.port);
		String envPort = System.getenv("GORILLACRAFT_PORT"); // overrides the file, e.g. for tests or a second instance
		if (envPort != null && envPort.matches("\\d+")) {
			c.port = Integer.parseInt(envPort);
		}
		c.radius = Math.clamp(intProp(p, "radius", c.radius), 1, 16);
		c.verticalRadius = Math.clamp(intProp(p, "verticalRadius", c.verticalRadius), 1, 24);
		c.sectionsPerTick = Math.clamp(intProp(p, "sectionsPerTick", c.sectionsPerTick), 1, 64);
		for (String old : new String[] {"overlayWidth", "overlayGrid", "overlayFov"}) {
			p.remove(old);
		}
		c.vr = boolProp(p, "vr", c.vr);
		c.vrResolution = Math.clamp(doubleProp(p, "vrResolution", c.vrResolution), 0.3, 2.0);
		c.launchGorillaTag = boolProp(p, "launchGorillaTag", c.launchGorillaTag);
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			// Tests and dev runs: no headset session and no Gorilla Tag unless asked for.
			c.vr = System.getenv("GORILLACRAFT_VR") != null;
			c.launchGorillaTag = System.getenv("GORILLACRAFT_LAUNCH") != null;
		}
		try (Writer w = Files.newBufferedWriter(file)) {
			p.store(w, "GorillaCraft (Minecraft side). port must match Port in Gorilla Tag's BepInEx/config/dev.gorillacraft.cfg");
		} catch (IOException e) {
			GorillaCraft.LOG.warn("Couldn't write {}", file, e);
		}
		return c;
	}

	private static boolean boolProp(Properties p, String key, boolean def) {
		String v = p.getProperty(key, Boolean.toString(def)).trim();
		boolean b = v.equalsIgnoreCase("true") || !v.equalsIgnoreCase("false") && def;
		p.setProperty(key, Boolean.toString(b));
		return b;
	}

	private static double doubleProp(Properties p, String key, double def) {
		try {
			double v = Double.parseDouble(p.getProperty(key, Double.toString(def)).trim());
			p.setProperty(key, Double.toString(v));
			return v;
		} catch (NumberFormatException e) {
			p.setProperty(key, Double.toString(def));
			return def;
		}
	}

	private static int intProp(Properties p, String key, int def) {
		try {
			int v = Integer.parseInt(p.getProperty(key, Integer.toString(def)).trim());
			p.setProperty(key, Integer.toString(v));
			return v;
		} catch (NumberFormatException e) {
			p.setProperty(key, Integer.toString(def));
			return def;
		}
	}
}
