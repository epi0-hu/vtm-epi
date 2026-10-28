package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.power.Form;
import com.epi0.vtm.power.Forms;
import com.epi0.vtm.power.Power;
import com.epi0.vtm.power.PowerKind;
import com.epi0.vtm.power.PowerManager;
import com.epi0.vtm.power.Powers;
import com.epi0.vtm.power.SenseLink;
import com.epi0.vtm.registry.ModEffects;
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
    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
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

    private static ServerPlayer vampire(GameTestHelper helper, String clan) {
        ServerPlayer player = survivalPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(4.5, 1, 4.5)).x, helper.absoluteVec(new Vec3(4.5, 1, 4.5)).y,
                helper.absoluteVec(new Vec3(4.5, 1, 4.5)).z, 0F, 0F);
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
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 1, 6));
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
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 7));
        helper.spawn(EntityType.VILLAGER, new BlockPos(5, 1, 7));
        helper.spawn(EntityType.WOLF, new BlockPos(3, 1, 7));
        helper.spawn(EntityType.COW, new BlockPos(2, 1, 6));
        helper.spawnItem(net.minecraft.world.item.Items.STICK, 7, 1, 7);

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
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 7));
        zombie.setTarget(player);
        helper.assertTrue(zombie.getTarget() == player, "precondition: zombie hunts the player");

        helper.assertTrue(PowerManager.activate(player, VtmMod.id("cloud_memory")), "cloud_memory should work");
        helper.assertTrue(zombie.getTarget() == null, "calmed zombie must drop its target");
        zombie.setTarget(player); // vanilla retargeting
        helper.assertTrue(zombie.getTarget() == null, "calmed zombie must refuse to retarget the player");

        Mob other = helper.spawn(EntityType.SKELETON, new BlockPos(2, 1, 2));
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
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 1, 6));
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 1, 6));
        helper.assertFalse(Feeding.bite(player, cow), "ventrue must refuse animal blood");
        helper.assertTrue(Feeding.bite(player, cow, true), "...unless the Beast is in control");
        Feeding.forget(player.getUUID());
        helper.assertTrue(Feeding.bite(player, villager), "ventrue should drink human blood");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void feedingBanesChangeTheBite(GameTestHelper helper) {
        ServerPlayer hecata = vampire(helper, "hecata");
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 1, 6));
        Feeding.bite(hecata, cow);
        helper.assertTrue(cow.getHealth() <= 5.5F, "hecata bite should hurt a lot, cow has " + cow.getHealth());

        ServerPlayer tremere = vampire(helper, "tremere");
        float before = VampireData.of(tremere).blood();
        Feeding.bite(tremere, helper.spawn(EntityType.COW, new BlockPos(2, 1, 6)));
        float gained = VampireData.of(tremere).blood() - before;
        helper.assertTrue(Math.abs(gained - 0.75F) < 0.01F, "tremere keeps 75% of the blood, gained " + gained);

        ServerPlayer salubri = vampire(helper, "salubri");
        float health = salubri.getHealth();
        Feeding.bite(salubri, helper.spawn(EntityType.COW, new BlockPos(6, 1, 6)));
        helper.assertTrue(salubri.getHealth() < health, "salubri should be hurt by biting");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hungerTriggersFrenzyAndTheBeastFeeds(GameTestHelper helper) {
        ServerPlayer player = vampire(helper, "banu_haqim");
        VampireManager.set(player, VampireData.of(player).withBlood(1F));
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 1, 5));
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
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 6));
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
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 1, 6));
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
        helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 7));

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

    @GameTest(template = "empty")
    public static void sunlightRules(GameTestHelper helper) {
        helper.assertTrue(VampireRules.maxBloodPotency(13) == 2 && VampireRules.maxBloodPotency(4) == 10, "bp table");
        helper.assertTrue(VampireRules.learnCost(3, true) == 15 && VampireRules.learnCost(3, false) == 21, "costs");
        helper.assertTrue(VampireRules.hunger(VampireData.DEFAULT.withSpecies(com.epi0.vtm.vampire.Species.VAMPIRE)
                .withBloodPotency(1).withBlood(0F)) == 5, "empty pool is max hunger");
        helper.succeed();
    }
}
