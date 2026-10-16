package dev.gorillacraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gorillacraft.GorillaCraft;
import dev.gorillacraft.link.Inbound;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * Gorilla Tag's real gorilla, drawn by Minecraft. The model comes from this player's own Gorilla
 * Tag (GT_MODEL); poses are bone matrices, either from the local Gorilla Tag or relayed by the
 * server from other gorillas. Every frame each gorilla is skinned on the CPU (about 3000 vertices)
 * and drawn with Minecraft's entity rendering, so it's lit like any mob.
 */
public final class GorillaMeshes {
	private static final long STALE_NANOS = 2_000_000_000L;

	/** The model, once Gorilla Tag has sent it. */
	private static volatile Model model;
	private static final Map<UUID, Pose> POSES = new ConcurrentHashMap<>();

	private GorillaMeshes() {
	}

	private static final class Part {
		int[] indices;
		boolean tinted;
		float r, g, b;
		float su, sv, ou, ov;
		Identifier texture;
	}

	private static final class Model {
		int vertices;
		float[] pos, nrm, uv, weight;
		int[] bone;
		int bones;
		List<Part> parts = new ArrayList<>();
	}

	/** Two most recent bone sets of one gorilla, to interpolate between (poses arrive about 20 times a second). */
	private static final class Pose {
		Vec3 ref, prevRef;
		float[] bones, prevBones;
		long at, prevAt;
		int color;
	}

	public static boolean hasModel() {
		return model != null;
	}

	/** Client thread: parses GT_MODEL and uploads its textures. */
	static void readModel(Inbound m) {
		Model md = new Model();
		int n = m.i32();
		md.vertices = n;
		md.pos = new float[n * 3];
		md.nrm = new float[n * 3];
		md.uv = new float[n * 2];
		md.bone = new int[n * 4];
		md.weight = new float[n * 4];
		for (int i = 0; i < n; i++) {
			for (int k = 0; k < 3; k++) {
				md.pos[i * 3 + k] = m.f32();
			}
			for (int k = 0; k < 3; k++) {
				md.nrm[i * 3 + k] = m.f32();
			}
			md.uv[i * 2] = m.f32();
			md.uv[i * 2 + 1] = m.f32();
			for (int k = 0; k < 4; k++) {
				md.bone[i * 4 + k] = m.u8();
			}
			for (int k = 0; k < 4; k++) {
				md.weight[i * 4 + k] = m.f32();
			}
		}
		int parts = m.i32();
		Minecraft mc = Minecraft.getInstance();
		for (int p = 0; p < parts; p++) {
			Part part = new Part();
			part.indices = new int[m.i32()];
			for (int i = 0; i < part.indices.length; i++) {
				part.indices[i] = m.i32();
			}
			part.tinted = m.u8() != 0;
			part.r = m.f32();
			part.g = m.f32();
			part.b = m.f32();
			part.su = m.f32();
			part.sv = m.f32();
			part.ou = m.f32();
			part.ov = m.f32();
			byte[] png = m.bytes(m.i32());
			part.texture = Identifier.fromNamespaceAndPath(GorillaCraft.MOD_ID, "gorilla/part_" + p);
			NativeImage image;
			try {
				image = NativeImage.read(png);
			} catch (Exception e) {
				GorillaCraft.LOG.warn("Gorilla texture {} unreadable ({}), using plain white", p, e.toString());
				image = new NativeImage(2, 2, false);
				image.fillRect(0, 0, 2, 2, 0xFFFFFFFF);
			}
			NativeImage texture = image;
			mc.getTextureManager().register(part.texture, new DynamicTexture(() -> "GorillaCraft gorilla " + part.texture, texture));
			md.parts.add(part);
		}
		md.bones = m.i32();
		model = md;
		GorillaCraft.LOG.info("Gorilla Tag's gorilla model: {} vertices, {} parts, {} bones", n, parts, md.bones);
	}

	/** A gorilla's new bones (local from Gorilla Tag, or relayed by the server). */
	public static void setPose(UUID owner, Vec3 ref, int color, float[] bones) {
		Pose p = POSES.computeIfAbsent(owner, k -> new Pose());
		synchronized (p) {
			p.prevRef = p.ref != null ? p.ref : ref;
			p.prevBones = p.bones != null ? p.bones : bones;
			p.prevAt = p.at != 0 ? p.at : System.nanoTime() - 50_000_000L;
			p.ref = ref;
			p.bones = bones;
			p.at = System.nanoTime();
			p.color = color;
		}
	}

	public static void forget(UUID owner) {
		POSES.remove(owner);
	}

	/** Whether this gorilla is drawn as the real model here (so its block avatar can be hidden). */
	public static boolean drawsReal(UUID owner) {
		Pose p = POSES.get(owner);
		return model != null && p != null && System.nanoTime() - p.at < STALE_NANOS;
	}

	/** Fabric COLLECT_SUBMITS: draws every gorilla with a fresh pose (not your own, unless debugging). */
	public static void render(LevelRenderContext context) {
		Model md = model;
		Minecraft mc = Minecraft.getInstance();
		if (md == null || mc.level == null || POSES.isEmpty()) {
			return;
		}
		UUID me = mc.player != null ? mc.player.getUUID() : null;
		boolean showOwn = System.getenv("GORILLACRAFT_SHOW_OWN_AVATAR") != null;
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		long now = System.nanoTime();
		for (Map.Entry<UUID, Pose> e : POSES.entrySet()) {
			if (e.getKey().equals(me) && !showOwn) {
				continue;
			}
			Pose p = e.getValue();
			float[] bones;
			Vec3 ref;
			int color;
			synchronized (p) {
				if (now - p.at > STALE_NANOS || p.bones == null || p.bones.length < md.bones * 12) {
					continue;
				}
				// Interpolate between the last two poses, a pose-interval behind (smooth at ~20 updates/s).
				float t = Math.clamp((float) (now - p.at) / Math.max(1, p.at - p.prevAt), 0f, 1f);
				bones = lerp(p.prevBones, p.bones, t);
				ref = p.prevRef.lerp(p.ref, t);
				color = p.color;
			}
			float[] skinned = new float[md.vertices * 6]; // the submits run later: each gorilla its own buffer
			skin(md, bones, skinned);
			int light = LightCoordsUtil.getLightCoords(mc.level, BlockPos.containing(ref));
			PoseStack ps = context.poseStack();
			ps.pushPose();
			ps.translate(ref.x - cam.x, ref.y - cam.y, ref.z - cam.z);
			for (Part part : md.parts) {
				int argb = part.tinted ? 0xFF000000 | color
						: 0xFF000000 | (int) (part.r * 255) << 16 | (int) (part.g * 255) << 8 | (int) (part.b * 255);
				int[] idx = part.indices;
				context.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.entityCutout(part.texture), (pose, vc) -> {
					// Triangles as quads with a repeated last corner (entity render types draw quads).
					for (int i = 0; i + 2 < idx.length; i += 3) {
						vertex(vc, pose, md, part, skinned, idx[i], argb, light);
						vertex(vc, pose, md, part, skinned, idx[i + 1], argb, light);
						vertex(vc, pose, md, part, skinned, idx[i + 2], argb, light);
						vertex(vc, pose, md, part, skinned, idx[i + 2], argb, light);
					}
				});
			}
			ps.popPose();
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Model md, Part part, float[] s, int i, int argb, int light) {
		float u = md.uv[i * 2] * part.su + part.ou;
		float v = 1f - (md.uv[i * 2 + 1] * part.sv + part.ov); // Unity's v goes up, Minecraft's down
		vc.addVertex(pose, s[i * 6], s[i * 6 + 1], s[i * 6 + 2]).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(pose, s[i * 6 + 3], s[i * 6 + 4], s[i * 6 + 5]);
	}

	/** Linear blend skinning with 4 bones per vertex; bones are 3x4 row-major mesh → Minecraft matrices. */
	private static void skin(Model md, float[] bones, float[] out) {
		for (int i = 0; i < md.vertices; i++) {
			float px = md.pos[i * 3], py = md.pos[i * 3 + 1], pz = md.pos[i * 3 + 2];
			float nx = md.nrm[i * 3], ny = md.nrm[i * 3 + 1], nz = md.nrm[i * 3 + 2];
			float x = 0, y = 0, z = 0, a = 0, b = 0, c = 0;
			for (int k = 0; k < 4; k++) {
				float w = md.weight[i * 4 + k];
				if (w <= 0) {
					continue;
				}
				int o = Math.min(md.bone[i * 4 + k], md.bones - 1) * 12;
				x += w * (bones[o] * px + bones[o + 1] * py + bones[o + 2] * pz + bones[o + 3]);
				y += w * (bones[o + 4] * px + bones[o + 5] * py + bones[o + 6] * pz + bones[o + 7]);
				z += w * (bones[o + 8] * px + bones[o + 9] * py + bones[o + 10] * pz + bones[o + 11]);
				a += w * (bones[o] * nx + bones[o + 1] * ny + bones[o + 2] * nz);
				b += w * (bones[o + 4] * nx + bones[o + 5] * ny + bones[o + 6] * nz);
				c += w * (bones[o + 8] * nx + bones[o + 9] * ny + bones[o + 10] * nz);
			}
			float len = (float) Math.sqrt(a * a + b * b + c * c);
			if (len > 1e-6f) {
				a /= len;
				b /= len;
				c /= len;
			}
			int o = i * 6;
			out[o] = x;
			out[o + 1] = y;
			out[o + 2] = z;
			out[o + 3] = a;
			out[o + 4] = b;
			out[o + 5] = c;
		}
	}

	private static float[] lerp(float[] a, float[] b, float t) {
		if (a == null || a.length != b.length) {
			return b;
		}
		float[] r = new float[b.length];
		for (int i = 0; i < r.length; i++) {
			r[i] = a[i] + (b[i] - a[i]) * t;
		}
		return r;
	}
}
