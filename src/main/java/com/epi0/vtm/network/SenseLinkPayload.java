package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: look through this entity's eyes, or {@code -1} to return to your own. */
public record SenseLinkPayload(int entityId) implements CustomPacketPayload {
    public static final Type<SenseLinkPayload> TYPE = new Type<>(VtmMod.id("sense_link"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SenseLinkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SenseLinkPayload::entityId,
            SenseLinkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
