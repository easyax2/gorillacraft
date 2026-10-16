package dev.gorillacraft.client.vr;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
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
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Your right hand in VR: it holds the held item, and it's Minecraft's crosshair. What the
 * right controller points at becomes Minecraft's hit result (so Minecraft draws the block
 * outline), and the trigger is Minecraft's use button: place blocks, open doors, eat, draw a bow,
 * exactly as right-clicking would.
 */
public final class VrHands {
	private static final ItemStackRenderState HELD = new ItemStackRenderState();
	/**
	 * Player-model hand frame → OpenXR grip frame: the model's arm (+Y) runs along the grip's
	 * pointing direction (-Z), its front (-Z) is the controller's top (+Y), its +X is the hand's left.
	 */
	private static final Quaternionf MODEL_TO_GRIP = new Quaternionf().setFromNormalized(new Matrix3f(
			-1, 0, 0,
			0, 0, -1,
			0, -1, 0));

	private static boolean useHeld;

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
		if (in == null || player == null || mc.level == null || !in.aimValid[1]) {
			return;
		}
		Vector3d o = vr.toWorld(in.aimPos[1], new Vector3d());
		Vector3f d = vr.toWorld(in.aimRot[1], new Quaternionf()).transform(new Vector3f(0, 0, -1));
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

	/** Each frame: the triggers hold Minecraft's use button (while no screen is open). */
	static void update(Minecraft mc, XrInput in, boolean screen) {
		boolean down = !screen && mc.player != null && (in.trigger_[1] > 0.6f || in.trigger_[0] > 0.6f);
		if (down != useHeld) {
			useHeld = down;
			mc.options.keyUse.setDown(down);
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
