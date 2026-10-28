package com.epi0.vtm.registry;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.block.CoffinBlock;
import com.epi0.vtm.block.CoffinBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VtmMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VtmMod.MOD_ID);

    public static final DeferredBlock<CoffinBlock> COFFIN = BLOCKS.register("coffin", () -> new CoffinBlock(
            BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.WOOD).noOcclusion()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoffinBlockEntity>> COFFIN_ENTITY =
            BLOCK_ENTITIES.register("coffin", () -> BlockEntityType.Builder.of(CoffinBlockEntity::new, COFFIN.get()).build(null));

    public static void registerItems() {
        ModItems.ITEMS.register("coffin", () -> new BlockItem(COFFIN.get(), new Item.Properties()));
    }

    private ModBlocks() {}
}
