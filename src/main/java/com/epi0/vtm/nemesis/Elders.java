package com.epi0.vtm.nemesis;

import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.vampire.VampireRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The ancients do not sit still. Each elder has an agenda that decides where they sleep next: wanderers drift between
 * every haven there is, patrons shore up the cities of their own sect, and recluses almost never stir. When one moves
 * the world's record moves with them, and whatever a player was once told about where to find them goes stale.
 */
public final class Elders {
    /** Blocks an elder must move for it to count as leaving. */
    private static final double MIN_MOVE = 64;
    /** Strength a patron adds to the city they take up residence in. */
    public static final int PATRON_BOOST = 6;

    public enum Agenda {
        WANDERER("wanderer", 0.25), PATRON("patron", 0.15), RECLUSE("recluse", 0.03);

        private final String id;
        private final double chance;

        Agenda(String id, double chance) {
            this.id = id;
            this.chance = chance;
        }

        public String id() {
            return id;
        }

        /** Chance per step that an elder with this agenda goes looking for a new haven. */
        public double chance() {
            return chance;
        }
    }

    public static boolean isElder(NemesisRecord r) {
        return r.generation <= VampireRules.ELDER_MAX;
    }

    /** What an elder is about. Fixed for a vampire's whole existence. */
    public static Agenda agenda(NemesisRecord r) {
        return switch (Math.floorMod(r.id.hashCode(), 4)) {
            case 0 -> Agenda.RECLUSE;
            case 1 -> Agenda.PATRON;
            default -> Agenda.WANDERER;
        };
    }

    /** One step of the world: each elder who is free to may take up a new haven. */
    public static void step(MinecraftServer server, NemesisData data, RandomSource random) {
        for (NemesisRecord r : data.all()) {
            if (!isElder(r) || r.status != Status.ACTIVE || NemesisManager.manifested(server, r) || warden(server, r)
                    || random.nextDouble() > agenda(r).chance()) {
                continue;
            }
            move(server, data, r, random);
        }
    }

    /** Sends {@code r} to a haven other than the one they keep. Returns whether they went. */
    public static boolean move(MinecraftServer server, NemesisData data, NemesisRecord r, RandomSource random) {
        List<City> cities = CityData.get(server).all().stream()
                .filter(c -> c.haven != null && (r.lair == null || !c.haven.equals(r.lair) || !c.dimension.equals(r.lairDimension))).toList();
        Agenda agenda = agenda(r);
        City city = null;
        BlockPos spot = null;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension = null;
        if (agenda == Agenda.PATRON) {
            // Their own sect's cities, the weakest first.
            city = cities.stream().filter(c -> r.faction.equals(c.owner) && far(r, c.haven, c.dimension))
                    .min(java.util.Comparator.comparingInt((City c) -> c.strength)).orElse(null);
        } else {
            List<City> reachable = cities.stream().filter(c -> far(r, c.haven, c.dimension)).toList();
            if (!reachable.isEmpty()) {
                city = reachable.get(random.nextInt(reachable.size()));
            }
        }
        if (city != null) {
            spot = city.haven;
            dimension = city.dimension;
        } else if (agenda != Agenda.PATRON) {
            // No city haven to go to: take over a lair someone else has left empty-handed, or stay put.
            List<NemesisRecord> hosts = new ArrayList<>(data.all().stream().filter(o -> o != r && o.lair != null && o.lairDimension != null
                    && o.status != Status.BOUND && far(r, o.lair, o.lairDimension)).toList());
            if (!hosts.isEmpty()) {
                NemesisRecord host = hosts.get(random.nextInt(hosts.size()));
                spot = host.lair;
                dimension = host.lairDimension;
            }
        }
        if (spot == null) {
            return false;
        }
        r.lair = spot;
        r.lairDimension = dimension;
        r.lastSeen = spot;
        r.seenDimension = dimension;
        if (city != null && agenda == Agenda.PATRON) {
            city.strength = Math.min(City.MAX_STRENGTH, city.strength + PATRON_BOOST);
            CityData.get(server).setDirty();
        }
        NemesisManager.log(server, agenda == Agenda.PATRON && city != null ? "elder_patron" : "elder_moved", r.faction, false, r,
                city == null ? "" : city.name);
        data.setDirty();
        return true;
    }

    private static boolean far(NemesisRecord r, BlockPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        return r.lair == null || !dimension.equals(r.lairDimension) || r.lair.distSqr(pos) > MIN_MOVE * MIN_MOVE;
    }

    private static boolean warden(MinecraftServer server, NemesisRecord r) {
        return CityData.get(server).all().stream().anyMatch(c -> r.id.equals(c.warden));
    }

    /** Whether what the player was told about where {@code r} is no longer true. */
    public static boolean stale(NemesisKnowledge.Known known, NemesisRecord r) {
        if (known.place().isEmpty() || r.lastSeen == null || r.seenDimension == null) {
            return false;
        }
        GlobalPos told = known.place().get();
        return !told.dimension().equals(r.seenDimension) || !told.pos().equals(r.lastSeen);
    }

    /**
     * Brings the player's idea of where the elders are up to date: the first elder they know of whose haven has changed.
     * Returns what they learned, if anything.
     */
    public static Optional<Component> trail(ServerPlayer player, NemesisData data) {
        NemesisKnowledge knowledge = NemesisKnowledge.of(player);
        for (NemesisRecord r : data.all()) {
            Optional<NemesisKnowledge.Known> known = knowledge.about(r.id);
            if (r.alive() && isElder(r) && known.isPresent() && stale(known.get(), r)) {
                Intel.locate(player, r);
                return Optional.of(Component.translatable("message.vtm.nemesis.elder_trail", r.name));
            }
        }
        return Optional.empty();
    }

    private Elders() {}
}
