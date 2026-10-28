package com.epi0.vtm.city;

import com.epi0.vtm.npc.Job;
import com.epi0.vtm.nemesis.NemesisManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** Enters generated cities into the world's records, and describes cities to and from NBT. */
public final class CityRegistry {

    /** Registers the city whose Domain Stone is at {@code heart}. Does nothing if it is already known. */
    public static City register(ServerLevel level, BlockPos heart, String name, ResourceLocation faction, CompoundTag pois) {
        CityData data = CityData.get(level.getServer());
        for (City existing : data.all()) {
            if (existing.center.equals(heart) && existing.dimension.equals(level.dimension())) {
                return existing;
            }
        }
        City city = new City(UUID.randomUUID(), name, level.dimension(), heart, faction);
        city.founded = level.getGameTime();
        read(city, pois);
        data.add(city);
        return city;
    }

    public static CompoundTag write(City city) {
        CompoundTag tag = new CompoundTag();
        tag.putLongArray("Homes", city.homes.stream().mapToLong(BlockPos::asLong).toArray());
        net.minecraft.nbt.ListTag works = new net.minecraft.nbt.ListTag();
        for (City.Work work : city.works) {
            CompoundTag t = new CompoundTag();
            t.putString("Job", work.job().id());
            t.putLong("Pos", work.pos().asLong());
            works.add(t);
        }
        tag.put("Works", works);
        if (city.plaza != null) {
            tag.putLong("Plaza", city.plaza.asLong());
        }
        if (city.club != null) {
            tag.putLong("Club", city.club.asLong());
        }
        if (city.watch != null) {
            tag.putLong("Watch", city.watch.asLong());
        }
        if (city.haven != null) {
            tag.putLong("Haven", city.haven.asLong());
        }
        return tag;
    }

    static void read(City city, CompoundTag pois) {
        for (long l : pois.getLongArray("Homes")) {
            city.homes.add(BlockPos.of(l));
        }
        for (Tag t : pois.getList("Works", Tag.TAG_COMPOUND)) {
            CompoundTag wt = (CompoundTag) t;
            Job.byId(wt.getString("Job")).ifPresent(job -> city.works.add(new City.Work(job, BlockPos.of(wt.getLong("Pos")))));
        }
        city.plaza = pois.contains("Plaza") ? BlockPos.of(pois.getLong("Plaza")) : null;
        city.club = pois.contains("Club") ? BlockPos.of(pois.getLong("Club")) : null;
        city.watch = pois.contains("Watch") ? BlockPos.of(pois.getLong("Watch")) : null;
        city.haven = pois.contains("Haven") ? BlockPos.of(pois.getLong("Haven")) : null;
    }

    private CityRegistry() {}
}
