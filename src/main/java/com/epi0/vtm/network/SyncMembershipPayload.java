package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.sect.Membership;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> owning client: the authoritative {@link Membership}. */
public record SyncMembershipPayload(Membership membership) implements CustomPacketPayload {
    public static final Type<SyncMembershipPayload> TYPE = new Type<>(VtmMod.id("sync_membership"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMembershipPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(Membership.CODEC), SyncMembershipPayload::membership,
            SyncMembershipPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
