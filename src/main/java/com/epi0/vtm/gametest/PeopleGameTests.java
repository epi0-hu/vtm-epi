package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.city.CityLife;
import com.epi0.vtm.city.CityRegistry;
import com.epi0.vtm.dialogue.DialogueManager;
import com.epi0.vtm.npc.Attitude;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Gossip;
import com.epi0.vtm.npc.Household;
import com.epi0.vtm.npc.Job;
import com.epi0.vtm.npc.NpcMemory;
import com.epi0.vtm.npc.Persona;
import com.epi0.vtm.registry.ModAttachments;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** The human side: who people are, who they live with, what they say about you, and what they do for you. */
@GameTestHolder(VtmMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class PeopleGameTests {

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(PeopleGameTests.class);
    }

    private static final ResourceLocation CAMARILLA = VtmMod.id("camarilla");

    private static final Persona ORDINARY = new Persona(Persona.Temperament.CURIOUS, Persona.Age.ADULT);

    private static City makeCity(GameTestHelper helper, String name) {
        City layout = new City(UUID.randomUUID(), name, Level.OVERWORLD, helper.absolutePos(new BlockPos(4, 2, 4)), CAMARILLA);
        for (int i = 0; i < 4; i++) {
            layout.homes.add(helper.absolutePos(new BlockPos(1 + i * 20, 2, 1))); // far apart: separate households
        }
        layout.homes.add(helper.absolutePos(new BlockPos(1, 2, 3))); // ...except these two
        layout.plaza = helper.absolutePos(new BlockPos(4, 2, 4));
        CityData.get(helper.getLevel().getServer()).clear();
        return CityRegistry.register(helper.getLevel(), layout.center, name, CAMARILLA, CityRegistry.write(layout));
    }

    private static CivilianNpc settle(GameTestHelper helper, City city, int bed, Job job, Persona persona) {
        CivilianNpc npc = CityLife.spawn(helper.getLevel(), city, city.homes.get(bed), city.homes.get(bed), job, null);
        npc.usePersona(persona);
        return npc;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return NemesisGameTests.player(helper);
    }

    // ---------------------------------------------------------------- who people are

    @GameTest(template = "empty", batch = "people_persona")
    public static void everyoneIsSomeoneAndStaysThem(GameTestHelper helper) {
        Set<Persona.Temperament> seen = EnumSet.noneOf(Persona.Temperament.class);
        Set<Persona.Age> ages = EnumSet.noneOf(Persona.Age.class);
        for (int i = 0; i < 300; i++) {
            UUID id = UUID.randomUUID();
            Persona p = Persona.of(id);
            helper.assertTrue(p.equals(Persona.of(id)), "the same person every time");
            seen.add(p.temperament());
            ages.add(p.age());
        }
        helper.assertTrue(seen.size() == Persona.Temperament.values().length && ages.size() == 3, "a spread of temperaments and ages: " + seen + ages);
        helper.assertTrue(new Persona(Persona.Temperament.SUSPICIOUS, Persona.Age.ADULT).scaleRapport(-1) == -2
                && new Persona(Persona.Temperament.CHEERFUL, Persona.Age.ADULT).scaleRapport(-1) == -1, "the suspicious take offence easily");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "people_attitude")
    public static void temperamentShapesHowPeopleTakeToYou(GameTestHelper helper) {
        City city = makeCity(helper, "Temperford");
        ServerPlayer player = player(helper);
        CivilianNpc nervous = settle(helper, city, 0, Job.RESIDENT, new Persona(Persona.Temperament.NERVOUS, Persona.Age.ADULT));
        CivilianNpc brave = settle(helper, city, 1, Job.RESIDENT, new Persona(Persona.Temperament.BRAVE, Persona.Age.ADULT));
        CivilianNpc plain = settle(helper, city, 2, Job.RESIDENT, ORDINARY);
        city.addHeat(player.getUUID(), 30);
        helper.assertTrue(nervous.attitudeTo(player) == Attitude.WARY && brave.attitudeTo(player) == Attitude.NEUTRAL && plain.attitudeTo(player) == Attitude.NEUTRAL,
                "at 30 only the nervous are afraid");
        city.addHeat(player.getUUID(), 20);
        helper.assertTrue(plain.attitudeTo(player) == Attitude.WARY && brave.attitudeTo(player) == Attitude.NEUTRAL, "at 50 so is everyone but the brave");
        city.addHeat(player.getUUID(), 10);
        helper.assertTrue(brave.attitudeTo(player) == Attitude.WARY, "and at 60 even they");
        city.heat.clear();

        CivilianNpc devout = settle(helper, city, 3, Job.RESIDENT, new Persona(Persona.Temperament.DEVOUT, Persona.Age.ELDER));
        helper.assertTrue(devout.attitudeTo(player) == Attitude.NEUTRAL, "the devout have no quarrel with a soul in order");
        player.setData(ModAttachments.VAMPIRE, VampireData.of(player).withHumanity(2));
        helper.assertTrue(devout.attitudeTo(player) == Attitude.WARY && plain.attitudeTo(player) == Attitude.NEUTRAL,
                "...but feel it when a soul has slipped far");
        helper.succeed();
    }

    // ---------------------------------------------------------------- families

    @GameTest(template = "empty", batch = "people_family")
    public static void householdsShareANameAndAGrief(GameTestHelper helper) {
        City city = makeCity(helper, "Kinford");
        ServerPlayer player = player(helper);
        CivilianNpc first = settle(helper, city, 0, Job.RESIDENT, new Persona(Persona.Temperament.CURIOUS, Persona.Age.ELDER));
        CivilianNpc second = settle(helper, city, 4, Job.RESIDENT, new Persona(Persona.Temperament.CURIOUS, Persona.Age.YOUNG));
        CivilianNpc stranger = settle(helper, city, 1, Job.RESIDENT, ORDINARY);
        helper.assertTrue(Household.surnameOf(first).equals(Household.surnameOf(second)) && !Household.surnameOf(first).isEmpty(),
                "people under one roof share a surname: " + first.getName().getString() + " / " + second.getName().getString());
        helper.assertTrue(Household.members(first).contains(second) && !Household.members(first).contains(stranger), "and are each other's household");
        helper.assertTrue(Household.relation(second, first).equals("grandparent") && Household.relation(first, second).equals("grandchild"),
                "an elder and a child are grandparent and grandchild");

        second.hurt(helper.getLevel().damageSources().playerAttack(player), 1F);
        helper.assertTrue(first.memory().isThreat(player.getUUID()) && !stranger.memory().isThreat(player.getUUID()), "a family hears at once what happens to one of its own");

        second.die(helper.getLevel().damageSources().playerAttack(player));
        helper.assertTrue(first.memory().hasFlag(player.getUUID(), "kin_slain") && first.memory().rapport(player.getUUID()) <= -10,
                "the survivor never forgets who killed their kin");
        helper.assertTrue(first.attitudeTo(player) == Attitude.WARY, "and is afraid of them");
        helper.assertTrue(DialogueManager.open(player, first), "but will speak");
        helper.assertTrue(DialogueManager.session(player).tree().equals(VtmMod.id("civilian_grieving")), "in grief");
        helper.succeed();
    }

    // ---------------------------------------------------------------- gossip

    @GameTest(template = "empty", batch = "people_gossip")
    public static void aCityLearnsWhatAPlayerIs(GameTestHelper helper) {
        City city = makeCity(helper, "Whisperford");
        ServerPlayer player = player(helper);
        CivilianNpc victim = settle(helper, city, 0, Job.RESIDENT, ORDINARY);
        CivilianNpc listener = settle(helper, city, 1, Job.RESIDENT, ORDINARY);
        CivilianNpc friend = settle(helper, city, 2, Job.RESIDENT, ORDINARY);
        CivilianNpc other = settle(helper, city, 3, Job.RESIDENT, ORDINARY);
        long now = helper.getLevel().getGameTime();

        victim.memory().remember(player.getUUID(), NpcMemory.Event.WITNESSED_FEEDING, now);
        int heat = city.heatOf(player.getUUID());
        Gossip.exchange(victim, listener, now);
        helper.assertTrue(listener.memory().hasFlag(player.getUUID(), "rumour") && !listener.memory().isThreat(player.getUUID()),
                "the listener hears a rumour, having seen nothing");
        helper.assertTrue(listener.attitudeTo(player) == Attitude.WARY, "and is wary of the player");
        helper.assertTrue(city.heatOf(player.getUUID()) > heat, "the city grows hotter for it");

        friend.memory().adjustRapport(player.getUUID(), 4, now);
        Gossip.exchange(friend, other, now);
        helper.assertTrue(other.memory().rapport(player.getUUID()) == 1 && other.memory().hasFlag(player.getUUID(), "recommended"),
                "a friend's good word moves a neighbour a step");
        Gossip.exchange(friend, other, now);
        helper.assertTrue(other.memory().rapport(player.getUUID()) == 1, "but only once");
        helper.succeed();
    }

    // ---------------------------------------------------------------- trades

    @GameTest(template = "empty", batch = "people_jobs")
    public static void priestsClerksAndDetectivesHaveTheirOwnWords(GameTestHelper helper) {
        City city = makeCity(helper, "Tradeford");
        ServerPlayer player = player(helper);
        player.setData(ModAttachments.VAMPIRE, VampireData.of(player).withHumanity(5));
        CivilianNpc priest = settle(helper, city, 0, Job.PRIEST, ORDINARY);
        CivilianNpc clerk = settle(helper, city, 1, Job.CLERK, ORDINARY);
        CivilianNpc detective = settle(helper, city, 2, Job.DETECTIVE, ORDINARY);
        helper.setDayTime(3000);

        helper.assertTrue(DialogueManager.open(player, priest) && DialogueManager.session(player).tree().equals(VtmMod.id("civilian_priest")), "the priest has a conversation");
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.priest.opt.confess"));
        helper.assertTrue(VampireData.of(player).humanity() == 6, "confession lightens the soul");
        DialogueManager.end(player);
        DialogueManager.open(player, priest);
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.priest.opt.confess"));
        helper.assertTrue(VampireData.of(player).humanity() == 6, "but only once a day");
        DialogueManager.end(player);
        helper.setDayTime(3000 + 24000);
        DialogueManager.open(player, priest);
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.priest.opt.confess"));
        helper.assertTrue(VampireData.of(player).humanity() == 7, "and again the next day");
        DialogueManager.end(player);

        helper.assertTrue(DialogueManager.open(player, clerk) && DialogueManager.session(player).tree().equals(VtmMod.id("civilian_clerk")), "the clerk has one");
        DialogueManager.end(player);

        city.addHeat(player.getUUID(), 40);
        helper.assertTrue(DialogueManager.open(player, detective) && DialogueManager.session(player).tree().equals(VtmMod.id("civilian_detective")), "so does the detective");
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.detective.opt.cooperate"));
        helper.assertTrue(city.heatOf(player.getUUID()) == 32, "cooperating takes the heat down: " + city.heatOf(player.getUUID()));
        helper.succeed();
    }

    // ---------------------------------------------------------------- the herd

    @GameTest(template = "empty", batch = "people_herd")
    public static void friendsBecomeVesselsAndVesselsBecomeGhouls(GameTestHelper helper) {
        City city = makeCity(helper, "Herdford");
        ServerPlayer player = player(helper);
        player.setData(ModAttachments.VAMPIRE, VampireData.of(player).withBlood(5F));
        CivilianNpc friend = settle(helper, city, 0, Job.RESIDENT, ORDINARY);
        helper.setDayTime(3000);
        friend.memory().adjustRapport(player.getUUID(), 4, 0);

        DialogueManager.open(player, friend);
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.vessel_ask"));
        helper.assertTrue(!friend.memory().hasFlag(player.getUUID(), "vessel"), "a friend is not yet that close");
        friend.memory().adjustRapport(player.getUUID(), 2, 0);
        DialogueManager.end(player);
        DialogueManager.open(player, friend);
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.vessel_ask"));
        helper.assertTrue(friend.memory().hasFlag(player.getUUID(), "vessel"), "but a close one agrees");

        DialogueManager.open(player, friend);
        float before = VampireData.of(player).blood();
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.vessel_drink"));
        helper.assertTrue(VampireData.of(player).blood() == before + 2F, "a vessel gives blood freely");
        DialogueManager.end(player);
        DialogueManager.open(player, friend);
        float after = VampireData.of(player).blood();
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.vessel_drink"));
        helper.assertTrue(VampireData.of(player).blood() == after, "but only once a day");
        DialogueManager.end(player);

        DialogueManager.open(player, friend);
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.ghoul_offer"));
        helper.assertTrue(friend.ghoulOwner() == null, "not before the trust is deep enough");
        friend.memory().adjustRapport(player.getUUID(), 3, 0);
        DialogueManager.end(player);
        DialogueManager.open(player, friend);
        float pool = VampireData.of(player).blood();
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.ghoul_offer"));
        helper.assertTrue(player.getUUID().equals(friend.ghoulOwner()), "then they become the player's ghoul");
        helper.assertTrue(VampireData.of(player).blood() == pool - 2F, "for a price in vitae");
        helper.assertTrue(friend.getMaxHealth() == 30F && friend.attitudeTo(player) == Attitude.FRIENDLY, "stronger, and devoted");
        DialogueManager.end(player);
        helper.assertTrue(DialogueManager.open(player, friend) && DialogueManager.session(player).tree().equals(VtmMod.id("civilian_ghoul")), "with a manner of their own");

        var tag = new net.minecraft.nbt.CompoundTag();
        friend.addAdditionalSaveData(tag);
        CivilianNpc copy = com.epi0.vtm.npc.NpcSpawner.civilian(helper.getLevel(), friend.position());
        copy.readAdditionalSaveData(tag);
        helper.assertTrue(player.getUUID().equals(copy.ghoulOwner()), "and it is remembered");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the codex

    @GameTest(template = "empty", batch = "people_codex")
    public static void theCodexKnowsYourHerdAndYourExposure(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        City city = makeCity(helper, "Codexford");
        com.epi0.vtm.city.HerdData.get(server).clear();
        ServerPlayer player = player(helper);
        player.setData(ModAttachments.VAMPIRE, VampireData.of(player).withBlood(5F));
        CivilianNpc friend = settle(helper, city, 0, Job.RESIDENT, ORDINARY);
        helper.setDayTime(3000);
        friend.memory().adjustRapport(player.getUUID(), 6, 0);

        helper.assertTrue(com.epi0.vtm.nemesis.NemesisViews.build(server, player).herd().isEmpty(), "no herd yet");
        DialogueManager.open(player, friend);
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.vessel_ask"));
        DialogueManager.end(player);
        DialogueManager.open(player, friend);
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.vessel_drink"));
        DialogueManager.end(player);

        var view = com.epi0.vtm.nemesis.NemesisViews.build(server, player);
        helper.assertTrue(view.herd().size() == 1, "the vessel is on the roll");
        var entry = view.herd().get(0);
        helper.assertTrue(entry.name().equals(friend.getName().getString()) && !entry.ghoul() && !entry.dead() && entry.rapport() >= 6
                && entry.drankDay() == helper.getLevel().getDayTime() / 24000L && entry.place().isPresent() && entry.city().equals("Codexford"),
                "with their name, trust, the day you last drank, and where they are: " + entry);

        friend.memory().adjustRapport(player.getUUID(), 4, 0);
        DialogueManager.open(player, friend);
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.ghoul_offer"));
        DialogueManager.end(player);
        helper.assertTrue(com.epi0.vtm.nemesis.NemesisViews.build(server, player).herd().get(0).ghoul(), "a ghoul is listed as one");

        city.addExposure(player.getUUID(), 55);
        city.inquisitor = UUID.randomUUID();
        var hunting = com.epi0.vtm.nemesis.NemesisViews.build(server, player).hunting();
        helper.assertTrue(hunting.stream().anyMatch(h -> h.name().equals("Codexford") && h.exposure() == 55), "the city appears with its exposure: " + hunting);

        var codec = com.epi0.vtm.nemesis.NemesisViews.Snapshot.CODEC;
        var snapshot = com.epi0.vtm.nemesis.NemesisViews.build(server, player);
        var round = codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, snapshot).getOrThrow()).getOrThrow();
        helper.assertTrue(round.herd().equals(snapshot.herd()) && round.hunting().equals(snapshot.hunting()), "and both survive the trip to the client");

        friend.die(helper.getLevel().damageSources().generic());
        helper.assertTrue(com.epi0.vtm.nemesis.NemesisViews.build(server, player).herd().get(0).dead(), "and when they die, the roll says so");
        com.epi0.vtm.city.HerdData.get(server).clear();
        helper.succeed();
    }
}
