package com.epi0.vtm.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One conversation, loaded from {@code data/<ns>/vtm/dialogue/<name>.json}. When a player talks to an NPC the tree
 * with the highest {@code priority} whose {@code when} holds is used, so specific trees (a sect, an attitude)
 * override the general ones.
 * <p>
 * All displayed text is a lang key; the translation receives the NPC's name, the player's name, the NPC's clan, its
 * faction, and its clan's description as arguments {@code %1$s ... %5$s}.
 *
 * @param nodes by id; {@code start} names the first
 */
public record DialogueTree(Condition when, int priority, String start, Map<String, Node> nodes) {
    public static final Codec<DialogueTree> CODEC = RecordCodecBuilder.create(i -> i.group(
            Condition.WHEN.forGetter(DialogueTree::when),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(DialogueTree::priority),
            Codec.STRING.fieldOf("start").forGetter(DialogueTree::start),
            Codec.unboundedMap(Codec.STRING, Node.CODEC).fieldOf("nodes").forGetter(DialogueTree::nodes)
    ).apply(i, DialogueTree::new));

    /** What the NPC says: the first line whose condition holds. */
    public record Line(Condition when, String key) {
        public static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
                Condition.WHEN.forGetter(Line::when),
                Codec.STRING.fieldOf("key").forGetter(Line::key)
        ).apply(i, Line::new));
    }

    public record Node(List<Line> text, List<Option> options) {
        private static final Codec<List<Line>> LINES = Codec.withAlternative(Line.CODEC.listOf(),
                Codec.STRING.<List<Line>>xmap(key -> List.of(new Line(Condition.ANY, key)), lines -> lines.get(0).key()));

        public static final Codec<Node> CODEC = RecordCodecBuilder.create(i -> i.group(
                LINES.fieldOf("text").forGetter(Node::text),
                Option.CODEC.listOf().optionalFieldOf("options", List.of()).forGetter(Node::options)
        ).apply(i, Node::new));
    }

    /**
     * A thing the player can say.
     *
     * @param show    hidden unless this holds
     * @param require shown but disabled (with {@code locked} as the hint) unless this holds
     * @param effects applied on choosing; with a {@code check}, applied only on success
     * @param next    node to go to; absent ends the conversation
     */
    public record Option(String text, Condition show, Condition require, Optional<String> locked, List<Effect> effects,
                         Optional<Check> check, List<Effect> failEffects, Optional<String> next) {
        public static final Codec<Option> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(Option::text),
                Condition.CODEC.optionalFieldOf("show", Condition.ANY).forGetter(Option::show),
                Condition.CODEC.optionalFieldOf("require", Condition.ANY).forGetter(Option::require),
                Codec.STRING.optionalFieldOf("locked").forGetter(Option::locked),
                Effect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Option::effects),
                Check.CODEC.optionalFieldOf("check").forGetter(Option::check),
                Effect.CODEC.listOf().optionalFieldOf("fail_effects", List.of()).forGetter(Option::failEffects),
                Codec.STRING.optionalFieldOf("next").forGetter(Option::next)
        ).apply(i, Option::new));
    }

    /** Every node id a node or option can lead to, for validating a tree at load time. */
    List<String> references() {
        List<String> refs = new java.util.ArrayList<>(List.of(start));
        for (Node node : nodes.values()) {
            for (Option option : node.options()) {
                option.next().ifPresent(refs::add);
                option.check().flatMap(Check::fail).ifPresent(refs::add);
            }
        }
        return refs;
    }
}
