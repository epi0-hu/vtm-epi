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
 */
public record Faction(int color, List<ResourceLocation> allies, List<ResourceLocation> enemies, boolean huntsMortals) {
    public static final Codec<Faction> CODEC = RecordCodecBuilder.create(i -> i.group(
            VtmCodecs.HEX_COLOR.fieldOf("color").forGetter(Faction::color),
            ResourceLocation.CODEC.listOf().optionalFieldOf("allies", List.of()).forGetter(Faction::allies),
            ResourceLocation.CODEC.listOf().optionalFieldOf("enemies", List.of()).forGetter(Faction::enemies),
            Codec.BOOL.optionalFieldOf("hunts_mortals", false).forGetter(Faction::huntsMortals)
    ).apply(i, Faction::new));

    public enum Relation { ALLY, NEUTRAL, ENEMY }

    /** How this faction regards {@code other}. A faction is always allied with itself. */
    public Relation relationTo(ResourceLocation self, ResourceLocation other) {
        if (self.equals(other) || allies.contains(other)) {
            return Relation.ALLY;
        }
        return enemies.contains(other) ? Relation.ENEMY : Relation.NEUTRAL;
    }
}
