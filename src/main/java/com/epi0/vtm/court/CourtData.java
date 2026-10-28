package com.epi0.vtm.court;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every player-run court in the world, by faction. Lives with the overworld. */
public final class CourtData extends SavedData {
    private static final String NAME = "vtm_courts";

    private final Map<ResourceLocation, Reign> reigns = new LinkedHashMap<>();

    public static CourtData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CourtData::new, CourtData::load, null), NAME);
    }

    @Nullable
    public Reign reign(ResourceLocation faction) {
        return reigns.get(faction);
    }

    @Nullable
    public Reign reignOf(UUID player) {
        return reigns.values().stream().filter(r -> r.ruler.equals(player)).findFirst().orElse(null);
    }

    public List<Reign> all() {
        return List.copyOf(reigns.values());
    }

    public void add(Reign reign) {
        reigns.put(reign.faction, reign);
        setDirty();
    }

    public void remove(ResourceLocation faction) {
        reigns.remove(faction);
        setDirty();
    }

    public void clear() {
        reigns.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        reigns.values().forEach(r -> list.add(r.save()));
        tag.put("Reigns", list);
        return tag;
    }

    public static CourtData load(CompoundTag tag, HolderLookup.Provider registries) {
        CourtData data = new CourtData();
        for (Tag t : tag.getList("Reigns", Tag.TAG_COMPOUND)) {
            Reign reign = Reign.load((CompoundTag) t);
            data.reigns.put(reign.faction, reign);
        }
        return data;
    }
}
