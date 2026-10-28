package com.epi0.vtm.block;

import com.epi0.vtm.city.CityRegistry;
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

/**
 * The Domain Stone at the heart of a generated city. It carries what structure generation knew (the name, the ruling
 * faction, where people live and work) and, once its chunk is live, enters the city into the world's records.
 */
public class CityHeartBlockEntity extends BlockEntity {
    private static final int REGISTER_INTERVAL = 40;

    private String name = "";
    @Nullable
    private ResourceLocation faction;
    private CompoundTag pois = new CompoundTag();
    private boolean registered;

    public CityHeartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CITY_HEART_ENTITY.get(), pos, state);
    }

    public void describe(String name, ResourceLocation faction, CompoundTag pois) {
        this.name = name;
        this.faction = faction;
        this.pois = pois;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CityHeartBlockEntity heart) {
        if (heart.registered || heart.faction == null || !(level instanceof ServerLevel server)
                || (level.getGameTime() + pos.asLong()) % REGISTER_INTERVAL != 0) {
            return;
        }
        CityRegistry.register(server, pos, heart.name, heart.faction, heart.pois);
        heart.registered = true;
        heart.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Name", name);
        if (faction != null) {
            tag.putString("Faction", faction.toString());
        }
        tag.put("Pois", pois);
        tag.putBoolean("Registered", registered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.getString("Name");
        faction = tag.contains("Faction") ? ResourceLocation.tryParse(tag.getString("Faction")) : null;
        pois = tag.getCompound("Pois");
        registered = tag.getBoolean("Registered");
    }
}
