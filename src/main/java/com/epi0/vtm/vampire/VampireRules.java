package com.epi0.vtm.vampire;

import com.epi0.vtm.registry.Clan;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.VampireNpc;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * Our spin on the V5 rules, kept in one place so balance changes are one-file edits.
 * <ul>
 *   <li>Blood is a pool (not just a hunger track); hunger 0-5 is derived from it like V5's Hunger dice.</li>
 *   <li>Generation caps Blood Potency; Blood Potency grows the blood pool.</li>
 *   <li>Disciplines cost experience: new level x5 in-clan, x7 out-of-clan.</li>
 * </ul>
 */
public final class VampireRules {
    public static final int DEFAULT_GENERATION = 13;
    public static final int MIN_GENERATION = 1;
    public static final int MAX_GENERATION = 16;
    public static final int DEFAULT_HUMANITY = 7;
    public static final int MAX_HUMANITY = 10;
    public static final int MAX_STAINS = 10;
    public static final int MAX_DISCIPLINE_LEVEL = 5;
    public static final int MAX_HUNGER = 5;
    public static final int STARTING_BLOOD_POTENCY = 1;
    public static final float STARTING_BLOOD = 6F;
    public static final float RESPAWN_BLOOD = 3F;

    /** 13th -> 2, 12th -> 3, ... 5th -> 10, and past it the ancients: 1st -> 14. */
    /** Vampires of this generation or older are elders: rumoured before they are met, and worth seeking out. */
    public static final int ELDER_MAX = 6;

    public static int maxBloodPotency(int generation) {
        return Mth.clamp(15 - generation, 1, 14);
    }

    // ---------------------------------------------------------------- what generation means

    /** Cooldowns of an older vampire's powers run shorter: 4% per generation below the 13th (and longer above). */
    public static float cooldownFactor(int generation) {
        return Mth.clamp(1F - 0.04F * (13 - generation), 0.45F, 1.15F);
    }

    /** Older blood spends less to do the same: 3% per generation below the 13th. */
    public static float costFactor(int generation) {
        return Mth.clamp(1F - 0.03F * (13 - generation), 0.5F, 1.1F);
    }

    /** Extra dice in every roll: one for every three generations below the 13th (a thin-blood loses one). */
    public static int poolBonus(int generation) {
        return Math.floorDiv(13 - generation, 3);
    }

    /** Experience to raise Blood Potency to {@code newLevel}. */
    public static int potencyCost(int newLevel) {
        return newLevel * 10;
    }

    public static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        return n + switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    // ---------------------------------------------------------------- where generations come from

    /**
     * The generation a new soul is born to: 0.5% are 1st, 1% are 2nd, 3% are 3rd, and the rest thin out from the
     * young blood of the 13th (the commonest) back towards the 4th.
     */
    public static int rollGeneration(net.minecraft.util.RandomSource random) {
        double roll = random.nextDouble() * 100.0;
        if (roll < 0.5) {
            return 1;
        }
        if (roll < 1.5) {
            return 2;
        }
        if (roll < 4.5) {
            return 3;
        }
        roll -= 4.5;
        int[] weights = {20, 30, 40, 50, 60, 80, 100, 120, 150, 305}; // 4th ... 13th, in tenths of a percent
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i] / 10.0;
            if (roll < 0) {
                return 4 + i;
            }
        }
        return 13;
    }

    /** The generation a named vampire is made at, by the seat they hold. */
    public static int npcGeneration(com.epi0.vtm.nemesis.Role role, boolean thinBlood, net.minecraft.util.RandomSource random) {
        if (thinBlood) {
            return 14 + random.nextInt(3);
        }
        return switch (role) {
            case LEADER -> Math.max(3, 5 + random.nextInt(4) - (random.nextInt(16) == 0 ? 2 : 0));
            case LIEUTENANT -> 8 + random.nextInt(3);
            case ENFORCER -> 10 + random.nextInt(3);
            case AGENT -> 11 + random.nextInt(3);
        };
    }

    public static float maxBlood(VampireData data) {
        return 10F + 2F * data.bloodPotency();
    }

    public static int hunger(VampireData data) {
        float max = maxBlood(data);
        if (data.blood() <= 0F) {
            return MAX_HUNGER;
        }
        return Mth.clamp(MAX_HUNGER - (int) Math.floor(data.blood() / max * MAX_HUNGER), 0, MAX_HUNGER);
    }

    public static int learnCost(int newLevel, boolean inClan) {
        return newLevel * (inClan ? 5 : 7);
    }

    public static boolean isInClan(RegistryAccess access, VampireData data, ResourceLocation discipline) {
        return data.clan()
                .flatMap(id -> VtmRegistries.clan(access, id))
                .map(Clan::disciplines)
                .map(list -> list.contains(discipline))
                .orElse(false);
    }

    /** Blood points gained from a full feed on this creature. */
    public static float bloodValue(LivingEntity target) {
        if (target instanceof Player || target instanceof AbstractVillager || target instanceof CivilianNpc) {
            return 3F;
        }
        if (target instanceof VampireNpc) {
            return 0F;
        }
        if (target.getType().is(EntityTypeTags.UNDEAD)) {
            return 0F;
        }
        if (target instanceof Animal) {
            return 1F;
        }
        return 0.5F;
    }

    /** Killing these costs humanity (tracked as stains). */
    public static boolean isInnocent(LivingEntity target) {
        return target instanceof Player || target instanceof AbstractVillager || target instanceof CivilianNpc;
    }

    private VampireRules() {}
}
