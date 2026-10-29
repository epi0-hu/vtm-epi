package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.city.CityLife;
import com.epi0.vtm.city.CityRegistry;
import com.epi0.vtm.city.Hunters;
import com.epi0.vtm.dialogue.DialogueManager;
import com.epi0.vtm.dialogue.Effect;
import com.epi0.vtm.nemesis.NemesisData;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.nemesis.NemesisRecord;
import com.epi0.vtm.nemesis.Role;
import com.epi0.vtm.npc.Attitude;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Job;
import com.epi0.vtm.npc.NpcMemory;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.npc.NpcWitness;
import com.epi0.vtm.registry.ModAttachments;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** The hunters: exposure, the people who come for you, what they do to a vampire, and how they fall. */
@GameTestHolder(VtmMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class HuntersGameTests {

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(HuntersGameTests.class);
    }

    private static final ResourceLocation CAMARILLA = VtmMod.id("camarilla");

    private static City makeCity(GameTestHelper helper, String name) {
        City layout = new City(UUID.randomUUID(), name, Level.OVERWORLD, helper.absolutePos(new BlockPos(4, 2, 4)), CAMARILLA);
        for (int i = 0; i < 4; i++) {
            layout.homes.add(helper.absolutePos(new BlockPos(1 + i * 20, 2, 1)));
        }
        layout.plaza = helper.absolutePos(new BlockPos(4, 2, 4));
        CityData.get(helper.getLevel().getServer()).clear();
        return CityRegistry.register(helper.getLevel(), layout.center, name, CAMARILLA, CityRegistry.write(layout));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return NemesisGameTests.player(helper);
    }

    private static int hunters(GameTestHelper helper, City city) {
        return helper.getLevel().getEntitiesOfClass(CivilianNpc.class, new net.minecraft.world.phys.AABB(city.center).inflate(120),
                c -> c.job() == Job.HUNTER && c.isAlive()).size();
    }

    // ---------------------------------------------------------------- exposure

    @GameTest(template = "empty", batch = "hunt_exposure")
    public static void beingSeenExposesAVampireAndItFades(GameTestHelper helper) {
        City city = makeCity(helper, "Seenford");
        ServerPlayer player = player(helper);
        var witness = NpcSpawner.civilian(helper.getLevel(), helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 6.5)));
        witness.settle(city, city.homes.get(0), Job.RESIDENT, null);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 4.5)));
        NpcWitness.broadcast(player, NpcMemory.Event.WITNESSED_FEEDING, 16);
        helper.assertTrue(city.exposureOf(player.getUUID()) == 12, "feeding in front of a mortal exposes you: " + city.exposureOf(player.getUUID()));
        NpcWitness.broadcast(player, NpcMemory.Event.WITNESSED_FRENZY, 16);
        helper.assertTrue(city.exposureOf(player.getUUID()) == 30 && city.alert() == 30, "and a frenzy more: " + city.exposureOf(player.getUUID()));

        ServerPlayer mortal = VtmGameTests.survivalPlayer(helper);
        Hunters.expose(mortal, city, 40);
        helper.assertTrue(city.exposureOf(mortal.getUUID()) == 0, "a mortal has nothing to be exposed");

        var copy = City.load(city.save());
        helper.assertTrue(copy.exposureOf(player.getUUID()) == 30, "exposure is remembered");
        helper.assertTrue(Hunters.wanted(10) == 0 && Hunters.wanted(30) == 1 && Hunters.wanted(55) == 2 && Hunters.wanted(80) == 4 && Hunters.wanted(95) == 6,
                "and the hunters it brings grow with it");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the people who come

    @GameTest(template = "empty", batch = "hunt_pursuit")
    public static void hunterssComeInProportionAndAreHostileOnlyWhenSure(GameTestHelper helper) {
        City city = makeCity(helper, "Huntford");
        var level = helper.getLevel();
        ServerPlayer player = player(helper);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 4.5)));

        city.addExposure(player.getUUID(), 20);
        helper.assertTrue(Hunters.tick(level, city) == 0, "a little exposure brings no one");

        city.addExposure(player.getUUID(), 15); // 35
        city.lastHunt = -100000L;
        helper.assertTrue(Hunters.tick(level, city) == 1 && hunters(helper, city) == 1, "a stranger who watches");
        CivilianNpc watcher = level.getEntitiesOfClass(CivilianNpc.class, new net.minecraft.world.phys.AABB(city.center).inflate(120), c -> c.job() == Job.HUNTER).get(0);
        helper.assertTrue(watcher.attitudeTo(player) == Attitude.WARY && watcher.getMainHandItem().isEmpty(), "unarmed and wary at 35");
        helper.assertTrue(watcher.factionId().equals(Hunters.FACTION), "of the hunters' faction");

        city.addExposure(player.getUUID(), 20); // 55
        helper.assertTrue(watcher.attitudeTo(player) == Attitude.HOSTILE, "hostile once they are sure");
        helper.assertTrue(DialogueManager.open(player, watcher) == false, "and they will not talk");

        ServerPlayer mortal = VtmGameTests.survivalPlayer(helper);
        helper.assertTrue(watcher.attitudeTo(mortal) != Attitude.HOSTILE, "a mortal is nothing to them");

        city.exposure.put(player.getUUID(), 80);
        city.lastHunt = -100000L;
        Hunters.tick(level, city);
        city.lastHunt = -100000L;
        Hunters.tick(level, city);
        city.lastHunt = -100000L;
        Hunters.tick(level, city);
        city.lastHunt = -100000L;
        Hunters.tick(level, city);
        helper.assertTrue(city.inquisitor != null, "at 80 an inquisitor leads the strike team");
        helper.assertTrue(hunters(helper, city) >= 4, "with hunters about him: " + hunters(helper, city));
        helper.succeed();
    }

    // ---------------------------------------------------------------- fighting them

    @GameTest(template = "empty", batch = "hunt_fight")
    public static void huntersHitVampiresHarderAndAnInquisitorBreaksTheCell(GameTestHelper helper) {
        City city = makeCity(helper, "Fightford");
        var level = helper.getLevel();
        ServerPlayer player = player(helper);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 4.5)));
        CivilianNpc hunter = Hunters.spawn(level, city, helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 6.5)), false);
        CivilianNpc plain = NpcSpawner.civilian(level, helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, 6.5)));

        player.setHealth(20F);
        player.invulnerableTime = 0;
        player.hurt(level.damageSources().mobAttack(plain), 4F);
        float plainLoss = 20F - player.getHealth();
        player.setHealth(20F);
        player.invulnerableTime = 0;
        player.hurt(level.damageSources().mobAttack(hunter), 4F);
        float hunterLoss = 20F - player.getHealth();
        helper.assertTrue(hunterLoss > plainLoss, "a hunter's blow lands harder on a vampire: " + hunterLoss + " vs " + plainLoss);

        CivilianNpc inquisitor = Hunters.spawn(level, city, helper.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 2, 6.5)), true);
        city.inquisitor = inquisitor.getUUID();
        city.exposure.put(player.getUUID(), 90);
        helper.assertTrue(inquisitor.getMaxHealth() > hunter.getMaxHealth() && !inquisitor.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(),
                "the inquisitor is tougher and better armed");
        int xp = VampireData.of(player).experience();
        inquisitor.die(level.damageSources().playerAttack(player));
        helper.assertTrue(city.inquisitor == null && city.exposureOf(player.getUUID()) == 50, "killing the inquisitor breaks the cell: " + city.exposureOf(player.getUUID()));
        helper.assertTrue(VampireData.of(player).experience() == xp + 3, "and teaches something");
        hunter.die(level.damageSources().playerAttack(player));
        helper.assertTrue(city.exposureOf(player.getUUID()) == 56, "but killing an ordinary hunter makes the others angrier: " + city.exposureOf(player.getUUID()));
        helper.succeed();
    }

    // ---------------------------------------------------------------- talking your way out

    @GameTest(template = "empty", batch = "hunt_talk")
    public static void aSuspiciousHunterCanBeTalkedRound(GameTestHelper helper) {
        City city = makeCity(helper, "Talkford");
        var level = helper.getLevel();
        ServerPlayer player = player(helper);
        city.exposure.put(player.getUUID(), 40);
        CivilianNpc hunter = Hunters.spawn(level, city, helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 6.5)), false);
        helper.setDayTime(18000);
        helper.assertTrue(hunter.attitudeTo(player) == Attitude.WARY, "wary at 40");
        helper.assertTrue(DialogueManager.open(player, hunter) && DialogueManager.session(player).tree().equals(VtmMod.id("civilian_hunter")), "a hunter has his own conversation");
        int explain = VtmGameTests.option(player, "dialogue.vtm.hunter.opt.explain");
        helper.assertTrue(explain >= 0, "and you can try to explain yourself");
        DialogueManager.end(player);

        Effect calm = Effect.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"exposure\": -20}")).getOrThrow();
        calm.apply(player, hunter);
        helper.assertTrue(city.exposureOf(player.getUUID()) == 20, "a convincing answer lowers exposure: " + city.exposureOf(player.getUUID()));
        helper.succeed();
    }

    // ---------------------------------------------------------------- raids

    @GameTest(template = "empty", batch = "hunt_raid")
    public static void huntersRaidTheHavensOfAnAlarmedCity(GameTestHelper helper) {
        City city = makeCity(helper, "Raidford");
        var server = helper.getLevel().getServer();
        NemesisData data = NemesisManager.data(server);
        data.reset();
        NemesisRecord victim = NemesisManager.create(server, CAMARILLA, Role.AGENT, RandomSource.create(5L));
        victim.lair = helper.absolutePos(new BlockPos(6, 2, 6));
        victim.lairDimension = helper.getLevel().dimension();
        data.setDirty();
        UUID wanted = UUID.randomUUID();

        city.exposure.put(wanted, 30);
        Hunters.raids(server, data, RandomSource.create(1L));
        helper.assertTrue(data.chronicle().stream().noneMatch(c -> c.key().startsWith("hunters_raid")), "no raid at 30");

        boolean raided = false;
        RandomSource luck = RandomSource.create(100L);
        for (int i = 0; i < 400 && !raided; i++) {
            city.exposure.put(wanted, 90);
            Hunters.raids(server, data, luck);
            raided = data.chronicle().stream().anyMatch(c -> c.key().startsWith("hunters_raid"));
        }
        helper.assertTrue(raided, "a city this alarmed is raided sooner or later");
        helper.assertTrue(city.exposureOf(wanted) < 90, "and the raid spends their anger");
        data.reset();
        helper.succeed();
    }
}
