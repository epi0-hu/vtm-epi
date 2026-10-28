package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.dialogue.DialogueManager;
import com.epi0.vtm.nemesis.ChronicleEntry;
import com.epi0.vtm.nemesis.DamageKind;
import com.epi0.vtm.nemesis.Feud;
import com.epi0.vtm.nemesis.Intel;
import com.epi0.vtm.nemesis.Mission;
import com.epi0.vtm.nemesis.MissionType;
import com.epi0.vtm.nemesis.NemesisData;
import com.epi0.vtm.nemesis.NemesisKnowledge;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.nemesis.NemesisRecord;
import com.epi0.vtm.nemesis.NemesisSimulator;
import com.epi0.vtm.nemesis.NemesisViews;
import com.epi0.vtm.nemesis.Relation;
import com.epi0.vtm.nemesis.Role;
import com.epi0.vtm.nemesis.Scar;
import com.epi0.vtm.nemesis.Status;
import com.epi0.vtm.nemesis.Trait;
import com.epi0.vtm.network.NemesisViewsPayload;
import com.epi0.vtm.npc.Attitude;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.registry.ModEntities;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The named vampires of the world. They share one world-wide record store, so every test here runs in a batch of its
 * own (tests in one batch run side by side) and starts by clearing it. The simulation is stepped by hand.
 */
@GameTestHolder(VtmMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NemesisGameTests {

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(NemesisGameTests.class);
    }

    private static final ResourceLocation CAMARILLA = VtmMod.id("camarilla");
    private static final ResourceLocation SABBAT = VtmMod.id("sabbat");
    private static final ResourceLocation ANARCH = VtmMod.id("anarch");

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static NemesisData fresh(GameTestHelper helper) {
        NemesisData data = NemesisManager.data(server(helper));
        data.records().clear();
        data.chronicle().clear();
        data.steps = 0;
        return data;
    }

    /** A new record with exactly the traits asked for. */
    private static NemesisRecord record(GameTestHelper helper, ResourceLocation faction, Role role, Trait... traits) {
        NemesisRecord r = NemesisManager.create(server(helper), faction, role, helper.getLevel().random);
        r.traits.clear();
        r.traits.addAll(List.of(traits));
        r.relations.clear();
        return r;
    }

    private static VampireNpc body(GameTestHelper helper, NemesisRecord r, double x, double z) {
        return NemesisManager.manifest(helper.getLevel(), r, VtmGameTests.at(helper, x, 2, z));
    }

    private static boolean logged(NemesisData data, String key) {
        return data.chronicle().stream().anyMatch(c -> c.key().equals(key));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = VtmGameTests.vampire(helper, "gangrel");
        player.moveTo(VtmGameTests.at(helper, 4.5, 2, 4.5));
        return player;
    }

    // ---------------------------------------------------------------- the living world

    @GameTest(template = "empty", batch = "nem_court")
    public static void courtsAreFoundedWithAHierarchy(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        RandomSource random = RandomSource.create(11L);
        NemesisSimulator.populate(server(helper), data, random);
        NemesisSimulator.succession(server(helper), data);
        for (ResourceLocation faction : List.of(CAMARILLA, ANARCH, SABBAT)) {
            helper.assertTrue(data.inFaction(faction).size() >= 7, faction + " has a full population");
            helper.assertTrue(data.inRole(faction, Role.LEADER).size() == 1, faction + " has one leader");
            helper.assertTrue(data.inRole(faction, Role.LIEUTENANT).size() == 2, faction + " has two lieutenants");
            helper.assertTrue(data.inRole(faction, Role.ENFORCER).size() == 3, faction + " has three enforcers");
        }
        helper.assertTrue(data.all().stream().map(r -> r.name).distinct().count() == data.all().size(), "names are unique");
        for (NemesisRecord r : data.all()) {
            helper.assertTrue(r.traits.stream().anyMatch(t -> t.kind() == Trait.Kind.STRENGTH), r.name + " has a strength");
            helper.assertTrue(r.traits.stream().anyMatch(t -> t.kind() == Trait.Kind.WEAKNESS), r.name + " has a weakness");
            for (Trait a : r.traits) {
                for (Trait b : r.traits) {
                    helper.assertTrue(!a.conflictsWith(b), r.name + " has contradictory traits " + a + "/" + b);
                }
            }
            helper.assertTrue(r.relations.stream().allMatch(rel -> data.get(rel.other()) != null
                    && data.get(rel.other()).related(r.id, rel.type().mirror())), r.name + "'s relations are mirrored");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_succession")
    public static void aFallenLeaderStartsAChainOfPromotions(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        RandomSource random = RandomSource.create(5L);
        NemesisSimulator.populate(server(helper), data, random);
        NemesisSimulator.succession(server(helper), data);
        NemesisRecord leader = data.inRole(CAMARILLA, Role.LEADER).get(0);
        List<UUID> lieutenants = data.inRole(CAMARILLA, Role.LIEUTENANT).stream().map(r -> r.id).toList();

        NemesisManager.destroy(server(helper), leader, null, null);
        helper.assertTrue(data.inRole(CAMARILLA, Role.LEADER).isEmpty(), "the throne is empty");
        helper.assertTrue(logged(data, "leader_destroyed"), "the chronicle records it");
        NemesisSimulator.succession(server(helper), data);
        helper.assertTrue(data.inRole(CAMARILLA, Role.LEADER).size() == 1, "someone takes the seat");
        helper.assertTrue(lieutenants.contains(data.inRole(CAMARILLA, Role.LEADER).get(0).id), "from the lieutenants");
        helper.assertTrue(data.inRole(CAMARILLA, Role.LIEUTENANT).size() == 2, "whose seat is refilled in turn");
        helper.assertTrue(data.inRole(CAMARILLA, Role.ENFORCER).size() == 3, "all the way down");
        helper.assertTrue(logged(data, "new_leader"), "and the new leader is announced");

        NemesisSimulator.populate(server(helper), data, random);
        helper.assertTrue(data.inFaction(CAMARILLA).size() >= 7, "fresh blood is recruited to replace the dead");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_coup", timeoutTicks = 100)
    public static void ambitiousLieutenantsOverthrowOrDie(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        RandomSource random = RandomSource.create(99L);
        NemesisSimulator.populate(server(helper), data, random);
        NemesisSimulator.succession(server(helper), data);
        for (int i = 0; i < 600 && !logged(data, "coup") && !logged(data, "coup_failed"); i++) {
            data.all().forEach(r -> {
                if (r.role == Role.LIEUTENANT) {
                    r.ambition = 100;
                }
            });
            NemesisSimulator.challenges(server(helper), data, random);
            NemesisSimulator.populate(server(helper), data, random);
            NemesisSimulator.succession(server(helper), data);
        }
        helper.assertTrue(logged(data, "coup") || logged(data, "coup_failed"), "an ambitious lieutenant eventually moves on the leader");
        helper.assertTrue(data.inRole(CAMARILLA, Role.LEADER).size() <= 1 && data.inRole(SABBAT, Role.LEADER).size() <= 1, "never two leaders");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_duel")
    public static void duelsFavourThePowerfulButNotAlways(GameTestHelper helper) {
        fresh(helper);
        NemesisRecord strong = record(helper, CAMARILLA, Role.LEADER, Trait.BRUTE);
        NemesisRecord weak = record(helper, SABBAT, Role.AGENT, Trait.GLASS_JAW);
        strong.level = 15;
        weak.level = 3;
        RandomSource random = RandomSource.create(3L);
        int strongWins = 0;
        for (int i = 0; i < 500; i++) {
            strongWins += NemesisSimulator.duel(strong, weak, random) ? 1 : 0;
        }
        helper.assertTrue(strongWins > 350 && strongWins < 500, "the stronger usually wins but not always: " + strongWins);

        weak.deaths = weak.resilience();
        NemesisSimulator.fall(server(helper), weak, strong, random, 0.0);
        helper.assertTrue(weak.status == Status.DESTROYED, "someone with no deaths left in them is finished");
        NemesisRecord fresh = record(helper, SABBAT, Role.AGENT);
        NemesisSimulator.fall(server(helper), fresh, strong, random, 0.0);
        helper.assertTrue(fresh.status == Status.TORPOR && fresh.deaths == 1 && fresh.wakeAt > helper.getLevel().getGameTime(),
                "otherwise the loser sleeps and wakes later");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_wake")
    public static void torporEndsAndTheWakerReturnsStronger(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        NemesisRecord r = record(helper, CAMARILLA, Role.AGENT);
        r.status = Status.TORPOR;
        r.wakeAt = helper.getLevel().getGameTime() + 100;
        int level = r.level;
        NemesisSimulator.wake(server(helper), data, helper.getLevel().getGameTime());
        helper.assertTrue(r.status == Status.TORPOR, "not yet");
        NemesisSimulator.wake(server(helper), data, helper.getLevel().getGameTime() + 200);
        helper.assertTrue(r.status == Status.ACTIVE && (r.level > level || r.xp > 0), "awake, and the better for it");
        helper.assertTrue(logged(data, "awoke"), "and it is chronicled");
        helper.succeed();
    }

    // ---------------------------------------------------------------- defeat

    @GameTest(template = "empty", batch = "nem_defeat")
    public static void defeatsAreSurvivedAndTeachLessons(GameTestHelper helper) {
        fresh(helper);
        ServerPlayer player = player(helper);
        NemesisRecord r = record(helper, SABBAT, Role.ENFORCER, Trait.PROUD); // PROUD never yields: it always escapes
        VampireNpc npc = body(helper, r, 6.5, 6.5);
        helper.assertTrue(NemesisManager.recordOf(npc) == r && r.entity != null, "the body is bound to the record");

        npc.hurt(helper.getLevel().damageSources().playerAttack(player), 10000F);
        helper.assertTrue(r.deaths == 1, "the first defeat is survived");
        helper.assertTrue(r.status == Status.TORPOR && r.wakeAt > helper.getLevel().getGameTime(), "they escape into torpor");
        helper.assertTrue(!npc.isAlive() || npc.isRemoved(), "and are gone from the world");
        helper.assertTrue(r.scars.contains(Scar.MAULED) && r.resistance(DamageKind.MELEE) == 1, "they keep a scar and learn from it");
        Feud feud = r.feud(player.getUUID());
        helper.assertTrue(feud.defeats == 1 && feud.enmity >= 25, "and hold a grudge");
        helper.assertTrue(NemesisKnowledge.of(player).about(r.id).isPresent(), "and the player knows who they are");
        helper.assertTrue(NemesisManager.epithet(r).equals(Optional.of("scar_mauled")), "they earn an epithet");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_final")
    public static void fireSunAndExhaustionAreFinal(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        ServerPlayer player = player(helper);
        NemesisRecord burned = record(helper, SABBAT, Role.ENFORCER, Trait.BRUTE);
        VampireNpc a = body(helper, burned, 6.5, 6.5);
        a.hurt(helper.getLevel().damageSources().inFire(), 10000F);
        helper.assertTrue(burned.status == Status.DESTROYED, "flame finishes a vampire");
        helper.assertTrue(logged(data, "destroyed"), "and the world hears of it");

        NemesisRecord hardened = record(helper, SABBAT, Role.ENFORCER, Trait.FIRE_HARDENED, Trait.PROUD);
        VampireNpc b = body(helper, hardened, 2.5, 6.5);
        b.hurt(helper.getLevel().damageSources().inFire(), 10000F);
        helper.assertTrue(hardened.status != Status.DESTROYED, "the fire-hardened can survive it");

        NemesisRecord spent = record(helper, SABBAT, Role.AGENT, Trait.BRUTE);
        VampireNpc c = body(helper, spent, 4.5, 6.5);
        spent.deaths = spent.resilience();
        c.hurt(helper.getLevel().damageSources().playerAttack(player), 10000F);
        helper.assertTrue(spent.status == Status.DESTROYED, "one defeat too many is final");
        helper.assertTrue(logged(data, "destroyed_by"), "credited to the victor");
        helper.assertTrue(VampireData.of(player).experience() > 0, "who earns experience for it");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_traits")
    public static void traitsChangeHowTheyFightAndAreLearnedByWatching(GameTestHelper helper) {
        fresh(helper);
        ServerPlayer player = player(helper);
        NemesisRecord plain = record(helper, CAMARILLA, Role.AGENT, Trait.SWIFT);
        NemesisRecord flammable = record(helper, CAMARILLA, Role.AGENT, Trait.FLAMMABLE);
        NemesisRecord hardened = record(helper, CAMARILLA, Role.AGENT, Trait.FIRE_HARDENED);
        for (NemesisRecord r : List.of(plain, flammable, hardened)) {
            r.level = 1;
        }
        VampireNpc a = body(helper, plain, 3.5, 6.5);
        VampireNpc b = body(helper, flammable, 5.5, 6.5);
        VampireNpc c = body(helper, hardened, 7.5, 6.5);
        for (VampireNpc npc : List.of(a, b, c)) {
            npc.setHealth(npc.getMaxHealth());
        }
        float before = a.getHealth();
        a.hurt(helper.getLevel().damageSources().onFire(), 4F);
        b.hurt(helper.getLevel().damageSources().onFire(), 4F);
        c.hurt(helper.getLevel().damageSources().onFire(), 4F);
        float plainLoss = before - a.getHealth();
        float flammableLoss = b.getMaxHealth() - b.getHealth();
        float hardenedLoss = c.getMaxHealth() - c.getHealth();
        helper.assertTrue(Math.abs(plainLoss - 4F) < 0.01F, "ordinary vampires take fire as it comes, lost " + plainLoss);
        helper.assertTrue(flammableLoss > plainLoss * 1.7F, "the flammable burn badly, lost " + flammableLoss);
        helper.assertTrue(hardenedLoss < plainLoss * 0.5F, "the fire-hardened barely notice, lost " + hardenedLoss);
        helper.assertTrue(NemesisKnowledge.of(player).about(flammable.id).map(k -> k.knows(Trait.FLAMMABLE)).orElse(false),
                "watching it work, the player learns the weakness");
        helper.assertTrue(NemesisKnowledge.of(player).about(plain.id).map(k -> !k.knows(Trait.SWIFT)).orElse(true),
                "but not what they have not seen");

        NemesisRecord strong = record(helper, CAMARILLA, Role.AGENT, Trait.BRUTE, Trait.IRON_WILL);
        strong.level = 1;
        VampireNpc brute = body(helper, strong, 1.5, 6.5);
        NemesisRecord slow = record(helper, CAMARILLA, Role.AGENT);
        slow.level = 1;
        VampireNpc ordinary = body(helper, slow, 8.5, 6.5);
        double withBrute = brute.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        strong.traits.remove(Trait.BRUTE);
        NemesisManager.refresh(brute, strong);
        double without = brute.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        helper.assertTrue(withBrute > without * 1.3, "brutes hit harder: " + withBrute + " vs " + without);
        helper.assertTrue(brute.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) >= 4, "iron will is armour");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the victor's verdict

    private static VampireNpc kneeling(GameTestHelper helper, NemesisRecord r, ServerPlayer victor, double x) {
        VampireNpc npc = body(helper, r, x, 6.5);
        npc.yieldTo(victor, helper.getLevel().getGameTime() + 900);
        return npc;
    }

    @GameTest(template = "empty", batch = "nem_verdict")
    public static void theVictorDecidesAKneelingVampiresFate(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        ServerPlayer player = player(helper);
        player.setData(com.epi0.vtm.registry.ModAttachments.VAMPIRE, VampireData.of(player).withBlood(10F));

        NemesisRecord doomed = record(helper, SABBAT, Role.AGENT);
        int xp = VampireData.of(player).experience();
        VampireNpc a = kneeling(helper, doomed, player, 2.5);
        NemesisManager.execute(player, a, doomed);
        helper.assertTrue(doomed.status == Status.DESTROYED && !a.isAlive(), "execution is final");
        helper.assertTrue(VampireData.of(player).experience() == xp + doomed.level, "and earns experience");

        NemesisRecord spared = record(helper, SABBAT, Role.AGENT);
        VampireNpc b = kneeling(helper, spared, player, 4.5);
        NemesisManager.spare(player, b, spared);
        Feud feud = spared.feud(player.getUUID());
        helper.assertTrue(feud.spared == 1 && feud.respect >= 30 && b.isRemoved(), "mercy earns respect, and they leave");
        helper.assertTrue(spared.status == Status.TORPOR, "to recover");

        NemesisRecord proud = record(helper, SABBAT, Role.AGENT);
        VampireNpc c = kneeling(helper, proud, player, 6.5);
        NemesisManager.humiliate(player, c, proud);
        helper.assertTrue(proud.feud(player.getUUID()).enmity >= 25 && proud.feud(player.getUUID()).respect == 0, "humiliation breeds hatred");

        NemesisRecord talker = record(helper, SABBAT, Role.LEADER, Trait.IRON_WILL);
        VampireNpc d = kneeling(helper, talker, player, 8.5);
        NemesisManager.interrogate(player, d, talker);
        helper.assertTrue(NemesisKnowledge.of(player).about(talker.id).map(k -> k.tier() >= 1).orElse(false), "they talk");
        helper.assertTrue(!logged(data, "bound"), "nothing was bound");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_bind")
    public static void bloodBondsMakeLoyalFollowers(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        ServerPlayer player = player(helper);
        player.setData(com.epi0.vtm.registry.ModAttachments.VAMPIRE, VampireData.of(player).withBlood(14F));

        NemesisRecord r = record(helper, SABBAT, Role.LIEUTENANT, Trait.GRATEFUL);
        VampireNpc npc = kneeling(helper, r, player, 3.5);
        float blood = VampireData.of(player).blood();
        helper.assertTrue(NemesisManager.bind(player, npc, r), "the bond takes");
        helper.assertTrue(r.status == Status.BOUND && player.getUUID().equals(r.boundTo) && r.role == Role.AGENT,
                "bound, and out of their court");
        helper.assertTrue(VampireData.of(player).blood() == blood - NemesisManager.BIND_COST, "at the cost of blood");
        helper.assertTrue(r.loyalty >= NemesisManager.STARTING_LOYALTY + 20, "a grateful one is more loyal");
        helper.assertTrue(player.getUUID().equals(npc.boundOwner()) && npc.attitudeTo(player) == Attitude.FRIENDLY, "friendly to their master");
        helper.assertTrue(NemesisKnowledge.of(player).about(r.id).map(k -> k.tier() == 2 && k.traits().contains("grateful")).orElse(false),
                "a thrall has no secrets from their master");

        for (int i = 0; i < NemesisManager.MAX_COTERIE - 1; i++) {
            NemesisRecord other = record(helper, SABBAT, Role.AGENT);
            VampireNpc o = kneeling(helper, other, player, 5.5 + i);
            helper.assertTrue(NemesisManager.bind(player, o, other), "bind " + i);
        }
        NemesisRecord fourth = record(helper, SABBAT, Role.AGENT);
        VampireNpc f = kneeling(helper, fourth, player, 8.5);
        helper.assertTrue(!NemesisManager.bind(player, f, fourth), "the coterie is full");
        helper.assertTrue(data.boundTo(player.getUUID()).size() == NemesisManager.MAX_COTERIE, "three at most");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_coterie")
    public static void errandsLoyaltyAndBetrayal(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        RandomSource random = RandomSource.create(21L);
        helper.setDayTime(18000);
        ServerPlayer player = player(helper);
        player.setData(com.epi0.vtm.registry.ModAttachments.VAMPIRE, VampireData.of(player).withBlood(14F));
        NemesisSimulator.populate(server(helper), data, random);

        NemesisRecord servant = record(helper, CAMARILLA, Role.AGENT);
        servant.level = 6;
        NemesisManager.bind(player, kneeling(helper, servant, player, 3.5), servant);

        // Tribute is delivered to a player who is here.
        helper.assertTrue(NemesisManager.assign(player, servant, MissionType.TRIBUTE, null, null), "sent on an errand");
        helper.assertTrue(servant.mission != null && NemesisManager.entityOf(server(helper), servant) == null, "and gone from the world");
        helper.assertTrue(!NemesisManager.assign(player, servant, MissionType.SPY, null, SABBAT), "one errand at a time");
        servant.mission = new Mission(MissionType.TRIBUTE, 0L, null, null);
        int xp = VampireData.of(player).experience();
        NemesisSimulator.resolveMissions(server(helper), data, helper.getLevel().getGameTime(), random);
        helper.assertTrue(servant.mission == null && servant.pendingTribute > 0, "the errand is done");
        NemesisManager.coterieTick(player);
        helper.assertTrue(VampireData.of(player).experience() > xp, "and the tribute paid");
        helper.assertTrue(NemesisManager.entityOf(server(helper), servant) != null, "the servant returns to their master's side by night");

        // Spying teaches the player about the target faction.
        NemesisRecord spy = record(helper, CAMARILLA, Role.AGENT);
        spy.level = 20;
        NemesisManager.bind(player, kneeling(helper, spy, player, 6.5), spy);
        helper.assertTrue(NemesisManager.assign(player, spy, MissionType.SPY, null, SABBAT), "spy on the Sabbat");
        spy.mission = new Mission(MissionType.SPY, 0L, null, SABBAT);
        int known = NemesisKnowledge.of(player).known().size();
        for (int i = 0; i < 20 && spy.mission != null; i++) {
            NemesisSimulator.resolveMissions(server(helper), data, helper.getLevel().getGameTime(), random);
        }
        helper.assertTrue(NemesisKnowledge.of(player).known().size() > known || spy.status != Status.BOUND, "the spy learns something, or is caught");

        // Hunting needs a target the player knows.
        NemesisRecord prey = record(helper, SABBAT, Role.ENFORCER);
        helper.assertTrue(!NemesisManager.assign(player, servant, MissionType.HUNT, prey.id, null), "you cannot hunt who you have never heard of");
        Intel.reveal(player, prey, random);
        helper.assertTrue(NemesisManager.assign(player, servant, MissionType.HUNT, prey.id, null), "but once you know them, you can");
        NemesisManager.recall(player, servant);
        helper.assertTrue(servant.mission == null, "and recall them");

        // Neglect breeds betrayal.
        servant.loyalty = 0;
        NemesisSimulator.betray(server(helper), servant);
        helper.assertTrue(servant.status == Status.ACTIVE && servant.boundTo == null, "the bond breaks");
        helper.assertTrue(servant.feud(player.getUUID()).enmity >= 80, "and turns to hatred");
        helper.assertTrue(logged(data, "betrayed"), "which is chronicled");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_tithe")
    public static void feedingBoundVampiresKeepsThemLoyal(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        ServerPlayer player = player(helper);
        player.setData(com.epi0.vtm.registry.ModAttachments.VAMPIRE, VampireData.of(player).withBlood(14F));
        NemesisRecord r = record(helper, CAMARILLA, Role.AGENT);
        NemesisManager.bind(player, kneeling(helper, r, player, 3.5), r);
        r.loyalty = 40;
        helper.assertTrue(NemesisManager.tithe(player, r) && r.loyalty == 55, "blood buys loyalty");
        for (int i = 0; i < 40; i++) {
            data.steps = 4L * (i + 1);
            NemesisSimulator.loyalty(server(helper), data, RandomSource.create(i));
        }
        helper.assertTrue(r.loyalty < 55 || r.status != Status.BOUND, "which drains away if neglected");
        NemesisManager.release(player, r);
        helper.assertTrue(r.status == Status.ACTIVE && r.boundTo == null, "and a bond can be broken by the master");
        helper.succeed();
    }

    // ---------------------------------------------------------------- vengeance

    private static void platform(GameTestHelper helper, int radius) {
        BlockPos centre = helper.absolutePos(new BlockPos(4, 1, 4));
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                helper.getLevel().setBlock(centre.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
    }

    @GameTest(template = "empty", batch = "nem_ambush", timeoutTicks = 400)
    public static void grudgesBecomeAmbushes(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        RandomSource random = RandomSource.create(8L);
        helper.setDayTime(18000);
        platform(helper, 50);
        ServerPlayer player = player(helper);
        NemesisSimulator.populate(server(helper), data, random);
        NemesisRecord foe = record(helper, SABBAT, Role.ENFORCER, Trait.PATIENT);
        foe.level = 6;
        foe.feud(player.getUUID()).enmity = 80;
        foe.feud(player.getUUID()).encounters = 1;
        foe.feud(player.getUUID()).hunt = 99; // the hunt is nearly done, so the first step settles it

        for (int i = 0; i < 80 && !NemesisManager.manifested(server(helper), foe); i++) {
            NemesisSimulator.grudges(server(helper), data, helper.getLevel().getGameTime(), random);
        }
        helper.assertTrue(NemesisManager.manifested(server(helper), foe), "a vampire with a grudge comes for the player");
        VampireNpc body = NemesisManager.entityOf(server(helper), foe);
        helper.assertTrue(body != null && body.getTarget() == player, "and means it");
        helper.assertTrue(body.distanceTo(player) >= 20, "from out of the dark");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(VampireNpc.class, player.getBoundingBox().inflate(70),
                v -> v.nemesisId() == null && v.getTarget() == player).isEmpty(), "with followers");
        helper.assertTrue(logged(data, "ambush"), "and it is chronicled");
        helper.assertTrue(foe.feud(player.getUUID()).hunt == 0, "the hunt starts over");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_day")
    public static void daylightKeepsAmbushersAway(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        RandomSource random = RandomSource.create(8L);
        helper.setDayTime(6000);
        ServerPlayer player = player(helper);
        NemesisRecord foe = record(helper, SABBAT, Role.ENFORCER);
        foe.feud(player.getUUID()).enmity = 100;
        for (int i = 0; i < 40; i++) {
            NemesisSimulator.grudges(server(helper), data, helper.getLevel().getGameTime(), random);
        }
        helper.assertTrue(foe.feud(player.getUUID()).hunt == 0 && !NemesisManager.manifested(server(helper), foe), "vampires do not hunt by day");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_bloodhunt")
    public static void outcastsHaveABloodHuntDeclaredOnThem(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        helper.setDayTime(6000);
        NemesisSimulator.populate(server(helper), data, RandomSource.create(2L));
        NemesisSimulator.succession(server(helper), data);
        ServerPlayer player = player(helper);
        SectManager.set(player, Membership.of(player).withStanding(SABBAT, SectManager.OUTCAST));
        NemesisSimulator.declareBloodHunts(server(helper), data, player);
        NemesisRecord leader = data.inRole(SABBAT, Role.LEADER).get(0);
        helper.assertTrue(leader.feud(player.getUUID()).bloodHunt && leader.feud(player.getUUID()).enmity >= 60, "the court answers the call");
        helper.assertTrue(data.inRole(SABBAT, Role.AGENT).stream().noneMatch(r -> r.feudOrNull(player.getUUID()) != null),
                "rank and file are not troubled");
        helper.assertTrue(data.inFaction(CAMARILLA).stream().noneMatch(r -> r.feudOrNull(player.getUUID()) != null),
                "and other sects are unaffected");
        helper.assertTrue(logged(data, "blood_hunt"), "chronicled");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_attitude")
    public static void grudgesAndMercyChangeAttitudes(GameTestHelper helper) {
        fresh(helper);
        ServerPlayer player = player(helper);
        NemesisRecord r = record(helper, CAMARILLA, Role.ENFORCER);
        VampireNpc npc = body(helper, r, 5.5, 6.5);
        helper.assertTrue(npc.attitudeTo(player) != Attitude.HOSTILE, "strangers are not enemies");
        r.feud(player.getUUID()).enmity = 60;
        helper.assertTrue(npc.attitudeTo(player) == Attitude.HOSTILE, "a deep grudge is hostility");
        npc.yieldTo(player, helper.getLevel().getGameTime() + 900);
        helper.assertTrue(npc.attitudeTo(player) == Attitude.NEUTRAL, "but not while they kneel");
        r.feud(player.getUUID()).enmity = 0;
        r.feud(player.getUUID()).respect = 60;
        npc.setState(com.epi0.vtm.npc.VtmNpc.State.NORMAL);
        helper.assertTrue(npc.attitudeTo(player) == Attitude.FRIENDLY, "respect is friendship");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the verdict, by conversation

    @GameTest(template = "empty", batch = "nem_talk")
    public static void kneelingVampiresAreJudgedInConversation(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        ServerPlayer player = player(helper);
        NemesisRecord r = record(helper, SABBAT, Role.AGENT, Trait.GRATEFUL);
        VampireNpc npc = kneeling(helper, r, player, 6.5);
        helper.assertTrue(DialogueManager.open(player, npc), "even an enemy talks while kneeling");
        helper.assertTrue(DialogueManager.session(player).tree().equals(VtmMod.id("nemesis_yield")), "the verdict tree applies");
        helper.assertTrue(npc.isYielding(), "and they stay on their knees while it goes on");
        DialogueManager.choose(player, VtmGameTests.option(player, "dialogue.vtm.opt.n_spare"));
        helper.assertTrue(r.feud(player.getUUID()).spared == 1 && npc.isRemoved(), "mercy is granted in conversation");
        helper.assertTrue(DialogueManager.session(player) == null, "and it ends the conversation");

        NemesisRecord bound = record(helper, SABBAT, Role.AGENT);
        VampireNpc other = kneeling(helper, bound, player, 2.5);
        NemesisManager.bind(player, other, bound);
        helper.assertTrue(DialogueManager.open(player, other), "a bound vampire talks to their master");
        helper.assertTrue(DialogueManager.session(player).tree().equals(VtmMod.id("nemesis_bound")), "in their own way");
        helper.succeed();
    }

    // ---------------------------------------------------------------- knowledge

    @GameTest(template = "empty", batch = "nem_intel")
    public static void playersOnlyLearnWhatTheyHaveBeenTold(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        RandomSource random = RandomSource.create(4L);
        ServerPlayer player = player(helper);
        NemesisRecord r = record(helper, SABBAT, Role.LEADER, Trait.BRUTE, Trait.FLAMMABLE);
        r.lastSeen = new BlockPos(100, 64, 100);
        r.seenDimension = helper.getLevel().dimension();
        NemesisRecord friend = record(helper, SABBAT, Role.AGENT);
        NemesisManager.link(r, friend, Relation.Type.ALLY);

        NemesisViews.Snapshot empty = NemesisViews.build(server(helper), player);
        helper.assertTrue(empty.entries().isEmpty(), "nobody is known at first");

        helper.assertTrue(Intel.reveal(player, r, random).isPresent(), "first: a name");
        NemesisViews.Entry heard = NemesisViews.build(server(helper), player).entries().get(0);
        helper.assertTrue(heard.core().tier() == 0 && heard.core().clan().isEmpty() && heard.core().role().isEmpty()
                && heard.core().traits().isEmpty() && heard.core().level() == 0, "a name and nothing else is sent");

        Intel.reveal(player, r, random);
        helper.assertTrue(NemesisViews.build(server(helper), player).entries().get(0).core().role().isPresent(), "then who they are");
        Intel.reveal(player, r, random);
        Intel.reveal(player, r, random);
        NemesisViews.Entry traits = NemesisViews.build(server(helper), player).entries().get(0);
        helper.assertTrue(traits.core().traits().size() == 2 && traits.core().unknownTraits() == 0, "then each trait in turn");
        helper.assertTrue(traits.kin().isEmpty(), "but not yet their kin");
        Intel.reveal(player, r, random);
        helper.assertTrue(Intel.reveal(player, r, random).isEmpty(), "and then there is nothing left to learn");

        // Courts show filled seats but name only the known.
        NemesisSimulator.populate(server(helper), data, random);
        NemesisSimulator.succession(server(helper), data);
        NemesisViews.Snapshot snap = NemesisViews.build(server(helper), player);
        var sabbat = snap.courts().stream().filter(c -> c.faction().equals(SABBAT)).findFirst().orElseThrow();
        helper.assertTrue(sabbat.seats().stream().allMatch(NemesisViews.Seat::filled), "every seat is filled");
        helper.assertTrue(sabbat.seats().stream().filter(s -> s.known().isPresent()).count() <= 1, "only the one you know is named");

        // Public news reaches everyone; private news only those who know the people in it.
        NemesisManager.log(server(helper), "torpor", SABBAT, false, friend);
        NemesisManager.log(server(helper), "destroyed", SABBAT, true, friend);
        List<ChronicleEntry> news = NemesisViews.build(server(helper), player).chronicle();
        helper.assertTrue(news.stream().anyMatch(c -> c.key().equals("destroyed")), "public news is heard");
        helper.assertTrue(news.stream().noneMatch(c -> c.key().equals("torpor") && c.involved().contains(friend.id)), "private news about strangers is not");

        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        NemesisViewsPayload.STREAM_CODEC.encode(buf, new NemesisViewsPayload(snap));
        helper.assertTrue(NemesisViewsPayload.STREAM_CODEC.decode(buf).snapshot().entries().size() == snap.entries().size(), "the snapshot survives the wire");
        helper.succeed();
    }

    // ---------------------------------------------------------------- persistence

    @GameTest(template = "empty", batch = "nem_save")
    public static void recordsSurviveASaveAndReload(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        ServerPlayer player = player(helper);
        NemesisSimulator.populate(server(helper), data, RandomSource.create(6L));
        NemesisSimulator.succession(server(helper), data);
        NemesisRecord r = data.inRole(CAMARILLA, Role.LEADER).get(0);
        r.deaths = 1;
        r.kills = 2;
        r.scars.add(Scar.BURNED);
        r.resist.put(DamageKind.FIRE, 2);
        r.feud(player.getUUID()).enmity = 42;
        r.feud(player.getUUID()).bloodHunt = true;
        r.mission = new Mission(MissionType.HUNT, 1234L, UUID.randomUUID(), null);
        r.boundTo = player.getUUID();
        r.status = Status.BOUND;
        r.lair = new BlockPos(1, 2, 3);
        r.lairDimension = helper.getLevel().dimension();
        NemesisManager.log(server(helper), "ambush", CAMARILLA, false, r, "x");

        CompoundTag tag = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        NemesisData loaded = NemesisData.load(tag, helper.getLevel().registryAccess());
        NemesisRecord copy = loaded.get(r.id);
        helper.assertTrue(loaded.all().size() == data.all().size(), "every record is saved");
        helper.assertTrue(copy.name.equals(r.name) && copy.traits.equals(r.traits) && copy.role == r.role && copy.level == r.level
                && copy.deaths == 1 && copy.kills == 2 && copy.scars.equals(r.scars) && copy.resistance(DamageKind.FIRE) == 2, "identity and history");
        helper.assertTrue(copy.feud(player.getUUID()).enmity == 42 && copy.feud(player.getUUID()).bloodHunt, "grudges");
        helper.assertTrue(copy.mission != null && copy.mission.type() == MissionType.HUNT && copy.mission.endsAt() == 1234L
                && copy.status == Status.BOUND && player.getUUID().equals(copy.boundTo), "bonds and errands");
        helper.assertTrue(new BlockPos(1, 2, 3).equals(copy.lair) && copy.relations.size() == r.relations.size(), "places and kin");
        helper.assertTrue(loaded.chronicle().size() == 1 && loaded.chronicle().get(0).key().equals("ambush"), "and the chronicle");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_stale")
    public static void staleBodiesAreRemovedAsTheyLoad(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        NemesisRecord dead = record(helper, CAMARILLA, Role.AGENT);
        NemesisManager.destroy(server(helper), dead, null, null);
        VampireNpc ghost = ModEntities.VAMPIRE.get().create(helper.getLevel());
        ghost.setNemesisId(dead.id);
        ghost.moveTo(VtmGameTests.at(helper, 4.5, 2, 4.5));
        helper.assertTrue(!helper.getLevel().addFreshEntity(ghost), "a body for a destroyed vampire is refused");

        NemesisRecord live = record(helper, CAMARILLA, Role.AGENT);
        VampireNpc real = body(helper, live, 2.5, 2.5);
        VampireNpc twin = ModEntities.VAMPIRE.get().create(helper.getLevel());
        twin.setNemesisId(live.id);
        twin.moveTo(VtmGameTests.at(helper, 6.5, 2, 2.5));
        helper.assertTrue(!helper.getLevel().addFreshEntity(twin), "and so is a second body for a living one");
        helper.assertTrue(live.entity.equals(real.getUUID()), "the first body stays");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_names")
    public static void deedsEarnEpithetsAndTauntsFollowHistory(GameTestHelper helper) {
        fresh(helper);
        ServerPlayer player = player(helper);
        NemesisRecord r = record(helper, CAMARILLA, Role.AGENT);
        helper.assertTrue(NemesisManager.epithet(r).isEmpty(), "no deeds, no epithet");
        VampireNpc npc = body(helper, r, 5.5, 6.5);
        npc.hurt(helper.getLevel().damageSources().playerAttack(player), 1F);
        r.kills = 1;
        helper.assertTrue(NemesisManager.epithet(r).equals(Optional.of("slayer")), "killing a player earns a name");
        r.kills = 3;
        helper.assertTrue(NemesisManager.epithet(r).equals(Optional.of("reaper")), "killing more earns a worse one");
        helper.assertTrue(NemesisManager.nameOf(r).getString().equals(r.name) || NemesisManager.nameOf(r).getSiblings().size() > 0,
                "the display name carries the epithet");

        Feud feud = new Feud();
        helper.assertTrue(com.epi0.vtm.nemesis.NemesisTaunts.greeting(r, null) == com.epi0.vtm.nemesis.NemesisTaunts.Situation.ENCOUNTER, "strangers");
        feud.defeats = 1;
        helper.assertTrue(com.epi0.vtm.nemesis.NemesisTaunts.greeting(r, feud) == com.epi0.vtm.nemesis.NemesisTaunts.Situation.REMATCH, "rematch");
        r.scars.add(Scar.BURNED);
        helper.assertTrue(com.epi0.vtm.nemesis.NemesisTaunts.greeting(r, feud) == com.epi0.vtm.nemesis.NemesisTaunts.Situation.SCARRED, "scarred");
        feud.losses = 1;
        helper.assertTrue(com.epi0.vtm.nemesis.NemesisTaunts.greeting(r, feud) == com.epi0.vtm.nemesis.NemesisTaunts.Situation.VICTOR, "it killed you once");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "nem_lair")
    public static void vampiresAreHomeInTheirLairs(GameTestHelper helper) {
        NemesisData data = fresh(helper);
        helper.setDayTime(18000);
        ServerPlayer player = player(helper);
        NemesisRecord r = record(helper, CAMARILLA, Role.AGENT);
        r.lair = player.blockPosition().offset(6, 0, 6);
        r.lairDimension = helper.getLevel().dimension();
        NemesisRecord far = record(helper, CAMARILLA, Role.AGENT);
        far.lair = player.blockPosition().offset(300, 0, 0);
        far.lairDimension = helper.getLevel().dimension();
        com.epi0.vtm.nemesis.NemesisEvents.manifestLairs(player);
        helper.assertTrue(NemesisManager.manifested(server(helper), r), "the player has walked into a lair, and its owner is home");
        helper.assertTrue(!NemesisManager.manifested(server(helper), far), "but not one far away");
        VampireNpc body = NemesisManager.entityOf(server(helper), r);
        helper.assertTrue(body.blockPosition().distManhattan(r.lair) <= 2, "at the lair itself");
        int before = helper.getLevel().getEntitiesOfClass(VampireNpc.class, player.getBoundingBox().inflate(40)).size();
        com.epi0.vtm.nemesis.NemesisEvents.manifestLairs(player);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(VampireNpc.class, player.getBoundingBox().inflate(40)).size() == before,
                "and it is not duplicated");
        helper.succeed();
    }
}
