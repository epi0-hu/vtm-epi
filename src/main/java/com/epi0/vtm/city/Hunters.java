package com.epi0.vtm.city;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.nemesis.NemesisData;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.nemesis.NemesisRecord;
import com.epi0.vtm.nemesis.NemesisSimulator;
import com.epi0.vtm.nemesis.Placement;
import com.epi0.vtm.nemesis.Status;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Job;
import com.epi0.vtm.npc.NpcNames;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Mortals who know what is out there. Every vampire who is seen doing what a vampire does, in a city, leaves a trail
 * of exposure; the more of it there is, the more the hunters take notice, from a stranger who watches, to armed
 * hunters who come for you, to a strike team with an inquisitor at its head. They also raid the havens of the city's
 * own vampires while nobody is looking.
 */
public final class Hunters {
    public static final ResourceLocation FACTION = VtmMod.id("hunters");
    /** Exposure at which strangers begin to watch you; at which armed hunters come; at which they send a strike team. */
    public static final int WATCHED = 30;
    public static final int HUNTED = 50;
    public static final int ASSAULT = 75;
    /** Hunters wait this long between spawns in a city. */
    private static final long SPAWN_GAP = 300L;
    private static final long HUNTER_LIFE = 12000L;
    private static final double RAID_CHANCE = 0.12;

    // ---------------------------------------------------------------- exposure

    /** Mortals have seen something. Only vampires have anything to be exposed. */
    public static void expose(ServerPlayer player, City city, int amount) {
        if (!VampireData.of(player).isVampire()) {
            return;
        }
        amount = com.epi0.vtm.item.Garb.exposure(player, amount);
        int before = city.exposureOf(player.getUUID());
        city.addExposure(player.getUUID(), amount);
        int after = city.exposureOf(player.getUUID());
        CityData.get(player.getServer()).setDirty();
        if (before < ASSAULT && after >= ASSAULT) {
            player.displayClientMessage(Component.translatable("message.vtm.hunters.assault", city.name), true);
        } else if (before < HUNTED && after >= HUNTED) {
            player.displayClientMessage(Component.translatable("message.vtm.hunters.hunted", city.name), true);
        } else if (before < WATCHED && after >= WATCHED) {
            player.displayClientMessage(Component.translatable("message.vtm.hunters.watching", city.name), true);
        }
    }

    /** What is half forgotten fades: one point every fifteen seconds. */
    static void cool(ServerLevel level, City city) {
        if ((level.getGameTime() / CityEvents.LIFE_INTERVAL) % 3 == 0) {
            city.exposure.replaceAll((player, value) -> value - 1);
            city.exposure.values().removeIf(v -> v <= 0);
        }
    }

    /** How many hunters a given exposure brings (the inquisitor is extra). */
    public static int wanted(int exposure) {
        return wanted(exposure, false);
    }

    /** As above; a city within reach of a standing lodge is sent two more. */
    public static int wanted(int exposure, boolean backed) {
        if (exposure < WATCHED) {
            return 0;
        }
        int base = exposure < HUNTED ? 1 : exposure < 65 ? 2 : exposure < ASSAULT ? 3 : exposure < 90 ? 4 : 6;
        return base + (backed ? 2 : 0);
    }

    // ---------------------------------------------------------------- the pass

    /** Sends hunters after the exposed vampires who are in this city. Returns how many were spawned. */
    public static int tick(ServerLevel level, City city) {
        long now = level.getGameTime();
        int spawned = 0;
        boolean backed = Lodges.supports(level.getServer(), city);
        int assault = backed ? ASSAULT - 15 : ASSAULT;
        long gap = backed ? SPAWN_GAP / 2 : SPAWN_GAP;
        for (ServerPlayer player : level.players()) {
            int exposure = city.exposureOf(player.getUUID());
            if (player.isSpectator() || exposure < WATCHED || !city.contains(level, player.blockPosition())) {
                continue;
            }
            AABB area = player.getBoundingBox().inflate(70, 40, 70);
            List<CivilianNpc> present = level.getEntitiesOfClass(CivilianNpc.class, area,
                    c -> c.job() == Job.HUNTER && c.isAlive() && city.id.equals(c.cityId()));
            if (present.size() < wanted(exposure, backed) && now - city.lastHunt >= gap) {
                Vec3 spot = Placement.spot(level, player.position(), 16, 28, level.random);
                if (spot != null) {
                    spawn(level, city, spot, false);
                    city.lastHunt = now;
                    spawned++;
                }
            }
            boolean inquisitorAlive = city.inquisitor != null && level.getEntity(city.inquisitor) != null;
            if (exposure >= assault && !inquisitorAlive && present.size() >= 2) {
                Vec3 spot = Placement.spot(level, player.position(), 18, 30, level.random);
                if (spot != null) {
                    city.inquisitor = spawn(level, city, spot, true).getUUID();
                    city.lastHunt = now;
                    spawned++;
                }
            }
        }
        return spawned;
    }

    /** A hunter, or the inquisitor who leads them. */
    public static CivilianNpc spawn(ServerLevel level, City city, Vec3 at, boolean inquisitor) {
        CivilianNpc hunter = NpcSpawner.civilian(level, at);
        hunter.settle(city, BlockPos.containing(at), Job.HUNTER, null);
        hunter.setFaction(FACTION);
        hunter.setExpiry(level.getGameTime() + (inquisitor ? HUNTER_LIFE * 2 : HUNTER_LIFE));
        if (inquisitor) {
            hunter.makeInquisitor();
            hunter.setCustomName(Component.literal(NpcNames.first(level.random) + " " + NpcNames.last(level.random)));
        }
        return hunter;
    }

    // ---------------------------------------------------------------- consequences

    /** A hunter fell to a player: the others take it badly, unless it was the inquisitor, whose fall breaks the cell. */
    public static void onHunterKilled(CivilianNpc hunter, ServerPlayer killer) {
        City city = hunter.city();
        if (city == null) {
            return;
        }
        if (hunter.getUUID().equals(city.inquisitor)) {
            city.inquisitor = null;
            city.exposure.replaceAll((player, value) -> value - 40);
            city.exposure.values().removeIf(v -> v <= 0);
            killer.displayClientMessage(Component.translatable("message.vtm.hunters.broken", city.name), true);
            VampireData data = VampireData.of(killer);
            if (data.isVampire()) {
                com.epi0.vtm.vampire.VampireManager.set(killer, data.withExperience(data.experience() + 3));
            }
        } else {
            expose(killer, city, 6);
        }
        CityData.get(killer.getServer()).setDirty();
    }

    // ---------------------------------------------------------------- raids, while nobody watches

    /** In a city the hunters are aroused against, they sometimes fall on a vampire's haven. Run with the simulation. */
    public static void raids(MinecraftServer server, NemesisData data, RandomSource random) {
        for (City city : CityData.get(server).all()) {
            int alert = city.alert();
            if (alert < HUNTED + 10 || random.nextDouble() > RAID_CHANCE) {
                continue;
            }
            List<NemesisRecord> targets = data.inFaction(city.owner).stream()
                    .filter(r -> r.status == Status.ACTIVE && !NemesisManager.manifested(server, r)
                            && (r.id.equals(city.warden) || r.lair != null && r.lairDimension != null
                            && r.lairDimension.equals(city.dimension) && inside(city, r.lair)))
                    .toList();
            if (targets.isEmpty()) {
                continue;
            }
            NemesisRecord victim = targets.get(random.nextInt(targets.size()));
            double hunters = 6 + alert / 8.0;
            boolean hunterWins = random.nextDouble() < hunters / (hunters + victim.power());
            if (hunterWins) {
                NemesisManager.log(server, "hunters_raid_won", city.owner, true, victim, city.name);
                NemesisSimulator.fall(server, victim, null, random, 0.2);
            } else {
                NemesisManager.log(server, "hunters_raid_lost", city.owner, true, victim, city.name);
            }
            // A raid spends the hunters' appetite for one: the exposure that drove it is spent.
            city.exposure.replaceAll((player, value) -> value - 20);
            city.exposure.values().removeIf(v -> v <= 0);
        }
    }

    private static boolean inside(City city, BlockPos pos) {
        return Math.abs(pos.getX() - city.center.getX()) <= city.radius && Math.abs(pos.getZ() - city.center.getZ()) <= city.radius;
    }

    private Hunters() {}
}
