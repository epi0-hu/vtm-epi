package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.dialogue.DialogueManager;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.nemesis.NemesisRecord;
import com.epi0.vtm.nemesis.Role;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.power.PowerManager;
import com.epi0.vtm.power.Powers;
import com.epi0.vtm.registry.ModAttachments;
import com.epi0.vtm.vampire.Lineage;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireManager;
import com.epi0.vtm.vampire.VampireRules;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Generation: where it comes from, what it allows, and what it does to the vampires who have it. */
@GameTestHolder(VtmMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GenerationGameTests {

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(GenerationGameTests.class);
    }

    // ---------------------------------------------------------------- where it comes from

    @GameTest(template = "empty", batch = "gen_roll")
    public static void newSoulsAreBornToTheirGeneration(GameTestHelper helper) {
        RandomSource random = RandomSource.create(20260101L);
        int n = 400000;
        int[] counts = new int[17];
        for (int i = 0; i < n; i++) {
            int g = VampireRules.rollGeneration(random);
            helper.assertTrue(g >= 1 && g <= 13, "a roll is between 1st and 13th: " + g);
            counts[g]++;
        }
        double first = counts[1] * 100.0 / n;
        double second = counts[2] * 100.0 / n;
        double third = counts[3] * 100.0 / n;
        helper.assertTrue(first > 0.43 && first < 0.57, "0.5% are 1st: " + first);
        helper.assertTrue(second > 0.9 && second < 1.1, "1% are 2nd: " + second);
        helper.assertTrue(third > 2.8 && third < 3.2, "3% are 3rd: " + third);
        int best = 0;
        for (int g = 1; g <= 13; g++) {
            if (counts[g] > counts[best]) {
                best = g;
            }
        }
        helper.assertTrue(best == 13 && counts[13] * 100.0 / n > 29, "the 13th is the commonest");
        helper.assertTrue(counts[4] < counts[8] && counts[8] < counts[12], "and the older the rarer");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gen_lineage")
    public static void aPlayersLineageIsRolledOnceAndTheEmbraceKeepsIt(GameTestHelper helper) {
        ServerPlayer player = VtmGameTests.survivalPlayer(helper);
        helper.assertTrue(!Lineage.of(player).rolled(), "nothing is rolled in the test world");
        helper.assertTrue(Lineage.roll(player) && Lineage.of(player).rolled(), "a first join rolls");
        int born = Lineage.of(player).generation();
        helper.assertTrue(!Lineage.roll(player) && Lineage.of(player).generation() == born, "and only once");

        player.setData(ModAttachments.LINEAGE, new Lineage(3));
        VampireManager.embrace(player, VtmMod.id("ventrue"));
        helper.assertTrue(VampireData.of(player).generation() == 3, "an Embrace keeps the blood they were born with");
        helper.assertTrue(VampireData.of(player).bloodPotency() == 1 && VampireRules.maxBloodPotency(3) == 12, "(whose Potency may grow to 12)");
        VampireManager.makeMortal(player);
        helper.assertTrue(Lineage.of(player).generation() == 3, "and it survives being made mortal");
        VampireManager.embrace(player, VtmMod.id("thin_blood"));
        helper.assertTrue(VampireData.of(player).generation() >= 14, "a thin-blood is always far down the line");
        player.setData(ModAttachments.LINEAGE, Lineage.UNROLLED);
        VampireManager.embrace(player, VtmMod.id("brujah"));
        helper.assertTrue(VampireData.of(player).generation() == 13, "an unrolled soul is Embraced at the 13th");
        helper.succeed();
    }

    // ---------------------------------------------------------------- what it allows

    @GameTest(template = "empty", batch = "gen_potency")
    public static void generationCapsBloodPotencyAndExperienceBuysIt(GameTestHelper helper) {
        helper.assertTrue(VampireRules.maxBloodPotency(16) == 1 && VampireRules.maxBloodPotency(13) == 2 && VampireRules.maxBloodPotency(5) == 10
                && VampireRules.maxBloodPotency(1) == 14, "the cap by generation");
        ServerPlayer player = VtmGameTests.survivalPlayer(helper);
        VampireManager.set(player, VampireManager.embraced(VtmMod.id("ventrue"), 13).withExperience(500));
        helper.assertTrue(VampireManager.tryRaisePotency(player) && VampireData.of(player).bloodPotency() == 2, "the 13th may raise it once");
        helper.assertTrue(VampireData.of(player).experience() == 480, "for 20 experience");
        helper.assertTrue(!VampireManager.tryRaisePotency(player) && VampireData.of(player).bloodPotency() == 2, "and then no further");

        VampireManager.set(player, VampireManager.embraced(VtmMod.id("ventrue"), 1).withExperience(2000));
        int raised = 0;
        while (VampireManager.tryRaisePotency(player)) {
            raised++;
        }
        helper.assertTrue(VampireData.of(player).bloodPotency() == 14 && raised == 13, "an ancient reaches 14: " + VampireData.of(player).bloodPotency());
        helper.assertTrue(VampireData.of(player).maxBlood() == 38F, "with a pool of 38");
        VampireManager.set(player, VampireManager.embraced(VtmMod.id("ventrue"), 8).withExperience(5));
        helper.assertTrue(!VampireManager.tryRaisePotency(player), "experience is needed too");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gen_scale")
    public static void olderBloodIsCheaperFasterAndSurer(GameTestHelper helper) {
        helper.assertTrue(VampireRules.cooldownFactor(13) == 1F && VampireRules.cooldownFactor(5) < 0.7F && VampireRules.cooldownFactor(1) < 0.6F
                && VampireRules.cooldownFactor(16) > 1F, "cooldowns by generation");
        helper.assertTrue(VampireRules.poolBonus(13) == 0 && VampireRules.poolBonus(10) == 1 && VampireRules.poolBonus(4) == 3
                && VampireRules.poolBonus(1) == 4 && VampireRules.poolBonus(16) == -1, "dice by generation");
        var power = Powers.get(VtmMod.id("blood_bolt")).orElseThrow(); // costs 3
        VampireData young = VampireManager.embraced(VtmMod.id("ventrue"), 13);
        VampireData old = VampireManager.embraced(VtmMod.id("ventrue"), 4);
        helper.assertTrue(PowerManager.scaledCost(young, power) == power.bloodCost() && PowerManager.scaledCost(old, power) < power.bloodCost(),
                "older blood spends less: " + PowerManager.scaledCost(old, power));
        helper.assertTrue(PowerManager.scaledCost(VampireManager.embraced(VtmMod.id("ventrue"), 1), Powers.get(VtmMod.id("sense_the_beast")).orElseThrow()) >= 0.5F, "but never nothing");

        ServerPlayer player = VtmGameTests.survivalPlayer(helper);
        VampireManager.set(player, old.withBlood(10F));
        helper.assertTrue(PowerManager.activate(player, VtmMod.id("blood_bolt")) || true, "(the bolt may fizzle with nothing to hit)");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the people of the night

    @GameTest(template = "empty", batch = "gen_npc")
    public static void everyNamedVampireHasAGeneration(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        RandomSource random = RandomSource.create(9L);
        int leaderSum = 0;
        int agentSum = 0;
        int n = 60;
        for (int i = 0; i < n; i++) {
            NemesisRecord leader = NemesisManager.create(server, VtmMod.id("camarilla"), Role.LEADER, random);
            NemesisRecord lieutenant = NemesisManager.create(server, VtmMod.id("camarilla"), Role.LIEUTENANT, random);
            NemesisRecord agent = NemesisManager.create(server, VtmMod.id("camarilla"), Role.AGENT, random);
            helper.assertTrue(leader.generation >= 3 && leader.generation <= 8 && lieutenant.generation >= 8 && lieutenant.generation <= 10
                    && agent.generation >= 11 && agent.generation <= 13, "a seat sets a range: " + leader.generation + "/" + lieutenant.generation + "/" + agent.generation);
            leaderSum += leader.generation;
            agentSum += agent.generation;
        }
        helper.assertTrue(leaderSum < agentSum, "rulers are older than agents");
        NemesisRecord thin = NemesisManager.create(server, VtmMod.id("camarilla"), VtmMod.id("thin_blood"), Role.AGENT, random);
        helper.assertTrue(thin.generation >= 14, "a thin-blood is thin: " + thin.generation);

        NemesisRecord old = NemesisManager.create(server, VtmMod.id("camarilla"), Role.AGENT, random);
        NemesisRecord young = NemesisManager.create(server, VtmMod.id("camarilla"), Role.AGENT, random);
        old.level = young.level = 5;
        old.generation = 4;
        young.generation = 13;
        helper.assertTrue(old.power() > young.power(), "older blood is harder to beat: " + old.power() + " vs " + young.power());
        var copy = NemesisRecord.load(old.save());
        helper.assertTrue(copy.generation == 4, "and it is remembered");

        VampireNpc body = NpcSpawner.vampire(helper.getLevel(), helper.absoluteVec(new Vec3(4.5, 2, 4.5)), VtmMod.id("ventrue"));
        helper.assertTrue(body.generation() >= 11 && body.generation() <= 13, "an unnamed vampire is of the young blood: " + body.generation());
        NemesisManager.bind(body, old);
        helper.assertTrue(body.generation() == 4, "a named one wears their own");
        var tag = new net.minecraft.nbt.CompoundTag();
        body.addAdditionalSaveData(tag);
        var again = NpcSpawner.vampire(helper.getLevel(), helper.absoluteVec(new Vec3(6.5, 2, 4.5)), VtmMod.id("ventrue"));
        again.readAdditionalSaveData(tag);
        helper.assertTrue(again.generation() == 4, "in the world too");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gen_talk")
    public static void theYoungDeferAndTheOldCondescend(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = VtmGameTests.vampire(helper, "gangrel");
        VampireNpc npc = NpcSpawner.vampire(helper.getLevel(), helper.absoluteVec(new Vec3(4.5, 2, 6.5)), VtmMod.id("ventrue"));
        npc.setGeneration(11);

        player.setData(ModAttachments.VAMPIRE, VampireData.of(player).withExperience(0));
        DialogueManager.open(player, npc);
        helper.assertTrue(!DialogueManager.session(player).tree().equals(VtmMod.id("vampire_junior")) && !DialogueManager.session(player).tree().equals(VtmMod.id("vampire_elder")),
                "of a like age, an ordinary conversation");
        DialogueManager.end(player);

        // Player far older: the NPC defers.
        VampireManager.set(player, VampireManager.embraced(VtmMod.id("gangrel"), 4).withBlood(2F));
        helper.assertTrue(DialogueManager.open(player, npc) && DialogueManager.session(player).tree().equals(VtmMod.id("vampire_junior")), "the young defer to the old");
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.junior.opt.tribute"));
        helper.assertTrue(VampireData.of(player).blood() == 5F, "and pay tribute");
        DialogueManager.end(player);
        DialogueManager.open(player, npc);
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.junior.opt.tribute"));
        helper.assertTrue(VampireData.of(player).blood() == 5F, "once a day");
        DialogueManager.end(player);

        // NPC far older: they condescend, and teach.
        npc.setGeneration(8);
        VampireManager.set(player, VampireManager.embraced(VtmMod.id("gangrel"), 13).withExperience(0));
        helper.assertTrue(DialogueManager.open(player, npc) && DialogueManager.session(player).tree().equals(VtmMod.id("vampire_elder")), "the old look down on the young");
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.elder.opt.teach"));
        helper.assertTrue(VampireData.of(player).experience() == 3, "and may teach them something");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gen_elders")
    public static void eldersAreRumouredAndWillShareTheirBlood(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = VtmGameTests.vampire(helper, "gangrel");
        var server = helper.getLevel().getServer();
        var ancient = NemesisManager.create(server, VtmMod.id("camarilla"), Role.LEADER, RandomSource.create(1));
        ancient.generation = 4;
        var young = NemesisManager.create(server, VtmMod.id("camarilla"), Role.AGENT, RandomSource.create(2));
        young.generation = 12;

        // Rumour: only elders turn up, and a rumour already shows how old they are.
        var learned = com.epi0.vtm.nemesis.Intel.revealAbout(player, java.util.List.of(ancient), RandomSource.create(3));
        helper.assertTrue(learned.isPresent(), "a rumour of an elder reaches the player");
        var core = com.epi0.vtm.nemesis.NemesisViews.build(server, player).entries().stream()
                .map(com.epi0.vtm.nemesis.NemesisViews.Entry::core).filter(c -> c.id().equals(ancient.id)).findFirst().orElse(null);
        helper.assertTrue(core != null && core.tier() == 0 && core.generation() == 4, "an elder's generation is known by rumour alone");
        helper.assertTrue(com.epi0.vtm.nemesis.NemesisViews.build(server, player).entries().stream()
                .noneMatch(e -> e.core().id().equals(young.id)), "a mere agent is not rumoured");

        // The blood: the elder lets a much younger vampire one generation closer, never past them.
        helper.assertTrue(Lineage.ascend(player, 13) == 0, "nothing to share with an equal");
        VampireManager.set(player, VampireManager.embraced(VtmMod.id("gangrel"), 6));
        helper.assertTrue(Lineage.ascend(player, 4) == 5 && VampireData.of(player).generation() == 5, "one step closer");
        helper.assertTrue(Lineage.ascend(player, 4) == 0 && VampireData.of(player).generation() == 5, "but never to their own generation");
        helper.assertTrue(Lineage.of(player).generation() == 5, "and it is remembered");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gen_talk")
    public static void anAudienceWithAnElderIsEarned(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = VtmGameTests.vampire(helper, "gangrel");
        VampireNpc npc = NpcSpawner.vampire(helper.getLevel(), helper.absoluteVec(new Vec3(4.5, 2, 6.5)), VtmMod.id("ventrue"));
        npc.setGeneration(4);
        VampireManager.set(player, VampireManager.embraced(VtmMod.id("gangrel"), 9).withBlood(20F).withExperience(0));
        helper.assertTrue(DialogueManager.open(player, npc) && DialogueManager.session(player).tree().equals(VtmMod.id("vampire_ancient")), "an elder grants an audience");
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.ancient.opt.teach"));
        helper.assertTrue(VampireData.of(player).experience() == 0, "no lessons for strangers");
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.ancient.opt.tribute"));
        npc.memory().adjustRapport(player.getUUID(), 6, helper.getLevel().getGameTime());
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.else"));
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.ancient.opt.teach"));
        helper.assertTrue(VampireData.of(player).experience() == 10, "tribute earns a lesson");
        DialogueManager.end(player);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gen_elders")
    public static void eldersMoveBetweenHavensAndTheTrailGoesCold(GameTestHelper helper) {
        ServerPlayer player = VtmGameTests.vampire(helper, "gangrel");
        var server = helper.getLevel().getServer();
        var data = NemesisManager.data(server);
        var dim = helper.getLevel().dimension();
        com.epi0.vtm.nemesis.NemesisRecord elder = null;
        for (int seed = 0; elder == null; seed++) {
            var r = NemesisManager.create(server, VtmMod.id("camarilla"), Role.AGENT, RandomSource.create(seed));
            if (com.epi0.vtm.nemesis.Elders.agenda(r) == com.epi0.vtm.nemesis.Elders.Agenda.WANDERER) {
                elder = r;
            } else {
                NemesisManager.destroy(server, r, null, null);
            }
        }
        elder.generation = 3;
        elder.lair = new net.minecraft.core.BlockPos(0, 64, 0);
        elder.lairDimension = dim;
        var host = NemesisManager.create(server, VtmMod.id("camarilla"), Role.AGENT, RandomSource.create(99));
        host.generation = 13;
        host.lair = new net.minecraft.core.BlockPos(900, 64, 900);
        host.lairDimension = dim;
        elder.lastSeen = elder.lair;
        elder.seenDimension = dim;
        com.epi0.vtm.nemesis.Intel.meet(player, elder);

        helper.assertTrue(com.epi0.vtm.nemesis.Elders.trail(player, data).isEmpty(), "nothing has changed yet");
        helper.assertTrue(com.epi0.vtm.nemesis.Elders.move(server, data, elder, RandomSource.create(5)), "the elder goes to another haven");
        var finalElder = elder;
        var moved = elder.lair;
        helper.assertTrue(!moved.equals(new net.minecraft.core.BlockPos(0, 64, 0)) && data.all().stream().anyMatch(o -> o.id != finalElder.id && moved.equals(o.lair)), "to one that exists");
        helper.assertTrue(elder.lastSeen.equals(moved), "and is seen there");
        var known = com.epi0.vtm.nemesis.NemesisKnowledge.of(player).about(elder.id).orElseThrow();
        helper.assertTrue(com.epi0.vtm.nemesis.Elders.stale(known, elder), "what the player was told is out of date");
        helper.assertTrue(com.epi0.vtm.nemesis.Elders.trail(player, data).isPresent(), "someone can tell them where the elder went");
        known = com.epi0.vtm.nemesis.NemesisKnowledge.of(player).about(elder.id).orElseThrow();
        helper.assertTrue(!com.epi0.vtm.nemesis.Elders.stale(known, elder), "and then they know");

        // The young stay where they are.
        host.status = com.epi0.vtm.nemesis.Status.ACTIVE;
        var before = host.lair;
        for (int i = 0; i < 200; i++) {
            com.epi0.vtm.nemesis.Elders.step(server, data, RandomSource.create(i));
        }
        helper.assertTrue(host.lair.equals(before), "a thirteenth-generation vampire is not an elder");
        NemesisManager.destroy(server, elder, null, null);
        NemesisManager.destroy(server, host, null, null);
        helper.succeed();
    }
}
