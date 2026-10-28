package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Server -> client: the city the player is standing in (empty when in the wilds), who holds it, and how hot they are there. */
public record CityNoticePayload(Optional<String> city, ResourceLocation owner, int heat) implements CustomPacketPayload {
    public static final Type<CityNoticePayload> TYPE = new Type<>(VtmMod.id("city_notice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityNoticePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), CityNoticePayload::city,
            ResourceLocation.STREAM_CODEC, CityNoticePayload::owner,
            ByteBufCodecs.VAR_INT, CityNoticePayload::heat,
            CityNoticePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
