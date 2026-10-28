package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.dialogue.DialogueManager;
import com.epi0.vtm.dialogue.DialogueTree;
import com.epi0.vtm.dialogue.Dialogues;
import com.epi0.vtm.dialogue.Roll;
import com.epi0.vtm.network.DialogueNodePayload;
import com.epi0.vtm.network.SyncMembershipPayload;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.npc.VtmNpc;
import com.epi0.vtm.power.Form;
import com.epi0.vtm.power.Forms;
import com.epi0.vtm.power.Power;
import com.epi0.vtm.power.PowerKind;
import com.epi0.vtm.power.PowerManager;
import com.epi0.vtm.power.Powers;
import com.epi0.vtm.power.SenseLink;
import com.epi0.vtm.npc.Attitude;
import com.epi0.vtm.npc.NpcMemory;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.registry.ModEffects;
import com.epi0.vtm.registry.ModEntities;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.vampire.Banes;
import com.epi0.vtm.vampire.Feeding;
import com.epi0.vtm.vampire.Frenzy;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireManager;
import com.epi0.vtm.vampire.VampireRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

/** Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(VtmMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VtmGameTests {

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(VtmGameTests.class);
    }

    /** The stock mock player is hard-wired to creative mode, which skips most of what we want to test. */
    static ServerPlayer survivalPlayer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        // A never-ticked player keeps its 60 ticks of spawn protection, which blocks all damage.
        try {
            var field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return player;
    }

    static ServerPlayer vampire(GameTestHelper helper, String clan) {
        ServerPlayer player = survivalPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(4.5, 2, 4.5)).x, helper.absoluteVec(new Vec3(4.5, 2, 4.5)).y,
                helper.absoluteVec(new Vec3(4.5, 2, 4.5)).z, 0F, 0F);
        VampireManager.embrace(player, VtmMod.id(clan));
        return player;
    }

    @GameTest(template = "empty")
    public static void dataIsRegistered(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        helper.assertTrue(access.registryOrThrow(VtmRegistries.CLAN).size() == 16, "expected 16 clans");
        helper.assertTrue(access.registryOrThrow(VtmRegistries.DISCIPLINE).size() == 12, "expected 12 disciplines");
        helper.assertTrue(Powers.all().size() == 62, "expected 62 powers, got " + Powers.all().size());
        for (Power power : Powers.all()) {
            helper.assertTrue(VtmRegistries.discipline(access, power.discipline()).isPresent(),
                    "power " + power.id() + " has no discipline");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void embraceAndLearn(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "brujah");
        VampireData d = VampireData.of(player);
        helper.assertTrue(d.isVampire() && d.clan().get().equals(VtmMod.id("brujah")), "embrace failed");
        helper.assertTrue(d.experience() == 15, "starting xp should be 15, was " + d.experience());

        ResourceLocation celerity = VtmMod.id("celerity");
        helper.assertTrue(VampireManager.tryLearnDiscipline(player, celerity), "learn level 1 failed");
        helper.assertTrue(VampireData.of(player).experience() == 10, "in-clan level 1 should cost 5");
        helper.assertTrue(VampireManager.tryLearnDiscipline(player, celerity), "learn level 2 failed");
        helper.assertFalse(VampireManager.tryLearnDiscipline(player, celerity), "level 3 should be unaffordable");
        helper.assertFalse(VampireManager.tryLearnDiscipline(player, VtmMod.id("thin_blood_alchemy")),
                "clan-only discipline must be refused out of clan");
        helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED)
                .hasModifier(VtmMod.id("celerity_speed")), "celerity passive missing");

        VampireManager.makeMortal(player);
        helper.assertFalse(player.getAttribute(Attributes.MOVEMENT_SPEED)
                .hasModifier(VtmMod.id("celerity_speed")), "passive should be removed when mortal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void feeding(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "ventrue");
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 6));
        float before = VampireData.of(player).blood();
        Feeding.bite(player, villager);
        helper.assertTrue(VampireData.of(player).blood() > before, "feeding should add blood");
        helper.succeed();
    }

    /** Every active power, at max level, with something to target in front of the caster. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void everyPowerRuns(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "caitiff");
        VampireData base = VampireData.of(player);
        for (var discipline : helper.getLevel().registryAccess().registryOrThrow(VtmRegistries.DISCIPLINE).keySet()) {
            base = base.withDiscipline(discipline, VampireRules.MAX_DISCIPLINE_LEVEL);
        }
        VampireManager.set(player, base.withBloodPotency(5));

        // Player faces +Z (yaw 0); put targets 3 blocks ahead.
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 7));
        helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 7));
        helper.spawn(EntityType.WOLF, new BlockPos(3, 2, 7));
        helper.spawn(EntityType.COW, new BlockPos(2, 2, 6));
        helper.spawnItem(net.minecraft.world.item.Items.STICK, 7, 2, 7);

        List<String> fizzled = new ArrayList<>();
        for (Power power : Powers.all()) {
            if (power.kind() != PowerKind.ACTIVE) {
                continue;
            }
            Forms.clear(player);
            SenseLink.end(player);
            Frenzy.quell(player);
            VampireManager.set(player, VampireData.of(player).withBlood(VampireData.of(player).maxBlood()));
            player.setHealth(10F);
            zombie.setPos(player.getX(), player.getY(), player.getZ() + 3);
            boolean blink = power.id().getPath().equals("blink");
            Vec3 home = player.position();
            if (blink) {
                // Open air, so the arena's walls cannot block the jump.
                player.setPos(home.x, home.y + 60, home.z);
            }
            Vec3 start = player.position();
            if (!PowerManager.activate(player, power.id())) {
                fizzled.add(power.id().getPath());
            } else if (blink) {
                helper.assertTrue(player.position().distanceTo(start) > 5.0, "blink should move the player");
            }
            if (blink) {
                player.setPos(home);
            }
        }
        // Only powers that legitimately need something we did not provide may fizzle.
        List<String> allowed = List.of("quell_the_beast");
        fizzled.removeAll(allowed);
        helper.assertTrue(fizzled.isEmpty(), "powers fizzled: " + fizzled);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mindPowersStopMobsTargetingYou(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "caitiff");
        VampireManager.set(player, VampireData.of(player)
                .withDiscipline(VtmMod.id("dominate"), 1).withDiscipline(VtmMod.id("obfuscate"), 3).withBlood(12F));
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 7));
        zombie.setTarget(player);
        helper.assertTrue(zombie.getTarget() == player, "precondition: zombie hunts the player");

        helper.assertTrue(PowerManager.activate(player, VtmMod.id("cloud_memory")), "cloud_memory should work");
        helper.assertTrue(zombie.getTarget() == null, "calmed zombie must drop its target");
        zombie.setTarget(player); // vanilla retargeting
        helper.assertTrue(zombie.getTarget() == null, "calmed zombie must refuse to retarget the player");

        Mob other = helper.spawn(EntityType.SKELETON, new BlockPos(2, 2, 2));
        helper.assertTrue(PowerManager.activate(player, VtmMod.id("ghost_walk")), "ghost_walk should work");
        other.setTarget(player);
        helper.assertTrue(other.getTarget() == null, "hidden player must not be targetable");
        helper.succeed();
    }


    private static void giveDisciplines(ServerPlayer player, int level, String... disciplines) {
        VampireData data = VampireData.of(player);
        for (String discipline : disciplines) {
            data = data.withDiscipline(VtmMod.id(discipline), level);
        }
        VampireManager.set(player, data.withBlood(data.maxBlood()));
    }

    @GameTest(template = "empty")
    public static void ventrueCanOnlyFeedOnHumans(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "ventrue");
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 6));
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 6));
        helper.assertFalse(Feeding.bite(player, cow), "ventrue must refuse animal blood");
        helper.assertTrue(Feeding.bite(player, cow, true), "...unless the Beast is in control");
        Feeding.forget(player.getUUID());
        helper.assertTrue(Feeding.bite(player, villager), "ventrue should drink human blood");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void feedingBanesChangeTheBite(GameTestHelper helper) {
        ServerPlayer hecata = vampire(helper, "hecata");
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 6));
        Feeding.bite(hecata, cow);
        helper.assertTrue(cow.getHealth() <= 5.5F, "hecata bite should hurt a lot, cow has " + cow.getHealth());

        ServerPlayer tremere = vampire(helper, "tremere");
        float before = VampireData.of(tremere).blood();
        Feeding.bite(tremere, helper.spawn(EntityType.COW, new BlockPos(2, 2, 6)));
        float gained = VampireData.of(tremere).blood() - before;
        helper.assertTrue(Math.abs(gained - 0.75F) < 0.01F, "tremere keeps 75% of the blood, gained " + gained);

        ServerPlayer salubri = vampire(helper, "salubri");
        float health = salubri.getHealth();
        Feeding.bite(salubri, helper.spawn(EntityType.COW, new BlockPos(6, 2, 6)));
        helper.assertTrue(salubri.getHealth() < health, "salubri should be hurt by biting");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hungerTriggersFrenzyAndTheBeastFeeds(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "banu_haqim");
        VampireManager.set(player, VampireData.of(player).withBlood(1F));
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 5));
        helper.assertTrue(VampireData.of(player).hunger() >= 4, "setup: should be hungry");
        for (int i = 0; i < 100 && !Frenzy.isFrenzied(player); i++) {
            Frenzy.tick(player, VampireData.of(player), Banes.of(player));
        }
        helper.assertTrue(Frenzy.isFrenzied(player), "a hungry vampire next to prey should frenzy");
        float before = VampireData.of(player).blood();
        Frenzy.tick(player, VampireData.of(player), Banes.of(player));
        helper.assertTrue(VampireData.of(player).blood() > before, "frenzied vampire should feed on the nearest creature");

        giveDisciplines(player, 5, "dominate");
        helper.assertFalse(PowerManager.activate(player, VtmMod.id("cloud_memory")), "no powers during frenzy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gangrelFrenzyLeavesFeaturesQuellRemovesThem(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "gangrel");
        giveDisciplines(player, 3, "animalism");
        helper.assertTrue(Frenzy.start(player, 10), "frenzy should start");
        player.removeEffect(ModEffects.FRENZY); // the frenzy runs its course
        helper.assertTrue(VampireData.of(player).bestialFeatures() == 1, "frenzy should leave a feature");

        helper.assertTrue(Frenzy.start(player, 10) == false, "frenzy has a cooldown");
        helper.assertTrue(PowerManager.activate(player, Powers.QUELL_THE_BEAST), "quell should work: sheds the feature");
        helper.assertTrue(VampireData.of(player).bestialFeatures() == 0, "quell should shed the feature");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void brujahRageFrenzy(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "brujah");
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 6));
        player.setHealth(10F);
        float before = player.getHealth();
        player.hurt(player.damageSources().mobAttack(zombie), 2F);
        helper.assertTrue(player.getHealth() < before, "control: the mock player must be hurtable");
        helper.assertTrue(Frenzy.isFrenzied(player), "a brujah below 40% health should frenzy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldBanesBite(GameTestHelper helper) {
        // Tzimisce far from their haven bleed.
        ServerPlayer tzimisce = vampire(helper, "tzimisce");
        tzimisce.setPos(tzimisce.getX() + 5000, tzimisce.getY(), tzimisce.getZ());
        VampireData data = VampireData.of(tzimisce);
        VampireData drained = Banes.tick(tzimisce, data, Banes.of(tzimisce));
        helper.assertTrue(drained.blood() < data.blood(), "tzimisce far from home should drain");

        // Ravnos linger too long.
        ServerPlayer ravnos = vampire(helper, "ravnos");
        VampireData rd = VampireData.of(ravnos);
        Banes.tick(ravnos, rd, Banes.of(ravnos), 0L);
        helper.assertTrue(Banes.tick(ravnos, rd, Banes.of(ravnos), 20L * 60 * 10).blood() == rd.blood(), "10 minutes is fine");
        helper.assertTrue(Banes.tick(ravnos, rd, Banes.of(ravnos), 20L * 60 * 31).blood() < rd.blood(), "31 minutes is doom");

        // Nosferatu unsettle villagers.
        ServerPlayer nosferatu = vampire(helper, "nosferatu");
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 6));
        nosferatu.tickCount = 200;
        Banes.tick(nosferatu, VampireData.of(nosferatu), Banes.of(nosferatu));
        int rep = villager.getGossips().getReputation(nosferatu.getUUID(), t -> true);
        helper.assertTrue(rep < 0, "villagers should distrust a nosferatu, reputation " + rep);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shapechangeForms(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "gangrel");
        giveDisciplines(player, 5, "protean");

        helper.assertTrue(PowerManager.activate(player, VtmMod.id("shapechange")), "wolf form failed");
        helper.assertTrue(Forms.isIn(player, Form.WOLF), "should be a wolf");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.SCALE) - 0.5) < 0.01, "wolves are smaller");
        float blood = VampireData.of(player).blood();
        helper.assertTrue(PowerManager.activate(player, VtmMod.id("shapechange")), "reverting failed");
        helper.assertFalse(Forms.of(player).isPresent(), "should be human again");
        helper.assertTrue(VampireData.of(player).blood() == blood, "reverting is free");

        VampireManager.set(player, VampireData.of(player).withBlood(VampireData.of(player).maxBlood()));
        Forms.clear(player);
        PowerManager.forget(player.getUUID());
        helper.assertTrue(PowerManager.activate(player, VtmMod.id("bat_form")), "bat form failed");
        helper.assertTrue(player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT) > 0, "bats fly");
        helper.assertTrue(player.getAttributeValue(Attributes.SCALE) <= 0.5, "bats are small");

        PowerManager.forget(player.getUUID());
        helper.assertTrue(PowerManager.activate(player, VtmMod.id("mist_form")), "mist form failed");
        helper.assertFalse(Forms.isIn(player, Form.BAT), "forms replace each other");
        helper.assertTrue(Forms.isIn(player, Form.MIST), "should be mist");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mistIsUntouchableExceptByFire(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "gangrel");
        giveDisciplines(player, 5, "protean");
        float health = player.getHealth();
        player.hurt(player.damageSources().generic(), 2F);
        helper.assertTrue(player.getHealth() < health, "control: the mock player must be hurtable");

        PowerManager.activate(player, VtmMod.id("mist_form"));
        health = player.getHealth();
        player.hurt(player.damageSources().generic(), 2F);
        helper.assertTrue(player.getHealth() == health, "mist should shrug off ordinary damage");
        player.invulnerableTime = 0; // clear the damage cooldown from the control hit
        player.hurt(player.damageSources().inFire(), 2F);
        helper.assertTrue(player.getHealth() < health, "mist should still burn");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shareTheSensesLinksAndReleases(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "caitiff");
        giveDisciplines(player, 5, "auspex");
        helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 7));

        helper.assertTrue(PowerManager.activate(player, VtmMod.id("share_the_senses")), "link failed");
        helper.assertTrue(SenseLink.isLinked(player), "should be linked");
        helper.assertTrue(player.hasEffect(ModEffects.PROJECTION), "body should be left helpless");
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < 0.001, "body cannot move");

        float blood = VampireData.of(player).blood();
        helper.assertTrue(PowerManager.activate(player, VtmMod.id("share_the_senses")), "release failed");
        helper.assertFalse(SenseLink.isLinked(player), "should be released");
        helper.assertFalse(player.hasEffect(ModEffects.PROJECTION), "body should move again");
        helper.assertTrue(VampireData.of(player).blood() == blood, "releasing is free");

        PowerManager.forget(player.getUUID());
        PowerManager.activate(player, VtmMod.id("share_the_senses"));
        player.hurt(player.damageSources().generic(), 1F);
        helper.assertFalse(SenseLink.isLinked(player), "being hurt should break the link");
        helper.succeed();
    }


    // ------------------------------------------------------------------ NPCs

    static Vec3 at(GameTestHelper helper, double x, double y, double z) {
        return helper.absoluteVec(new Vec3(x, y, z));
    }

    @GameTest(template = "empty")
    public static void npcsSpawnWithIdentity(GameTestHelper helper) {
        var civilian = NpcSpawner.civilian(helper.getLevel(), at(helper, 2.5, 2, 2.5));
        helper.assertTrue(civilian.hasCustomName(), "civilians get a name");
        helper.assertTrue(civilian.home() != null, "civilians get a home");
        helper.assertTrue(civilian.factionId().equals(VtmMod.id("mortals")), "civilians are mortals");

        var brujah = NpcSpawner.vampire(helper.getLevel(), at(helper, 5.5, 2, 2.5), VtmMod.id("brujah"));
        helper.assertTrue(brujah.clanId().equals(VtmMod.id("brujah")), "clan set");
        helper.assertTrue(brujah.factionId().equals(VtmMod.id("anarch")), "brujah are anarchs");
        helper.assertTrue(brujah.getAttributeValue(Attributes.ATTACK_DAMAGE) > 4.5, "brujah hit harder (potence)");

        var ventrue = NpcSpawner.vampire(helper.getLevel(), at(helper, 6.5, 2, 2.5), VtmMod.id("ventrue"));
        helper.assertTrue(ventrue.getAttributeValue(Attributes.ARMOR) > 0, "ventrue are tougher (fortitude)");

        var random = NpcSpawner.vampire(helper.getLevel(), at(helper, 3.5, 2, 2.5), null);
        helper.assertTrue(random.clanId() != null && random.factionId() != null, "random vampires get a clan and sect");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void attitudesFollowMemoryAndSect(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "caitiff");
        var civilian = NpcSpawner.civilian(helper.getLevel(), at(helper, 2.5, 2, 2.5));
        helper.assertTrue(civilian.attitudeTo(player) == Attitude.NEUTRAL, "strangers are fine");
        civilian.memory().remember(player.getUUID(), NpcMemory.Event.WITNESSED_FEEDING, 0L);
        helper.assertTrue(civilian.attitudeTo(player) == Attitude.WARY, "witnessed feeding makes civilians wary");

        helper.setDayTime(18000);
        ServerPlayer mortal = survivalPlayer(helper);
        var sabbat = NpcSpawner.vampire(helper.getLevel(), at(helper, 5.5, 2, 2.5), VtmMod.id("tzimisce"));
        var camarilla = NpcSpawner.vampire(helper.getLevel(), at(helper, 6.5, 2, 2.5), VtmMod.id("ventrue"));
        sabbat.setBlood(1F);
        camarilla.setBlood(1F);
        helper.assertTrue(sabbat.attitudeTo(mortal) == Attitude.HOSTILE, "hungry sabbat hunt mortals at night");
        helper.assertTrue(camarilla.attitudeTo(mortal) == Attitude.NEUTRAL, "camarilla keep the masquerade");
        helper.assertTrue(sabbat.attitudeTo(camarilla) == Attitude.HOSTILE, "sabbat and camarilla are enemies");
        camarilla.memory().remember(mortal.getUUID(), NpcMemory.Event.HARMED, 0L);
        helper.assertTrue(camarilla.attitudeTo(mortal) == Attitude.HOSTILE, "anyone who hurts a vampire is an enemy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void npcsSurviveSaveAndLoad(GameTestHelper helper) {
        var original = NpcSpawner.vampire(helper.getLevel(), at(helper, 3.5, 2, 3.5), VtmMod.id("toreador"));
        original.setBlood(2.5F);
        var id = UUID.randomUUID();
        original.memory().remember(id, NpcMemory.Event.HARMED, 100L);
        var tag = new net.minecraft.nbt.CompoundTag();
        original.saveWithoutId(tag);

        var copy = ModEntities.VAMPIRE.get().create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.clanId().equals(VtmMod.id("toreador")), "clan persists");
        helper.assertTrue(copy.factionId().equals(original.factionId()), "faction persists");
        helper.assertTrue(copy.variant() == original.variant(), "look persists");
        helper.assertTrue(copy.blood() == 2.5F, "hunger persists");
        helper.assertTrue(copy.home() != null && copy.home().equals(original.home()), "home persists");
        helper.assertTrue(copy.memory().about(id).map(e -> e.harmed() == 1).orElse(false), "memory persists");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vtm_day", timeoutTicks = 200)
    public static void vampiresBurnInDaylight(GameTestHelper helper) {
        helper.setDayTime(6000);
        var vampire = NpcSpawner.vampire(helper.getLevel(), at(helper, 4.5, 2, 4.5), VtmMod.id("brujah"));
        vampire.setNoAi(true);
        helper.succeedWhen(() -> helper.assertTrue(vampire.isOnFire(), "a vampire in open sunlight should burn"));
    }

    @GameTest(template = "empty", batch = "vtm_night", timeoutTicks = 600)
    public static void civiliansGoHomeAndSleepAtNight(GameTestHelper helper) {
        helper.setDayTime(18000);
        var civilian = NpcSpawner.civilian(helper.getLevel(), at(helper, 1.5, 2, 1.5));
        civilian.setHome(BlockPos.containing(at(helper, 6.5, 2, 6.5)));
        helper.succeedWhen(() -> {
            helper.assertTrue(civilian.isResting(), "the civilian should have gone to bed");
            helper.assertTrue(civilian.distanceToSqr(at(helper, 6.5, 2, 6.5)) < 25, "...at home, not where they started");
        });
    }

    @GameTest(template = "empty", batch = "vtm_night_feed", timeoutTicks = 800)
    public static void hungryVampiresFeedOnIsolatedCivilians(GameTestHelper helper) {
        helper.setDayTime(18000);
        // Other tests share this ground and leave their people behind; a witness nearby would stop the bite.
        helper.getLevel().getEntitiesOfClass(com.epi0.vtm.npc.VtmNpc.class, new net.minecraft.world.phys.AABB(helper.absolutePos(new net.minecraft.core.BlockPos(4, 2, 4))).inflate(40))
                .forEach(net.minecraft.world.entity.Entity::discard);
        helper.getLevel().players().forEach(p -> p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR));
        var vampire = NpcSpawner.vampire(helper.getLevel(), at(helper, 1.5, 2, 1.5), VtmMod.id("ventrue"));
        var victim = NpcSpawner.civilian(helper.getLevel(), at(helper, 7.5, 2, 7.5));
        vampire.setBlood(1F);
        helper.succeedWhen(() -> {
            helper.setDayTime(18000); // other tests share the clock
            helper.assertTrue(vampire.blood() > 3F, "the vampire should have fed");
            helper.assertTrue(victim.getHealth() < victim.getMaxHealth() || !victim.isAlive(), "...from the civilian");
        });
    }

    @GameTest(template = "empty")
    public static void civiliansRememberWhatPlayersDo(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "gangrel");
        var victim = NpcSpawner.civilian(helper.getLevel(), at(helper, 4.5, 2, 6.5));
        var bystander = NpcSpawner.civilian(helper.getLevel(), at(helper, 2.5, 2, 6.5));
        player.moveTo(at(helper, 4.5, 2, 4.5));
        helper.assertTrue(Feeding.bite(player, victim), "bite failed");
        helper.assertTrue(victim.memory().isThreat(player.getUUID()), "the victim remembers who hurt them");
        helper.assertTrue(bystander.memory().about(player.getUUID()).map(e -> e.witnessedFeeding() > 0).orElse(false),
                "the bystander saw it");
        helper.assertTrue(bystander.attitudeTo(player) == Attitude.WARY, "...and is now afraid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sunlightRules(GameTestHelper helper) {
        helper.assertTrue(VampireRules.maxBloodPotency(13) == 2 && VampireRules.maxBloodPotency(4) == 10, "bp table");
        helper.assertTrue(VampireRules.learnCost(3, true) == 15 && VampireRules.learnCost(3, false) == 21, "costs");
        helper.assertTrue(VampireRules.hunger(VampireData.DEFAULT.withSpecies(com.epi0.vtm.vampire.Species.VAMPIRE)
                .withBloodPotency(1).withBlood(0F)) == 5, "empty pool is max hunger");
        helper.succeed();
    }

    // ---------------------------------------------------------------- dialogue

    private static ResourceLocation tree(String name) {
        return VtmMod.id(name);
    }

    /** Index of the option with this text key in the player's current node. */
    static int option(ServerPlayer player, String textKey) {
        var session = DialogueManager.session(player);
        var options = Dialogues.byId(session.tree()).orElseThrow().tree().nodes().get(session.node()).options();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).text().equals(textKey)) {
                return i;
            }
        }
        throw new IllegalStateException("no option " + textKey + " in " + session.node());
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void dialogueSelectsTheMostSpecificTree(GameTestHelper helper) {
        helper.setDayTime(18000);
        helper.assertTrue(Dialogues.count() >= 9, "dialogue trees load from the datapack, got " + Dialogues.count());
        ServerPlayer vampire = vampire(helper, "gangrel");
        ServerPlayer mortal = survivalPlayer(helper);
        var civilian = NpcSpawner.civilian(helper.getLevel(), at(helper, 3.5, 2, 3.5));
        var camarilla = NpcSpawner.vampire(helper.getLevel(), at(helper, 5.5, 2, 3.5), VtmMod.id("ventrue"));
        var anarch = NpcSpawner.vampire(helper.getLevel(), at(helper, 6.5, 2, 3.5), VtmMod.id("brujah"));

        helper.assertTrue(Dialogues.select(vampire, civilian).get().id().equals(tree("civilian")), "civilians get the default tree");
        helper.assertTrue(Dialogues.select(vampire, camarilla).get().id().equals(tree("vampire_camarilla")), "camarilla talk like the camarilla");
        helper.assertTrue(Dialogues.select(vampire, anarch).get().id().equals(tree("vampire_anarch")), "anarchs talk like anarchs");
        helper.assertTrue(Dialogues.select(mortal, camarilla).get().id().equals(tree("vampire_mortal")), "vampires are curt with mortals");

        civilian.memory().remember(vampire.getUUID(), NpcMemory.Event.WITNESSED_FEEDING, 0L);
        helper.assertTrue(Dialogues.select(vampire, civilian).get().id().equals(tree("civilian_wary")), "frightened civilians are wary");
        civilian.memory().adjustRapport(vampire.getUUID(), 5, 0L);
        helper.assertTrue(civilian.attitudeTo(vampire) == Attitude.FRIENDLY, "made amends, rapport 5, and they are forgiving");
        helper.assertTrue(Dialogues.select(vampire, civilian).get().id().equals(tree("civilian_friendly")), "...and friendly");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void conversationsApplyEffectsAndEnd(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = vampire(helper, "gangrel");
        var civilian = NpcSpawner.civilian(helper.getLevel(), at(helper, 4.5, 2, 6.5));

        helper.assertTrue(DialogueManager.open(player, civilian), "the civilian will talk");
        helper.assertTrue(civilian.state() == VtmNpc.State.TALKING && player.getUUID().equals(civilian.talker()),
                "the civilian stops for the conversation");
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.smalltalk"));
        helper.assertTrue(civilian.memory().rapport(player.getUUID()) == 1, "small talk builds rapport");
        helper.assertTrue(civilian.memory().hasFlag(player.getUUID(), "smalltalk"), "...once");
        helper.assertTrue(DialogueManager.session(player).node().equals("smalltalk"), "the conversation moved on");

        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.leave"));
        helper.assertTrue(DialogueManager.session(player) == null, "leaving ends it");
        helper.assertTrue(civilian.state() == VtmNpc.State.NORMAL && civilian.talker() == null, "the civilian goes back to their evening");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void clientsCannotForgeChoices(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer mortal = survivalPlayer(helper);
        mortal.moveTo(at(helper, 4.5, 2, 4.5));
        var civilian = NpcSpawner.civilian(helper.getLevel(), at(helper, 4.5, 2, 6.5));
        DialogueManager.open(mortal, civilian);
        var hub = DialogueManager.session(mortal);
        helper.assertTrue(hub != null, "the civilian talks to mortals too");

        int charm = option(mortal, "dialogue.vtm.opt.charm");
        DialogueManager.choose(mortal, charm); // hidden from mortals: must do nothing
        helper.assertTrue(DialogueManager.session(mortal).equals(hub), "a hidden option cannot be picked");
        helper.assertTrue(civilian.memory().about(mortal.getUUID()).map(e -> e.rapport() == 0).orElse(true), "...and has no effect");

        DialogueManager.choose(mortal, 99); // out of range ends the conversation instead of throwing
        helper.assertTrue(DialogueManager.session(mortal) == null, "garbage ends the conversation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void diceChecksBranchAndHonourEffects(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = vampire(helper, "toreador");
        var civilian = NpcSpawner.civilian(helper.getLevel(), at(helper, 4.5, 2, 6.5));
        // A tree whose single option always passes (difficulty 0) and one that always fails (difficulty 99).
        com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString("""
                {"start": "a", "nodes": {
                  "a": {"text": "t", "options": [
                    {"text": "win",  "check": {"difficulty": 0, "fail": "lost"}, "effects": [{"blood": 1.0, "flag": "won"}], "fail_effects": [{"flag": "bad"}], "next": "ok"},
                    {"text": "lose", "check": {"difficulty": 99, "fail": "lost"}, "effects": [{"flag": "good"}], "fail_effects": [{"rapport": -2}], "next": "ok"}]},
                  "ok": {"text": "ok"}, "lost": {"text": "lost"}}}""");
        Dialogues.install(VtmMod.id("test_dice"), DialogueTree.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
        player.setData(com.epi0.vtm.registry.ModAttachments.VAMPIRE, VampireData.of(player).withBlood(2F));

        DialogueManager.open(player, civilian, VtmMod.id("test_dice"));
        DialogueManager.choose(player, 0);
        helper.assertTrue(DialogueManager.session(player).node().equals("ok"), "a passed check goes to next");
        helper.assertTrue(civilian.memory().hasFlag(player.getUUID(), "won") && !civilian.memory().hasFlag(player.getUUID(), "bad"),
                "success effects apply, failure ones do not");
        helper.assertTrue(VampireData.of(player).blood() == 3F, "the blood was given");

        DialogueManager.open(player, civilian, VtmMod.id("test_dice"));
        DialogueManager.choose(player, 1);
        helper.assertTrue(DialogueManager.session(player).node().equals("lost"), "a failed check goes to its fail node");
        helper.assertTrue(civilian.memory().rapport(player.getUUID()) == -2 && !civilian.memory().hasFlag(player.getUUID(), "good"),
                "failure effects apply, success ones do not");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dicePoolsFollowV5Rules(GameTestHelper helper) {
        var random = net.minecraft.util.RandomSource.create(7L);
        int messy = 0;
        int bestial = 0;
        long successes = 0;
        for (int i = 0; i < 4000; i++) {
            var roll = Roll.make(random, 6, 0, 3);
            helper.assertTrue(!roll.messyCritical() && !roll.bestialFailure(), "no hunger dice, no Beast");
            successes += roll.successes();
            var hungry = Roll.make(random, 6, 6, 3);
            messy += hungry.messyCritical() ? 1 : 0;
            bestial += hungry.bestialFailure() ? 1 : 0;
        }
        double mean = successes / 4000.0;
        helper.assertTrue(mean > 3.0 && mean < 3.9, "six dice average about three successes plus crits, got " + mean);
        helper.assertTrue(messy > 0 && bestial > 0, "all-hunger pools do show the Beast");
        helper.assertTrue(Roll.make(random, 0, 5, 1).pool() == 1, "a pool is never empty");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void hostileAndBusyNpcsRefuse(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = vampire(helper, "gangrel");
        ServerPlayer other = vampire(helper, "nosferatu");
        var elder = NpcSpawner.vampire(helper.getLevel(), at(helper, 4.5, 2, 6.5), VtmMod.id("ventrue"));
        var sleeper = NpcSpawner.civilian(helper.getLevel(), at(helper, 2.5, 2, 6.5));
        sleeper.setState(VtmNpc.State.SLEEPING);
        helper.assertTrue(!DialogueManager.open(player, sleeper), "people asleep do not chat");

        helper.assertTrue(DialogueManager.open(player, elder), "a stranger vampire will talk");
        helper.assertTrue(!DialogueManager.open(other, elder), "...but only to one person at a time");
        DialogueManager.end(player);
        helper.assertTrue(DialogueManager.open(other, elder), "free again once the first has left");
        DialogueManager.end(other);

        elder.memory().adjustRapport(player.getUUID(), -5, 0L);
        helper.assertTrue(elder.attitudeTo(player) == Attitude.HOSTILE, "enough insults and a vampire turns on you");
        helper.assertTrue(!DialogueManager.open(player, elder), "hostile vampires do not talk");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void conversationEndsWhenDisturbed(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = vampire(helper, "gangrel");
        var civilian = NpcSpawner.civilian(helper.getLevel(), at(helper, 4.5, 2, 6.5));
        DialogueManager.open(player, civilian);
        helper.assertTrue(DialogueManager.session(player) != null, "talking");
        civilian.hurt(helper.getLevel().damageSources().playerAttack(player), 1F);
        helper.assertTrue(DialogueManager.session(player) == null && civilian.state() != VtmNpc.State.TALKING,
                "a punch ends the conversation");
        helper.assertTrue(civilian.memory().isThreat(player.getUUID()), "and is remembered");

        var elder = NpcSpawner.vampire(helper.getLevel(), at(helper, 2.5, 2, 6.5), VtmMod.id("ventrue"));
        DialogueManager.open(player, elder);
        player.moveTo(at(helper, 4.5, 2, 4.5).add(0, 100, 0)); // far above, so it is not a stray witness for other tests
        helper.succeedWhen(() -> helper.assertTrue(DialogueManager.session(player) == null, "walking away ends the conversation"));
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void vampiresTeachTrustedFriends(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = vampire(helper, "gangrel");
        var elder = NpcSpawner.vampire(helper.getLevel(), at(helper, 4.5, 2, 6.5), VtmMod.id("ventrue"));
        int xp = VampireData.of(player).experience();

        DialogueManager.open(player, elder);
        var teach = DialogueManager.session(player);
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.ask_teach")); // locked: no trust yet
        helper.assertTrue(DialogueManager.session(player).equals(teach), "a stranger is not taught");

        for (String step : new String[]{"introduce", "ask_clan", "ask_sect"}) {
            DialogueManager.choose(player, option(player, "dialogue.vtm.opt." + step));
            DialogueManager.choose(player, option(player, "dialogue.vtm.opt.else"));
        }
        helper.assertTrue(elder.memory().rapport(player.getUUID()) == 3, "three questions earn their trust");
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.ask_teach"));
        helper.assertTrue(VampireData.of(player).experience() == xp + 3, "and a lesson");
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.else"));
        helper.assertTrue(elder.memory().hasFlag(player.getUUID(), "taught"), "taught once only");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dialoguePayloadsSurviveTheWire(GameTestHelper helper) {
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        var node = new DialogueNodePayload.Node(42, net.minecraft.network.chat.Component.literal("Mara"), 0x5522AA,
                net.minecraft.network.chat.Component.translatable("dialogue.vtm.civilian.greet.day", "Mara"),
                java.util.Optional.of(net.minecraft.network.chat.Component.literal("roll")), 2, -3,
                List.of(new DialogueNodePayload.Choice(net.minecraft.network.chat.Component.literal("hi"), false,
                        java.util.Optional.of(net.minecraft.network.chat.Component.literal("locked")))));
        DialogueNodePayload.STREAM_CODEC.encode(buf, new DialogueNodePayload(java.util.Optional.of(node)));
        var decoded = DialogueNodePayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(decoded.node().isPresent() && decoded.node().get().equals(node), "node round-trips");
        DialogueNodePayload.STREAM_CODEC.encode(buf, DialogueNodePayload.close());
        helper.assertTrue(DialogueNodePayload.STREAM_CODEC.decode(buf).node().isEmpty(), "close round-trips");

        // Rapport and flags persist with the NPC.
        NpcMemory memory = new NpcMemory();
        UUID id = UUID.randomUUID();
        memory.adjustRapport(id, 4, 10L);
        memory.setFlag(id, "introduced", true, 10L);
        NpcMemory loaded = new NpcMemory();
        loaded.load(memory.save());
        helper.assertTrue(loaded.rapport(id) == 4 && loaded.hasFlag(id, "introduced"), "memory round-trips through NBT");
        helper.succeed();
    }

    // ---------------------------------------------------------------- sects

    private static ResourceLocation sect(String name) {
        return VtmMod.id(name);
    }

    @GameTest(template = "empty")
    public static void sectJoiningFollowsTheRules(GameTestHelper helper) {
        ServerPlayer mortal = survivalPlayer(helper);
        helper.assertTrue(!SectManager.join(mortal, sect("camarilla")), "mortals cannot join");

        ServerPlayer player = vampire(helper, "gangrel");
        helper.assertTrue(!SectManager.join(player, sect("hunters")), "hunters are not joinable");
        helper.assertTrue(SectManager.join(player, sect("camarilla")), "vampires can join the Camarilla");
        Membership m = Membership.of(player);
        helper.assertTrue(m.isMemberOf(sect("camarilla")) && m.rank() == 0, "...as the lowest rank");
        helper.assertTrue(!SectManager.join(player, sect("anarch")), "one sect at a time");

        helper.assertTrue(!SectManager.promote(player), "no promotion without standing");
        SectManager.set(player, m.withStanding(sect("camarilla"), 25));
        helper.assertTrue(SectManager.promote(player) && Membership.of(player).rank() == 1, "25 standing makes an Ancilla");
        helper.assertTrue(!SectManager.promote(player), "...and not an Elder");
        SectManager.set(player, Membership.of(player).withStanding(sect("camarilla"), 100));
        helper.assertTrue(SectManager.promote(player) && !SectManager.promote(player), "the top rank is the top");

        SectManager.leave(player);
        m = Membership.of(player);
        helper.assertTrue(m.faction().isEmpty() && m.rank() == 0, "renouncing clears membership");
        helper.assertTrue(m.standingWith(sect("camarilla")) == 80, "...at a price in standing");

        SectManager.set(player, m.withStanding(sect("sabbat"), SectManager.OUTCAST));
        helper.assertTrue(!SectManager.join(player, sect("sabbat")), "outcasts are not taken in");
        VampireManager.makeMortal(player);
        helper.assertTrue(Membership.of(player).equals(Membership.DEFAULT), "becoming mortal forgets all of it");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void deedsMoveStanding(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = vampire(helper, "ventrue");
        SectManager.join(player, sect("camarilla"));
        var sabbatVictim = NpcSpawner.vampire(helper.getLevel(), at(helper, 3.5, 2, 6.5), VtmMod.id("tzimisce"));
        var friendVictim = NpcSpawner.vampire(helper.getLevel(), at(helper, 5.5, 2, 6.5), VtmMod.id("toreador"));
        player.moveTo(at(helper, 4.5, 2, 4.5));

        sabbatVictim.hurt(helper.getLevel().damageSources().playerAttack(player), 1000F);
        Membership m = Membership.of(player);
        helper.assertTrue(m.standingWith(sect("camarilla")) == SectManager.KILL_ENEMY, "killing an enemy of the sect earns standing");
        helper.assertTrue(m.standingWith(sect("sabbat")) == SectManager.KILL_RESENTMENT, "...and the victim's sect resents it");

        friendVictim.hurt(helper.getLevel().damageSources().playerAttack(player), 1000F);
        helper.assertTrue(Membership.of(player).standingWith(sect("camarilla")) == SectManager.KILL_ENEMY + SectManager.KILL_MEMBER,
                "killing a member of your own sect costs you");

        var civilian = NpcSpawner.civilian(helper.getLevel(), at(helper, 4.5, 2, 6.5));
        int before = Membership.of(player).standingWith(sect("camarilla"));
        helper.assertTrue(Feeding.bite(player, civilian), "bite failed");
        helper.assertTrue(Membership.of(player).standingWith(sect("camarilla")) == before + SectManager.MASQUERADE_BREACH,
                "feeding in front of a civilian breaks the Masquerade");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void sectsShapeHowVampiresTreatYou(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = vampire(helper, "gangrel");
        var camarilla = NpcSpawner.vampire(helper.getLevel(), at(helper, 3.5, 2, 6.5), VtmMod.id("ventrue"));
        var sabbat = NpcSpawner.vampire(helper.getLevel(), at(helper, 5.5, 2, 6.5), VtmMod.id("tzimisce"));
        helper.assertTrue(camarilla.attitudeTo(player) == Attitude.NEUTRAL, "strangers are strangers");

        SectManager.join(player, sect("camarilla"));
        helper.assertTrue(camarilla.attitudeTo(player) == Attitude.FRIENDLY, "your own sect is friendly");
        helper.assertTrue(sabbat.attitudeTo(player) == Attitude.HOSTILE, "an enemy sect attacks a sworn member");

        SectManager.leave(player);
        helper.assertTrue(sabbat.attitudeTo(player) == Attitude.NEUTRAL, "...but not a free agent");
        SectManager.set(player, Membership.of(player).withStanding(sect("sabbat"), SectManager.OUTCAST));
        helper.assertTrue(sabbat.attitudeTo(player) == Attitude.HOSTILE, "an outcast is hunted by the sect they wronged");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void sectsAreJoinedThroughConversation(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = vampire(helper, "gangrel");
        var elder = NpcSpawner.vampire(helper.getLevel(), at(helper, 4.5, 2, 6.5), VtmMod.id("ventrue"));
        elder.setRank(2);
        DialogueManager.open(player, elder);
        var hub = DialogueManager.session(player);

        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.join")); // locked: no rapport yet
        helper.assertTrue(DialogueManager.session(player).equals(hub) && Membership.of(player).faction().isEmpty(),
                "a stranger is not accepted");

        elder.memory().adjustRapport(player.getUUID(), 3, 0L);
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.join"));
        helper.assertTrue(Membership.of(player).isMemberOf(sect("camarilla")), "a trusted vampire vouches for you");
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.else"));

        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.serve"));
        helper.assertTrue(Membership.of(player).standingWith(sect("camarilla")) == 5, "service earns standing");
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.else"));

        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.promote")); // locked: 5 < 25
        helper.assertTrue(Membership.of(player).rank() == 0, "not enough standing for a promotion");
        SectManager.set(player, Membership.of(player).withStanding(sect("camarilla"), 30));
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.promote"));
        helper.assertTrue(Membership.of(player).rank() == 1, "with standing, an elder promotes you");
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.else"));

        elder.setRank(1);
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.renounce"));
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.renounce_yes"));
        helper.assertTrue(Membership.of(player).faction().isEmpty(), "renouncing in conversation leaves the sect");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vtm_night")
    public static void peersCannotPromoteYou(GameTestHelper helper) {
        helper.setDayTime(18000);
        ServerPlayer player = vampire(helper, "gangrel");
        SectManager.join(player, sect("camarilla"));
        SectManager.set(player, Membership.of(player).withStanding(sect("camarilla"), 50));
        var peer = NpcSpawner.vampire(helper.getLevel(), at(helper, 4.5, 2, 6.5), VtmMod.id("ventrue"));
        peer.setRank(0);
        DialogueManager.open(player, peer);
        var hub = DialogueManager.session(player);
        DialogueManager.choose(player, option(player, "dialogue.vtm.opt.promote"));
        helper.assertTrue(DialogueManager.session(player).equals(hub) && Membership.of(player).rank() == 0,
                "only someone of higher rank can promote you");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void membershipSurvivesTheWire(GameTestHelper helper) {
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        Membership m = Membership.DEFAULT.withFaction(java.util.Optional.of(sect("sabbat")), 2).withStanding(sect("anarch"), -40);
        SyncMembershipPayload.STREAM_CODEC.encode(buf, new SyncMembershipPayload(m));
        helper.assertTrue(SyncMembershipPayload.STREAM_CODEC.decode(buf).membership().equals(m), "membership round-trips");
        helper.assertTrue(Membership.of(survivalPlayer(helper)).equals(Membership.DEFAULT), "new players belong nowhere");

        var sabbat = VtmRegistries.faction(helper.getLevel().registryAccess(), sect("sabbat")).orElseThrow();
        helper.assertTrue(sabbat.joinable() && sabbat.rankCount() == 3 && sabbat.standingFor(2) == 60, "ranks come from the datapack");
        helper.succeed();
    }
}
