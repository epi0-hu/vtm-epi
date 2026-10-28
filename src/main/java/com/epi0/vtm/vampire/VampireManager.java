package com.epi0.vtm.vampire;

import com.epi0.vtm.VtmConfig;
import com.epi0.vtm.network.SyncVampirePayload;
import com.epi0.vtm.registry.ModAttachments;
import com.epi0.vtm.registry.Discipline;
import com.epi0.vtm.registry.VtmRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/** Server-side entry point for changing a player's vampire state. Every change is validated here and synced. */
public final class VampireManager {

    public static void set(ServerPlayer player, VampireData data) {
        player.setData(ModAttachments.VAMPIRE, data);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncVampirePayload(VampireData.of(player)));
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
