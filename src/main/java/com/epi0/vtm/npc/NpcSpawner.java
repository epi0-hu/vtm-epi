package com.epi0.vtm.npc;

import com.epi0.vtm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Places fully initialised NPCs (name, look, home, faction). Used by commands, spawn eggs' logic and tests. */
public final class NpcSpawner {

    public static CivilianNpc civilian(ServerLevel level, Vec3 pos) {
        CivilianNpc npc = ModEntities.CIVILIAN.get().create(level);
        place(level, npc, pos);
        return npc;
    }

    /** @param clan the clan, or null for a random one */
    public static VampireNpc vampire(ServerLevel level, Vec3 pos, @Nullable ResourceLocation clan) {
        VampireNpc npc = ModEntities.VAMPIRE.get().create(level);
        if (clan != null) {
            npc.setClan(clan);
        }
        place(level, npc, pos);
        return npc;
    }

    private static void place(ServerLevel level, VtmNpc npc, Vec3 pos) {
        npc.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360F, 0F);
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.COMMAND, null);
        level.addFreshEntityWithPassengers(npc);
    }

    private NpcSpawner() {}
}
