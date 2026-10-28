package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: request to spend experience on the next level of a discipline. */
public record LearnDisciplinePayload(ResourceLocation discipline) implements CustomPacketPayload {
    public static final Type<LearnDisciplinePayload> TYPE = new Type<>(VtmMod.id("learn_discipline"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LearnDisciplinePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, LearnDisciplinePayload::discipline,
            LearnDisciplinePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
