package com.epi0.vtm.npc;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.registry.ModEntities;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Mod-bus wiring: attributes and where each kind of NPC may spawn. */
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NpcSetup {

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CIVILIAN.get(), CivilianNpc.createAttributes().build());
        event.put(ModEntities.VAMPIRE.get(), VampireNpc.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.CIVILIAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);
        event.register(ModEntities.VAMPIRE.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) -> level.getDifficulty() != Difficulty.PEACEFUL
                        && Monster.isDarkEnoughToSpawn(level, pos, random)
                        && Mob.checkMobSpawnRules(type, level, spawnType, pos, random),
                RegisterSpawnPlacementsEvent.Operation.OR);
    }

    private NpcSetup() {}
}
