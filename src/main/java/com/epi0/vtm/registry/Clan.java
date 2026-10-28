package com.epi0.vtm.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A vampire clan, loaded from {@code data/<ns>/vtm/clan/<name>.json}.
 * Display text comes from the lang keys {@code clan.<ns>.<name>}, {@code .desc} and {@code .bane}.
 *
 * @param color               accent colour used by the GUI
 * @param disciplines         in-clan disciplines (cheaper to learn)
 * @param sunDamageMultiplier mechanical hook for clan banes that change sunlight vulnerability
 */
public record Clan(int color, List<ResourceLocation> disciplines, float sunDamageMultiplier) {
    public static final Codec<Clan> CODEC = RecordCodecBuilder.create(i -> i.group(
            VtmCodecs.HEX_COLOR.fieldOf("color").forGetter(Clan::color),
            ResourceLocation.CODEC.listOf().optionalFieldOf("disciplines", List.of()).forGetter(Clan::disciplines),
            Codec.FLOAT.optionalFieldOf("sun_damage_multiplier", 1.0F).forGetter(Clan::sunDamageMultiplier)
    ).apply(i, Clan::new));
}
