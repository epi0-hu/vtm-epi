package com.epi0.vtm.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What one NPC remembers about each player it has dealt with. Persisted with the NPC, bounded in size, and the
 * natural foundation for the Nemesis System: grudges, fear and reputation all read from here.
 */
public final class NpcMemory {
    public static final int CAPACITY = 16;

    public enum Event { SEEN, HARMED, WITNESSED_FEEDING, WITNESSED_FRENZY }

    public static final int MAX_RAPPORT = 10;

    /**
     * @param rapport how the NPC feels about the player, from -10 (loathing) to +10 (devoted); moved by conversation
     * @param flags   things already said or done between them, so a conversation can remember it happened
     */
    public record Encounter(long firstSeen, long lastSeen, int seen, int harmed, int witnessedFeeding, int witnessedFrenzy,
                            int rapport, Set<String> flags) {
        public static final Codec<Encounter> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("first_seen").forGetter(Encounter::firstSeen),
                Codec.LONG.fieldOf("last_seen").forGetter(Encounter::lastSeen),
                Codec.INT.optionalFieldOf("seen", 0).forGetter(Encounter::seen),
                Codec.INT.optionalFieldOf("harmed", 0).forGetter(Encounter::harmed),
                Codec.INT.optionalFieldOf("witnessed_feeding", 0).forGetter(Encounter::witnessedFeeding),
                Codec.INT.optionalFieldOf("witnessed_frenzy", 0).forGetter(Encounter::witnessedFrenzy),
                Codec.INT.optionalFieldOf("rapport", 0).forGetter(Encounter::rapport),
                Codec.STRING.listOf().<Set<String>>xmap(LinkedHashSet::new, List::copyOf)
                        .optionalFieldOf("flags", Set.of()).forGetter(Encounter::flags)
        ).apply(i, Encounter::new));

        Encounter with(Event event, long now) {
            return switch (event) {
                case SEEN -> new Encounter(firstSeen, now, seen + 1, harmed, witnessedFeeding, witnessedFrenzy, rapport, flags);
                case HARMED -> new Encounter(firstSeen, now, seen, harmed + 1, witnessedFeeding, witnessedFrenzy, rapport, flags);
                case WITNESSED_FEEDING -> new Encounter(firstSeen, now, seen, harmed, witnessedFeeding + 1, witnessedFrenzy, rapport, flags);
                case WITNESSED_FRENZY -> new Encounter(firstSeen, now, seen, harmed, witnessedFeeding, witnessedFrenzy + 1, rapport, flags);
            };
        }

        Encounter withRapport(int delta, long now) {
            int value = Math.max(-MAX_RAPPORT, Math.min(MAX_RAPPORT, rapport + delta));
            return new Encounter(firstSeen, now, seen, harmed, witnessedFeeding, witnessedFrenzy, value, flags);
        }

        Encounter withFlag(String flag, boolean present, long now) {
            Set<String> next = new LinkedHashSet<>(flags);
            if (present) {
                next.add(flag);
            } else {
                next.remove(flag);
            }
            return new Encounter(firstSeen, now, seen, harmed, witnessedFeeding, witnessedFrenzy, rapport, next);
        }

        /** Has this player ever given the NPC reason to fear or hate them? */
        public boolean isThreat() {
            return harmed > 0 || witnessedFeeding > 0 || witnessedFrenzy > 0;
        }
    }

    private static final Codec<Map<UUID, Encounter>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Encounter.CODEC);

    private final Map<UUID, Encounter> encounters = new LinkedHashMap<>();

    public void remember(UUID player, Event event, long now) {
        update(player, now, e -> e.with(event, now));
    }

    public void adjustRapport(UUID player, int delta, long now) {
        update(player, now, e -> e.withRapport(delta, now));
    }

    public void setFlag(UUID player, String flag, boolean present, long now) {
        update(player, now, e -> e.withFlag(flag, present, now));
    }

    private void update(UUID player, long now, java.util.function.UnaryOperator<Encounter> change) {
        Encounter previous = encounters.remove(player); // re-insert so the map stays ordered by recency
        Encounter base = previous != null ? previous : new Encounter(now, now, 0, 0, 0, 0, 0, Set.of());
        encounters.put(player, change.apply(base));
        while (encounters.size() > CAPACITY) {
            encounters.remove(encounters.keySet().iterator().next());
        }
    }

    public int rapport(UUID player) {
        return about(player).map(Encounter::rapport).orElse(0);
    }

    public boolean hasFlag(UUID player, String flag) {
        return about(player).map(e -> e.flags().contains(flag)).orElse(false);
    }

    public Optional<Encounter> about(UUID player) {
        return Optional.ofNullable(encounters.get(player));
    }

    public boolean isThreat(UUID player) {
        return about(player).map(Encounter::isThreat).orElse(false);
    }

    public Tag save() {
        return MAP_CODEC.encodeStart(NbtOps.INSTANCE, encounters).getOrThrow();
    }

    public void load(Tag tag) {
        encounters.clear();
        MAP_CODEC.parse(NbtOps.INSTANCE, tag).result().ifPresent(encounters::putAll);
    }
}
