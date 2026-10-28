package com.epi0.vtm.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A political bloc, loaded from {@code data/<ns>/vtm/faction/<name>.json}. Display text comes from the lang key
 * {@code faction.<ns>.<name>}.
 *
 * @param color       accent colour
 * @param allies      factions this one cooperates with
 * @param enemies     factions this one fights
 * @param huntsMortals members feed on mortals openly when hungry, Masquerade be damned
 * @param joinable    vampire players can become members
 * @param masquerade  members who let mortals witness feeding lose standing
 * @param ranks       standing needed for each rank, lowest first; the first is where a new member starts. Rank titles
 *                    are the lang keys {@code faction.<ns>.<name>.rank.<n>}
 */
public record Faction(int color, List<ResourceLocation> allies, List<ResourceLocation> enemies, boolean huntsMortals,
                      boolean joinable, boolean masquerade, List<Rank> ranks) {
    public record Rank(int standing) {
        public static final Codec<Rank> CODEC = Codec.INT.xmap(Rank::new, Rank::standing);
    }

    public static final Codec<Faction> CODEC = RecordCodecBuilder.create(i -> i.group(
            VtmCodecs.HEX_COLOR.fieldOf("color").forGetter(Faction::color),
            ResourceLocation.CODEC.listOf().optionalFieldOf("allies", List.of()).forGetter(Faction::allies),
            ResourceLocation.CODEC.listOf().optionalFieldOf("enemies", List.of()).forGetter(Faction::enemies),
            Codec.BOOL.optionalFieldOf("hunts_mortals", false).forGetter(Faction::huntsMortals),
            Codec.BOOL.optionalFieldOf("joinable", false).forGetter(Faction::joinable),
            Codec.BOOL.optionalFieldOf("masquerade", false).forGetter(Faction::masquerade),
            Rank.CODEC.listOf().optionalFieldOf("ranks", List.of(new Rank(0))).forGetter(Faction::ranks)
    ).apply(i, Faction::new));

    public int rankCount() {
        return Math.max(1, ranks.size());
    }

    /** Standing required to hold {@code rank}. */
    public int standingFor(int rank) {
        return ranks.isEmpty() ? 0 : ranks.get(Math.max(0, Math.min(rank, ranks.size() - 1))).standing();
    }

    public enum Relation { ALLY, NEUTRAL, ENEMY }

    /** How this faction regards {@code other}. A faction is always allied with itself. */
    public Relation relationTo(ResourceLocation self, ResourceLocation other) {
        if (self.equals(other) || allies.contains(other)) {
            return Relation.ALLY;
        }
        return enemies.contains(other) ? Relation.ENEMY : Relation.NEUTRAL;
    }
}
