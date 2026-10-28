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
    public static final int MIN_GENERATION = 4;
    public static final int MAX_GENERATION = 16;
    public static final int DEFAULT_HUMANITY = 7;
    public static final int MAX_HUMANITY = 10;
    public static final int MAX_STAINS = 10;
    public static final int MAX_DISCIPLINE_LEVEL = 5;
    public static final int MAX_HUNGER = 5;
    public static final int STARTING_BLOOD_POTENCY = 1;
    public static final float STARTING_BLOOD = 6F;
    public static final float RESPAWN_BLOOD = 3F;

    /** 13th -> 2, 12th -> 3, ... 5th -> 10. */
    public static int maxBloodPotency(int generation) {
        return Mth.clamp(15 - generation, 1, 10);
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
