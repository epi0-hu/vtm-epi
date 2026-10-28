package com.epi0.vtm.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every city in the world. Lives with the overworld. */
public final class CityData extends SavedData {
    private static final String NAME = "vtm_cities";

    private final Map<UUID, City> cities = new LinkedHashMap<>();

    public static CityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CityData::new, CityData::load, null), NAME);
    }

    public List<City> all() {
        return List.copyOf(cities.values());
    }

    @Nullable
    public City get(UUID id) {
        return cities.get(id);
    }

    public void add(City city) {
        cities.put(city.id, city);
        setDirty();
    }

    /** The city whose streets {@code pos} lies in (the nearest, if borders overlap), if any. */
    @Nullable
    public City at(Level level, BlockPos pos) {
        City best = null;
        double bestDistance = Double.MAX_VALUE;
        for (City city : cities.values()) {
            if (city.contains(level, pos)) {
                double d = city.center.distSqr(pos);
                if (d < bestDistance) {
                    best = city;
                    bestDistance = d;
                }
            }
        }
        return best;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        cities.values().forEach(c -> list.add(c.save()));
        tag.put("Cities", list);
        return tag;
    }

    public static CityData load(CompoundTag tag, HolderLookup.Provider registries) {
        CityData data = new CityData();
        for (Tag t : tag.getList("Cities", Tag.TAG_COMPOUND)) {
            City city = City.load((CompoundTag) t);
            data.cities.put(city.id, city);
        }
        return data;
    }
}
