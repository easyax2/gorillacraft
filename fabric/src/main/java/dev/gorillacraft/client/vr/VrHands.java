package dev.gorillacraft.client.vr;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Your hands in VR, Minecraft's way:
 *
 * - The controllers are Minecraft's crosshair: what the hand you're using points at becomes
 *   Minecraft's hit result (so Minecraft draws the block outline).
 * - Right trigger: use (right-click): place blocks, open doors, eat, draw a bow.
 * - Left trigger: attack (left-click): mine the block you point at for as long as you hold it,
 *   or hit the mob.
 * - Room-scale: punch a block with either real hand (moving into it faster than PUNCH_SPEED)
 *   and Minecraft mines it for a moment, like Gorilla Tag's punches. Your real hands, not the
 *   gorilla's (which stop at the surface).
 * - The held item is in your right hand.
 */
public final class VrHands {
	private static final ItemStackRenderState HELD = new ItemStackRenderState();
	/**
	 * Player-model hand frame → OpenXR grip frame. Grip: -Z runs through the fist from little
	 * finger to thumb (forward, holding a controller), +X out of the back of the right hand, +Y up
	 * along the forearm. Player model, arm hanging: +Y down the arm (grip -Y), -Z forward (grip -Z),
	 * +X the player's left (grip -X). So a half turn about Z.
	 */
	private static final Quaternionf MODEL_TO_GRIP = new Quaternionf().rotationZ((float) Math.PI);

	/** Metres per second into a block that count as a punch, and how long each punch mines. */
	private static final double PUNCH_SPEED = 3.0;
	private static final long PUNCH_NANOS = 400_000_000L;

	private static boolean useHeld;
	private static boolean breaking;
	/** Which controller is the crosshair: the left while its trigger breaks, else the right. */
	private static int aimHand = 1;

	// Punches: last real-hand position per hand (world) and whether it was inside a block.
	private static final Vec3[] lastHand = new Vec3[2];
	private static final boolean[] wasInside = new boolean[2];
	private static long lastHandTime;
	private static BlockPos punchPos;
	private static net.minecraft.core.Direction punchFace;
	private static long punchUntil;

	// Mining state (client tick).
	private static BlockPos miningPos;
	private static boolean attackWasDown;

	private VrHands() {
	}

	private static XrInput input(VrRenderer vr) {
		return vr != null && vr.active() ? vr.xr().input() : null;
	}

	/** Minecraft.pick: the right controller's ray instead of the head's. */
	public static void pick(Minecraft mc) {
		VrRenderer vr = VrRenderer.get();
		XrInput in = input(vr);
		Player player = mc.player;
		int h = aimHand;
		if (in == null || player == null || mc.level == null || !in.aimValid[h]) {
			return;
		}
		Vector3d o = vr.toWorld(in.aimPos[h], new Vector3d());
		Vector3f d = vr.toWorld(in.aimRot[h], new Quaternionf()).transform(new Vector3f(0, 0, -1));
		Vec3 from = new Vec3(o.x, o.y, o.z), dir = new Vec3(d.x, d.y, d.z).normalize();
		double reach = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
		Vec3 to = from.add(dir.scale(reach));
		HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		double blockDist = hit.getType() == HitResult.Type.MISS ? reach : hit.getLocation().distanceTo(from);
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, from, from.add(dir.scale(blockDist)),
				new AABB(from, to).inflate(1), e -> !e.isSpectator() && e.isPickable() && e != player, blockDist * blockDist);
		if (entity != null && entity.getLocation().distanceTo(from) <= player.entityInteractionRange()) {
			mc.hitResult = entity;
			mc.crosshairPickEntity = entity.getEntity();
		} else {
			mc.hitResult = hit.getType() == HitResult.Type.BLOCK && blockDist <= player.blockInteractionRange() ? hit
					: BlockHitResult.miss(to, net.minecraft.core.Direction.getApproximateNearest(dir), BlockPos.containing(to));
			mc.crosshairPickEntity = null;
		}
	}

	/** Each frame: triggers and punches (while no screen is open). */
	static void update(Minecraft mc, XrInput in, boolean screen) {
		boolean active = !screen && mc.player != null && mc.level != null;
		boolean down = active && in.trigger_[1] > 0.6f;
		if (down != useHeld) {
			useHeld = down;
			mc.options.keyUse.setDown(down);
		}
		breaking = active && in.trigger_[0] > 0.6f;
		aimHand = breaking || in.trigger_[0] > 0.3f && in.trigger_[1] < 0.3f ? 0 : 1;
		VrRenderer vr = VrRenderer.get();
		long now = System.nanoTime();
		double dt = lastHandTime == 0 ? 0 : (now - lastHandTime) / 1e9;
		lastHandTime = now;
		for (int h = 0; h < 2; h++) {
			if (!active || !in.gripValid[h]) {
				lastHand[h] = null;
				continue;
			}
			// The fist: a little in front of the grip point.
			Vector3f fist = in.gripRot[h].transform(new Vector3f(0, 0, -0.04f)).add(in.gripPos[h]);
			Vector3d w = vr.toWorld(fist, new Vector3d());
			Vec3 p = new Vec3(w.x, w.y, w.z);
			BlockPos pos = BlockPos.containing(p);
			boolean inside = insideShape(mc, pos, p);
			if (inside && !wasInside[h] && lastHand[h] != null && dt > 0 && dt < 0.1) {
				Vec3 v = p.subtract(lastHand[h]).scale(1 / dt);
				double speed = v.length() / Math.max(1e-6, vr.worldScale()); // blocks/s → m/s
				if (speed >= PUNCH_SPEED) {
					punchPos = pos;
					punchFace = net.minecraft.core.Direction.getApproximateNearest(-v.x, -v.y, -v.z);
					punchUntil = now + PUNCH_NANOS;
				}
			}
			wasInside[h] = inside;
			// Mobs: the fist entering one fast enough hits it.
			Entity hitMob = mobAt(mc, p);
			if (hitMob != null && hitMob != lastMob[h] && lastHand[h] != null && dt > 0 && dt < 0.1) {
				double speed = p.subtract(lastHand[h]).length() / dt / Math.max(1e-6, vr.worldScale());
				if (speed >= PUNCH_SPEED && mc.gameMode != null) {
					mc.gameMode.attack(mc.player, hitMob);
					mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
				}
			}
			lastMob[h] = hitMob;
			lastHand[h] = p;
		}
	}

	private static final Entity[] lastMob = new Entity[2];

	private static Entity mobAt(Minecraft mc, Vec3 p) {
		for (Entity e : mc.level.getEntities(mc.player, new AABB(p, p).inflate(0.05))) {
			if (e.isPickable() && !e.isSpectator() && e.isAttackable() && e.getBoundingBox().inflate(0.05).contains(p)) {
				return e;
			}
		}
		return null;
	}

	private static boolean insideShape(Minecraft mc, BlockPos pos, Vec3 p) {
		var state = mc.level.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		var shape = state.getShape(mc.level, pos);
		Vec3 local = p.subtract(pos.getX(), pos.getY(), pos.getZ());
		for (AABB box : shape.toAabbs()) {
			if (box.inflate(0.02).contains(local)) {
				return true;
			}
		}
		return false;
	}

	/** True while a trigger or a punch is mining (Gorilla Tag's own punches wait). */
	public static boolean mining() {
		return miningPos != null;
	}

	/** Client tick: mine what the left trigger points at, or what you just punched; hit mobs. */
	public static void tick(Minecraft mc) {
		var gm = mc.gameMode;
		Player player = mc.player;
		BlockPos target = null;
		net.minecraft.core.Direction face = null;
		boolean attackPressed = breaking && !attackWasDown;
		attackWasDown = breaking;
		if (gm != null && player != null && mc.level != null) {
			if (breaking && mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
				target = b.getBlockPos();
				face = b.getDirection();
			} else if (attackPressed && mc.hitResult instanceof EntityHitResult e) {
				gm.attack(player, e.getEntity());
				player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
			} else if (punchPos != null && System.nanoTime() < punchUntil) {
				target = punchPos;
				face = punchFace;
			}
		}
		if (target == null) {
			if (miningPos != null && gm != null) {
				gm.stopDestroyBlock();
			}
			miningPos = null;
			return;
		}
		if (miningPos == null) {
			gm.startDestroyBlock(target, face);
		} else {
			gm.continueDestroyBlock(target, face);
		}
		miningPos = target;
		if (player.tickCount % 5 == 0) {
			player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
		}
	}

	/** Fabric COLLECT_SUBMITS: the held item in your right hand. */
	public static void render(LevelRenderContext context) {
		VrRenderer vr = VrRenderer.get();
		XrInput in = input(vr);
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (in == null || player == null || !in.gripValid[1]) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty()) {
			return;
		}
		mc.getItemModelResolver().updateForLiving(HELD, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player);
		Vector3d hand = vr.toWorld(in.gripPos[1], new Vector3d());
		Quaternionf rot = vr.toWorld(in.gripRot[1], new Quaternionf());
		Vec3 cam = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.pos;
		float scale = (float) vr.worldScale();
		PoseStack ps = context.poseStack();
		ps.pushPose();
		ps.translate(hand.x - cam.x, hand.y - cam.y, hand.z - cam.z);
		ps.rotate(rot);
		ps.scale(scale, scale, scale);
		ps.rotate(MODEL_TO_GRIP);
		// What Minecraft's held-item layer does at the end of the arm (ItemInHandLayer).
		ps.rotateDegrees(com.mojang.math.Axis.XP, -90);
		ps.rotateDegrees(com.mojang.math.Axis.YP, 180);
		ps.translate(-1 / 16f, 0.125f, -0.1f);
		int light = LightCoordsUtil.getLightCoords(mc.level, BlockPos.containing(hand.x, hand.y, hand.z));
		HELD.submit(ps, context.submitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
		ps.popPose();
	}
}
