package dev.gorillacraft;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players currently driven by Gorilla Tag. Shared by the client and the integrated server (same
 * UUID in singleplayer), which is what we want: both sides then skip collision for that player.
 */
public final class GorillaPlayers {
	private static final Set<UUID> GORILLAS = ConcurrentHashMap.newKeySet();

	/** A gorilla's player box: about the gorilla's head and chest, eyes near the top. */
	public static final net.minecraft.world.entity.EntityDimensions DIMENSIONS =
			net.minecraft.world.entity.EntityDimensions.scalable(0.5f, 0.9f).withEyeHeight(0.75f);

	private GorillaPlayers() {
	}

	public static void set(UUID id, boolean gorilla) {
		if (gorilla) {
			GORILLAS.add(id);
		} else {
			GORILLAS.remove(id);
		}
	}

	public static boolean isGorilla(UUID id) {
		return GORILLAS.contains(id);
	}
}
