package com.epi0.vtm.city;

import com.epi0.vtm.npc.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A generated city: where its people live and work, who rules it, and how much trouble each player has caused in it.
 * Registered by the city's Domain Stone the first time its chunk is live, and kept in {@link CityData}.
 */
public final class City {
    /** Half the width of a town; bigger cities say so in {@link #radius}. */
    public static final int RADIUS = 44;
    public static final int MAX_STRENGTH = 100;
    public static final int MAX_HEAT = 100;

    public record Work(Job job, BlockPos pos) {}

    public final UUID id;
    public String name;
    public final ResourceKey<Level> dimension;
    public final BlockPos center;
    /** The faction that rules the city. */
    public ResourceLocation owner;
    /** How firmly they hold it, 0 to 100. */
    public int strength = 60;
    public final List<BlockPos> homes = new ArrayList<>();
    public final List<Work> works = new ArrayList<>();
    @Nullable
    public BlockPos plaza;
    @Nullable
    public BlockPos club;
    @Nullable
    public BlockPos watch;
    @Nullable
    public BlockPos haven;
    /** Heat per player: how wanted they are here, 0 to 100. */
    public final Map<UUID, Integer> heat = new HashMap<>();
    /** Per vampire: how much of what they are the city's mortals have worked out, 0 to 100. Hunters act on it. */
    public final Map<UUID, Integer> exposure = new HashMap<>();
    /** The hunters' inquisitor in this city, while one is in the field. */
    @Nullable
    public UUID inquisitor;
    public long lastHunt = -100000L;
    /** The named vampire who holds the city for its ruler, if anyone does. */
    @Nullable
    public UUID warden;
    public long lastReinforcement = -100000L;
    public long founded;
    /** How far the city reaches from its Domain Stone, along each axis. */
    public int radius = RADIUS;

    public City(UUID id, String name, ResourceKey<Level> dimension, BlockPos center, ResourceLocation owner) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.center = center;
        this.owner = owner;
    }

    public boolean contains(Level level, BlockPos pos) {
        return level.dimension().equals(dimension) && Math.abs(pos.getX() - center.getX()) <= radius
                && Math.abs(pos.getZ() - center.getZ()) <= radius;
    }

    public int heatOf(UUID player) {
        return heat.getOrDefault(player, 0);
    }

    public int exposureOf(UUID player) {
        return exposure.getOrDefault(player, 0);
    }

    public void addExposure(UUID player, int amount) {
        exposure.merge(player, amount, Integer::sum);
        exposure.computeIfPresent(player, (p, v) -> Math.max(0, Math.min(MAX_HEAT, v)));
        exposure.values().removeIf(v -> v <= 0);
    }

    /** How alarmed the hunters are: the worst exposure of anyone in the city. */
    public int alert() {
        return exposure.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public void addHeat(UUID player, int amount) {
        heat.merge(player, amount, Integer::sum);
        heat.computeIfPresent(player, (p, v) -> Math.max(0, Math.min(MAX_HEAT, v)));
        heat.values().removeIf(v -> v <= 0);
    }

    // ---------------------------------------------------------------- persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Dimension", dimension.location().toString());
        tag.putLong("Center", center.asLong());
        tag.putString("Owner", owner.toString());
        tag.putInt("Strength", strength);
        tag.putLongArray("Homes", homes.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag jobs = new ListTag();
        for (Work work : works) {
            CompoundTag t = new CompoundTag();
            t.putString("Job", work.job().id());
            t.putLong("Pos", work.pos().asLong());
            jobs.add(t);
        }
        tag.put("Works", jobs);
        put(tag, "Plaza", plaza);
        put(tag, "Club", club);
        put(tag, "Watch", watch);
        put(tag, "Haven", haven);
        CompoundTag heatTag = new CompoundTag();
        heat.forEach((player, value) -> heatTag.putInt(player.toString(), value));
        tag.put("Heat", heatTag);
        CompoundTag exposureTag = new CompoundTag();
        exposure.forEach((player, value) -> exposureTag.putInt(player.toString(), value));
        tag.put("Exposure", exposureTag);
        if (inquisitor != null) {
            tag.putUUID("Inquisitor", inquisitor);
        }
        tag.putLong("LastHunt", lastHunt);
        if (warden != null) {
            tag.putUUID("Warden", warden);
        }
        tag.putLong("Reinforced", lastReinforcement);
        tag.putLong("Founded", founded);
        tag.putInt("Radius", radius);
        return tag;
    }

    public static City load(CompoundTag tag) {
        City city = new City(tag.getUUID("Id"), tag.getString("Name"),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                BlockPos.of(tag.getLong("Center")), ResourceLocation.parse(tag.getString("Owner")));
        city.strength = tag.getInt("Strength");
        for (long l : tag.getLongArray("Homes")) {
            city.homes.add(BlockPos.of(l));
        }
        for (Tag t : tag.getList("Works", Tag.TAG_COMPOUND)) {
            CompoundTag wt = (CompoundTag) t;
            Job.byId(wt.getString("Job")).ifPresent(job -> city.works.add(new Work(job, BlockPos.of(wt.getLong("Pos")))));
        }
        city.plaza = get(tag, "Plaza");
        city.club = get(tag, "Club");
        city.watch = get(tag, "Watch");
        city.haven = get(tag, "Haven");
        CompoundTag heatTag = tag.getCompound("Heat");
        for (String key : heatTag.getAllKeys()) {
            try {
                city.heat.put(UUID.fromString(key), heatTag.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // corrupt key
            }
        }
        CompoundTag exposureTag = tag.getCompound("Exposure");
        for (String key : exposureTag.getAllKeys()) {
            try {
                city.exposure.put(UUID.fromString(key), exposureTag.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // corrupt key
            }
        }
        city.inquisitor = tag.hasUUID("Inquisitor") ? tag.getUUID("Inquisitor") : null;
        city.lastHunt = tag.contains("LastHunt") ? tag.getLong("LastHunt") : -100000L;
        city.warden = tag.hasUUID("Warden") ? tag.getUUID("Warden") : null;
        city.lastReinforcement = tag.getLong("Reinforced");
        city.founded = tag.getLong("Founded");
        city.radius = tag.contains("Radius") ? tag.getInt("Radius") : RADIUS;
        return city;
    }

    private static void put(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos != null) {
            tag.putLong(key, pos.asLong());
        }
    }

    @Nullable
    private static BlockPos get(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }
}
