package com.epi0.vtm.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * A dice check on an option. The pool is {@code pool} plus the player's level in {@code discipline}, if any.
 *
 * @param fail node to go to on failure; absent ends the conversation
 */
public record Check(int pool, Optional<ResourceLocation> discipline, int difficulty, Optional<String> fail) {
    public static final Codec<Check> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("pool", 4).forGetter(Check::pool),
            ResourceLocation.CODEC.optionalFieldOf("discipline").forGetter(Check::discipline),
            Codec.INT.optionalFieldOf("difficulty", 2).forGetter(Check::difficulty),
            Codec.STRING.optionalFieldOf("fail").forGetter(Check::fail)
    ).apply(i, Check::new));
}
