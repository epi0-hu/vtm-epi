package com.epi0.vtm.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A vampiric discipline, loaded from {@code data/<ns>/vtm/discipline/<name>.json}.
 *
 * @param color    accent colour used by the GUI
 * @param clanOnly if true, only clans that list this discipline can learn it (e.g. Thin-Blood Alchemy)
 */
public record Discipline(int color, boolean clanOnly) {
    public static final Codec<Discipline> CODEC = RecordCodecBuilder.create(i -> i.group(
            VtmCodecs.HEX_COLOR.fieldOf("color").forGetter(Discipline::color),
            Codec.BOOL.optionalFieldOf("clan_only", false).forGetter(Discipline::clanOnly)
    ).apply(i, Discipline::new));
}
