package com.epi0.vtm.registry;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.VampireNpc;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, VtmMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CivilianNpc>> CIVILIAN = ENTITIES.register("civilian",
            () -> EntityType.Builder.of(CivilianNpc::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10).build("civilian"));

    public static final DeferredHolder<EntityType<?>, EntityType<VampireNpc>> VAMPIRE = ENTITIES.register("vampire",
            () -> EntityType.Builder.of(VampireNpc::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10).build("vampire"));

    private ModEntities() {}
}
