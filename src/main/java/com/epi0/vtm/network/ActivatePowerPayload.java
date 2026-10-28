package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: use a discipline power. */
public record ActivatePowerPayload(ResourceLocation power) implements CustomPacketPayload {
    public static final Type<ActivatePowerPayload> TYPE = new Type<>(VtmMod.id("activate_power"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActivatePowerPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ActivatePowerPayload::power,
            ActivatePowerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
