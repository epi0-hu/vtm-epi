package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;

/**
 * Server -> client: what the NPC says now and what the player may answer. An empty {@code node} closes the screen.
 * The server resolves every line, so the client never needs the dialogue data.
 */
public record DialogueNodePayload(Optional<Node> node) implements CustomPacketPayload {
    public static final Type<DialogueNodePayload> TYPE = new Type<>(VtmMod.id("dialogue_node"));

    public record Choice(Component text, boolean enabled, Optional<Component> hint) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Choice> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Choice::text,
                ByteBufCodecs.BOOL, Choice::enabled,
                ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), Choice::hint,
                Choice::new);
    }

    /**
     * @param note    result of the last dice check, shown under the line
     * @param attitude ordinal of {@link com.epi0.vtm.npc.Attitude}
     */
    public record Node(int npcId, Component speaker, int color, Component text, Optional<Component> note, int attitude,
                       int rapport, List<Choice> choices) {
        // composite() stops at six fields, so this one is written out.
        public static final StreamCodec<RegistryFriendlyByteBuf, Node> STREAM_CODEC = StreamCodec.of(
                (buf, node) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, node.npcId());
                    ComponentSerialization.STREAM_CODEC.encode(buf, node.speaker());
                    buf.writeInt(node.color());
                    ComponentSerialization.STREAM_CODEC.encode(buf, node.text());
                    ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC).encode(buf, node.note());
                    ByteBufCodecs.VAR_INT.encode(buf, node.attitude());
                    ByteBufCodecs.VAR_INT.encode(buf, node.rapport());
                    Choice.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, node.choices());
                },
                buf -> new Node(
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ComponentSerialization.STREAM_CODEC.decode(buf),
                        buf.readInt(),
                        ComponentSerialization.STREAM_CODEC.decode(buf),
                        ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC).decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        Choice.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DialogueNodePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Node.STREAM_CODEC), DialogueNodePayload::node,
            DialogueNodePayload::new);

    public static DialogueNodePayload close() {
        return new DialogueNodePayload(Optional.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
