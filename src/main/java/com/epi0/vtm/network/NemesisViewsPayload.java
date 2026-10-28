package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.nemesis.NemesisViews;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: everything the player may see on the Nemesis screen. */
public record NemesisViewsPayload(NemesisViews.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<NemesisViewsPayload> TYPE = new Type<>(VtmMod.id("nemesis_views"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NemesisViewsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(NemesisViews.Snapshot.CODEC), NemesisViewsPayload::snapshot,
            NemesisViewsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
