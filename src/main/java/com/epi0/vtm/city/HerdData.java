package com.epi0.vtm.city;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every mortal who has agreed to be a vampire's vessel, or has been made their ghoul, as that vampire last saw them.
 * The people themselves live in the world; this is what the Herd page of the codex can say about them when they are
 * far away.
 */
public final class HerdData extends SavedData {
    private static final String NAME = "vtm_herd";

    /** @param drankDay the day (game time / 24000) the vampire last drank from them, or -1 */
    public static final class Retainer {
        public final UUID npc;
        public final UUID owner;
        public String name;
        public boolean ghoul;
        public boolean dead;
        public int rapport;
        public long drankDay = -1;
        public GlobalPos place;
        public String city = "";

        Retainer(UUID npc, UUID owner, String name) {
            this.npc = npc;
            this.owner = owner;
            this.name = name;
        }
    }

    private final Map<String, Retainer> retainers = new LinkedHashMap<>();

    public static HerdData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(HerdData::new, HerdData::load, null), NAME);
    }

    private static String key(UUID owner, UUID npc) {
        return owner + "/" + npc;
    }

    public Retainer entry(UUID owner, UUID npc, String name) {
        return retainers.computeIfAbsent(key(owner, npc), k -> new Retainer(npc, owner, name));
    }

    public Retainer find(UUID owner, UUID npc) {
        return retainers.get(key(owner, npc));
    }

    public List<Retainer> of(UUID owner) {
        List<Retainer> list = new ArrayList<>();
        for (Retainer r : retainers.values()) {
            if (r.owner.equals(owner)) {
                list.add(r);
            }
        }
        return list;
    }

    /** Everyone who has this person down as theirs. */
    public List<Retainer> about(UUID npc) {
        return retainers.values().stream().filter(r -> r.npc.equals(npc)).toList();
    }

    public void clear() {
        retainers.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Retainer r : retainers.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Npc", r.npc);
            t.putUUID("Owner", r.owner);
            t.putString("Name", r.name);
            t.putBoolean("Ghoul", r.ghoul);
            t.putBoolean("Dead", r.dead);
            t.putInt("Rapport", r.rapport);
            t.putLong("Drank", r.drankDay);
            t.putString("City", r.city);
            if (r.place != null) {
                GlobalPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, r.place).result().ifPresent(p -> t.put("Place", p));
            }
            list.add(t);
        }
        tag.put("Retainers", list);
        return tag;
    }

    public static HerdData load(CompoundTag tag, HolderLookup.Provider registries) {
        HerdData data = new HerdData();
        for (Tag raw : tag.getList("Retainers", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Retainer r = new Retainer(t.getUUID("Npc"), t.getUUID("Owner"), t.getString("Name"));
            r.ghoul = t.getBoolean("Ghoul");
            r.dead = t.getBoolean("Dead");
            r.rapport = t.getInt("Rapport");
            r.drankDay = t.getLong("Drank");
            r.city = t.getString("City");
            if (t.contains("Place")) {
                r.place = GlobalPos.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, t.get("Place")).result().orElse(null);
            }
            data.retainers.put(key(r.owner, r.npc), r);
        }
        return data;
    }
}
