package com.epi0.vtm.court;

import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.city.Territory;
import com.epi0.vtm.nemesis.Feud;
import com.epi0.vtm.nemesis.NemesisData;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.nemesis.NemesisRecord;
import com.epi0.vtm.nemesis.Role;
import com.epi0.vtm.nemesis.Status;
import com.epi0.vtm.nemesis.Trait;
import com.epi0.vtm.registry.Faction;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireManager;
import net.minecraft.core.RegistryAccess;
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
 * Rule by a player. A member of a sect who is senior enough, and trusted enough, may take a throne that stands empty;
 * then they appoint the court, issue decrees, collect tribute from the cities their faction holds, answer petitions,
 * and keep their lieutenants loyal, or face a rebellion.
 */
public final class Court {
    public static final int CLAIM_RANK = 1;
    public static final int CLAIM_STANDING = 40;
    public static final int APPOINT_COST = 5;
    public static final long VACANCY_TICKS = 48000L;
    public static final long REVOLT_TICKS = 48000L;

    public static boolean isSeat(Role role) {
        return role == Role.LIEUTENANT || role == Role.ENFORCER;
    }

    // ---------------------------------------------------------------- taking the throne

    /** Why this player cannot claim a throne now (a lang suffix), or empty if they can. */
    public static Optional<String> blocker(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        Membership m = Membership.of(player);
        CourtData courts = CourtData.get(server);
        if (!VampireData.of(player).isVampire()) {
            return Optional.of("not_vampire");
        }
        if (m.faction().isEmpty() || !VtmRegistries.faction(player.level().registryAccess(), m.faction().get()).map(Faction::joinable).orElse(false)) {
            return Optional.of("no_sect");
        }
        ResourceLocation faction = m.faction().get();
        if (courts.reignOf(player.getUUID()) != null) {
            return Optional.of("already_ruling");
        }
        if (courts.reign(faction) != null) {
            return Optional.of("ruled");
        }
        if (m.rank() < CLAIM_RANK) {
            return Optional.of("low_rank");
        }
        if (m.standingWith(faction) < CLAIM_STANDING) {
            return Optional.of("low_standing");
        }
        if (!NemesisManager.data(server).inRole(faction, Role.LEADER).isEmpty()) {
            return Optional.of("throne_held");
        }
        return Optional.empty();
    }

    public static boolean claim(ServerPlayer player) {
        if (blocker(player).isPresent()) {
            return false;
        }
        MinecraftServer server = player.getServer();
        ResourceLocation faction = Membership.of(player).faction().orElseThrow();
        long now = server.overworld().getGameTime();
        Reign reign = new Reign(faction, player.getUUID(), player.getGameProfile().getName(), now);
        CourtData.get(server).add(reign);
        NemesisManager.data(server).releaseThrone(faction);
        SectManager.setRank(player, Integer.MAX_VALUE);
        for (NemesisRecord r : seatHolders(server, faction)) {
            r.courtLoyalty = 50 + (r.has(Trait.GRATEFUL) ? 10 : 0);
        }
        NemesisManager.log(server, "throne_claimed", faction, true, List.of(), player.getGameProfile().getName(), Territory.factionKey(faction));
        player.displayClientMessage(Component.translatable("message.vtm.court.claimed", VtmRegistries.name("faction", faction)), false);
        return true;
    }

    public static void abdicate(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        CourtData courts = CourtData.get(server);
        Reign reign = courts.reignOf(player.getUUID());
        if (reign == null) {
            return;
        }
        courts.remove(reign.faction);
        SectManager.adjustStanding(player, reign.faction, -10);
        SectManager.setRank(player, 1);
        NemesisManager.log(server, "abdicated", reign.faction, true, List.of(), player.getGameProfile().getName(), Territory.factionKey(reign.faction));
        player.displayClientMessage(Component.translatable("message.vtm.court.abdicated"), false);
    }

    // ---------------------------------------------------------------- the court

    public static List<NemesisRecord> seatHolders(MinecraftServer server, ResourceLocation faction) {
        return NemesisManager.data(server).inFaction(faction).stream()
                .filter(r -> isSeat(r.role) && r.status != Status.BOUND).toList();
    }

    public static boolean appoint(ServerPlayer player, UUID recordId, Role role) {
        MinecraftServer server = player.getServer();
        Reign reign = CourtData.get(server).reignOf(player.getUUID());
        NemesisRecord r = NemesisManager.data(server).get(recordId);
        if (reign == null || r == null || !isSeat(role) || !r.alive() || r.status != Status.ACTIVE || !r.faction.equals(reign.faction)
                || r.role == Role.LEADER || r.revoltSince > 0 || reign.influence < APPOINT_COST) {
            return false;
        }
        long filled = NemesisManager.data(server).inRole(reign.faction, role).stream().filter(o -> o != r).count();
        if (filled >= role.seats()) {
            return false;
        }
        reign.gain(-APPOINT_COST);
        r.role = role;
        r.courtLoyalty = 55 + (r.has(Trait.GRATEFUL) ? 15 : 0);
        NemesisManager.log(server, "appointed", reign.faction, false, r, player.getGameProfile().getName());
        syncBody(server, r);
        CourtData.get(server).setDirty();
        return true;
    }

    public static boolean dismiss(ServerPlayer player, UUID recordId) {
        MinecraftServer server = player.getServer();
        Reign reign = CourtData.get(server).reignOf(player.getUUID());
        NemesisRecord r = NemesisManager.data(server).get(recordId);
        if (reign == null || r == null || !r.faction.equals(reign.faction) || !isSeat(r.role)) {
            return false;
        }
        r.role = Role.AGENT;
        r.revoltSince = 0;
        NemesisManager.log(server, "dismissed", reign.faction, false, r, player.getGameProfile().getName());
        syncBody(server, r);
        CourtData.get(server).setDirty();
        return true;
    }

    private static void syncBody(MinecraftServer server, NemesisRecord r) {
        var body = NemesisManager.entityOf(server, r);
        if (body != null) {
            body.setRank(r.role.rank());
            NemesisManager.refresh(body, r);
        }
    }

    // ---------------------------------------------------------------- decrees and tribute

    /** Puts a decree into force, or revokes it. Enacting costs influence; revoking is free. */
    public static boolean toggleDecree(ServerPlayer player, Decree decree) {
        MinecraftServer server = player.getServer();
        Reign reign = CourtData.get(server).reignOf(player.getUUID());
        if (reign == null) {
            return false;
        }
        if (reign.decrees.remove(decree)) {
            CourtData.get(server).setDirty();
            return true;
        }
        if (reign.decrees.size() >= Reign.MAX_DECREES || reign.influence < Decree.COST
                || reign.decrees.stream().anyMatch(decree::conflictsWith)) {
            return false;
        }
        reign.gain(-Decree.COST);
        reign.decrees.add(decree);
        CourtData.get(server).setDirty();
        return true;
    }

    public static int collect(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        Reign reign = CourtData.get(server).reignOf(player.getUUID());
        VampireData data = VampireData.of(player);
        if (reign == null || reign.tribute <= 0 || !data.isVampire()) {
            return 0;
        }
        int xp = reign.tribute;
        reign.tribute = 0;
        VampireManager.set(player, data.withExperience(data.experience() + xp));
        CourtData.get(server).setDirty();
        player.displayClientMessage(Component.translatable("message.vtm.court.collected", xp), false);
        return xp;
    }

    // ---------------------------------------------------------------- how decrees change the world

    public static double warMultiplier(MinecraftServer server, ResourceLocation faction) {
        Reign reign = CourtData.get(server).reign(faction);
        if (reign == null) {
            return 1.0;
        }
        return reign.has(Decree.PEACE) ? 0.0 : reign.has(Decree.WAR_FOOTING) ? 3.0 : 1.0;
    }

    public static double raidMultiplier(MinecraftServer server, ResourceLocation faction) {
        Reign reign = CourtData.get(server).reign(faction);
        if (reign == null) {
            return 1.0;
        }
        return reign.has(Decree.PEACE) ? 0.0 : reign.has(Decree.WAR_FOOTING) ? 2.0 : 1.0;
    }

    /** Whether a truce keeps these two factions from fighting. */
    public static boolean truce(MinecraftServer server, ResourceLocation a, ResourceLocation b) {
        long now = server.overworld().getGameTime();
        CourtData courts = CourtData.get(server);
        Reign ra = courts.reign(a);
        Reign rb = courts.reign(b);
        return ra != null && ra.truceWith(b, now) || rb != null && rb.truceWith(a, now);
    }

    /** Whether the faction keeps the Masquerade: by nature, or by its ruler's decree. */
    public static boolean masquerade(MinecraftServer server, RegistryAccess access, ResourceLocation faction) {
        Reign reign = server == null ? null : CourtData.get(server).reign(faction);
        if (reign != null) {
            if (reign.has(Decree.STRICT_MASQUERADE)) {
                return true;
            }
            if (reign.has(Decree.OPEN_HUNTING)) {
                return false;
            }
        }
        return VtmRegistries.faction(access, faction).map(Faction::masquerade).orElse(false);
    }

    // ---------------------------------------------------------------- the passing of a minute

    /** One step of every court whose ruler is here. */
    public static void step(MinecraftServer server, RandomSource random) {
        CourtData courts = CourtData.get(server);
        NemesisData data = NemesisManager.data(server);
        long now = server.overworld().getGameTime();
        for (Reign reign : courts.all()) {
            ServerPlayer ruler = server.getPlayerList().getPlayer(reign.ruler);
            if (ruler == null) {
                continue; // a court is frozen while its ruler is away
            }
            reign.rulerName = ruler.getGameProfile().getName();
            loyalty(server, data, reign, ruler);
            if (!rebellions(server, data, courts, reign, ruler, now, random)) {
                continue;
            }
            petitions(server, data, reign, ruler, now, random);
            income(server, data, reign);
            courts.setDirty();
        }
    }

    private static void loyalty(MinecraftServer server, NemesisData data, Reign reign, ServerPlayer ruler) {
        boolean slow = data.steps % 4 == 0;
        for (NemesisRecord r : seatHolders(server, reign.faction)) {
            int change = 0;
            if (data.steps % 6 == 0 && r.ambition > 70) {
                change--;
            }
            if (slow && reign.has(Decree.TITHES)) {
                change--;
            }
            if (slow && reign.has(Decree.WAR_FOOTING) && r.role == Role.ENFORCER) {
                change++;
            }
            if (slow && reign.has(Decree.PEACE)) {
                r.ambition = Math.min(100, r.ambition + 1);
            }
            if (reign.has(Decree.PATRONAGE)) {
                if (reign.influence >= 1) {
                    reign.gain(-1);
                    change++;
                } else {
                    reign.decrees.remove(Decree.PATRONAGE);
                    ruler.displayClientMessage(Component.translatable("message.vtm.court.patronage_lapsed"), false);
                }
            }
            r.courtLoyalty = Math.max(0, Math.min(100, r.courtLoyalty + change));
        }
    }

    /** Returns false if the reign ended this step. */
    private static boolean rebellions(MinecraftServer server, NemesisData data, CourtData courts, Reign reign, ServerPlayer ruler,
                                      long now, RandomSource random) {
        for (NemesisRecord r : data.inFaction(reign.faction)) {
            if (r.revoltSince > 0) {
                Feud feud = r.feud(ruler.getUUID());
                boolean gone = !r.alive() || r.status == Status.BOUND || !isSeat(r.role);
                if (gone || feud.defeats + feud.spared > r.revoltBase) {
                    r.revoltSince = 0;
                    r.courtLoyalty = 50;
                    if (!gone) {
                        NemesisManager.log(server, "rebellion_crushed", reign.faction, true, r, ruler.getGameProfile().getName());
                        ruler.displayClientMessage(Component.translatable("message.vtm.court.crushed", r.name), false);
                    }
                } else if (now - r.revoltSince > REVOLT_TICKS) {
                    depose(server, data, courts, reign, ruler, r);
                    return false;
                }
            } else if (isSeat(r.role) && r.status == Status.ACTIVE && r.courtLoyalty < 25 && r.ambition >= 60 && random.nextDouble() < 0.1) {
                Feud feud = r.feud(ruler.getUUID());
                r.revoltSince = now;
                r.revoltBase = feud.defeats + feud.spared;
                feud.enmity = Math.max(feud.enmity, 70);
                feud.bloodHunt = true;
                NemesisManager.log(server, "rebellion", reign.faction, true, r, ruler.getGameProfile().getName());
                ruler.displayClientMessage(Component.translatable("message.vtm.court.rebellion", r.name), false);
            }
        }
        return true;
    }

    private static void depose(MinecraftServer server, NemesisData data, CourtData courts, Reign reign, ServerPlayer ruler, NemesisRecord rebel) {
        courts.remove(reign.faction);
        rebel.revoltSince = 0;
        rebel.role = Role.LEADER;
        rebel.courtLoyalty = 50;
        SectManager.adjustStanding(ruler, reign.faction, -30);
        SectManager.setRank(ruler, 1);
        NemesisManager.log(server, "deposed", reign.faction, true, rebel, ruler.getGameProfile().getName());
        ruler.displayClientMessage(Component.translatable("message.vtm.court.deposed", rebel.name), false);
        syncBody(server, rebel);
    }

    private static void income(MinecraftServer server, NemesisData data, Reign reign) {
        CityData cities = CityData.get(server);
        List<City> owned = Territory.ownedBy(cities, reign.faction);
        int income = 1;
        for (City city : owned) {
            income += city.strength / 25;
        }
        double multiplier = (reign.has(Decree.TITHES) ? 1.5 : 1.0) * (reign.has(Decree.OPEN_HUNTING) ? 1.25 : 1.0);
        reign.gain((int) Math.floor(income * multiplier));
        if (data.steps % 4 == 0) {
            reign.tribute += (int) Math.round(owned.size() * (reign.has(Decree.TITHES) ? 1.5 : 1.0));
            for (City city : owned) {
                if (reign.has(Decree.OPEN_HUNTING)) {
                    city.strength = Math.max(10, city.strength - 1);
                }
            }
        }
        if (reign.has(Decree.STRICT_MASQUERADE)) {
            owned.forEach(c -> c.heat.replaceAll((p, h) -> Math.max(0, h - 2)));
        }
        cities.setDirty();
    }

    // ---------------------------------------------------------------- petitions

    private static void petitions(MinecraftServer server, NemesisData data, Reign reign, ServerPlayer ruler, long now, RandomSource random) {
        for (Petition petition : List.copyOf(reign.petitions)) {
            if (now - petition.created() > Petition.LIFETIME_TICKS) {
                answer(server, reign, ruler, petition, mildChoice(petition.type()), true);
            }
        }
        if (reign.petitions.size() < Reign.MAX_PETITIONS && data.steps % 6 == 0 && random.nextDouble() < 0.6) {
            spawnPetition(server, data, reign, now, random);
        }
    }

    private static int mildChoice(Petition.Type type) {
        return switch (type) {
            case ENVOY, SANCTUARY, FORTIFY -> 1;
            case QUARREL -> 1;
            case TRIBUNAL -> 1;
        };
    }

    /** Raises a petition if there is something to petition about. Returns it, if one was raised. */
    public static Optional<Petition> spawnPetition(MinecraftServer server, NemesisData data, Reign reign, long now, RandomSource random) {
        if (reign.petitions.size() >= Reign.MAX_PETITIONS) {
            return Optional.empty();
        }
        List<Petition.Type> options = new ArrayList<>(List.of(Petition.Type.values()));
        List<NemesisRecord> holders = seatHolders(server, reign.faction).stream().filter(r -> r.revoltSince == 0).toList();
        if (holders.size() < 2) {
            options.remove(Petition.Type.QUARREL);
        }
        if (holders.isEmpty()) {
            options.remove(Petition.Type.TRIBUNAL);
        }
        List<City> owned = Territory.ownedBy(CityData.get(server), reign.faction);
        if (owned.isEmpty()) {
            options.remove(Petition.Type.FORTIFY);
        }
        List<ResourceLocation> enemies = VtmRegistries.faction(server.registryAccess(), reign.faction).map(Faction::enemies).orElse(List.of())
                .stream().filter(f -> !reign.truceWith(f, now)).toList();
        if (enemies.isEmpty()) {
            options.remove(Petition.Type.ENVOY);
            options.remove(Petition.Type.SANCTUARY);
        }
        // Do not raise a second petition of the same kind.
        reign.petitions.forEach(p -> options.remove(p.type()));
        if (options.isEmpty()) {
            return Optional.empty();
        }
        Petition.Type type = options.get(random.nextInt(options.size()));
        UUID id = UUID.randomUUID();
        Petition petition;
        switch (type) {
            case QUARREL -> {
                List<NemesisRecord> pair = new ArrayList<>(holders);
                java.util.Collections.shuffle(pair, new java.util.Random(random.nextLong()));
                petition = new Petition(id, type, now, List.of(pair.get(0).id, pair.get(1).id), List.of(pair.get(0).name, pair.get(1).name));
            }
            case TRIBUNAL -> {
                NemesisRecord accused = holders.get(random.nextInt(holders.size()));
                petition = new Petition(id, type, now, List.of(accused.id), List.of(accused.name));
            }
            case FORTIFY -> {
                City weakest = owned.stream().min(Comparator.comparingInt((City c) -> c.strength)).get();
                petition = new Petition(id, type, now, List.of(weakest.id), List.of(weakest.name));
            }
            case ENVOY -> {
                ResourceLocation from = enemies.get(random.nextInt(enemies.size()));
                petition = new Petition(id, type, now, List.of(), List.of(Territory.factionKey(from), from.toString()));
            }
            default -> {
                ResourceLocation from = enemies.get(random.nextInt(enemies.size()));
                petition = new Petition(id, type, now, List.of(), List.of(Territory.factionKey(from), from.toString(),
                        com.epi0.vtm.npc.NpcNames.random(random)));
            }
        }
        reign.petitions.add(petition);
        CourtData.get(server).setDirty();
        ServerPlayer ruler = server.getPlayerList().getPlayer(reign.ruler);
        if (ruler != null) {
            ruler.displayClientMessage(Component.translatable("message.vtm.court.petition"), true);
        }
        return Optional.of(petition);
    }

    /** The ruler answers a petition. Returns false if they cannot (no such petition, or not enough influence). */
    public static boolean resolve(ServerPlayer player, UUID petitionId, int choice) {
        MinecraftServer server = player.getServer();
        Reign reign = CourtData.get(server).reignOf(player.getUUID());
        if (reign == null || (choice != 0 && choice != 1)) {
            return false;
        }
        Petition petition = reign.petitions.stream().filter(p -> p.id().equals(petitionId)).findFirst().orElse(null);
        if (petition == null || reign.influence < petition.type().costOf(choice)) {
            return false;
        }
        answer(server, reign, player, petition, choice, false);
        return true;
    }

    private static void answer(MinecraftServer server, Reign reign, ServerPlayer ruler, Petition petition, int choice, boolean lapsed) {
        NemesisData data = NemesisManager.data(server);
        long now = server.overworld().getGameTime();
        if (!lapsed) {
            reign.gain(-petition.type().costOf(choice));
        }
        reign.petitions.remove(petition);
        List<NemesisRecord> subjects = petition.subjects().stream().map(data::get).filter(java.util.Objects::nonNull).toList();
        switch (petition.type()) {
            case QUARREL -> {
                if (subjects.size() == 2) {
                    if (choice == 0) {
                        subjects.get(0).courtLoyalty = Math.min(100, subjects.get(0).courtLoyalty + 10);
                        subjects.get(1).courtLoyalty = Math.max(0, subjects.get(1).courtLoyalty - 10);
                        subjects.get(1).ambition = Math.min(100, subjects.get(1).ambition + 5);
                    } else if (!lapsed) {
                        subjects.forEach(r -> r.courtLoyalty = Math.min(100, r.courtLoyalty + 3));
                    } else {
                        subjects.forEach(r -> r.courtLoyalty = Math.max(0, r.courtLoyalty - 3));
                    }
                }
            }
            case ENVOY -> {
                if (choice == 0 && petition.args().size() > 1) {
                    reign.truces.put(petition.args().get(1), now + 48000L);
                    reign.gain(5);
                    NemesisManager.log(server, "truce", reign.faction, true, List.of(), ruler.getGameProfile().getName(), petition.args().get(0));
                } else {
                    seatHolders(server, reign.faction).forEach(r -> r.courtLoyalty = Math.min(100, r.courtLoyalty + 2));
                }
            }
            case SANCTUARY -> {
                if (choice == 0) {
                    NemesisRecord refugee = NemesisManager.create(server, reign.faction, Role.AGENT, server.overworld().random);
                    refugee.level = 2 + server.overworld().random.nextInt(4);
                    refugee.courtLoyalty = 70;
                    NemesisManager.log(server, "sanctuary", reign.faction, false, refugee, petition.args().get(0));
                }
            }
            case FORTIFY -> {
                City city = petition.subjects().isEmpty() ? null : CityData.get(server).get(petition.subjects().get(0));
                if (city != null) {
                    city.strength = Math.max(10, Math.min(City.MAX_STRENGTH, city.strength + (choice == 0 ? 15 : -8)));
                    CityData.get(server).setDirty();
                }
            }
            case TRIBUNAL -> {
                if (!subjects.isEmpty()) {
                    NemesisRecord accused = subjects.get(0);
                    if (choice == 0) {
                        accused.role = Role.AGENT;
                        accused.ambition = Math.max(0, accused.ambition - 20);
                        seatHolders(server, reign.faction).forEach(r -> r.courtLoyalty = Math.min(100, r.courtLoyalty + 5));
                        NemesisManager.log(server, "dismissed", reign.faction, false, accused, ruler.getGameProfile().getName());
                        syncBody(server, accused);
                    } else {
                        accused.courtLoyalty = Math.min(100, accused.courtLoyalty + 10);
                        seatHolders(server, reign.faction).stream().filter(r -> r != accused)
                                .forEach(r -> r.courtLoyalty = Math.max(0, r.courtLoyalty - 3));
                    }
                }
            }
        }
        CourtData.get(server).setDirty();
    }

    // ---------------------------------------------------------------- the view

    public static CourtView view(MinecraftServer server, ServerPlayer player) {
        CourtData courts = CourtData.get(server);
        NemesisData data = NemesisManager.data(server);
        Membership m = Membership.of(player);
        Reign reign = courts.reignOf(player.getUUID());
        Optional<ResourceLocation> faction = reign != null ? Optional.of(reign.faction) : m.faction();
        long now = server.overworld().getGameTime();
        if (reign == null) {
            return new CourtView(faction, false, blocker(player), 0, 0, List.of(), Reign.MAX_DECREES, List.of(), List.of(), List.of(), List.of());
        }
        List<CourtView.SeatView> seats = new ArrayList<>();
        for (Role role : new Role[]{Role.LIEUTENANT, Role.ENFORCER}) {
            List<NemesisRecord> holders = data.inRole(reign.faction, role);
            for (int i = 0; i < role.seats(); i++) {
                if (i < holders.size()) {
                    NemesisRecord r = holders.get(i);
                    seats.add(new CourtView.SeatView(role.id(), Optional.of(r.id), r.name, r.level, r.courtLoyalty, r.revoltSince > 0));
                } else {
                    seats.add(new CourtView.SeatView(role.id(), Optional.empty(), "", 0, 0, false));
                }
            }
        }
        List<CourtView.Candidate> candidates = data.inFaction(reign.faction).stream()
                .filter(r -> r.status == Status.ACTIVE && r.role == Role.AGENT)
                .sorted(Comparator.comparingInt(NemesisRecord::power).reversed()).limit(8)
                .map(r -> new CourtView.Candidate(r.id, r.name, r.level, r.ambition)).toList();
        List<CourtView.PetitionView> petitions = reign.petitions.stream().map(p -> new CourtView.PetitionView(p.id(), p.type().id(),
                p.args(), Math.max(0, Petition.LIFETIME_TICKS - (now - p.created())), p.type().costOf(0), p.type().costOf(1))).toList();
        return new CourtView(faction, true, Optional.empty(), reign.influence, reign.tribute,
                reign.decrees.stream().map(Decree::id).toList(), Reign.MAX_DECREES, seats, candidates, petitions,
                Territory.ownedBy(CityData.get(server), reign.faction).stream().map(c -> c.name).toList());
    }

    private Court() {}
}
