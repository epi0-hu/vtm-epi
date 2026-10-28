package com.epi0.vtm.block;

import com.epi0.vtm.nemesis.LairRegistry;
import com.epi0.vtm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A coffin that is somebody's resting place. A coffin placed by structure generation carries the faction of the
 * lair it belongs to; once its chunk is live it claims a named vampire for itself (see {@link LairRegistry}).
 */
public class CoffinBlockEntity extends BlockEntity {
    private static final int CLAIM_INTERVAL = 40;

    @Nullable
    private ResourceLocation faction;
    @Nullable
    private ResourceLocation clan;
    @Nullable
    private UUID claimed;

    public CoffinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.COFFIN_ENTITY.get(), pos, state);
    }

    public void setFaction(ResourceLocation faction) {
        this.faction = faction;
        setChanged();
    }

    @Nullable
    public ResourceLocation faction() {
        return faction;
    }

    /** The clan of whoever sleeps here, if the lair is a particular clan's. */
    public void setClan(@Nullable ResourceLocation clan) {
        this.clan = clan;
        setChanged();
    }

    @Nullable
    public ResourceLocation clan() {
        return clan;
    }

    @Nullable
    public UUID claimed() {
        return claimed;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoffinBlockEntity coffin) {
        if (coffin.faction == null || coffin.claimed != null || !(level instanceof ServerLevel server)
                || (level.getGameTime() + pos.asLong()) % CLAIM_INTERVAL != 0) {
            return;
        }
        LairRegistry.claim(server, pos, coffin.faction, coffin.clan).ifPresent(id -> {
            coffin.claimed = id;
            coffin.setChanged();
        });
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (faction != null) {
            tag.putString("Faction", faction.toString());
        }
        if (clan != null) {
            tag.putString("Clan", clan.toString());
        }
        if (claimed != null) {
            tag.putUUID("Claimed", claimed);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        faction = tag.contains("Faction") ? ResourceLocation.tryParse(tag.getString("Faction")) : null;
        clan = tag.contains("Clan") ? ResourceLocation.tryParse(tag.getString("Clan")) : null;
        claimed = tag.hasUUID("Claimed") ? tag.getUUID("Claimed") : null;
    }
}
