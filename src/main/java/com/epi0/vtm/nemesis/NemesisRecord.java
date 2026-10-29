package com.epi0.vtm.nemesis;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A named vampire who exists whether or not anyone is looking at them. The record is the person; a
 * {@code VampireNpc} is merely where they happen to be standing. Records live in {@link NemesisData}, are advanced by
 * {@link NemesisSimulator} while unobserved, and are bound to an entity by {@link NemesisManager} when they appear.
 */
public final class NemesisRecord {
    public static final int MAX_LEVEL = 20;
    public static final int MAX_RESIST = 3;

    public final UUID id;
    public String name;
    public ResourceLocation clan;
    public ResourceLocation faction;
    public Role role = Role.AGENT;
    public Status status = Status.ACTIVE;
    public int level = 1;
    /** Which generation of the blood they are: 1st is the oldest. */
    public int generation = 13;
    public int xp;
    /** 0 to 100: how badly they want the seat above theirs. */
    public int ambition;
    /** Times they have been beaten and survived. */
    public int deaths;
    /** Players they have killed. */
    public int kills;
    public long wakeAt;
    public final List<Trait> traits = new ArrayList<>();
    public final List<Scar> scars = new ArrayList<>();
    public final Map<DamageKind, Integer> resist = new EnumMap<>(DamageKind.class);
    public final List<Relation> relations = new ArrayList<>();
    public final Map<UUID, Feud> feuds = new LinkedHashMap<>();
    @Nullable
    public ResourceKey<Level> lairDimension;
    @Nullable
    public BlockPos lair;
    @Nullable
    public ResourceKey<Level> seenDimension;
    @Nullable
    public BlockPos lastSeen;
    /** The player this vampire is bound to by blood, if any. */
    @Nullable
    public UUID boundTo;
    public int loyalty;
    @Nullable
    public Mission mission;
    /** Tribute earned on a mission, waiting for the owner to come online. */
    public int pendingTribute;
    /** How loyal they are to a player-run court they sit in (0 to 100). */
    public int courtLoyalty = 50;
    /** When they rose against the ruler (0 if they have not), and how many times the ruler had beaten or spared them then. */
    public long revoltSince;
    public int revoltBase;
    /** The entity currently standing in for this vampire (kept to stop duplicates). */
    @Nullable
    public UUID entity;

    public NemesisRecord(UUID id, String name, ResourceLocation clan, ResourceLocation faction) {
        this.id = id;
        this.name = name;
        this.clan = clan;
        this.faction = faction;
    }

    // ---------------------------------------------------------------- derived

    public boolean has(Trait trait) {
        return traits.contains(trait);
    }

    public boolean alive() {
        return status != Status.DESTROYED;
    }

    /** How hard they are to beat, on a rough scale: level plus what their seat and strengths add. */
    public int power() {
        int power = level + role.power();
        for (Trait t : traits) {
            power += switch (t.kind()) {
                case STRENGTH -> 1;
                case WEAKNESS -> -1;
                case QUIRK -> 0;
            };
        }
        // Older blood is stronger: a point for every two generations below the 13th.
        return Math.max(1, power + deaths + Math.max(-1, (13 - generation) / 2));
    }

    /** Defeats they can survive before the next one is final. */
    public int resilience() {
        return 1 + (role == Role.LEADER || role == Role.LIEUTENANT ? 1 : 0) + (has(Trait.UNDYING) ? 1 : 0);
    }

    public int resistance(DamageKind kind) {
        return resist.getOrDefault(kind, 0);
    }

    public Feud feud(UUID player) {
        return feuds.computeIfAbsent(player, p -> new Feud());
    }

    @Nullable
    public Feud feudOrNull(UUID player) {
        return feuds.get(player);
    }

    public boolean related(UUID other, Relation.Type type) {
        return relations.stream().anyMatch(r -> r.other().equals(other) && r.type() == type);
    }

    public int xpForNextLevel() {
        return 6 + level * 3;
    }

    // ---------------------------------------------------------------- persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Clan", clan.toString());
        tag.putString("Faction", faction.toString());
        tag.putString("Role", role.id());
        tag.putString("Status", status.id());
        tag.putInt("Level", level);
        tag.putInt("Generation", generation);
        tag.putInt("Xp", xp);
        tag.putInt("Ambition", ambition);
        tag.putInt("Deaths", deaths);
        tag.putInt("Kills", kills);
        tag.putLong("WakeAt", wakeAt);
        tag.put("Traits", strings(traits.stream().map(Trait::id).toList()));
        tag.put("Scars", strings(scars.stream().map(Scar::id).toList()));
        CompoundTag resistTag = new CompoundTag();
        resist.forEach((kind, level) -> resistTag.putInt(kind.id(), level));
        tag.put("Resist", resistTag);
        ListTag rel = new ListTag();
        for (Relation r : relations) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Other", r.other());
            t.putString("Type", r.type().id());
            rel.add(t);
        }
        tag.put("Relations", rel);
        CompoundTag feudTag = new CompoundTag();
        feuds.forEach((player, feud) -> feudTag.put(player.toString(), feud.save()));
        tag.put("Feuds", feudTag);
        if (lair != null && lairDimension != null) {
            tag.putLong("Lair", lair.asLong());
            tag.putString("LairDim", lairDimension.location().toString());
        }
        if (lastSeen != null && seenDimension != null) {
            tag.putLong("Seen", lastSeen.asLong());
            tag.putString("SeenDim", seenDimension.location().toString());
        }
        if (boundTo != null) {
            tag.putUUID("BoundTo", boundTo);
        }
        tag.putInt("Loyalty", loyalty);
        tag.putInt("Tribute", pendingTribute);
        if (mission != null) {
            tag.put("Mission", mission.save());
        }
        tag.putInt("CourtLoyalty", courtLoyalty);
        tag.putLong("RevoltSince", revoltSince);
        tag.putInt("RevoltBase", revoltBase);
        if (entity != null) {
            tag.putUUID("Entity", entity);
        }
        return tag;
    }

    public static NemesisRecord load(CompoundTag tag) {
        ResourceLocation clan = ResourceLocation.parse(tag.getString("Clan"));
        ResourceLocation faction = ResourceLocation.parse(tag.getString("Faction"));
        NemesisRecord r = new NemesisRecord(tag.getUUID("Id"), tag.getString("Name"), clan, faction);
        r.role = Role.byId(tag.getString("Role")).orElse(Role.AGENT);
        r.status = Status.byId(tag.getString("Status"));
        r.level = Math.max(1, tag.getInt("Level"));
        r.generation = tag.contains("Generation") ? Math.max(1, Math.min(16, tag.getInt("Generation"))) : 13;
        r.xp = tag.getInt("Xp");
        r.ambition = tag.getInt("Ambition");
        r.deaths = tag.getInt("Deaths");
        r.kills = tag.getInt("Kills");
        r.wakeAt = tag.getLong("WakeAt");
        for (Tag t : tag.getList("Traits", Tag.TAG_STRING)) {
            Trait.byId(t.getAsString()).ifPresent(r.traits::add);
        }
        for (Tag t : tag.getList("Scars", Tag.TAG_STRING)) {
            Scar.byId(t.getAsString()).ifPresent(r.scars::add);
        }
        CompoundTag resistTag = tag.getCompound("Resist");
        for (DamageKind kind : DamageKind.values()) {
            if (resistTag.contains(kind.id())) {
                r.resist.put(kind, resistTag.getInt(kind.id()));
            }
        }
        for (Tag t : tag.getList("Relations", Tag.TAG_COMPOUND)) {
            CompoundTag rt = (CompoundTag) t;
            Relation.Type.byId(rt.getString("Type")).ifPresent(type -> r.relations.add(new Relation(rt.getUUID("Other"), type)));
        }
        CompoundTag feudTag = tag.getCompound("Feuds");
        for (String key : feudTag.getAllKeys()) {
            try {
                r.feuds.put(UUID.fromString(key), Feud.load(feudTag.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // a corrupt key: drop that feud
            }
        }
        if (tag.contains("Lair")) {
            r.lair = BlockPos.of(tag.getLong("Lair"));
            r.lairDimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(tag.getString("LairDim")));
        }
        if (tag.contains("Seen")) {
            r.lastSeen = BlockPos.of(tag.getLong("Seen"));
            r.seenDimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(tag.getString("SeenDim")));
        }
        r.boundTo = tag.hasUUID("BoundTo") ? tag.getUUID("BoundTo") : null;
        r.loyalty = tag.getInt("Loyalty");
        r.pendingTribute = tag.getInt("Tribute");
        r.mission = tag.contains("Mission") ? Mission.load(tag.getCompound("Mission")) : null;
        r.courtLoyalty = tag.contains("CourtLoyalty") ? tag.getInt("CourtLoyalty") : 50;
        r.revoltSince = tag.getLong("RevoltSince");
        r.revoltBase = tag.getInt("RevoltBase");
        r.entity = tag.hasUUID("Entity") ? tag.getUUID("Entity") : null;
        return r;
    }

    private static ListTag strings(List<String> values) {
        ListTag list = new ListTag();
        values.forEach(v -> list.add(StringTag.valueOf(v)));
        return list;
    }
}
