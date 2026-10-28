package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> owning client: the authoritative {@link VampireData}. */
public record SyncVampirePayload(VampireData data) implements CustomPacketPayload {
    public static final Type<SyncVampirePayload> TYPE = new Type<>(VtmMod.id("sync_vampire"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncVampirePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(VampireData.CODEC), SyncVampirePayload::data,
            SyncVampirePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
