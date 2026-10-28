package com.epi0.vtm.vampire;

import com.epi0.vtm.registry.ModAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable per-player vampire state. All mutation goes through the {@code with*} methods and is
 * written back (and synced) by {@link VampireManager}. Game rules live in {@link VampireRules}.
 */
public record VampireData(
        Species species,
        Optional<ResourceLocation> clan,
        int generation,
        int bloodPotency,
        float blood,
        int humanity,
        int stains,
        int experience,
        Map<ResourceLocation, Integer> disciplines
) {
    public static final VampireData DEFAULT = new VampireData(
            Species.MORTAL, Optional.empty(), VampireRules.DEFAULT_GENERATION, 0, 0F,
            VampireRules.DEFAULT_HUMANITY, 0, 0, Map.of());

    public static final Codec<VampireData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Species.CODEC.optionalFieldOf("species", DEFAULT.species).forGetter(VampireData::species),
            ResourceLocation.CODEC.optionalFieldOf("clan").forGetter(VampireData::clan),
            Codec.INT.optionalFieldOf("generation", DEFAULT.generation).forGetter(VampireData::generation),
            Codec.INT.optionalFieldOf("blood_potency", DEFAULT.bloodPotency).forGetter(VampireData::bloodPotency),
            Codec.FLOAT.optionalFieldOf("blood", DEFAULT.blood).forGetter(VampireData::blood),
            Codec.INT.optionalFieldOf("humanity", DEFAULT.humanity).forGetter(VampireData::humanity),
            Codec.INT.optionalFieldOf("stains", DEFAULT.stains).forGetter(VampireData::stains),
            Codec.INT.optionalFieldOf("experience", DEFAULT.experience).forGetter(VampireData::experience),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("disciplines", Map.of())
                    .forGetter(VampireData::disciplines)
    ).apply(i, VampireData::new));

    public static VampireData of(Player player) {
        return player.getData(ModAttachments.VAMPIRE);
    }

    public boolean isVampire() {
        return species == Species.VAMPIRE;
    }

    public int disciplineLevel(ResourceLocation discipline) {
        return disciplines.getOrDefault(discipline, 0);
    }

    public float maxBlood() {
        return VampireRules.maxBlood(this);
    }

    /** V5-style hunger: 0 (sated) to 5 (ravenous). */
    public int hunger() {
        return VampireRules.hunger(this);
    }

    public VampireData withSpecies(Species v) {
        return new VampireData(v, clan, generation, bloodPotency, blood, humanity, stains, experience, disciplines);
    }

    public VampireData withClan(Optional<ResourceLocation> v) {
        return new VampireData(species, v, generation, bloodPotency, blood, humanity, stains, experience, disciplines);
    }

    public VampireData withGeneration(int v) {
        return new VampireData(species, clan, v, bloodPotency, blood, humanity, stains, experience, disciplines);
    }

    public VampireData withBloodPotency(int v) {
        return new VampireData(species, clan, generation, v, blood, humanity, stains, experience, disciplines);
    }

    public VampireData withBlood(float v) {
        return new VampireData(species, clan, generation, bloodPotency, v, humanity, stains, experience, disciplines);
    }

    public VampireData withHumanity(int v) {
        return new VampireData(species, clan, generation, bloodPotency, blood, v, stains, experience, disciplines);
    }

    public VampireData withStains(int v) {
        return new VampireData(species, clan, generation, bloodPotency, blood, humanity, v, experience, disciplines);
    }

    public VampireData withExperience(int v) {
        return new VampireData(species, clan, generation, bloodPotency, blood, humanity, stains, v, disciplines);
    }

    public VampireData withDiscipline(ResourceLocation discipline, int level) {
        Map<ResourceLocation, Integer> copy = new HashMap<>(disciplines);
        if (level <= 0) {
            copy.remove(discipline);
        } else {
            copy.put(discipline, level);
        }
        return new VampireData(species, clan, generation, bloodPotency, blood, humanity, stains, experience, Map.copyOf(copy));
    }
}
