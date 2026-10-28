package com.epi0.vtm.nemesis;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every named vampire in the world, and the history of what has happened to them. Lives with the overworld. */
public final class NemesisData extends SavedData {
    public static final int CHRONICLE_CAPACITY = 300;
    private static final String NAME = "vtm_nemeses";

    private final Map<UUID, NemesisRecord> records = new LinkedHashMap<>();
    private final List<ChronicleEntry> chronicle = new ArrayList<>();
    /** Simulation steps run so far; lets slow processes (loyalty, enmity decay) tick every few steps. */
    public long steps;

    public static NemesisData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NemesisData::new, NemesisData::load, null), NAME);
    }

    public Map<UUID, NemesisRecord> records() {
        return records;
    }

    public NemesisRecord get(UUID id) {
        return records.get(id);
    }

    public void add(NemesisRecord record) {
        records.put(record.id, record);
        setDirty();
    }

    public List<NemesisRecord> all() {
        return List.copyOf(records.values());
    }

    public List<NemesisRecord> inFaction(ResourceLocation faction) {
        return records.values().stream().filter(r -> r.alive() && r.faction.equals(faction)).toList();
    }

    public List<NemesisRecord> inRole(ResourceLocation faction, Role role) {
        return records.values().stream().filter(r -> r.alive() && r.status != Status.BOUND
                && r.faction.equals(faction) && r.role == role).toList();
    }

    public List<NemesisRecord> boundTo(UUID player) {
        return records.values().stream().filter(r -> r.status == Status.BOUND && player.equals(r.boundTo)).toList();
    }

    public List<ChronicleEntry> chronicle() {
        return chronicle;
    }

    public void log(ChronicleEntry entry) {
        chronicle.add(entry);
        while (chronicle.size() > CHRONICLE_CAPACITY) {
            chronicle.remove(0);
        }
        setDirty();
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        records.values().forEach(r -> list.add(r.save()));
        tag.put("Records", list);
        ListTag history = new ListTag();
        for (ChronicleEntry entry : chronicle) {
            ChronicleEntry.CODEC.encodeStart(NbtOps.INSTANCE, entry).result().ifPresent(history::add);
        }
        tag.put("Chronicle", history);
        tag.putLong("Steps", steps);
        return tag;
    }

    public static NemesisData load(CompoundTag tag, HolderLookup.Provider registries) {
        NemesisData data = new NemesisData();
        for (Tag t : tag.getList("Records", Tag.TAG_COMPOUND)) {
            NemesisRecord record = NemesisRecord.load((CompoundTag) t);
            data.records.put(record.id, record);
        }
        for (Tag t : tag.getList("Chronicle", Tag.TAG_COMPOUND)) {
            ChronicleEntry.CODEC.parse(NbtOps.INSTANCE, t).result().ifPresent(data.chronicle::add);
        }
        data.steps = tag.getLong("Steps");
        return data;
    }
}
