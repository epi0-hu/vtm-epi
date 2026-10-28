package com.epi0.vtm.city;

import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Job;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.nemesis.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What keeps a city alive: its people are settled in it, trouble makes the watch take notice, and the memory of
 * trouble fades. Runs for cities with a player close enough to see them.
 */
public final class CityLife {
    public static final int ACTIVE_RANGE = 110;
    public static final int MAX_POPULATION = 16;
    private static final int SPAWNS_PER_PASS = 3;

    /** The city whose streets the player is standing in. */
    @Nullable
    public static City at(ServerPlayer player) {
        return CityData.get(player.getServer()).at(player.level(), player.blockPosition());
    }

    public static int targetPopulation(City city) {
        return Math.min(MAX_POPULATION, city.homes.size() + city.works.size() / 2);
    }

    // ---------------------------------------------------------------- trouble

    /** The player did something the city will remember. */
    public static void crime(ServerPlayer player, City city, int amount) {
        int before = city.heatOf(player.getUUID());
        city.addHeat(player.getUUID(), amount);
        int after = city.heatOf(player.getUUID());
        CityData.get(player.getServer()).setDirty();
        if (before < CivilianNpc.HOT && after >= CivilianNpc.HOT) {
            player.displayClientMessage(Component.translatable("message.vtm.city.wanted", city.name), true);
        } else if (before < CivilianNpc.SUSPICIOUS && after >= CivilianNpc.SUSPICIOUS) {
            player.displayClientMessage(Component.translatable("message.vtm.city.watched", city.name), true);
        }
    }

    /** Convenience for the places that notice crimes: finds the city the player is in. */
    public static void crime(ServerPlayer player, int amount) {
        City city = at(player);
        if (city != null) {
            crime(player, city, amount);
        }
    }

    // ---------------------------------------------------------------- the pass

    /** One pass over every city that has a player near it. */
    public static void tick(MinecraftServer server) {
        CityData data = CityData.get(server);
        for (City city : data.all()) {
            ServerLevel level = server.getLevel(city.dimension);
            if (level == null) {
                continue;
            }
            List<ServerPlayer> near = level.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(city.center)) < ACTIVE_RANGE * ACTIVE_RANGE);
            if (near.isEmpty()) {
                continue;
            }
            cool(city);
            populate(level, city);
            for (ServerPlayer player : near) {
                reinforce(level, city, player);
            }
            data.setDirty();
        }
    }

    /** Trouble is forgotten, slowly. */
    static void cool(City city) {
        city.heat.replaceAll((player, heat) -> heat - 1);
        city.heat.values().removeIf(heat -> heat <= 0);
    }

    /** Everyone who belongs to the city and is standing in the world right now. */
    public static List<CivilianNpc> residents(ServerLevel level, City city) {
        AABB area = new AABB(city.center).inflate(City.RADIUS + 8, 40, City.RADIUS + 8);
        return level.getEntitiesOfClass(CivilianNpc.class, area, c -> city.id.equals(c.cityId()) && c.isAlive());
    }

    /** Fills the city's beds and workplaces, a few people at a time. */
    public static int populate(ServerLevel level, City city) {
        List<CivilianNpc> here = residents(level, city);
        int spawned = 0;
        Set<BlockPos> worked = new HashSet<>();
        Set<BlockPos> slept = new HashSet<>();
        here.forEach(c -> {
            if (c.work() != null) {
                worked.add(c.work());
            }
            if (c.home() != null) {
                slept.add(c.home());
            }
        });
        int target = targetPopulation(city);
        for (City.Work work : city.works) {
            if (here.size() + spawned >= target + city.works.size() || spawned >= SPAWNS_PER_PASS) {
                break;
            }
            if (worked.contains(work.pos()) || !level.isLoaded(work.pos())) {
                continue;
            }
            BlockPos bed = freeBed(city, slept);
            if (bed == null) {
                bed = work.pos();
            }
            spawn(level, city, work.pos(), bed, work.job(), work.pos());
            slept.add(bed);
            spawned++;
        }
        for (BlockPos bed : city.homes) {
            if (here.size() + spawned >= target + city.works.size() || spawned >= SPAWNS_PER_PASS) {
                break;
            }
            if (slept.contains(bed) || !level.isLoaded(bed)) {
                continue;
            }
            spawn(level, city, bed, bed, Job.RESIDENT, null);
            slept.add(bed);
            spawned++;
        }
        return spawned;
    }

    @Nullable
    private static BlockPos freeBed(City city, Set<BlockPos> taken) {
        return city.homes.stream().filter(b -> !taken.contains(b)).findFirst().orElse(null);
    }

    public static CivilianNpc spawn(ServerLevel level, City city, BlockPos at, BlockPos bed, Job job, @Nullable BlockPos work) {
        CivilianNpc npc = NpcSpawner.civilian(level, Vec3.atBottomCenterOf(at.above()));
        npc.settle(city, bed, job, work);
        return npc;
    }

    /** A wanted player brings the watch down on themselves. */
    static void reinforce(ServerLevel level, City city, ServerPlayer player) {
        long now = level.getGameTime();
        if (city.heatOf(player.getUUID()) < 90 || now - city.lastReinforcement < 12000L || !city.contains(level, player.blockPosition())) {
            return;
        }
        city.lastReinforcement = now;
        for (int i = 0; i < 2; i++) {
            Vec3 spot = Placement.spot(level, player.position(), 14, 22, player.getRandom());
            if (spot == null) {
                continue;
            }
            CivilianNpc guard = NpcSpawner.civilian(level, spot);
            guard.settle(city, BlockPos.containing(spot), Job.WATCHMAN, null);
            guard.setExpiry(now + 6000L);
            guard.setTarget(player);
        }
        player.displayClientMessage(Component.translatable("message.vtm.city.reinforcements", city.name), true);
    }

    private CityLife() {}
}
