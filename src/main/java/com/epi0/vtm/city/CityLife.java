package com.epi0.vtm.city;

import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Job;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.npc.VtmNpc;
import com.epi0.vtm.nemesis.NemesisManager;
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
    /** How far past a city's edge a player still keeps it alive. */
    public static final int ACTIVE_MARGIN = 60;
    public static final int MAX_POPULATION = 16;
    private static final int SPAWNS_PER_PASS = 3;

    /** The city whose streets the player is standing in. */
    @Nullable
    public static City at(ServerPlayer player) {
        return CityData.get(player.getServer()).at(player.level(), player.blockPosition());
    }

    public static int targetPopulation(City city) {
        return Math.min(populationCap(city), city.homes.size() + city.works.size() / 2);
    }

    /** A town holds sixteen people; a metropolis a good many more. */
    public static int populationCap(City city) {
        return MAX_POPULATION + Math.max(0, city.radius - City.RADIUS) * 3 / 4;
    }

    private static int spawnsPerPass(City city) {
        return SPAWNS_PER_PASS + Math.max(0, city.radius - City.RADIUS) / 20;
    }

    /** Whether a player is close enough to the city for it to be going about its business. */
    static boolean isNear(City city, ServerPlayer player) {
        return !player.isSpectator() && Math.abs(player.getX() - city.center.getX()) <= city.radius + ACTIVE_MARGIN
                && Math.abs(player.getZ() - city.center.getZ()) <= city.radius + ACTIVE_MARGIN;
    }

    private static double distanceToPlayers(ServerLevel level, BlockPos pos) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                best = Math.min(best, player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
            }
        }
        return best;
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
            List<ServerPlayer> near = level.getPlayers(p -> isNear(city, p));
            if (near.isEmpty()) {
                continue;
            }
            cool(city);
            populate(level, city);
            if (VtmNpc.isNight(level)) {
                patrol(level, city);
            }
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
        AABB area = new AABB(city.center).inflate(city.radius + 8, 80, city.radius + 8);
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
        int perPass = spawnsPerPass(city);
        // The people nearest a player are the ones worth settling first.
        List<City.Work> works = city.works.size() < 2 ? city.works : city.works.stream()
                .sorted(java.util.Comparator.comparingDouble(w -> distanceToPlayers(level, w.pos()))).toList();
        List<BlockPos> homes = city.homes.size() < 2 ? city.homes : city.homes.stream()
                .sorted(java.util.Comparator.comparingDouble(b -> distanceToPlayers(level, b))).toList();
        for (City.Work work : works) {
            if (here.size() + spawned >= target + city.works.size() || spawned >= perPass) {
                break;
            }
            if (worked.contains(work.pos()) || !level.isLoaded(work.pos())) {
                continue;
            }
            BlockPos bed = freeBed(homes, slept);
            if (bed == null) {
                bed = work.pos();
            }
            spawn(level, city, work.pos(), bed, work.job(), work.pos());
            slept.add(bed);
            spawned++;
        }
        for (BlockPos bed : homes) {
            if (here.size() + spawned >= target + city.works.size() || spawned >= perPass) {
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
    private static BlockPos freeBed(List<BlockPos> homes, Set<BlockPos> taken) {
        return homes.stream().filter(b -> !taken.contains(b)).findFirst().orElse(null);
    }

    public static CivilianNpc spawn(ServerLevel level, City city, BlockPos at, BlockPos bed, Job job, @Nullable BlockPos work) {
        CivilianNpc npc = NpcSpawner.civilian(level, Vec3.atBottomCenterOf(at.above()));
        npc.settle(city, bed, job, work);
        return npc;
    }

    /** Vampires of the ruling faction walk the streets after dark; the stronger the hold, the more of them. */
    public static int patrol(ServerLevel level, City city) {
        int wanted = 1 + city.strength / 40 + (city.radius - City.RADIUS) / 40;
        AABB area = new AABB(city.center).inflate(city.radius + 8, 80, city.radius + 8);
        int present = level.getEntitiesOfClass(VampireNpc.class, area, v -> city.id.equals(v.patrol()) && v.isAlive()).size();
        long dawn = level.getGameTime() + Math.max(200L, 23500L - level.getDayTime() % 24000L);
        int spawned = 0;
        while (present + spawned < wanted && city.plaza != null) {
            Vec3 spot = Placement.spot(level, Vec3.atCenterOf(city.plaza), 4, 24, level.random);
            if (spot == null) {
                break;
            }
            VampireNpc patrol = NpcSpawner.vampire(level, spot, NemesisManager.clanFor(level.getServer(), city.owner, level.random));
            patrol.setFaction(city.owner);
            patrol.setPatrol(city.id);
            patrol.setExpiry(dawn);
            spawned++;
        }
        return spawned;
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
