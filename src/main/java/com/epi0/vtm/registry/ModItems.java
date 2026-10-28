package com.epi0.vtm.registry;

import com.epi0.vtm.VtmMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VtmMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VtmMod.MOD_ID);

    public static final DeferredItem<DeferredSpawnEggItem> CIVILIAN_SPAWN_EGG = ITEMS.register("civilian_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CIVILIAN, 0xD7C9A8, 0x7F6A4F, new net.minecraft.world.item.Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> VAMPIRE_SPAWN_EGG = ITEMS.register("vampire_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VAMPIRE, 0x1B1B24, 0x8B0000, new net.minecraft.world.item.Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("vtm",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.vtm"))
                    .icon(() -> new ItemStack(VAMPIRE_SPAWN_EGG.get()))
                    .displayItems((params, output) -> {
                        output.accept(CIVILIAN_SPAWN_EGG.get());
                        output.accept(VAMPIRE_SPAWN_EGG.get());
                    })
                    .build());

    private ModItems() {}
}
