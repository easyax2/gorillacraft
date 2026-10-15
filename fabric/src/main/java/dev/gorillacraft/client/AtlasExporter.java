package dev.gorillacraft.client;

import dev.gorillacraft.GorillaCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Exports Minecraft's block atlas as a PNG, using the same dump F3+S uses. The block quads'
 * UVs point into this atlas, so Gorilla Tag can texture them without knowing anything about
 * sprites. The dump reads the texture back from the GPU asynchronously, so {@link #poll()} is
 * called each tick until the file has been written.
 */
final class AtlasExporter {
	private static final int TIMEOUT_TICKS = 200;

	private Path dir;
	private int waited;
	private long lastSize = -1;

	boolean pending() {
		return dir != null;
	}

	void request() {
		cleanup();
		try {
			AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
			if (!(texture instanceof TextureAtlas atlas)) {
				GorillaCraft.LOG.error("Block atlas is {}, not a TextureAtlas", texture);
				return;
			}
			dir = Files.createTempDirectory("gorillacraft-atlas");
			atlas.dumpContents(TextureAtlas.LOCATION_BLOCKS, dir);
			waited = 0;
			lastSize = -1;
		} catch (IOException e) {
			GorillaCraft.LOG.error("Couldn't dump the block atlas", e);
			cleanup();
		}
	}

	/** @return the PNG once it's completely written, else null. */
	byte[] poll() {
		if (dir == null) {
			return null;
		}
		if (++waited > TIMEOUT_TICKS) {
			GorillaCraft.LOG.error("Block atlas dump never appeared in {}", dir);
			cleanup();
			return null;
		}
		try (Stream<Path> files = Files.list(dir)) {
			// Mip level 0 is the largest image.
			Optional<Path> png = files.filter(p -> p.toString().endsWith(".png"))
					.max(Comparator.comparingLong(p -> p.toFile().length()));
			if (png.isEmpty()) {
				return null;
			}
			long size = png.get().toFile().length();
			if (size == 0 || size != lastSize) {
				lastSize = size; // still being written, or just appeared: check again next tick
				return null;
			}
			byte[] bytes = Files.readAllBytes(png.get());
			GorillaCraft.LOG.info("Block atlas exported ({} KiB)", bytes.length / 1024);
			cleanup();
			return bytes;
		} catch (IOException e) {
			GorillaCraft.LOG.error("Couldn't read the block atlas dump", e);
			cleanup();
			return null;
		}
	}

	private void cleanup() {
		if (dir == null) {
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path p : files.toList()) {
				Files.deleteIfExists(p);
			}
			Files.deleteIfExists(dir);
		} catch (IOException ignored) {
		}
		dir = null;
	}
}
