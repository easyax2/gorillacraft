package dev.gorillacraft;

import com.mojang.math.Transformation;
import dev.gorillacraft.mixin.BlockDisplayAccess;
import dev.gorillacraft.mixin.DisplayAccess;
import dev.gorillacraft.mixin.ItemDisplayAccess;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Gorilla Tag's gorillas, drawn in Minecraft. Every player driven by Gorilla Tag becomes a
 * Minecraft-style gorilla made of display entities, posed from the gorilla's real head and hands:
 * a head with a dark face, a body, long arms reaching to where the hands are, dark hands and the
 * held item. Display entities are plain Minecraft, so everyone on the server sees the gorillas,
 * even players without GorillaCraft; the player's own Minecraft model is hidden.
 *
 * The parts are named "gorillacraft:&lt;uuid&gt;" so clients can recognise them (the owner doesn't
 * draw its own, Gorilla Tag already shows your hands without delay), and they're never saved:
 * leftovers are removed when they load.
 */
public final class GorillaAvatars {
	public static final String NAME_PREFIX = "gorillacraft:";
	private static final int INTERPOLATION_TICKS = 2;

	private static final Map<UUID, Avatar> AVATARS = new HashMap<>();
	/** True while an avatar's parts are being added (they "load" before they're registered). */
	private static boolean spawning;

	private GorillaAvatars() {
	}

	private enum Part {
		HEAD, FACE, BODY, LEFT_ARM, RIGHT_ARM, LEFT_HAND, RIGHT_HAND
	}

	private static final class Avatar {
		final Map<Part, Display.BlockDisplay> parts = new HashMap<>();
		Display.ItemDisplay held;
		ServerLevel level;
		int color = -1;
		ItemStack lastHeld = ItemStack.EMPTY;
	}

	/** Server thread: a gorilla's new pose. */
	public static void update(ServerPlayer player, GorillaPosePayload pose) {
		if (!GorillaPlayers.isGorilla(player.getUUID())) {
			return;
		}
		ServerLevel level = player.level();
		Avatar a = AVATARS.get(player.getUUID());
		if (a != null && a.level != level) {
			remove(player.getUUID()); // changed dimension
			a = null;
		}
		if (a == null) {
			a = spawn(player, level);
			AVATARS.put(player.getUUID(), a);
		}
		if (a.color != pose.color()) {
			a.color = pose.color();
			BlockState fur = furBlock(pose.color());
			for (Part p : new Part[] {Part.HEAD, Part.BODY, Part.LEFT_ARM, Part.RIGHT_ARM}) {
				((BlockDisplayAccess) a.parts.get(p)).gorillacraft$setBlockState(fur);
			}
		}
		ItemStack hand = player.getMainHandItem();
		if (!ItemStack.matches(hand, a.lastHeld)) {
			a.lastHeld = hand.copy();
			((ItemDisplayAccess) a.held).gorillacraft$setItemStack(a.lastHeld);
		}
		pose(a, pose);
	}

	private static Avatar spawn(ServerPlayer player, ServerLevel level) {
		spawning = true;
		try {
			return spawnParts(player, level);
		} finally {
			spawning = false;
		}
	}

	private static Avatar spawnParts(ServerPlayer player, ServerLevel level) {
		Avatar a = new Avatar();
		a.level = level;
		Component name = Component.literal(NAME_PREFIX + player.getStringUUID());
		for (Part p : Part.values()) {
			Display.BlockDisplay d = net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			BlockState block = block(p == Part.FACE || p == Part.LEFT_HAND || p == Part.RIGHT_HAND ? "black_concrete" : "black_wool");
			((BlockDisplayAccess) d).gorillacraft$setBlockState(block);
			setUp(d, name, player.getEyePosition());
			level.addFreshEntity(d);
			a.parts.put(p, d);
		}
		a.held = net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		((ItemDisplayAccess) a.held).gorillacraft$setItemTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
		setUp(a.held, name, player.getEyePosition());
		level.addFreshEntity(a.held);
		return a;
	}

	private static void setUp(Display d, Component name, Vec3 at) {
		d.setCustomName(name);
		d.setCustomNameVisible(false);
		d.snapTo(at);
		DisplayAccess access = (DisplayAccess) d;
		access.gorillacraft$setPosRotInterpolationDuration(INTERPOLATION_TICKS);
		access.gorillacraft$setViewRange(2f);
		// Culling box: the parts reach well beyond the entity's own position.
		access.gorillacraft$setWidth(4f);
		access.gorillacraft$setHeight(4f);
	}

	/**
	 * Places every part around the head. All parts sit at the head's position; their shapes come
	 * from their transformations, which clients interpolate between updates.
	 */
	private static void pose(Avatar a, GorillaPosePayload p) {
		Vec3 head = p.head();
		float yaw = (float) Math.toRadians(p.yaw());
		float pitch = (float) Math.toRadians(p.pitch());
		Vector3f forward = new Vector3f((float) -Math.sin(yaw), 0, (float) Math.cos(yaw));
		Vector3f right = new Vector3f((float) -Math.cos(yaw), 0, (float) -Math.sin(yaw));
		Quaternionf facing = new Quaternionf().rotateY(-yaw);
		Quaternionf looking = new Quaternionf().rotateY(-yaw).rotateX(pitch);

		// Head and face.
		box(a, Part.HEAD, head, new Vector3f(), looking, 0.30f, 0.30f, 0.30f);
		Vector3f faceAt = looking.transform(new Vector3f(0, -0.03f, 0.15f));
		box(a, Part.FACE, head, faceAt, looking, 0.22f, 0.16f, 0.02f);

		// Body hangs below the head, leaning a little forward like a gorilla.
		Vector3f bodyAt = new Vector3f(0, -0.40f, 0).add(new Vector3f(forward).mul(-0.04f));
		box(a, Part.BODY, head, bodyAt, facing, 0.46f, 0.52f, 0.34f);

		// Arms from the shoulders to the hands.
		Vector3f shoulderL = new Vector3f(bodyAt).add(0, 0.20f, 0).add(new Vector3f(right).mul(-0.26f));
		Vector3f shoulderR = new Vector3f(bodyAt).add(0, 0.20f, 0).add(new Vector3f(right).mul(0.26f));
		Vector3f handL = rel(p.leftHand(), head, shoulderL);
		Vector3f handR = rel(p.rightHand(), head, shoulderR);
		limb(a, Part.LEFT_ARM, head, shoulderL, handL, 0.13f);
		limb(a, Part.RIGHT_ARM, head, shoulderR, handR, 0.13f);
		box(a, Part.LEFT_HAND, head, handL, facing, 0.15f, 0.15f, 0.15f);
		box(a, Part.RIGHT_HAND, head, handR, facing, 0.15f, 0.15f, 0.15f);

		// Held item in the right hand.
		move(a.held, head);
		Matrix4f m = new Matrix4f().translate(handR).rotate(facing).scale(0.45f);
		transform(a.held, m);
	}

	/** A hand position relative to the head; if it's missing or absurdly far, a resting hand below the shoulder. */
	private static Vector3f rel(Vec3 hand, Vec3 head, Vector3f shoulder) {
		Vector3f v = new Vector3f((float) (hand.x - head.x), (float) (hand.y - head.y), (float) (hand.z - head.z));
		if (!Float.isFinite(v.x) || v.distance(shoulder) > 1.6f) {
			return new Vector3f(shoulder).add(0, -0.55f, 0);
		}
		return v;
	}

	private static void box(Avatar a, Part part, Vec3 head, Vector3f center, Quaternionf rotation, float sx, float sy, float sz) {
		Display d = a.parts.get(part);
		move(d, head);
		transform(d, new Matrix4f().translate(center).rotate(rotation).scale(sx, sy, sz).translate(-0.5f, -0.5f, -0.5f));
	}

	/** A cuboid from one point to another, {@code thickness} across. */
	private static void limb(Avatar a, Part part, Vec3 head, Vector3f from, Vector3f to, float thickness) {
		Vector3f dir = new Vector3f(to).sub(from);
		float length = Math.max(dir.length(), 0.01f);
		Quaternionf along = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir.normalize());
		Display d = a.parts.get(part);
		move(d, head);
		transform(d, new Matrix4f().translate(from).rotate(along).scale(thickness, length, thickness).translate(-0.5f, 0f, -0.5f));
	}

	private static void move(Display d, Vec3 head) {
		if (d.position().distanceToSqr(head) > 1e-6) {
			d.snapTo(head);
		}
	}

	private static void transform(Display d, Matrix4f m) {
		DisplayAccess access = (DisplayAccess) d;
		access.gorillacraft$setTransformation(new Transformation(m));
		access.gorillacraft$setTransformationInterpolationDelay(0);
		access.gorillacraft$setTransformationInterpolationDuration(INTERPOLATION_TICKS);
	}

	private static BlockState block(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	/** The wool closest to the gorilla's Gorilla Tag colour (0xRRGGBB). */
	private static BlockState furBlock(int rgb) {
		DyeColor best = DyeColor.BLACK;
		long bestDist = Long.MAX_VALUE;
		for (DyeColor c : DyeColor.values()) {
			int t = c.getTextureDiffuseColor();
			long dr = ((t >> 16) & 255) - ((rgb >> 16) & 255), dg = ((t >> 8) & 255) - ((rgb >> 8) & 255), db = (t & 255) - (rgb & 255);
			long dist = dr * dr * 3 + dg * dg * 4 + db * db * 2;
			if (dist < bestDist) {
				bestDist = dist;
				best = c;
			}
		}
		return block(best.getSerializedName() + "_wool");
	}

	/** The gorilla left (stopped playing, disconnected): take its avatar away. */
	public static void remove(UUID id) {
		Avatar a = AVATARS.remove(id);
		if (a == null) {
			return;
		}
		for (Display d : a.parts.values()) {
			d.discard();
		}
		a.held.discard();
	}

	/** Avatar parts are never kept: anything named like one that loads from disk is leftover. */
	public static boolean isLeftover(Entity e) {
		if (spawning || !(e instanceof Display) || e.getCustomName() == null) {
			return false;
		}
		String name = e.getCustomName().getString();
		if (!name.startsWith(NAME_PREFIX)) {
			return false;
		}
		try {
			Avatar a = AVATARS.get(UUID.fromString(name.substring(NAME_PREFIX.length())));
			return a == null || !a.parts.containsValue(e) && a.held != e;
		} catch (IllegalArgumentException notAUuid) {
			return false;
		}
	}
}
