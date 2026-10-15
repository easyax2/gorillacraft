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
		c.radius = Math.clamp(intProp(p, "radius", c.radius), 1, 16);
		c.verticalRadius = Math.clamp(intProp(p, "verticalRadius", c.verticalRadius), 1, 24);
		c.sectionsPerTick = Math.clamp(intProp(p, "sectionsPerTick", c.sectionsPerTick), 1, 64);
		try (Writer w = Files.newBufferedWriter(file)) {
			p.store(w, "GorillaCraft (Minecraft side). port must match Port in Gorilla Tag's BepInEx/config/dev.gorillacraft.cfg");
		} catch (IOException e) {
			GorillaCraft.LOG.warn("Couldn't write {}", file, e);
		}
		return c;
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
