package com.epi0.vtm.nemesis;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the Nemesis screen is allowed to show a player: a snapshot built from the world's records and the player's own
 * knowledge. Nothing the player has not learned ever leaves the server.
 */
public final class NemesisViews {
    public static final int CHRONICLE_LINES = 80;

    /**
     * @param tier          0 heard of, 1 met, 2 studied; fields beyond what the tier allows are empty
     * @param unknownTraits how many traits the player has yet to learn of (which ones is not revealed)
     */
    public record Core(UUID id, String name, ResourceLocation faction, int tier, Optional<ResourceLocation> clan,
                       Optional<String> role, int level, String status, List<String> traits, int unknownTraits,
                       List<String> scars, Optional<GlobalPos> place, Optional<String> epithet) {
        static final Codec<Core> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Core::id),
                Codec.STRING.fieldOf("name").forGetter(Core::name),
                ResourceLocation.CODEC.fieldOf("faction").forGetter(Core::faction),
                Codec.INT.fieldOf("tier").forGetter(Core::tier),
                ResourceLocation.CODEC.optionalFieldOf("clan").forGetter(Core::clan),
                Codec.STRING.optionalFieldOf("role").forGetter(Core::role),
                Codec.INT.fieldOf("level").forGetter(Core::level),
                Codec.STRING.fieldOf("status").forGetter(Core::status),
                Codec.STRING.listOf().fieldOf("traits").forGetter(Core::traits),
                Codec.INT.fieldOf("unknown_traits").forGetter(Core::unknownTraits),
                Codec.STRING.listOf().fieldOf("scars").forGetter(Core::scars),
                GlobalPos.CODEC.optionalFieldOf("place").forGetter(Core::place),
                Codec.STRING.optionalFieldOf("epithet").forGetter(Core::epithet)
        ).apply(i, Core::new));
    }

    /** What has passed between this vampire and the player. */
    public record History(int enmity, int respect, int defeats, int losses, int spared, boolean bloodHunt) {
        static final History NONE = new History(0, 0, 0, 0, 0, false);
        static final Codec<History> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("enmity").forGetter(History::enmity),
                Codec.INT.fieldOf("respect").forGetter(History::respect),
                Codec.INT.fieldOf("defeats").forGetter(History::defeats),
                Codec.INT.fieldOf("losses").forGetter(History::losses),
                Codec.INT.fieldOf("spared").forGetter(History::spared),
                Codec.BOOL.fieldOf("blood_hunt").forGetter(History::bloodHunt)
        ).apply(i, History::new));
    }

    /** A vampire bound to the player: how loyal, and what errand they are on. */
    public record Bond(int loyalty, Optional<String> mission, long missionEnds, long wakeAt) {
        static final Bond NONE = new Bond(-1, Optional.empty(), 0, 0);
        static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("loyalty").forGetter(Bond::loyalty),
                Codec.STRING.optionalFieldOf("mission").forGetter(Bond::mission),
                Codec.LONG.fieldOf("mission_ends").forGetter(Bond::missionEnds),
                Codec.LONG.fieldOf("wake_at").forGetter(Bond::wakeAt)
        ).apply(i, Bond::new));

        public boolean bound() {
            return loyalty >= 0;
        }
    }

    public record Kin(UUID id, String name, String type) {
        static final Codec<Kin> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Kin::id),
                Codec.STRING.fieldOf("name").forGetter(Kin::name),
                Codec.STRING.fieldOf("type").forGetter(Kin::type)
        ).apply(i, Kin::new));
    }

    public record Entry(Core core, History history, Bond bond, List<Kin> kin) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Core.CODEC.fieldOf("core").forGetter(Entry::core),
                History.CODEC.fieldOf("history").forGetter(Entry::history),
                Bond.CODEC.fieldOf("bond").forGetter(Entry::bond),
                Kin.CODEC.listOf().fieldOf("kin").forGetter(Entry::kin)
        ).apply(i, Entry::new));

        public UUID id() {
            return core.id();
        }
    }

    /** A seat in a faction's court: whether anyone holds it, and who, if the player knows them. */
    public record Seat(String role, boolean filled, Optional<UUID> known, Optional<String> ruler) {
        static final Codec<Seat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("role").forGetter(Seat::role),
                Codec.BOOL.fieldOf("filled").forGetter(Seat::filled),
                UUIDUtil.CODEC.optionalFieldOf("known").forGetter(Seat::known),
                Codec.STRING.optionalFieldOf("ruler").forGetter(Seat::ruler)
        ).apply(i, Seat::new));
    }

    public record Court(ResourceLocation faction, List<Seat> seats) {
        static final Codec<Court> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("faction").forGetter(Court::faction),
                Seat.CODEC.listOf().fieldOf("seats").forGetter(Court::seats)
        ).apply(i, Court::new));
    }

    /** A city the player knows of. The warden is named only if the player knows who they are. */
    public record Domain(UUID id, String name, ResourceLocation owner, int strength, Optional<String> warden, GlobalPos place,
                         int heat) {
        static final Codec<Domain> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Domain::id),
                Codec.STRING.fieldOf("name").forGetter(Domain::name),
                ResourceLocation.CODEC.fieldOf("owner").forGetter(Domain::owner),
                Codec.INT.fieldOf("strength").forGetter(Domain::strength),
                Codec.STRING.optionalFieldOf("warden").forGetter(Domain::warden),
                GlobalPos.CODEC.fieldOf("place").forGetter(Domain::place),
                Codec.INT.fieldOf("heat").forGetter(Domain::heat)
        ).apply(i, Domain::new));
    }

    public record Snapshot(List<Entry> entries, List<Court> courts, List<ChronicleEntry> chronicle, List<Domain> domains,
                           Optional<com.epi0.vtm.court.CourtView> court, long now) {
        public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Snapshot::entries),
                Court.CODEC.listOf().fieldOf("courts").forGetter(Snapshot::courts),
                ChronicleEntry.CODEC.listOf().fieldOf("chronicle").forGetter(Snapshot::chronicle),
                Domain.CODEC.listOf().fieldOf("domains").forGetter(Snapshot::domains),
                com.epi0.vtm.court.CourtView.CODEC.optionalFieldOf("court").forGetter(Snapshot::court),
                Codec.LONG.fieldOf("now").forGetter(Snapshot::now)
        ).apply(i, Snapshot::new));

        public static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), List.of(), List.of(), Optional.empty(), 0L);
    }

    public static Snapshot build(MinecraftServer server, ServerPlayer player) {
        NemesisData data = NemesisManager.data(server);
        NemesisKnowledge knowledge = NemesisKnowledge.of(player);
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<UUID, NemesisKnowledge.Known> e : knowledge.known().entrySet()) {
            NemesisRecord r = data.get(e.getKey());
            if (r != null) {
                entries.add(entry(data, player, r, e.getValue(), knowledge));
            }
        }

        List<Court> courts = new ArrayList<>();
        for (ResourceLocation faction : NemesisSimulator.joinable(server)) {
            List<Seat> seats = new ArrayList<>();
            for (Role role : new Role[]{Role.LEADER, Role.LIEUTENANT, Role.ENFORCER}) {
                List<NemesisRecord> holders = data.inRole(faction, role);
                var reign = role == Role.LEADER ? com.epi0.vtm.court.CourtData.get(server).reign(faction) : null;
                for (int i = 0; i < role.seats(); i++) {
                    NemesisRecord holder = i < holders.size() ? holders.get(i) : null;
                    seats.add(new Seat(role.id(), holder != null || reign != null,
                            holder != null && knowledge.about(holder.id).isPresent() ? Optional.of(holder.id) : Optional.empty(),
                            reign == null ? Optional.empty() : Optional.of(reign.rulerName)));
                }
            }
            courts.add(new Court(faction, seats));
        }

        List<ChronicleEntry> news = data.chronicle().stream()
                .filter(c -> c.publicNews() || c.involved().stream().anyMatch(id -> knowledge.about(id).isPresent()))
                .toList();
        news = news.subList(Math.max(0, news.size() - CHRONICLE_LINES), news.size());
        List<Domain> domains = new ArrayList<>();
        var cityKnowledge = com.epi0.vtm.city.CityKnowledge.of(player);
        for (com.epi0.vtm.city.City city : com.epi0.vtm.city.CityData.get(server).all()) {
            if (cityKnowledge.knows(city.id)) {
                NemesisRecord warden = city.warden == null ? null : data.get(city.warden);
                Optional<String> wardenName = warden != null && warden.alive() && knowledge.about(warden.id).isPresent()
                        ? Optional.of(warden.name) : Optional.empty();
                domains.add(new Domain(city.id, city.name, city.owner, city.strength, wardenName,
                        com.epi0.vtm.city.Territory.where(city), city.heatOf(player.getUUID())));
            }
        }
        return new Snapshot(entries, courts, news, domains, Optional.of(com.epi0.vtm.court.Court.view(server, player)), server.overworld().getGameTime());
    }

    private static Entry entry(NemesisData data, ServerPlayer player, NemesisRecord r, NemesisKnowledge.Known known, NemesisKnowledge knowledge) {
        boolean met = known.tier() >= 1;
        List<String> traits = r.traits.stream().filter(known::knows).map(Trait::id).toList();
        Core core = new Core(r.id, r.name, r.faction, known.tier(),
                met ? Optional.of(r.clan) : Optional.empty(),
                met ? Optional.of(r.role.id()) : Optional.empty(),
                met ? r.level : 0, r.status.id(), traits, r.traits.size() - traits.size(),
                met ? r.scars.stream().map(Scar::id).toList() : List.of(),
                known.place(), met ? NemesisManager.epithet(r) : Optional.empty());

        Feud feud = r.feudOrNull(player.getUUID());
        History history = feud == null ? History.NONE
                : new History(feud.enmity, feud.respect, feud.defeats, feud.losses, feud.spared, feud.bloodHunt);

        boolean mine = r.status == Status.BOUND && player.getUUID().equals(r.boundTo);
        Bond bond = mine ? new Bond(r.loyalty, Optional.ofNullable(r.mission).map(m -> m.type().id()),
                r.mission == null ? 0 : r.mission.endsAt(), r.wakeAt) : Bond.NONE;

        List<Kin> kin = new ArrayList<>();
        if (known.tier() >= 2) {
            for (Relation relation : r.relations) {
                NemesisRecord other = data.get(relation.other());
                if (other != null && knowledge.about(other.id).isPresent()) {
                    kin.add(new Kin(other.id, other.name, relation.type().id()));
                }
            }
        }
        return new Entry(core, history, bond, kin);
    }

    private NemesisViews() {}
}
