package com.epi0.vtm.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * A clan's mechanical curse. Every field is optional in JSON and defaults to "no effect",
 * so a clan can combine any of them.
 *
 * @param frenzyHunger        hunger level (1-5) at which a nearby victim can trigger a feeding frenzy
 * @param rageFrenzy          frenzy when badly hurt in a fight (Brujah)
 * @param vampireBloodFrenzy  frenzy on tasting vampire vitae (Banu Haqim)
 * @param bestialFeatures     every frenzy leaves a lasting animal trait that unsettles mortals (Gangrel)
 * @param unease              how strongly villagers distrust you (Lasombra, Nosferatu)
 * @param biteDamage          damage dealt by a bite (Hecata: feeding is painful)
 * @param biteSelfDamage      damage you take from biting (Salubri)
 * @param preyTag             entity type tag you can feed from; anything else sickens you (Ventrue)
 * @param bloodGainMultiplier share of blood actually gained when feeding (Tremere: stunted blood)
 * @param derangement         periodic hallucinations (Malkavian)
 * @param doomedMinutes       staying near one place this long curses you (Ravnos); 0 disables
 * @param groundedBlocks      straying this far from your haven drains you (Tzimisce); 0 disables
 * @param darkFatigue         squalid, lightless places dull you (Toreador)
 */
public record Bane(int frenzyHunger, boolean rageFrenzy, boolean vampireBloodFrenzy, boolean bestialFeatures,
                   int unease, float biteDamage, float biteSelfDamage, Optional<ResourceLocation> preyTag,
                   float bloodGainMultiplier, boolean derangement, int doomedMinutes, int groundedBlocks,
                   boolean darkFatigue) {

    public static final Bane NONE = new Bane(5, false, false, false, 0, 2F, 0F, Optional.empty(),
            1F, false, 0, 0, false);

    public static final Codec<Bane> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 5).optionalFieldOf("frenzy_hunger", NONE.frenzyHunger).forGetter(Bane::frenzyHunger),
            Codec.BOOL.optionalFieldOf("rage_frenzy", false).forGetter(Bane::rageFrenzy),
            Codec.BOOL.optionalFieldOf("vampire_blood_frenzy", false).forGetter(Bane::vampireBloodFrenzy),
            Codec.BOOL.optionalFieldOf("bestial_features", false).forGetter(Bane::bestialFeatures),
            Codec.INT.optionalFieldOf("unease", 0).forGetter(Bane::unease),
            Codec.FLOAT.optionalFieldOf("bite_damage", NONE.biteDamage).forGetter(Bane::biteDamage),
            Codec.FLOAT.optionalFieldOf("bite_self_damage", 0F).forGetter(Bane::biteSelfDamage),
            ResourceLocation.CODEC.optionalFieldOf("prey_tag").forGetter(Bane::preyTag),
            Codec.FLOAT.optionalFieldOf("blood_gain_multiplier", 1F).forGetter(Bane::bloodGainMultiplier),
            Codec.BOOL.optionalFieldOf("derangement", false).forGetter(Bane::derangement),
            Codec.INT.optionalFieldOf("doomed_minutes", 0).forGetter(Bane::doomedMinutes),
            Codec.INT.optionalFieldOf("grounded_blocks", 0).forGetter(Bane::groundedBlocks),
            Codec.BOOL.optionalFieldOf("dark_fatigue", false).forGetter(Bane::darkFatigue)
    ).apply(i, Bane::new));
}
