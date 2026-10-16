package dev.gorillacraft;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;

/**
 * Server side: how a gorilla really moves. Gorilla Tag moves the player by position only, so
 * Minecraft sees no walking (no hunger) and no speed (spears need it). Every tick this measures
 * each gorilla's movement from its position:
 *
 * - Hunger: every 10 seconds, the distance travelled (speed × time) costs a little food
 *   exhaustion, EXHAUSTION_PER_BLOCK per block: about 400 blocks per half drumstick. Sprinting
 *   costs 10 times more in vanilla.
 * - Speed: what the player's known speed reports (KineticWeapon reads it), so a spear's charge
 *   hits as hard as the gorilla is moving.
 * - Spears: the charge attack is always on while a gorilla holds one, no need to hold use.
 */
public final class GorillaMotion {
	public static final float EXHAUSTION_PER_BLOCK = 0.01f;
	private static final int HUNGER_INTERVAL_TICKS = 200;
	/** Further than this in one tick is a teleport, not movement. */
	private static final double TELEPORT = 8.0;

	private static final class State {
		Vec3 last;
		Vec3 velocity = Vec3.ZERO; // blocks per tick
		double distance;
		int ticks;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private GorillaMotion() {
	}

	/** Blocks per tick, or null when the player isn't a gorilla. */
	public static Vec3 velocity(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && GorillaPlayers.isGorilla(player.getUUID()) ? s.velocity : null;
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!GorillaPlayers.isGorilla(player.getUUID())) {
				STATES.remove(player.getUUID());
				continue;
			}
			State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
			Vec3 pos = player.position();
			if (s.last != null) {
				Vec3 d = pos.subtract(s.last);
				if (d.length() < TELEPORT) {
					s.velocity = d;
					s.distance += d.length();
				} else {
					s.velocity = Vec3.ZERO;
				}
			}
			s.last = pos;
			if (++s.ticks >= HUNGER_INTERVAL_TICKS) {
				if (s.distance > 0) {
					player.causeFoodExhaustion((float) (s.distance * EXHAUSTION_PER_BLOCK));
				}
				s.ticks = 0;
				s.distance = 0;
			}
			spear(player);
		}
	}

	/** The held spear's charge check, every tick (when not already charging the vanilla way). */
	private static void spear(ServerPlayer player) {
		if (player.isUsingItem() || player.isSpectator()) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		KineticWeapon weapon = stack.get(DataComponents.KINETIC_WEAPON);
		if (weapon == null) {
			return;
		}
		// As if the charge had just started (past its delay): the conditions' time limits never run out.
		int ticksRemaining = stack.getUseDuration(player) - weapon.delayTicks();
		weapon.damageEntities(stack, ticksRemaining, player, EquipmentSlot.MAINHAND);
	}

	public static void forget(UUID id) {
		STATES.remove(id);
	}
}
