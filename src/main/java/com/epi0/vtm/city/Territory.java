package com.epi0.vtm.city;

import com.epi0.vtm.nemesis.Intel;
import com.epi0.vtm.nemesis.NemesisData;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.nemesis.NemesisRecord;
import com.epi0.vtm.nemesis.NemesisSimulator;
import com.epi0.vtm.nemesis.Role;
import com.epi0.vtm.nemesis.Status;
import com.epi0.vtm.registry.Faction;
import com.epi0.vtm.registry.ModAttachments;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.Util;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Who holds which city. Each city has a ruling faction and a warden, a named vampire who keeps the haven; strength
 * is how firmly the faction holds it. Factions at war take cities from one another while nobody is looking, and what
 * a player does to a warden shakes the city. A faction's stance on the Masquerade decides what the city tolerates.
 */
public final class Territory {
    public static final int HELD_STRENGTH = 60;
    public static final int CAPTURED_STRENGTH = 35;
    public static final double WAR_CHANCE = 0.06;

    /** A faction's name, in the form chronicle lines translate. */
    public static String factionKey(ResourceLocation faction) {
        return Util.makeDescriptionId("faction", faction);
    }

    public static List<City> ownedBy(CityData cities, ResourceLocation faction) {
        return cities.all().stream().filter(c -> c.owner.equals(faction)).toList();
    }

    // ---------------------------------------------------------------- simulation

    /** One step of politics: wardens are seated, strength drifts, and factions at war take cities. */
    public static void step(MinecraftServer server, NemesisData nemeses, RandomSource random) {
        CityData cities = CityData.get(server);
        for (City city : cities.all()) {
            seatWarden(server, nemeses, city);
            drift(nemeses, city);
        }
        war(server, nemeses, cities, random, 1.0);
        cities.setDirty();
    }

    /** Makes sure the city has a warden of its ruling faction, if one can be found. */
    public static void seatWarden(MinecraftServer server, NemesisData nemeses, City city) {
        NemesisRecord current = city.warden == null ? null : nemeses.get(city.warden);
        if (current != null && current.alive() && current.faction.equals(city.owner) && current.status != Status.BOUND) {
            return;
        }
        city.warden = null;
        NemesisRecord next = nemeses.inFaction(city.owner).stream()
                .filter(r -> r.status != Status.BOUND && r.role != Role.LEADER && !isWarden(server, r))
                .max(Comparator.comparingInt((NemesisRecord r) -> haven(city, r) ? 1000 : 0).thenComparingInt(NemesisRecord::power))
                .orElse(null);
        if (next == null) {
            return;
        }
        city.warden = next.id;
        if (city.haven != null) {
            next.lair = city.haven;
            next.lairDimension = city.dimension;
        }
        nemeses.setDirty();
    }

    private static boolean haven(City city, NemesisRecord r) {
        return city.haven != null && city.haven.equals(r.lair);
    }

    private static boolean isWarden(MinecraftServer server, NemesisRecord r) {
        return CityData.get(server).all().stream().anyMatch(c -> r.id.equals(c.warden));
    }

    /** A held city firms up; one without a warden slips. */
    public static void drift(NemesisData nemeses, City city) {
        boolean warded = city.warden != null && nemeses.get(city.warden) != null && nemeses.get(city.warden).alive();
        if (warded && city.strength < HELD_STRENGTH) {
            city.strength++;
        } else if (!warded && city.strength > 10) {
            city.strength--;
        }
    }

    /** Factions at war try to take one another's cities. {@code intensity} scales how often. */
    public static void war(MinecraftServer server, NemesisData nemeses, CityData cities, RandomSource random, double intensity) {
        for (ResourceLocation attacker : NemesisSimulator.joinable(server)) {
            Optional<Faction> faction = VtmRegistries.faction(server.registryAccess(), attacker);
            if (faction.isEmpty() || random.nextDouble() > WAR_CHANCE * intensity) {
                continue;
            }
            List<City> targets = cities.all().stream().filter(c -> faction.get().enemies().contains(c.owner)).toList();
            if (targets.isEmpty()) {
                continue;
            }
            City target = targets.stream().min(Comparator.comparingInt((City c) -> c.strength)).get();
            if (target.owner.equals(attacker)) {
                continue;
            }
            List<NemesisRecord> band = nemeses.inFaction(attacker).stream()
                    .filter(r -> r.status == Status.ACTIVE && !NemesisManager.manifested(server, r))
                    .sorted(Comparator.comparingInt(NemesisRecord::power).reversed()).limit(3).toList();
            if (band.isEmpty()) {
                continue;
            }
            resolveAssault(server, nemeses, target, attacker, band, random);
        }
    }

    /** One assault on one city. Returns whether the attackers won. */
    public static boolean resolveAssault(MinecraftServer server, NemesisData nemeses, City city, ResourceLocation attacker,
                                         List<NemesisRecord> band, RandomSource random) {
        double offence = band.stream().mapToInt(NemesisRecord::power).sum();
        NemesisRecord warden = city.warden == null ? null : nemeses.get(city.warden);
        double defence = (warden != null && warden.alive() ? warden.power() * 1.5 : 0) + city.strength / 4.0
                + nemeses.inFaction(city.owner).stream().filter(r -> r.status == Status.ACTIVE && r.role != Role.AGENT)
                .mapToInt(NemesisRecord::power).limit(2).sum() * 0.5;
        boolean won = random.nextDouble() < offence / (offence + defence);
        ResourceLocation defender = city.owner;
        if (won) {
            if (warden != null && warden.alive()) {
                NemesisSimulator.fall(server, warden, band.get(0), random, 0.4);
            }
            city.owner = attacker;
            city.warden = null;
            city.strength = CAPTURED_STRENGTH;
            band.forEach(r -> NemesisManager.grantXp(r, 4));
            NemesisManager.log(server, "city_taken", attacker, true, band.subList(0, 1), city.name, factionKey(attacker), factionKey(defender));
        } else {
            NemesisRecord loser = band.get(0);
            city.strength = Math.max(10, city.strength - 5);
            NemesisSimulator.fall(server, loser, warden, random, 0.15);
            NemesisManager.log(server, "city_held", defender, false, band.subList(0, 1), city.name, factionKey(defender));
        }
        CityData.get(server).setDirty();
        return won;
    }

    /** Called when a named vampire is destroyed or leaves their seat: a city loses its warden and its footing. */
    public static void wardenLost(MinecraftServer server, NemesisRecord record, boolean byPlayer) {
        for (City city : CityData.get(server).all()) {
            if (record.id.equals(city.warden)) {
                city.warden = null;
                city.strength = Math.max(5, city.strength - (byPlayer ? 25 : 10));
                NemesisManager.log(server, "city_weakened", city.owner, byPlayer, record, city.name);
                CityData.get(server).setDirty();
            }
        }
    }

    // ---------------------------------------------------------------- rules

    /** Mortals saw a vampire feed in a city whose ruler keeps the Masquerade: the ruler will not forget it. */
    public static void masqueradeBreach(ServerPlayer player, City city) {
        boolean strict = VtmRegistries.faction(player.level().registryAccess(), city.owner).map(Faction::masquerade).orElse(false);
        if (strict && VampireData.of(player).isVampire()) {
            SectManager.adjustStanding(player, city.owner, SectManager.MASQUERADE_BREACH);
        }
    }

    /** Whether the ruler of {@code city} treats the player's trouble as a breach of the Masquerade. */
    public static boolean enforcesMasquerade(net.minecraft.core.RegistryAccess access, City city) {
        return VtmRegistries.faction(access, city.owner).map(Faction::masquerade).orElse(false);
    }

    // ---------------------------------------------------------------- knowledge

    public static void discover(ServerPlayer player, City city) {
        CityKnowledge known = CityKnowledge.of(player);
        if (!known.knows(city.id)) {
            player.setData(ModAttachments.CITY_KNOWLEDGE, known.with(city.id));
        }
    }

    /** Tells the player of one city they do not know yet. */
    public static Optional<Component> revealCity(ServerPlayer player, RandomSource random) {
        CityKnowledge known = CityKnowledge.of(player);
        List<City> unknown = new ArrayList<>(CityData.get(player.getServer()).all().stream().filter(c -> !known.knows(c.id)).toList());
        if (unknown.isEmpty()) {
            return Optional.empty();
        }
        City city = unknown.get(random.nextInt(unknown.size()));
        discover(player, city);
        return Optional.of(Component.translatable("message.vtm.city.learned", city.name, VtmRegistries.name("faction", city.owner)));
    }

    public static GlobalPos where(City city) {
        return GlobalPos.of(city.dimension, city.center);
    }

    private Territory() {}
}
