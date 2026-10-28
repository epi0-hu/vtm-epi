package com.epi0.vtm.nemesis;

import com.epi0.vtm.registry.ModAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * What one player knows about the named vampires of the world: that they exist, who they are, what they can do, and
 * where they were last seen. The Nemesis screen shows nothing beyond this, and the server only ever sends this.
 */
public record NemesisKnowledge(Map<UUID, Known> known) {
    public static final int CAPACITY = 80;
    public static final NemesisKnowledge EMPTY = new NemesisKnowledge(Map.of());

    /**
     * @param tier   0 heard of (name, sect), 1 met (clan, seat, level, scars, where they were), 2 studied (kin and rivals)
     * @param traits traits the player has seen at work or been told of
     * @param place  where the player last learned they were
     */
    public record Known(int tier, Set<String> traits, Optional<GlobalPos> place) {
        public static final Known HEARD = new Known(0, Set.of(), Optional.empty());

        public static final Codec<Known> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("tier", 0).forGetter(Known::tier),
                Codec.STRING.listOf().<Set<String>>xmap(LinkedHashSet::new, java.util.List::copyOf)
                        .optionalFieldOf("traits", Set.of()).forGetter(Known::traits),
                GlobalPos.CODEC.optionalFieldOf("place").forGetter(Known::place)
        ).apply(i, Known::new));

        public Known withTier(int tier) {
            return new Known(Math.max(this.tier, tier), traits, place);
        }

        public Known withTrait(Trait trait) {
            Set<String> next = new LinkedHashSet<>(traits);
            next.add(trait.id());
            return new Known(tier, next, place);
        }

        public Known withPlace(GlobalPos pos) {
            return new Known(tier, traits, Optional.of(pos));
        }

        public boolean knows(Trait trait) {
            return traits.contains(trait.id());
        }
    }

    public static final Codec<NemesisKnowledge> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Known.CODEC)
            .xmap(NemesisKnowledge::new, NemesisKnowledge::known);

    public static NemesisKnowledge of(Player player) {
        return player.getData(ModAttachments.NEMESIS_KNOWLEDGE);
    }

    public Optional<Known> about(UUID id) {
        return Optional.ofNullable(known.get(id));
    }

    public NemesisKnowledge update(UUID id, UnaryOperator<Known> change) {
        Map<UUID, Known> next = new LinkedHashMap<>(known);
        Known previous = next.remove(id); // re-insert: the map stays ordered by recency
        next.put(id, change.apply(previous == null ? Known.HEARD : previous));
        while (next.size() > CAPACITY) {
            next.remove(next.keySet().iterator().next());
        }
        return new NemesisKnowledge(next);
    }
}
