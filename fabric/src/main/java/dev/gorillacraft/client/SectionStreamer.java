package dev.gorillacraft.client;

import dev.gorillacraft.link.FrameWriter;
import dev.gorillacraft.link.LinkServer;
import dev.gorillacraft.link.Proto;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;

/**
 * Keeps Gorilla Tag's copy of the world in step with Minecraft's: sends sections that come into
 * range or change, and removes the ones that leave range. Change notifications come from
 * LevelExtractorMixin, the same hook that makes Minecraft rebuild its own section meshes.
 */
final class SectionStreamer {
	/** Don't queue more while this much is still waiting to go out to Gorilla Tag. */
	private static final long MAX_PENDING_BYTES = 6L << 20;

	private static final LongOpenHashSet DIRTY = new LongOpenHashSet();

	private final LongOpenHashSet sent = new LongOpenHashSet();
	private final SectionMesher mesher = new SectionMesher();
	private final LongArrayList candidates = new LongArrayList();

	/** Called on the client thread whenever Minecraft marks a section for re-render. */
	static void markDirty(int x, int y, int z) {
		DIRTY.add(SectionPos.asLong(x, y, z));
	}

	void reset() {
		sent.clear();
		DIRTY.clear();
	}

	void tick(ClientLevel level, Entity center, LinkServer link, ClientConfig cfg) {
		int cx = SectionPos.blockToSectionCoord(center.getBlockX());
		int cy = SectionPos.blockToSectionCoord(center.getBlockY());
		int cz = SectionPos.blockToSectionCoord(center.getBlockZ());
		int r = cfg.radius;
		int vr = cfg.verticalRadius;
		int minY = Math.max(level.getMinSectionY(), cy - vr);
		int maxY = Math.min(level.getMaxSectionY(), cy + vr);

		// Out of range: tell Gorilla Tag to drop it.
		var it = sent.iterator();
		while (it.hasNext()) {
			long s = it.nextLong();
			if (!inRange(s, cx, cz, r, minY, maxY)) {
				it.remove();
				link.send(new FrameWriter(Proto.MC_SECTION_REMOVE, 16)
						.i32(SectionPos.x(s)).i32(SectionPos.y(s)).i32(SectionPos.z(s)).finish());
			}
		}

		// Changed sections we've sent (or will send) go first, then new ones, nearest first.
		candidates.clear();
		var dit = DIRTY.iterator();
		while (dit.hasNext()) {
			long s = dit.nextLong();
			if (inRange(s, cx, cz, r, minY, maxY)) {
				if (sent.contains(s)) {
					candidates.add(s);
				}
			} else {
				dit.remove();
			}
		}
		int dirtyCount = candidates.size();
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > r * r) {
					continue;
				}
				for (int y = minY; y <= maxY; y++) {
					long s = SectionPos.asLong(x, y, z);
					if (!sent.contains(s)) {
						candidates.add(s);
					}
				}
			}
		}
		candidates.subList(dirtyCount, candidates.size()).sort((a, b) -> Integer.compare(dist2(a, cx, cy, cz), dist2(b, cx, cy, cz)));

		int budget = cfg.sectionsPerTick;
		for (int i = 0; i < candidates.size() && budget > 0 && link.pendingBytes() < MAX_PENDING_BYTES; i++) {
			long s = candidates.getLong(i);
			byte[] frame = mesher.mesh(level, SectionPos.x(s), SectionPos.y(s), SectionPos.z(s));
			if (frame == null) {
				continue; // chunk not loaded yet
			}
			link.send(frame);
			sent.add(s);
			DIRTY.remove(s);
			budget--;
		}
	}

	private static boolean inRange(long s, int cx, int cz, int r, int minY, int maxY) {
		int dx = SectionPos.x(s) - cx;
		int dz = SectionPos.z(s) - cz;
		int y = SectionPos.y(s);
		return dx * dx + dz * dz <= r * r && y >= minY && y <= maxY;
	}

	private static int dist2(long s, int cx, int cy, int cz) {
		int dx = SectionPos.x(s) - cx;
		int dy = SectionPos.y(s) - cy;
		int dz = SectionPos.z(s) - cz;
		return dx * dx + dy * dy + dz * dz;
	}
}
