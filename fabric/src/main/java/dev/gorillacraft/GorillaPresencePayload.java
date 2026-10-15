package dev.gorillacraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → server: "Gorilla Tag is (not) driving me now". */
public record GorillaPresencePayload(boolean active) implements CustomPacketPayload {
	public static final Type<GorillaPresencePayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(GorillaCraft.MOD_ID, "presence"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GorillaPresencePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.BOOL, GorillaPresencePayload::active, GorillaPresencePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
