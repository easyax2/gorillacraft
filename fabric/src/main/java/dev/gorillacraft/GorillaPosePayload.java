package dev.gorillacraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Client → server, every tick while Gorilla Tag drives the player: where the gorilla's head and
 * hands are and what colour it is, so the server can show everyone a gorilla (GorillaAvatars).
 */
public record GorillaPosePayload(Vec3 head, float yaw, float pitch, Vec3 leftHand, Vec3 rightHand, int color) implements CustomPacketPayload {
	public static final Type<GorillaPosePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GorillaCraft.MOD_ID, "pose"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GorillaPosePayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeDouble(p.head.x).writeDouble(p.head.y).writeDouble(p.head.z);
				buf.writeFloat(p.yaw).writeFloat(p.pitch);
				buf.writeDouble(p.leftHand.x).writeDouble(p.leftHand.y).writeDouble(p.leftHand.z);
				buf.writeDouble(p.rightHand.x).writeDouble(p.rightHand.y).writeDouble(p.rightHand.z);
				buf.writeInt(p.color);
			},
			buf -> new GorillaPosePayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(),
					new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
					buf.readInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
