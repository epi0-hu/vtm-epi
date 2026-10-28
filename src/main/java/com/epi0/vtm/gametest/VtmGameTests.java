package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.power.Power;
import com.epi0.vtm.power.PowerKind;
import com.epi0.vtm.power.PowerManager;
import com.epi0.vtm.power.Powers;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.vampire.Feeding;
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
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
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

    private static ServerPlayer vampire(GameTestHelper helper, String clan) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
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
        helper.assertTrue(Powers.all().size() == 60, "expected 60 powers, got " + Powers.all().size());
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

    @GameTest(template = "empty")
    public static void sunlightRules(GameTestHelper helper) {
        helper.assertTrue(VampireRules.maxBloodPotency(13) == 2 && VampireRules.maxBloodPotency(4) == 10, "bp table");
        helper.assertTrue(VampireRules.learnCost(3, true) == 15 && VampireRules.learnCost(3, false) == 21, "costs");
        helper.assertTrue(VampireRules.hunger(VampireData.DEFAULT.withSpecies(com.epi0.vtm.vampire.Species.VAMPIRE)
                .withBloodPotency(1).withBlood(0F)) == 5, "empty pool is max hunger");
        helper.succeed();
    }
}
