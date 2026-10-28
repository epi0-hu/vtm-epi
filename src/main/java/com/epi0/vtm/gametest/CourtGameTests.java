package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.city.CityRegistry;
import com.epi0.vtm.court.Court;
import com.epi0.vtm.court.CourtData;
import com.epi0.vtm.court.Decree;
import com.epi0.vtm.court.Petition;
import com.epi0.vtm.court.Reign;
import com.epi0.vtm.dialogue.DialogueManager;
import com.epi0.vtm.nemesis.NemesisData;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.nemesis.NemesisRecord;
import com.epi0.vtm.nemesis.NemesisSimulator;
import com.epi0.vtm.nemesis.Role;
import com.epi0.vtm.nemesis.Status;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Player-run courts: taking a throne, governing, and being governed. Each test has a batch of its own (shared world state). */
@GameTestHolder(VtmMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CourtGameTests {

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(CourtGameTests.class);
    }

    private static final ResourceLocation CAMARILLA = VtmMod.id("camarilla");
    private static final ResourceLocation SABBAT = VtmMod.id("sabbat");
    private static final ResourceLocation ANARCH = VtmMod.id("anarch");

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    /** A clean world with full courts, and a vampire of the Camarilla senior enough to rule. */
    private static ServerPlayer setup(GameTestHelper helper, RandomSource random) {
        var server = server(helper);
        CityData.get(server).clear();
        CourtData.get(server).clear();
        NemesisData data = NemesisManager.data(server);
        data.reset();
        NemesisSimulator.populate(server, data, random);
        NemesisSimulator.succession(server, data);
        ServerPlayer player = NemesisGameTests.player(helper);
        SectManager.set(player, Membership.DEFAULT.withFaction(java.util.Optional.of(CAMARILLA), 1).withStanding(CAMARILLA, 60));
        return player;
    }

    /** Topples the Camarilla's Prince, so the throne stands empty. */
    private static void topple(GameTestHelper helper, ServerPlayer player) {
        var server = server(helper);
        NemesisRecord prince = NemesisManager.data(server).inRole(CAMARILLA, Role.LEADER).get(0);
        NemesisManager.destroy(server, prince, player, null);
    }

    private static Reign crown(GameTestHelper helper, ServerPlayer player) {
        topple(helper, player);
        helper.assertTrue(Court.claim(player), "the throne is claimed");
        return CourtData.get(server(helper)).reignOf(player.getUUID());
    }

    // ---------------------------------------------------------------- the throne

    @GameTest(template = "empty", batch = "court_throne")
    public static void emptyThronesCanBeClaimedByTheWorthy(GameTestHelper helper) {
        var random = RandomSource.create(3L);
        ServerPlayer player = setup(helper, random);
        var server = server(helper);
        NemesisData data = NemesisManager.data(server);
        helper.assertTrue(Court.blocker(player).equals(java.util.Optional.of("throne_held")), "a sitting Prince blocks the way");

        ServerPlayer mortal = VtmGameTests.survivalPlayer(helper);
        helper.assertTrue(Court.blocker(mortal).equals(java.util.Optional.of("not_vampire")), "mortals cannot rule");
        SectManager.set(player, Membership.of(player).withFaction(java.util.Optional.empty(), 0));
        helper.assertTrue(Court.blocker(player).equals(java.util.Optional.of("no_sect")), "nor the sect-less");
        SectManager.set(player, Membership.DEFAULT.withFaction(java.util.Optional.of(CAMARILLA), 0).withStanding(CAMARILLA, 60));
        helper.assertTrue(Court.blocker(player).equals(java.util.Optional.of("low_rank")), "nor the junior");
        SectManager.set(player, Membership.DEFAULT.withFaction(java.util.Optional.of(CAMARILLA), 1).withStanding(CAMARILLA, 10));
        helper.assertTrue(Court.blocker(player).equals(java.util.Optional.of("low_standing")), "nor the untrusted");
        SectManager.set(player, Membership.DEFAULT.withFaction(java.util.Optional.of(CAMARILLA), 1).withStanding(CAMARILLA, 60));

        topple(helper, player);
        helper.assertTrue(data.inRole(CAMARILLA, Role.LEADER).isEmpty() && data.throneHeld(CAMARILLA, helper.getLevel().getGameTime()),
                "destroying the Prince leaves the throne empty, and held open");
        NemesisSimulator.succession(server, data);
        NemesisSimulator.populate(server, data, random);
        helper.assertTrue(data.inRole(CAMARILLA, Role.LEADER).isEmpty(), "the simulation leaves it alone for now");
        helper.assertTrue(Court.blocker(player).isEmpty(), "and the player may take it");

        helper.assertTrue(Court.claim(player), "claimed");
        Reign reign = CourtData.get(server).reignOf(player.getUUID());
        helper.assertTrue(reign != null && reign.faction.equals(CAMARILLA) && reign.influence == Reign.STARTING_INFLUENCE, "a reign begins");
        helper.assertTrue(Membership.of(player).rank() == 2, "at the highest rank");
        helper.assertTrue(data.chronicle().stream().anyMatch(c -> c.key().equals("throne_claimed") && c.publicNews()), "and the world hears");
        NemesisSimulator.succession(server, data);
        NemesisSimulator.populate(server, data, random);
        NemesisSimulator.challenges(server, data, random);
        helper.assertTrue(data.inRole(CAMARILLA, Role.LEADER).isEmpty(), "no vampire takes the seat from the player");
        helper.assertTrue(data.inRole(CAMARILLA, Role.LIEUTENANT).size() == 2, "but the lesser seats stay filled");
        var views = com.epi0.vtm.nemesis.NemesisViews.build(server, player);
        var seat = views.courts().stream().filter(c -> c.faction().equals(CAMARILLA)).findFirst().orElseThrow().seats().get(0);
        helper.assertTrue(seat.ruler().isPresent() && seat.filled(), "the Courts tab shows you on the throne");

        Court.abdicate(player);
        helper.assertTrue(CourtData.get(server).reign(CAMARILLA) == null && Membership.of(player).standingWith(CAMARILLA) == 50, "abdicating costs standing");
        NemesisSimulator.succession(server, data);
        helper.assertTrue(CourtData.get(server).reign(CAMARILLA) == null, "and the throne is open to the simulation again");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the court

    @GameTest(template = "empty", batch = "court_seats")
    public static void rulersAppointDismissAndDecree(GameTestHelper helper) {
        var random = RandomSource.create(4L);
        ServerPlayer player = setup(helper, random);
        var server = server(helper);
        NemesisData data = NemesisManager.data(server);
        Reign reign = crown(helper, player);

        NemesisRecord lieutenant = data.inRole(CAMARILLA, Role.LIEUTENANT).get(0);
        helper.assertTrue(Court.dismiss(player, lieutenant.id) && lieutenant.role == Role.AGENT, "a ruler can dismiss");
        reign.autoAppoint = false;
        NemesisSimulator.succession(server, data);
        helper.assertTrue(data.inRole(CAMARILLA, Role.LIEUTENANT).size() == 1, "and the seat stays empty if they wish");

        NemesisRecord heir = data.inRole(CAMARILLA, Role.AGENT).stream().filter(r -> r.status == Status.ACTIVE).findFirst().orElseThrow();
        int influence = reign.influence;
        helper.assertTrue(Court.appoint(player, heir.id, Role.LIEUTENANT) && heir.role == Role.LIEUTENANT, "an appointment");
        helper.assertTrue(reign.influence == influence - Court.APPOINT_COST && heir.courtLoyalty >= 55, "costs influence, and buys some loyalty");
        NemesisRecord extra = data.inRole(CAMARILLA, Role.AGENT).stream().filter(r -> r.status == Status.ACTIVE).findFirst().orElseThrow();
        helper.assertTrue(!Court.appoint(player, extra.id, Role.LIEUTENANT), "but not into a full court");
        helper.assertTrue(!Court.appoint(player, data.inFaction(SABBAT).get(0).id, Role.ENFORCER), "nor from another sect");
        reign.influence = 0;
        helper.assertTrue(!Court.appoint(player, extra.id, Role.ENFORCER), "nor without influence");

        reign.influence = 100;
        helper.assertTrue(Court.toggleDecree(player, Decree.STRICT_MASQUERADE) && reign.has(Decree.STRICT_MASQUERADE) && reign.influence == 90, "a decree costs influence");
        helper.assertTrue(!Court.toggleDecree(player, Decree.OPEN_HUNTING), "contradictory decrees are refused");
        helper.assertTrue(Court.toggleDecree(player, Decree.TITHES), "a second is fine");
        helper.assertTrue(!Court.toggleDecree(player, Decree.PATRONAGE), "a third is not");
        helper.assertTrue(Court.toggleDecree(player, Decree.TITHES) && !reign.has(Decree.TITHES) && reign.influence == 80, "revoking is free");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "court_income")
    public static void citiesPayTributeAndDecreesBendTheWorld(GameTestHelper helper) {
        var random = RandomSource.create(5L);
        ServerPlayer player = setup(helper, random);
        var server = server(helper);
        NemesisData data = NemesisManager.data(server);
        Reign reign = crown(helper, player);

        City layout = new City(UUID.randomUUID(), "Tributeton", Level.OVERWORLD, helper.absolutePos(new BlockPos(4, 2, 4)), CAMARILLA);
        City city = CityRegistry.register(helper.getLevel(), layout.center, "Tributeton", CAMARILLA, CityRegistry.write(layout));
        city.owner = CAMARILLA;
        city.strength = 50;
        reign.influence = 0;
        data.steps = 3;
        Court.step(server, random);
        helper.assertTrue(reign.influence >= 1 + 50 / 25, "a step brings income: " + reign.influence);
        data.steps = 3; // the next step is a fourth step: tribute accrues
        Court.step(server, random);
        data.steps = 4;
        Court.step(server, random);
        helper.assertTrue(reign.tribute >= 1, "and tribute: " + reign.tribute);
        int xp = VampireData.of(player).experience();
        int tribute = reign.tribute;
        helper.assertTrue(Court.collect(player) == tribute && VampireData.of(player).experience() == xp + tribute, "which the ruler collects as experience");
        helper.assertTrue(Court.collect(player) == 0, "once");

        helper.assertTrue(Court.masquerade(server, helper.getLevel().registryAccess(), CAMARILLA), "the Camarilla keep the Masquerade");
        reign.gain(100);
        Court.toggleDecree(player, Decree.OPEN_HUNTING);
        helper.assertTrue(!Court.masquerade(server, helper.getLevel().registryAccess(), CAMARILLA), "until their ruler decrees open hunting");
        int hold = city.strength;
        data.steps = 8;
        Court.step(server, random);
        helper.assertTrue(city.strength == hold - 1, "which costs the cities their hold");
        Court.toggleDecree(player, Decree.OPEN_HUNTING);
        Court.toggleDecree(player, Decree.WAR_FOOTING);
        helper.assertTrue(Court.warMultiplier(server, CAMARILLA) == 3.0 && Court.raidMultiplier(server, CAMARILLA) == 2.0, "war footing means more war");
        Court.toggleDecree(player, Decree.WAR_FOOTING);
        Court.toggleDecree(player, Decree.PEACE);
        helper.assertTrue(Court.warMultiplier(server, CAMARILLA) == 0.0 && Court.raidMultiplier(server, CAMARILLA) == 0.0, "peace means none");
        helper.assertTrue(Court.masquerade(server, helper.getLevel().registryAccess(), SABBAT) == false, "other sects are unaffected");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "court_petition")
    public static void petitionsAreRaisedAndAnswered(GameTestHelper helper) {
        var random = RandomSource.create(6L);
        ServerPlayer player = setup(helper, random);
        var server = server(helper);
        NemesisData data = NemesisManager.data(server);
        Reign reign = crown(helper, player);
        City layout = new City(UUID.randomUUID(), "Petitionford", Level.OVERWORLD, helper.absolutePos(new BlockPos(4, 2, 4)), CAMARILLA);
        City city = CityRegistry.register(helper.getLevel(), layout.center, "Petitionford", CAMARILLA, CityRegistry.write(layout));
        city.owner = CAMARILLA;
        long now = helper.getLevel().getGameTime();

        java.util.Set<Petition.Type> seen = new java.util.HashSet<>();
        for (int i = 0; i < 40 && seen.size() < Petition.Type.values().length; i++) {
            reign.petitions.clear();
            Court.spawnPetition(server, data, reign, now, random).ifPresent(p -> seen.add(p.type()));
        }
        helper.assertTrue(seen.size() == Petition.Type.values().length, "every kind of petition turns up: " + seen);

        // Tribunal: punish.
        reign.petitions.clear();
        NemesisRecord accused = data.inRole(CAMARILLA, Role.ENFORCER).get(0);
        Petition tribunal = new Petition(UUID.randomUUID(), Petition.Type.TRIBUNAL, now, List.of(accused.id), List.of(accused.name));
        reign.petitions.add(tribunal);
        helper.assertTrue(Court.resolve(player, tribunal.id(), 0) && accused.role == Role.AGENT && reign.petitions.isEmpty(), "punishing a tribunal subject dismisses them");

        // Fortify: costs influence, strengthens the city; refused without influence.
        city.strength = 40;
        Petition fortify = new Petition(UUID.randomUUID(), Petition.Type.FORTIFY, now, List.of(city.id), List.of(city.name));
        reign.petitions.add(fortify);
        reign.influence = 3;
        helper.assertTrue(!Court.resolve(player, fortify.id(), 0), "you cannot send aid you cannot pay for");
        reign.influence = 20;
        helper.assertTrue(Court.resolve(player, fortify.id(), 0) && city.strength == 55 && reign.influence == 12, "aid costs 8 and adds 15");

        // Envoy: accepting makes a truce that stops the war.
        Petition envoy = new Petition(UUID.randomUUID(), Petition.Type.ENVOY, now, List.of(), List.of("faction.vtm.sabbat", SABBAT.toString()));
        reign.petitions.add(envoy);
        helper.assertTrue(Court.resolve(player, envoy.id(), 0) && Court.truce(server, CAMARILLA, SABBAT), "a truce is made");
        helper.assertTrue(Court.truce(server, SABBAT, CAMARILLA), "which binds both ways");
        for (int i = 0; i < 300; i++) {
            Court.truce(server, SABBAT, CAMARILLA);
        }
        city.owner = CAMARILLA;
        for (int i = 0; i < 200; i++) {
            com.epi0.vtm.city.Territory.war(server, data, CityData.get(server), random, 50.0);
        }
        helper.assertTrue(city.owner.equals(CAMARILLA), "the Sabbat do not attack a city of a faction they have a truce with");

        // Sanctuary: a new recruit.
        int before = data.inFaction(CAMARILLA).size();
        Petition sanctuary = new Petition(UUID.randomUUID(), Petition.Type.SANCTUARY, now, List.of(), List.of("faction.vtm.sabbat", SABBAT.toString(), "Mara Quill"));
        reign.petitions.add(sanctuary);
        helper.assertTrue(Court.resolve(player, sanctuary.id(), 0) && data.inFaction(CAMARILLA).size() == before + 1, "shelter brings a new member");

        // Quarrel: backing one side shifts loyalty.
        var pair = data.inRole(CAMARILLA, Role.LIEUTENANT);
        pair.forEach(r -> r.courtLoyalty = 50);
        Petition quarrel = new Petition(UUID.randomUUID(), Petition.Type.QUARREL, now, List.of(pair.get(0).id, pair.get(1).id), List.of(pair.get(0).name, pair.get(1).name));
        reign.petitions.add(quarrel);
        Court.resolve(player, quarrel.id(), 0);
        helper.assertTrue(pair.get(0).courtLoyalty == 60 && pair.get(1).courtLoyalty == 40, "the favoured gain loyalty and the other loses it");

        // Unanswered petitions lapse to the mild answer.
        Petition old = new Petition(UUID.randomUUID(), Petition.Type.FORTIFY, now - Petition.LIFETIME_TICKS - 10, List.of(city.id), List.of(city.name));
        reign.petitions.add(old);
        int hold = city.strength;
        data.steps = 1;
        Court.step(server, random);
        helper.assertTrue(reign.petitions.stream().noneMatch(p -> p.id().equals(old.id())) && city.strength == hold - 8, "ignored petitions are answered for you, mildly");
        helper.succeed();
    }

    // ---------------------------------------------------------------- rebellion

    @GameTest(template = "empty", batch = "court_revolt", timeoutTicks = 200)
    public static void neglectedCourtsRebelAndRulersCanFall(GameTestHelper helper) {
        var random = RandomSource.create(7L);
        // A test world is young; rebellions are timed in days, so start the clock well past two of them.
        ((net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData()).setGameTime(500000L);
        ServerPlayer player = setup(helper, random);
        var server = server(helper);
        NemesisData data = NemesisManager.data(server);
        Reign reign = crown(helper, player);
        reign.autoAppoint = true;
        NemesisRecord rebel = data.inRole(CAMARILLA, Role.LIEUTENANT).get(0);
        rebel.courtLoyalty = 5;
        rebel.ambition = 95;
        for (int i = 0; i < 300 && rebel.revoltSince == 0; i++) {
            data.steps = i + 1;
            Court.step(server, random);
            rebel.courtLoyalty = 5;
        }
        helper.assertTrue(rebel.revoltSince > 0, "a disloyal, ambitious lieutenant eventually rebels");
        helper.assertTrue(rebel.feud(player.getUUID()).bloodHunt && rebel.feud(player.getUUID()).enmity >= 70, "and hunts the ruler");
        helper.assertTrue(data.chronicle().stream().anyMatch(c -> c.key().equals("rebellion") && c.publicNews()), "publicly");

        // Beating the rebel ends it.
        rebel.feud(player.getUUID()).defeats++;
        data.steps = 1;
        Court.step(server, random);
        helper.assertTrue(rebel.revoltSince == 0 && rebel.courtLoyalty == 50 && CourtData.get(server).reign(CAMARILLA) != null, "a beaten rebel is a loyal one");
        helper.assertTrue(data.chronicle().stream().anyMatch(c -> c.key().equals("rebellion_crushed")), "which is chronicled");

        // Leaving a rebellion alone costs the throne.
        rebel.revoltSince = helper.getLevel().getGameTime() - Court.REVOLT_TICKS - 5;
        rebel.revoltBase = rebel.feud(player.getUUID()).defeats + rebel.feud(player.getUUID()).spared;
        data.steps = 1;
        Court.step(server, random);
        helper.assertTrue(CourtData.get(server).reign(CAMARILLA) == null, "the ruler is deposed");
        helper.assertTrue(rebel.role == Role.LEADER, "by the rebel, who takes the throne");
        helper.assertTrue(Membership.of(player).standingWith(CAMARILLA) <= 30 && Membership.of(player).rank() == 1, "the ruler loses standing and rank");
        helper.assertTrue(data.chronicle().stream().anyMatch(c -> c.key().equals("deposed") && c.publicNews()), "and the world hears");
        helper.succeed();
    }

    // ---------------------------------------------------------------- conversation, views, persistence

    @GameTest(template = "empty", batch = "court_talk")
    public static void subjectsAddressTheirRuler(GameTestHelper helper) {
        var random = RandomSource.create(8L);
        ServerPlayer player = setup(helper, random);
        helper.setDayTime(18000);
        Reign reign = crown(helper, player);
        NemesisRecord subject = NemesisManager.data(server(helper)).inRole(CAMARILLA, Role.ENFORCER).get(0);
        var body = NemesisManager.manifest(helper.getLevel(), subject, VtmGameTests.at(helper, 4.5, 2, 6.5));
        subject.courtLoyalty = 30;
        helper.assertTrue(DialogueManager.open(player, body), "a subject will talk to the ruler");
        helper.assertTrue(DialogueManager.session(player).tree().equals(VtmMod.id("court_subject")), "in the way of a subject");
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.c_oath"));
        helper.assertTrue(subject.courtLoyalty == 38, "reminding them of their oath firms their loyalty");
        DialogueManager.end(player);
        Court.abdicate(player);
        helper.assertTrue(!DialogueManager.open(player, body) || !DialogueManager.session(player).tree().equals(VtmMod.id("court_subject")), "and not to anyone else");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "court_view")
    public static void theCourtScreenShowsOnlyWhatMatters(GameTestHelper helper) {
        var random = RandomSource.create(9L);
        ServerPlayer player = setup(helper, random);
        var server = server(helper);
        var view = Court.view(server, player);
        helper.assertTrue(!view.ruling() && view.block().equals(java.util.Optional.of("throne_held")) && view.seats().isEmpty(), "before: what stands in the way");
        Reign reign = crown(helper, player);
        reign.petitions.add(new Petition(UUID.randomUUID(), Petition.Type.FORTIFY, helper.getLevel().getGameTime(), List.of(), List.of("X")));
        Court.toggleDecree(player, Decree.TITHES);
        view = Court.view(server, player);
        helper.assertTrue(view.ruling() && view.seats().size() == 5 && view.decrees().equals(List.of("tithes")) && view.petitions().size() == 1, "after: the court");
        helper.assertTrue(view.seats().stream().allMatch(s -> s.id().isPresent()), "with every seat filled");
        helper.assertTrue(!view.candidates().isEmpty() && view.candidates().stream().allMatch(c -> NemesisManager.data(server).get(c.id()).role == Role.AGENT), "and agents who could be raised");

        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        var snap = com.epi0.vtm.nemesis.NemesisViews.build(server, player);
        com.epi0.vtm.network.NemesisViewsPayload.STREAM_CODEC.encode(buf, new com.epi0.vtm.network.NemesisViewsPayload(snap));
        var decoded = com.epi0.vtm.network.NemesisViewsPayload.STREAM_CODEC.decode(buf).snapshot();
        helper.assertTrue(decoded.court().isPresent() && decoded.court().get().equals(snap.court().get()), "the court survives the wire");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "court_save")
    public static void reignsSurviveASaveAndReload(GameTestHelper helper) {
        var random = RandomSource.create(10L);
        ServerPlayer player = setup(helper, random);
        var server = server(helper);
        Reign reign = crown(helper, player);
        reign.gain(40);
        reign.tribute = 7;
        reign.decrees.add(Decree.WAR_FOOTING);
        reign.truces.put(SABBAT.toString(), 99999L);
        reign.petitions.add(new Petition(UUID.randomUUID(), Petition.Type.ENVOY, 12L, List.of(UUID.randomUUID()), List.of("a", "b")));
        reign.autoAppoint = false;
        NemesisManager.data(server).holdThrone(ANARCH, 5000L);
        NemesisRecord r = NemesisManager.data(server).inRole(CAMARILLA, Role.ENFORCER).get(0);
        r.courtLoyalty = 17;
        r.revoltSince = 33L;

        CompoundTag tag = CourtData.get(server).save(new CompoundTag(), helper.getLevel().registryAccess());
        CourtData loaded = CourtData.load(tag, helper.getLevel().registryAccess());
        Reign copy = loaded.reign(CAMARILLA);
        helper.assertTrue(copy != null && copy.ruler.equals(player.getUUID()) && copy.influence == reign.influence && copy.tribute == 7, "the reign");
        helper.assertTrue(copy.has(Decree.WAR_FOOTING) && copy.truces.get(SABBAT.toString()) == 99999L && !copy.autoAppoint, "its decrees, truces and settings");
        helper.assertTrue(copy.petitions.size() == 1 && copy.petitions.get(0).type() == Petition.Type.ENVOY && copy.petitions.get(0).args().equals(List.of("a", "b")), "its petitions");

        NemesisData nem = NemesisManager.data(server);
        NemesisData nemLoaded = NemesisData.load(nem.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(nemLoaded.get(r.id).courtLoyalty == 17 && nemLoaded.get(r.id).revoltSince == 33L, "and its subjects' loyalty and revolts");
        helper.assertTrue(nemLoaded.throneHeld(ANARCH, 4000L) && !nemLoaded.throneHeld(ANARCH, 6000L), "and held thrones");
        helper.succeed();
    }
}
