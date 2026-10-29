package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: spend experience to raise Blood Potency by one. */
public record RaisePotencyPayload() implements CustomPacketPayload {
    public static final Type<RaisePotencyPayload> TYPE = new Type<>(VtmMod.id("raise_potency"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaisePotencyPayload> STREAM_CODEC = StreamCodec.unit(new RaisePotencyPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
