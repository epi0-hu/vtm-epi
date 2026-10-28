package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the player picked an option (by its index in the node), or {@code -1} to walk away. */
public record DialogueChoicePayload(int option) implements CustomPacketPayload {
    public static final Type<DialogueChoicePayload> TYPE = new Type<>(VtmMod.id("dialogue_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DialogueChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DialogueChoicePayload::option,
            DialogueChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
