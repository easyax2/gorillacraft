package dev.gorillacraft;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Server → clients: another gorilla's Gorilla Tag bone pose (3x4 matrices from its mesh to
 * Minecraft coordinates, relative to {@code ref}), so clients with GorillaCraft draw the real
 * Gorilla Tag gorilla (GorillaMeshes) instead of the block one.
 */
public record GorillaBonesPayload(UUID owner, Vec3 ref, int color, float[] bones) implements CustomPacketPayload {
	public static final Type<GorillaBonesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GorillaCraft.MOD_ID, "bones"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GorillaBonesPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				UUIDUtil.STREAM_CODEC.encode(buf, p.owner);
				buf.writeDouble(p.ref.x).writeDouble(p.ref.y).writeDouble(p.ref.z);
				buf.writeInt(p.color);
				writeFloats(buf, p.bones);
			},
			buf -> new GorillaBonesPayload(UUIDUtil.STREAM_CODEC.decode(buf), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
					buf.readInt(), readFloats(buf)));

	static void writeFloats(RegistryFriendlyByteBuf buf, float[] f) {
		buf.writeVarInt(f.length);
		for (float v : f) {
			buf.writeFloat(v);
		}
	}

	static float[] readFloats(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		if (n < 0 || n > 64 * 12) {
			throw new IllegalArgumentException("too many bones: " + n);
		}
		float[] f = new float[n];
		for (int i = 0; i < n; i++) {
			f[i] = buf.readFloat();
		}
		return f;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
