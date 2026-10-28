package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * Client -> server: a request from the Nemesis screen. An empty {@code nemesis} (all zeros) with action
 * {@code refresh} just asks for a fresh snapshot; the others act on one of the player's bound vampires:
 * {@code mission:spy}, {@code mission:hunt}, {@code mission:tribute}, {@code recall}, {@code tithe}, {@code release}.
 */
public record NemesisActionPayload(String action, UUID nemesis, Optional<UUID> target, Optional<ResourceLocation> faction)
        implements CustomPacketPayload {
    public static final Type<NemesisActionPayload> TYPE = new Type<>(VtmMod.id("nemesis_action"));
    public static final UUID NONE = new UUID(0L, 0L);

    public static final StreamCodec<RegistryFriendlyByteBuf, NemesisActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NemesisActionPayload::action,
            UUIDUtil.STREAM_CODEC, NemesisActionPayload::nemesis,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), NemesisActionPayload::target,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), NemesisActionPayload::faction,
            NemesisActionPayload::new);

    public static NemesisActionPayload refresh() {
        return new NemesisActionPayload("refresh", NONE, Optional.empty(), Optional.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
