package dev.gorillacraft.client;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gorillacraft.link.FrameWriter;
import dev.gorillacraft.link.Proto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3fc;

import java.util.List;

/**
 * Turns one 16³ section into an MC_SECTION frame. Minecraft does all the work, the same way its
 * own SectionCompiler does: ModelBlockRenderer and FluidRenderer write into capture buffers
 * instead of GPU vertex buffers, so culling, ambient occlusion, biome tints, random offsets and
 * multipart models all come out exactly as Minecraft draws them.
 *
 * Coordinates are section-relative Minecraft coordinates (0..16); Gorilla Tag does the axis flip.
 */
public final class SectionMesher {
	private static final int LAYERS = 3; // SOLID, CUTOUT, TRANSLUCENT, in ChunkSectionLayer order
	private static final int MAX_SHAPE_BOXES = 32;

	private final LayerBuffer[] layers = new LayerBuffer[LAYERS];
	private final FloatList collision = new FloatList();
	private final FluidCapture fluidCapture = new FluidCapture();

	public SectionMesher() {
		for (int i = 0; i < LAYERS; i++) {
			layers[i] = new LayerBuffer();
		}
	}

	/**
	 * @param renderQuads false when Gorilla Tag shows Minecraft's own picture (overlay mode) and
	 *                    only needs collision and shapes.
	 * @return the frame, or null when the section's chunk isn't loaded yet.
	 */
	public byte[] mesh(ClientLevel level, int sx, int sy, int sz, boolean renderQuads) {
		LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
		if (chunk == null) {
			return null;
		}
		int index = level.getSectionIndexFromSectionY(sy);
		LevelChunkSection section = index >= 0 && index < chunk.getSectionsCount() ? chunk.getSection(index) : null;

		for (LayerBuffer l : layers) {
			l.clear();
		}
		collision.clear();
		FrameWriter w = new FrameWriter(Proto.MC_SECTION, 1 << 14);
		w.i32(sx).i32(sy).i32(sz);

		if (section == null || section.hasOnlyAir()) {
			for (int i = 0; i < LAYERS; i++) {
				w.i32(0);
			}
			w.i32(0); // collision quads
			w.i32(0); // shape entries
			w.i32(0); // water runs
			return w.finish();
		}

		Minecraft mc = Minecraft.getInstance();
		BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
		ModelBlockRenderer blockRenderer = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true, mc.getBlockColors());
		FluidRenderer fluidRenderer = new FluidRenderer(mc.getModelManager().getFluidStateModelSet());

		BlockQuadOutput byMaterial = (x, y, z, quad, instance) -> putQuad(layerIndex(quad.materialInfo().layer()), x, y, z, quad, instance);
		BlockQuadOutput forcedOpaque = (x, y, z, quad, instance) -> putQuad(0, x, y, z, quad, instance);
		FluidRenderer.Output fluidOut = layer -> fluidCapture.target(layers[layerIndex(layer)]);

		int ox = sx << 4, oy = sy << 4, oz = sz << 4;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos npos = new BlockPos.MutableBlockPos();
		int shapeCount = 0;
		FrameWriter shapes = new FrameWriter(0, 1 << 12); // scratch: only its body is copied out
		// Water, per block: its surface height in 1/16 (0 = no water), for Gorilla Tag's swimming.
		byte[] water = new byte[4096];

		BlockModelLighter.enableCaching();
		try {
			for (int ly = 0; ly < 16; ly++) {
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						BlockState state = section.getBlockState(lx, ly, lz);
						if (state.isAir()) {
							continue;
						}
						pos.set(ox + lx, oy + ly, oz + lz);

						FluidState fluid = state.getFluidState();
						if (fluid.is(net.minecraft.tags.FluidTags.WATER)) {
							water[ly << 8 | lz << 4 | lx] = (byte) Math.clamp(Math.round(fluid.getHeight(level, pos) * 16), 1, 16);
						}
						if (renderQuads && !fluid.isEmpty()) {
							fluidRenderer.tesselate(level, pos, fluidOut, state, fluid);
							fluidCapture.flush();
						}
						if (renderQuads && state.getRenderShape() == RenderShape.MODEL) {
							BlockQuadOutput out = ModelBlockRenderer.forceOpaque(true, state) ? forcedOpaque : byMaterial;
							blockRenderer.tesselateBlock(out, lx, ly, lz, level, pos, state, models.get(state), state.getSeed(pos));
						}

						boolean fullCollision = addCollision(level, state, pos, npos, lx, ly, lz);
						if (addShape(shapes, level, state, pos, npos, fullCollision, lx, ly, lz)) {
							shapeCount++;
						}
					}
				}
			}
		} finally {
			BlockModelLighter.clearCache();
		}

		for (LayerBuffer l : layers) {
			w.i32(l.vertexCount / 4);
			w.bytes(l.toBytes());
		}
		w.i32(collision.size / 12);
		for (int i = 0; i < collision.size; i++) {
			w.f32(collision.data[i]);
		}
		w.i32(shapeCount);
		byte[] shapeFrame = shapes.finish();
		// Skip the scratch frame's own [u32 length][u8 type] header.
		w.bytes(java.util.Arrays.copyOfRange(shapeFrame, 5, shapeFrame.length));
		writeWater(w, water);
		return w.finish();
	}

	/**
	 * Water as vertical runs per column: u8 x, u8 z, u8 bottom y, u8 top y (inclusive, section
	 * local), u8 surface height of the top block in 1/16 (Minecraft reports 16 when there's water
	 * above it, also across the section border).
	 */
	private static void writeWater(FrameWriter w, byte[] water) {
		FrameWriter runs = new FrameWriter(0, 1 << 10);
		int count = 0;
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int y = 0;
				while (y < 16) {
					if (water[y << 8 | lz << 4 | lx] == 0) {
						y++;
						continue;
					}
					int y0 = y;
					while (y < 16 && water[y << 8 | lz << 4 | lx] != 0) {
						y++;
					}
					int top = y - 1;
					int height = water[top << 8 | lz << 4 | lx];
					runs.u8(lx).u8(lz).u8(y0).u8(top).u8(height);
					count++;
				}
			}
		}
		w.i32(count);
		byte[] f = runs.finish();
		w.bytes(java.util.Arrays.copyOfRange(f, 5, f.length));
	}

	private static int layerIndex(ChunkSectionLayer layer) {
		return switch (layer) {
			case SOLID -> 0;
			case CUTOUT -> 1;
			case TRANSLUCENT -> 2;
		};
	}

	private void putQuad(int layer, float x, float y, float z, BakedQuad quad, QuadInstance instance) {
		LayerBuffer buf = layers[layer];
		int emission = quad.materialInfo().lightEmission();
		for (int i = 0; i < 4; i++) {
			Vector3fc p = quad.position(i);
			long uv = quad.packedUV(i);
			buf.vertex(x + p.x(), y + p.y(), z + p.z(), UVPair.unpackU(uv), UVPair.unpackV(uv),
					instance.getColor(i), instance.getLightCoordsWithEmission(i, emission));
		}
	}

	/**
	 * Collision faces, as quads wound counter-clockwise seen from outside. Faces between two full
	 * collision cubes are skipped, which is most of them underground.
	 *
	 * @return whether this block's collision shape is a full cube.
	 */
	private boolean addCollision(ClientLevel level, BlockState state, BlockPos pos, BlockPos.MutableBlockPos npos, int lx, int ly, int lz) {
		VoxelShape shape = state.getCollisionShape(level, pos);
		if (shape.isEmpty()) {
			return false;
		}
		if (Block.isShapeFullBlock(shape)) {
			for (Direction d : Direction.values()) {
				npos.setWithOffset(pos, d);
				if (!level.getBlockState(npos).isCollisionShapeFullBlock(level, npos)) {
					face(d, lx, ly, lz, lx + 1, ly + 1, lz + 1);
				}
			}
			return true;
		}
		for (AABB box : shape.toAabbs()) {
			for (Direction d : Direction.values()) {
				face(d, (float) (lx + box.minX), (float) (ly + box.minY), (float) (lz + box.minZ),
						(float) (lx + box.maxX), (float) (ly + box.maxY), (float) (lz + box.maxZ));
			}
		}
		return false;
	}

	private void face(Direction d, float x0, float y0, float z0, float x1, float y1, float z1) {
		switch (d) {
			case UP -> collision.quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
			case DOWN -> collision.quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
			case EAST -> collision.quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
			case WEST -> collision.quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
			case SOUTH -> collision.quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
			case NORTH -> collision.quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
		}
	}

	/**
	 * Outline shape of a block a hand could touch, for Gorilla Tag's hit testing. Full cubes
	 * buried on all six sides are left out.
	 */
	private static boolean addShape(FrameWriter out, ClientLevel level, BlockState state, BlockPos pos, BlockPos.MutableBlockPos npos,
			boolean fullCollision, int lx, int ly, int lz) {
		VoxelShape shape = state.getShape(level, pos);
		if (shape.isEmpty()) {
			return false;
		}
		if (fullCollision) {
			boolean exposed = false;
			for (Direction d : Direction.values()) {
				npos.setWithOffset(pos, d);
				if (!level.getBlockState(npos).isCollisionShapeFullBlock(level, npos)) {
					exposed = true;
					break;
				}
			}
			if (!exposed) {
				return false;
			}
		}
		out.u16(ly << 8 | lz << 4 | lx);
		if (Block.isShapeFullBlock(shape)) {
			out.u8(Proto.SHAPE_FULL_CUBE);
			return true;
		}
		List<AABB> boxes = shape.toAabbs();
		int n = Math.min(boxes.size(), MAX_SHAPE_BOXES);
		out.u8(n);
		for (int i = 0; i < n; i++) {
			AABB b = boxes.get(i);
			out.f32((float) b.minX).f32((float) b.minY).f32((float) b.minZ)
					.f32((float) b.maxX).f32((float) b.maxY).f32((float) b.maxZ);
		}
		return true;
	}

	/**
	 * Bakes Minecraft's light level into the vertex colour, since Gorilla Tag draws the blocks
	 * unlit. Uses Minecraft's brightness curve at its default gamma, with full daylight.
	 */
	static int shade(int argb, int packedLight) {
		int block = (packedLight >> 4) & 0xF;
		int sky = (packedLight >> 20) & 0xF;
		float l = Math.max(block, sky) / 15f;
		float curve = l / (4f - 3f * l);
		float bright = 1f - (1f - curve) * (1f - curve) * (1f - curve) * (1f - curve);
		float f = 0.06f + 0.94f * (0.5f * curve + 0.5f * bright);
		return ARGB.color(ARGB.alpha(argb), (int) (ARGB.red(argb) * f), (int) (ARGB.green(argb) * f), (int) (ARGB.blue(argb) * f));
	}

	/** Vertices of one render layer: pos f32×3, uv f32×2, rgba u8×4 (24 bytes). */
	static final class LayerBuffer {
		private float[] f = new float[1024 * 5];
		private int[] c = new int[1024];
		int vertexCount;

		void clear() {
			vertexCount = 0;
		}

		void vertex(float x, float y, float z, float u, float v, int argb, int light) {
			if (vertexCount == c.length) {
				f = java.util.Arrays.copyOf(f, f.length * 2);
				c = java.util.Arrays.copyOf(c, c.length * 2);
			}
			int o = vertexCount * 5;
			f[o] = x;
			f[o + 1] = y;
			f[o + 2] = z;
			f[o + 3] = u;
			f[o + 4] = v;
			c[vertexCount++] = shade(argb, light);
		}

		byte[] toBytes() {
			java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(vertexCount * 24).order(java.nio.ByteOrder.LITTLE_ENDIAN);
			for (int i = 0; i < vertexCount; i++) {
				int o = i * 5;
				b.putFloat(f[o]).putFloat(f[o + 1]).putFloat(f[o + 2]).putFloat(f[o + 3]).putFloat(f[o + 4]);
				int argb = c[i];
				b.put((byte) ARGB.red(argb)).put((byte) ARGB.green(argb)).put((byte) ARGB.blue(argb)).put((byte) ARGB.alpha(argb));
			}
			return b.array();
		}
	}

	static final class FloatList {
		float[] data = new float[4096];
		int size;

		void clear() {
			size = 0;
		}

		void quad(float... v) {
			if (size + v.length > data.length) {
				data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, size + v.length));
			}
			System.arraycopy(v, 0, data, size, v.length);
			size += v.length;
		}
	}

	/** FluidRenderer writes through a VertexConsumer; this one records into a LayerBuffer. */
	static final class FluidCapture implements VertexConsumer {
		private LayerBuffer target;
		private boolean open;
		private float x, y, z, u, v;
		private int color, light;

		VertexConsumer target(LayerBuffer layer) {
			flush();
			target = layer;
			return this;
		}

		void flush() {
			if (open && target != null) {
				target.vertex(x, y, z, u, v, color, light);
			}
			open = false;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			flush();
			this.x = x;
			this.y = y;
			this.z = z;
			u = v = 0;
			color = -1;
			light = 0xF000F0;
			open = true;
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			color = ARGB.color(a, r, g, b);
			return this;
		}

		@Override
		public VertexConsumer setColor(int argb) {
			color = argb;
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			this.u = u;
			this.v = v;
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			light = (u & 0xFFFF) | (v & 0xFFFF) << 16;
			return this;
		}

		@Override
		public VertexConsumer setUv3(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}
	}
}
