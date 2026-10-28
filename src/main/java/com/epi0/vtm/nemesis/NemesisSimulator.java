package com.epi0.vtm.nemesis;

import com.epi0.vtm.VtmConfig;
import com.epi0.vtm.registry.Faction;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The world turning while nobody watches. Each step the named vampires train, wake, scheme, challenge their superiors,
 * raid their enemies, fill empty seats, run errands for whoever has bound them, and plan their revenge on players they
 * hold a grudge against. Everything that happens goes into the chronicle. A vampire currently standing in the world is
 * never touched: what the player can see is simulated by the game itself.
 */
public final class NemesisSimulator {
    public static final int HUNT_ENMITY = 40;
    public static final int AMBITION_TO_CHALLENGE = 60;
    private static final double CHALLENGE_CHANCE = 0.05;
    private static final double RAID_CHANCE = 0.08;

    /** One step of the world. {@code random} is injectable so tests can replay a history. */
    public static void step(MinecraftServer server, RandomSource random) {
        NemesisData data = NemesisManager.data(server);
        long now = server.overworld().getGameTime();
        data.steps++;
        populate(server, data, random);
        wake(server, data, now);
        train(server, data, random);
        resolveMissions(server, data, now, random);
        loyalty(server, data, random);
        succession(server, data);
        challenges(server, data, random);
        raids(server, data, random);
        com.epi0.vtm.city.Territory.step(server, data, random);
        grudges(server, data, now, random);
        if (data.steps % 20 == 0) {
            decay(data);
        }
        data.setDirty();
    }

    // ---------------------------------------------------------------- the court fills itself

    /** Keeps every joinable faction supplied with named vampires, founding new ones where the old have fallen. */
    public static void populate(MinecraftServer server, NemesisData data, RandomSource random) {
        int target = VtmConfig.NEMESIS_POPULATION.get();
        for (ResourceLocation id : joinable(server)) {
            List<NemesisRecord> members = data.inFaction(id).stream().filter(r -> r.status != Status.BOUND).toList();
            int missing = target - members.size();
            // Seats are filled from below; a brand-new court is founded top-down so it starts with a hierarchy.
            for (int i = 0; i < missing; i++) {
                Role role = Role.AGENT;
                for (Role candidate : new Role[]{Role.LEADER, Role.LIEUTENANT, Role.ENFORCER}) {
                    if (data.inRole(id, candidate).size() < candidate.seats() && members.size() + i < 1 + 2 + 3) {
                        role = candidate;
                        break;
                    }
                }
                NemesisManager.create(server, id, role, random);
            }
        }
    }

    public static List<ResourceLocation> joinable(MinecraftServer server) {
        return server.registryAccess().registry(VtmRegistries.FACTION).map(r -> r.entrySet().stream()
                .filter(e -> e.getValue().joinable()).map(e -> e.getKey().location()).sorted().toList()).orElse(List.of());
    }

    // ---------------------------------------------------------------- time passes

    public static void wake(MinecraftServer server, NemesisData data, long now) {
        for (NemesisRecord r : data.all()) {
            if (r.status == Status.TORPOR && r.wakeAt <= now) {
                r.status = Status.ACTIVE;
                NemesisManager.grantXp(r, 4 + r.deaths);
                NemesisManager.log(server, "awoke", r.faction, r.role == Role.LEADER, r);
            }
        }
    }

    static void train(MinecraftServer server, NemesisData data, RandomSource random) {
        for (NemesisRecord r : data.all()) {
            if (r.status != Status.ACTIVE || NemesisManager.manifested(server, r)) {
                continue;
            }
            int gained = NemesisManager.grantXp(r, 1 + random.nextInt(2));
            r.ambition = Math.max(0, Math.min(100, r.ambition + random.nextInt(5) - 1));
            if (gained > 0 && r.level % 5 == 0) {
                NemesisManager.log(server, "grew_stronger", r.faction, false, r);
            }
        }
    }

    /** Standing grudges fade a little each day, except for the vengeful, who nurse them. */
    static void decay(NemesisData data) {
        for (NemesisRecord r : data.all()) {
            r.feuds.values().forEach(f -> {
                if (!r.has(Trait.VENGEFUL) && !f.bloodHunt && f.enmity > 0) {
                    f.enmity--;
                }
                if (f.respect > 0) {
                    f.respect--;
                }
            });
            r.feuds.values().removeIf(f -> f.enmity == 0 && f.respect == 0 && f.encounters == 0 && f.spared == 0);
        }
    }

    // ---------------------------------------------------------------- the hierarchy

    /** Empty seats go to whoever below them is strongest, bottom to top, so a fallen leader starts a chain of promotions. */
    public static void succession(MinecraftServer server, NemesisData data) {
        for (ResourceLocation faction : joinable(server)) {
            for (Role role : new Role[]{Role.LEADER, Role.LIEUTENANT, Role.ENFORCER}) {
                while (data.inRole(faction, role).size() < role.seats()) {
                    Optional<NemesisRecord> heir = heir(data, faction, role);
                    if (heir.isEmpty()) {
                        break;
                    }
                    NemesisRecord next = heir.get();
                    Role before = next.role;
                    next.role = role;
                    NemesisManager.log(server, role == Role.LEADER ? "new_leader" : "promoted", faction, role == Role.LEADER, next);
                    VampireNpcRefresh.apply(server, next);
                    if (before == next.role) {
                        break;
                    }
                }
            }
        }
    }

    private static Optional<NemesisRecord> heir(NemesisData data, ResourceLocation faction, Role seat) {
        return data.inFaction(faction).stream()
                .filter(r -> r.status == Status.ACTIVE && r.role.ordinal() > seat.ordinal())
                .max(Comparator.comparingInt((NemesisRecord r) -> r.power() * 10 + r.ambition / 10));
    }

    /** An ambitious lieutenant may try to take the leader's seat; the loser pays for it. */
    public static void challenges(MinecraftServer server, NemesisData data, RandomSource random) {
        for (ResourceLocation faction : joinable(server)) {
            List<NemesisRecord> leaders = data.inRole(faction, Role.LEADER);
            if (leaders.isEmpty()) {
                continue;
            }
            NemesisRecord leader = leaders.get(0);
            for (NemesisRecord challenger : data.inRole(faction, Role.LIEUTENANT)) {
                if (challenger.ambition < AMBITION_TO_CHALLENGE || challenger.status != Status.ACTIVE || leader.status != Status.ACTIVE
                        || random.nextDouble() > CHALLENGE_CHANCE || NemesisManager.manifested(server, challenger)
                        || NemesisManager.manifested(server, leader)) {
                    continue;
                }
                boolean challengerWins = duel(challenger, leader, random);
                NemesisRecord winner = challengerWins ? challenger : leader;
                NemesisRecord loser = challengerWins ? leader : challenger;
                NemesisManager.grantXp(winner, 5);
                if (challengerWins) {
                    leader.role = Role.AGENT; // dethroned: whatever becomes of them, the seat is empty
                    NemesisManager.log(server, "coup", faction, true, List.of(challenger, leader));
                } else {
                    NemesisManager.log(server, "coup_failed", faction, true, List.of(challenger, leader));
                }
                fall(server, loser, winner, random, 0.4);
                break;
            }
        }
    }

    // ---------------------------------------------------------------- wars

    /** Enemy sects skirmish; rivals are drawn to each other first. */
    public static void raids(MinecraftServer server, NemesisData data, RandomSource random) {
        for (ResourceLocation id : joinable(server)) {
            Optional<Faction> faction = VtmRegistries.faction(server.registryAccess(), id);
            if (faction.isEmpty() || random.nextDouble() > RAID_CHANCE) {
                continue;
            }
            List<NemesisRecord> mine = fighters(server, data, id);
            List<NemesisRecord> theirs = new ArrayList<>();
            for (ResourceLocation enemy : faction.get().enemies()) {
                theirs.addAll(fighters(server, data, enemy));
            }
            if (mine.isEmpty() || theirs.isEmpty()) {
                continue;
            }
            NemesisRecord attacker = mine.get(random.nextInt(mine.size()));
            NemesisRecord defender = theirs.stream().filter(t -> attacker.related(t.id, Relation.Type.RIVAL)).findFirst()
                    .orElse(theirs.get(random.nextInt(theirs.size())));
            boolean attackerWins = duel(attacker, defender, random);
            NemesisRecord winner = attackerWins ? attacker : defender;
            NemesisRecord loser = attackerWins ? defender : attacker;
            NemesisManager.grantXp(winner, 3);
            NemesisManager.link(attacker, defender, Relation.Type.RIVAL);
            NemesisManager.log(server, attackerWins ? "raid_won" : "raid_lost", id, false, List.of(attacker, defender));
            fall(server, loser, winner, random, 0.25);
        }
    }

    private static List<NemesisRecord> fighters(MinecraftServer server, NemesisData data, ResourceLocation faction) {
        return data.inFaction(faction).stream().filter(r -> r.status == Status.ACTIVE && !NemesisManager.manifested(server, r)).toList();
    }

    /** Who wins a fight between two named vampires, by power and temperament, with luck. */
    public static boolean duel(NemesisRecord a, NemesisRecord b, RandomSource random) {
        double edge = a.power() / (double) (a.power() + b.power());
        edge += (a.has(Trait.RECKLESS) ? 0.05 : 0) - (a.has(Trait.COWARD) ? 0.1 : 0) + (a.has(Trait.PATIENT) ? 0.03 : 0);
        edge -= (b.has(Trait.RECKLESS) ? 0.05 : 0) - (b.has(Trait.COWARD) ? 0.1 : 0) + (b.has(Trait.PATIENT) ? 0.03 : 0);
        return random.nextDouble() < edge;
    }

    /**
     * The loser of an offscreen fight: usually torpor, sometimes final death (always, if they had no deaths left in
     * them, and otherwise {@code finality} of the time).
     */
    public static void fall(MinecraftServer server, NemesisRecord loser, NemesisRecord victor, RandomSource random, double finality) {
        long now = server.overworld().getGameTime();
        if (loser.deaths >= loser.resilience() || random.nextDouble() < finality * (loser.deaths > 0 ? 1.5 : 1.0)) {
            NemesisManager.destroy(server, loser, null, null);
            if (loser.boundTo == null && victor != null && victor != loser) {
                for (Relation relation : java.util.List.copyOf(loser.relations)) {
                    NemesisRecord kin = NemesisManager.data(server).get(relation.other());
                    if (kin != null && kin != victor && kin.alive() && relation.type() != Relation.Type.RIVAL && !kin.related(victor.id, Relation.Type.RIVAL)) {
                        NemesisManager.link(kin, victor, Relation.Type.RIVAL);
                    }
                }
            }
            return;
        }
        loser.deaths++;
        if (loser.status != Status.BOUND) {
            loser.status = Status.TORPOR;
        }
        loser.wakeAt = now + NemesisManager.NIGHT * (1 + loser.deaths);
        loser.entity = null;
        NemesisManager.log(server, "torpor", loser.faction, false, loser);
    }

    // ---------------------------------------------------------------- errands, loyalty, betrayal

    public static void resolveMissions(MinecraftServer server, NemesisData data, long now, RandomSource random) {
        for (NemesisRecord r : data.all()) {
            if (r.status != Status.BOUND || r.mission == null || r.mission.endsAt() > now || r.boundTo == null) {
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(r.boundTo);
            if (owner == null) {
                continue; // errands are reported to their owner; wait until they are here to hear it
            }
            Mission mission = r.mission;
            r.mission = null;
            NemesisManager.grantXp(r, 3);
            switch (mission.type()) {
                case TRIBUTE -> r.pendingTribute += r.level * 2;
                case SPY -> spied(server, data, owner, r, mission, random);
                case HUNT -> hunted(server, data, owner, r, mission, random);
            }
        }
    }

    private static void spied(MinecraftServer server, NemesisData data, ServerPlayer owner, NemesisRecord spy, Mission mission, RandomSource random) {
        List<NemesisRecord> targets = mission.targetFaction() == null ? List.of() : data.inFaction(mission.targetFaction());
        int guard = targets.stream().mapToInt(NemesisRecord::power).max().orElse(spy.power());
        double success = Math.max(0.2, Math.min(0.9, 0.5 + (spy.power() - guard) * 0.04));
        if (random.nextDouble() > success) {
            owner.displayClientMessage(Component.translatable("message.vtm.nemesis.spy_caught", spy.name), false);
            fall(server, spy, targets.isEmpty() ? spy : targets.get(0), random, 0.3);
            return;
        }
        int learned = 0;
        for (int i = 0; i < 2; i++) {
            Optional<Component> msg = Intel.revealAbout(owner, targets, random);
            if (msg.isPresent()) {
                owner.displayClientMessage(msg.get(), false);
                learned++;
            }
        }
        owner.displayClientMessage(Component.translatable(learned > 0 ? "message.vtm.nemesis.spy_report" : "message.vtm.nemesis.spy_nothing", spy.name), false);
    }

    private static void hunted(MinecraftServer server, NemesisData data, ServerPlayer owner, NemesisRecord hunter, Mission mission, RandomSource random) {
        NemesisRecord prey = mission.targetNemesis() == null ? null : data.get(mission.targetNemesis());
        if (prey == null || !prey.alive() || prey.status == Status.BOUND || NemesisManager.manifested(server, prey)) {
            owner.displayClientMessage(Component.translatable("message.vtm.nemesis.hunt_nothing", hunter.name), false);
            return;
        }
        boolean won = duel(hunter, prey, random);
        NemesisManager.log(server, won ? "hunted_down" : "hunt_failed", prey.faction, false, List.of(hunter, prey), owner.getGameProfile().getName());
        owner.displayClientMessage(Component.translatable(won ? "message.vtm.nemesis.hunt_won" : "message.vtm.nemesis.hunt_lost", hunter.name, prey.name), false);
        if (won) {
            NemesisManager.grantXp(hunter, 5);
            fall(server, prey, hunter, random, 0.5);
            Membership m = Membership.of(owner);
            if (m.faction().isPresent()) {
                Faction.Relation relation = VtmRegistries.faction(server.registryAccess(), m.faction().get())
                        .map(f -> f.relationTo(m.faction().get(), prey.faction)).orElse(Faction.Relation.NEUTRAL);
                if (relation == Faction.Relation.ENEMY) {
                    SectManager.adjustStanding(owner, m.faction().get(), SectManager.KILL_ENEMY);
                }
            }
        } else {
            fall(server, hunter, prey, random, 0.2);
        }
    }

    /** Loyalty drains unless fed; a vampire who no longer feels bound may turn on their master. */
    public static void loyalty(MinecraftServer server, NemesisData data, RandomSource random) {
        for (NemesisRecord r : data.all()) {
            if (r.status != Status.BOUND || r.boundTo == null) {
                continue;
            }
            if (data.steps % 4 == 0 && r.loyalty > 0) {
                r.loyalty--;
            }
            if (r.loyalty < 20 && random.nextDouble() < 0.08) {
                betray(server, r);
            }
        }
    }

    public static void betray(MinecraftServer server, NemesisRecord r) {
        UUID master = r.boundTo;
        ServerPlayer owner = master == null ? null : server.getPlayerList().getPlayer(master);
        r.status = Status.ACTIVE;
        r.boundTo = null;
        r.mission = null;
        if (master != null) {
            Feud feud = r.feud(master);
            feud.enmity = Math.max(feud.enmity, 80);
            feud.respect = 0;
            feud.hunt = 100;
        }
        com.epi0.vtm.npc.VampireNpc body = NemesisManager.entityOf(server, r);
        if (body != null) {
            body.setBoundOwner(null);
            body.setTarget(owner);
        }
        NemesisManager.log(server, "betrayed", r.faction, false, r, owner == null ? "?" : owner.getGameProfile().getName());
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("message.vtm.nemesis.betrayed", r.name), false);
        }
    }

    // ---------------------------------------------------------------- vengeance

    /**
     * Declares blood hunts on outcasts, advances the hunts of vampires with a grudge, and sends them when ready.
     * Hunting only happens after dark and only to players in the overworld.
     */
    public static void grudges(MinecraftServer server, NemesisData data, long now, RandomSource random) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative() || !VampireData.of(player).isVampire()) {
                continue;
            }
            declareBloodHunts(server, data, player);
            if (!VtmConfig.NEMESIS_AMBUSHES.get() || player.level().dimension() != Level.OVERWORLD || !com.epi0.vtm.npc.VtmNpc.isNight(player.serverLevel())) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            for (NemesisRecord r : data.all()) {
                Feud feud = r.feudOrNull(player.getUUID());
                if (feud == null || feud.enmity < HUNT_ENMITY || r.status != Status.ACTIVE || NemesisManager.manifested(server, r)) {
                    continue;
                }
                feud.hunt += (3 + r.level / 3) * (r.has(Trait.PATIENT) ? 3 : 2) / 2 + (feud.bloodHunt ? 3 : 0);
                feud.clamp();
                if (feud.hunt >= 100 && NemesisManager.ambush(player, r)) {
                    feud.hunt = 0;
                    break; // one at a time
                }
            }
            wanderer(server, data, player, level, random);
        }
    }

    /** A faction that has outlawed a player sets its court on them. */
    public static void declareBloodHunts(MinecraftServer server, NemesisData data, ServerPlayer player) {
        Membership m = Membership.of(player);
        for (ResourceLocation faction : joinable(server)) {
            if (m.standingWith(faction) > SectManager.OUTCAST) {
                continue;
            }
            for (NemesisRecord r : data.inFaction(faction)) {
                if (r.role == Role.AGENT || r.status == Status.BOUND) {
                    continue;
                }
                Feud feud = r.feud(player.getUUID());
                if (!feud.bloodHunt) {
                    feud.bloodHunt = true;
                    feud.enmity = Math.max(feud.enmity, 60);
                    NemesisManager.log(server, "blood_hunt", faction, true, r, player.getGameProfile().getName());
                }
            }
        }
    }

    /** Now and then a named vampire is simply out in the night near a player: an introduction, or a surprise. */
    static void wanderer(MinecraftServer server, NemesisData data, ServerPlayer player, ServerLevel level, RandomSource random) {
        if (random.nextDouble() > 0.12) {
            return;
        }
        List<NemesisRecord> free = data.all().stream().filter(r -> r.status == Status.ACTIVE && !NemesisManager.manifested(server, r)).toList();
        if (free.isEmpty()) {
            return;
        }
        boolean someoneNear = !level.getEntitiesOfClass(com.epi0.vtm.npc.VampireNpc.class, player.getBoundingBox().inflate(80),
                v -> v.nemesisId() != null).isEmpty();
        Vec3 spot = someoneNear ? null : Placement.spot(level, player.position(), 55, 90, random);
        if (spot != null) {
            NemesisManager.manifest(level, free.get(random.nextInt(free.size())), spot);
        }
    }

    private NemesisSimulator() {}
}
