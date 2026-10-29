package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.block.HunterStandardBlockEntity;
import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.city.CityLife;
import com.epi0.vtm.city.CityRegistry;
import com.epi0.vtm.city.Hunters;
import com.epi0.vtm.city.LodgeData;
import com.epi0.vtm.city.Lodges;
import com.epi0.vtm.npc.Attitude;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Job;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.power.PowerExposure;
import com.epi0.vtm.power.Powers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** The hunters' lodges and the cost of using a power where mortals can see. */
@GameTestHolder(VtmMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class LodgeGameTests {

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(LodgeGameTests.class);
    }

    private static final ResourceLocation CAMARILLA = VtmMod.id("camarilla");

    private static City makeCity(GameTestHelper helper, String name) {
        City layout = new City(UUID.randomUUID(), name, Level.OVERWORLD, helper.absolutePos(new BlockPos(4, 2, 4)), CAMARILLA);
        for (int i = 0; i < 3; i++) {
            layout.homes.add(helper.absolutePos(new BlockPos(1 + i * 20, 2, 1)));
        }
        layout.plaza = helper.absolutePos(new BlockPos(4, 2, 4));
        CityData.get(helper.getLevel().getServer()).clear();
        return CityRegistry.register(helper.getLevel(), layout.center, name, CAMARILLA, CityRegistry.write(layout));
    }

    // ---------------------------------------------------------------- loud powers

    @GameTest(template = "empty", batch = "lodge_power")
    public static void usingAPowerInFrontOfMortalsTeachesThemWhatYouAre(GameTestHelper helper) {
        for (ResourceLocation id : PowerExposure.noisyPowers()) {
            helper.assertTrue(Powers.get(id).isPresent(), "every power in the noise table exists: " + id);
        }
        City city = makeCity(helper, "Noiseford");
        ServerPlayer player = NemesisGameTests.player(helper);
        player.moveTo(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
        helper.assertTrue(PowerExposure.cast(player, VtmMod.id("blood_bolt")) == 0 && city.exposureOf(player.getUUID()) == 0, "alone, nobody learns anything");

        var witness = NpcSpawner.civilian(helper.getLevel(), helper.absoluteVec(new Vec3(4.5, 2, 6.5)));
        witness.settle(city, city.homes.get(0), Job.RESIDENT, null);
        helper.assertTrue(PowerExposure.cast(player, VtmMod.id("blood_bolt")) == 12, "a blood bolt in front of a witness teaches them: " + city.exposureOf(player.getUUID()));
        helper.assertTrue(PowerExposure.cast(player, VtmMod.id("sense_the_beast")) == 0, "a quiet power teaches them nothing");
        helper.assertTrue(PowerExposure.cast(player, VtmMod.id("cloud_memory")) == -12 && city.exposureOf(player.getUUID()) == 0, "clouding memory undoes it");

        for (int i = 0; i < 2; i++) {
            var more = NpcSpawner.civilian(helper.getLevel(), helper.absoluteVec(new Vec3(3.5 + i * 2, 2, 7.5)));
            more.settle(city, city.homes.get(1 + i), Job.RESIDENT, null);
        }
        helper.assertTrue(PowerExposure.cast(player, VtmMod.id("blood_bolt")) == 24, "a crowd learns twice as much");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the lodge

    private static HunterStandardBlockEntity standard(GameTestHelper helper, BlockPos centre) {
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                var chunk = helper.getLevel().getChunk((centre.getX() >> 4) + cx, (centre.getZ() >> 4) + cz);
                for (var be : chunk.getBlockEntities().values()) {
                    if (be instanceof HunterStandardBlockEntity standard) {
                        return standard;
                    }
                }
            }
        }
        return null;
    }

    @GameTest(template = "empty", batch = "lodge_gen", timeoutTicks = 400)
    public static void lodgesStandWithAGarrisonAndBreakWhenTheirMasterFalls(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        LodgeData.get(server).clear();
        BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
        var source = server.createCommandSourceStack().withLevel(level).withPosition(Vec3.atCenterOf(centre)).withPermission(4);
        server.getCommands().performPrefixedCommand(source, "place structure vtm:hunter_lodge " + centre.getX() + " " + centre.getY() + " " + centre.getZ());
        var standard = standard(helper, centre);
        helper.assertTrue(standard != null, "the lodge flies a standard");
        BlockPos flag = standard.getBlockPos();
        ServerPlayer player = NemesisGameTests.player(helper);
        player.moveTo(Vec3.atBottomCenterOf(flag.above(2)));

        helper.succeedWhen(() -> {
            LodgeData.Lodge lodge = LodgeData.get(server).at(level.dimension(), flag);
            helper.assertTrue(lodge != null, "the standard registers its lodge once its chunk is live");
            if (lodge.master == null) {
                Lodges.tick(server);
            }
            List<CivilianNpc> hunters = level.getEntitiesOfClass(CivilianNpc.class, new AABB(flag).inflate(48), c -> lodge.id.equals(c.lodgeId()) && c.isAlive());
            helper.assertTrue(hunters.size() == Lodges.GARRISON + 1, "a master and a garrison are raised: " + hunters.size());
            CivilianNpc master = hunters.stream().filter(h -> h.getUUID().equals(lodge.master)).findFirst().orElse(null);
            helper.assertTrue(master != null && master.getMaxHealth() > 40F, "the master is the toughest of them");
            helper.assertTrue(hunters.stream().allMatch(h -> h.job() == Job.HUNTER && h.factionId().equals(Hunters.FACTION)), "all hunters");

            // Who they are hostile to depends on how well the hunters know the player in the cities about.
            City city = makeCity(helper, "Lodgeford");
            helper.assertTrue(master.attitudeTo(player) != Attitude.HOSTILE, "unknown, a vampire may walk in");
            city.exposure.put(player.getUUID(), 60);
            helper.assertTrue(master.attitudeTo(player) == Attitude.HOSTILE && Lodges.notoriety(server, level.dimension(), flag, player.getUUID()) == 60,
                    "known, they are on them at once");
            helper.assertTrue(Lodges.supports(server, city) && Hunters.wanted(60, true) == Hunters.wanted(60) + 2, "and the city is backed by the lodge");

            // The master falls: the lodge is broken, the cities forget a little, and nothing is raised for a week.
            long now = server.overworld().getGameTime();
            int xp = com.epi0.vtm.vampire.VampireData.of(player).experience();
            master.die(level.damageSources().playerAttack(player));
            helper.assertTrue(lodge.master == null && lodge.brokenUntil > now, "the lodge is broken");
            helper.assertTrue(city.exposureOf(player.getUUID()) == 20 && com.epi0.vtm.vampire.VampireData.of(player).experience() == xp + 6, "the exposure and the reward");
            helper.assertTrue(!Lodges.supports(server, city), "a broken lodge no longer backs a city");
            hunters.stream().filter(h -> h != master).forEach(h -> h.discard());
            helper.assertTrue(Lodges.tick(server) == 0, "and its garrison is not raised again");
            lodge.brokenUntil = 0; // (a week passes)
            helper.assertTrue(Lodges.tick(server) >= 1 + Lodges.GARRISON - 1 && lodge.master != null, "until the week is out");
            CityData.get(server).clear();
            LodgeData.get(server).clear();
        });
    }
}
