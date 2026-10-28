package com.epi0.vtm.vampire;

import com.epi0.vtm.VtmConfig;
import com.epi0.vtm.network.SyncVampirePayload;
import com.epi0.vtm.network.VtmNetwork;
import com.epi0.vtm.power.Forms;
import com.epi0.vtm.power.PassiveEffects;
import com.epi0.vtm.power.SenseLink;
import com.epi0.vtm.registry.ModAttachments;
import com.epi0.vtm.registry.Discipline;
import com.epi0.vtm.registry.VtmRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Server-side entry point for changing a player's vampire state. Every change is validated here and synced. */
public final class VampireManager {

    public static void set(ServerPlayer player, VampireData data) {
        player.setData(ModAttachments.VAMPIRE, data);
        PassiveEffects.apply(player, data);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        VtmNetwork.send(player, new SyncVampirePayload(VampireData.of(player)));
    }

    public static VampireData embraced(ResourceLocation clan) {
        return VampireData.DEFAULT
                .withSpecies(Species.VAMPIRE)
                .withClan(Optional.of(clan))
                .withBloodPotency(VampireRules.STARTING_BLOOD_POTENCY)
                .withBlood(VampireRules.STARTING_BLOOD)
                .withExperience(VtmConfig.STARTING_EXPERIENCE.get());
    }

    public static void embrace(ServerPlayer player, ResourceLocation clan) {
        set(player, embraced(clan));
    }

    public static void makeMortal(ServerPlayer player) {
        Frenzy.quell(player);
        SenseLink.end(player);
        Forms.clear(player);
        set(player, VampireData.DEFAULT);
    }

    /** Spends experience on the next level of {@code disciplineId}, if the player is allowed to. */
    public static boolean tryLearnDiscipline(ServerPlayer player, ResourceLocation disciplineId) {
        VampireData data = VampireData.of(player);
        if (!data.isVampire()) {
            return false;
        }
        Optional<Discipline> discipline = VtmRegistries.discipline(player.level().registryAccess(), disciplineId);
        if (discipline.isEmpty()) {
            return false;
        }
        boolean inClan = VampireRules.isInClan(player.level().registryAccess(), data, disciplineId);
        if (discipline.get().clanOnly() && !inClan) {
            return false;
        }
        int current = data.disciplineLevel(disciplineId);
        if (current >= VampireRules.MAX_DISCIPLINE_LEVEL) {
            return false;
        }
        int cost = VampireRules.learnCost(current + 1, inClan);
        if (data.experience() < cost) {
            return false;
        }
        set(player, data.withExperience(data.experience() - cost).withDiscipline(disciplineId, current + 1));
        return true;
    }

    private VampireManager() {}
}
