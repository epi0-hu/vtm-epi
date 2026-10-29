package com.epi0.vtm.city;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.nemesis.Placement;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Job;
import com.epi0.vtm.npc.NpcNames;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

/**
 * The hunters' lodges: fortified houses in the wild, each with a garrison and a master. A lodge that stands makes every
 * city within its reach more dangerous to be exposed in; a lodge whose master has fallen is broken for a week.
 */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class Lodges {
    /** How far a lodge's influence reaches over cities, and how far its notoriety map extends. */
    public static final int REACH = 600;
    public static final int GARRISON = 3;
    public static final long BROKEN_FOR = 7L * 24000L;
    /** A lodge keeps its garrison only while someone is near enough to see it. */
    private static final double WAKE_RANGE = 96.0;

    // ---------------------------------------------------------------- records

    public static LodgeData.Lodge register(ServerLevel level, BlockPos standard) {
        LodgeData data = LodgeData.get(level.getServer());
        LodgeData.Lodge existing = data.at(level.dimension(), standard);
        return existing != null ? existing : data.add(new LodgeData.Lodge(UUID.randomUUID(), level.dimension(), standard));
    }

    public static Component describe(ServerLevel level, BlockPos pos) {
        LodgeData.Lodge lodge = LodgeData.get(level.getServer()).at(level.dimension(), pos);
        if (lodge == null) {
            return Component.translatable("message.vtm.lodge.unknown");
        }
        long left = lodge.brokenUntil - level.getGameTime();
        return left > 0 ? Component.translatable("message.vtm.lodge.broken", left / 24000L + 1)
                : Component.translatable("message.vtm.lodge.standing");
    }

    // ---------------------------------------------------------------- reach

    /** Whether a lodge that still stands is within reach of the city. */
    public static boolean supports(MinecraftServer server, City city) {
        long now = server.overworld().getGameTime();
        return LodgeData.get(server).all().stream().anyMatch(l -> l.dimension.equals(city.dimension) && l.standing(now)
                && Math.abs(l.pos.getX() - city.center.getX()) <= REACH && Math.abs(l.pos.getZ() - city.center.getZ()) <= REACH);
    }

    /** How well the hunters know a vampire in this part of the world: their worst exposure in any city within reach. */
    public static int notoriety(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos, UUID player) {
        int worst = 0;
        for (City city : CityData.get(server).all()) {
            if (city.dimension.equals(dimension) && Math.abs(city.center.getX() - pos.getX()) <= REACH
                    && Math.abs(city.center.getZ() - pos.getZ()) <= REACH) {
                worst = Math.max(worst, city.exposureOf(player));
            }
        }
        return worst;
    }

    // ---------------------------------------------------------------- the garrison

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 100 != 0 || server instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;
        }
        tick(server);
    }

    /** One pass: every standing lodge with someone near it has its garrison made up. */
    public static int tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        int spawned = 0;
        for (LodgeData.Lodge lodge : LodgeData.get(server).all()) {
            ServerLevel level = server.getLevel(lodge.dimension);
            if (level == null || !lodge.standing(now) || !level.isLoaded(lodge.pos)
                    || level.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(lodge.pos)) < WAKE_RANGE * WAKE_RANGE).isEmpty()) {
                continue;
            }
            spawned += garrison(level, lodge);
        }
        return spawned;
    }

    /** Makes up a lodge's master and garrison. Returns how many were raised. */
    public static int garrison(ServerLevel level, LodgeData.Lodge lodge) {
        AABB area = new AABB(lodge.pos).inflate(48, 24, 48);
        List<CivilianNpc> present = level.getEntitiesOfClass(CivilianNpc.class, area, c -> lodge.id.equals(c.lodgeId()) && c.isAlive());
        int spawned = 0;
        boolean masterHere = lodge.master != null && present.stream().anyMatch(c -> c.getUUID().equals(lodge.master));
        if (!masterHere) {
            Vec3 spot = Placement.spot(level, Vec3.atCenterOf(lodge.pos), 2, 7, level.random);
            if (spot != null) {
                CivilianNpc master = spawn(level, lodge, spot, true);
                lodge.master = master.getUUID();
                spawned++;
            }
        }
        long soldiers = present.stream().filter(c -> !c.getUUID().equals(lodge.master)).count();
        for (long i = soldiers; i < GARRISON; i++) {
            Vec3 spot = Placement.spot(level, Vec3.atCenterOf(lodge.pos), 2, 9, level.random);
            if (spot != null) {
                spawn(level, lodge, spot, false);
                spawned++;
            }
        }
        if (spawned > 0) {
            LodgeData.get(level.getServer()).setDirty();
        }
        return spawned;
    }

    private static CivilianNpc spawn(ServerLevel level, LodgeData.Lodge lodge, Vec3 at, boolean master) {
        CivilianNpc hunter = NpcSpawner.civilian(level, at);
        hunter.settleAtLodge(lodge.pos, lodge.id);
        hunter.setFaction(Hunters.FACTION);
        hunter.armAsHunter();
        if (master) {
            hunter.makeInquisitor();
            hunter.setCustomName(Component.literal(NpcNames.first(level.random) + " " + NpcNames.last(level.random)));
            hunter.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(60);
            hunter.setHealth(60F);
        }
        return hunter;
    }

    // ---------------------------------------------------------------- consequences

    /** A lodge hunter died to a player; if it was the master the lodge is broken. */
    public static void onHunterKilled(CivilianNpc hunter, ServerPlayer killer) {
        MinecraftServer server = killer.getServer();
        LodgeData.Lodge lodge = server == null || hunter.lodgeId() == null ? null : LodgeData.get(server).get(hunter.lodgeId());
        if (lodge == null || !hunter.getUUID().equals(lodge.master)) {
            return;
        }
        lodge.master = null;
        lodge.brokenUntil = server.overworld().getGameTime() + BROKEN_FOR;
        LodgeData.get(server).setDirty();
        for (City city : CityData.get(server).all()) {
            if (city.dimension.equals(lodge.dimension) && Math.abs(city.center.getX() - lodge.pos.getX()) <= REACH
                    && Math.abs(city.center.getZ() - lodge.pos.getZ()) <= REACH) {
                city.addExposure(killer.getUUID(), -40);
            }
        }
        CityData.get(server).setDirty();
        VampireData data = VampireData.of(killer);
        if (data.isVampire()) {
            VampireManager.set(killer, data.withExperience(data.experience() + 6));
        }
        killer.displayClientMessage(Component.translatable("message.vtm.lodge.master_fell"), true);
    }

    private Lodges() {}
}
