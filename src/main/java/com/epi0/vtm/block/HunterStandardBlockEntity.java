package com.epi0.vtm.block;

import com.epi0.vtm.city.Lodges;
import com.epi0.vtm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Enters its lodge into the world's records once its chunk is live. */
public class HunterStandardBlockEntity extends BlockEntity {
    private static final int REGISTER_INTERVAL = 40;

    private boolean registered;

    public HunterStandardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.HUNTER_STANDARD_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HunterStandardBlockEntity standard) {
        if (standard.registered || !(level instanceof ServerLevel server) || (level.getGameTime() + pos.asLong()) % REGISTER_INTERVAL != 0) {
            return;
        }
        Lodges.register(server, pos);
        standard.registered = true;
        standard.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Registered", registered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        registered = tag.getBoolean("Registered");
    }
}
