package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.city.CityLife;
import com.epi0.vtm.city.CityRegistry;
import com.epi0.vtm.dialogue.Dialogues;
import com.epi0.vtm.npc.Attitude;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Job;
import com.epi0.vtm.npc.NpcMemory;
import com.epi0.vtm.npc.NpcWitness;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Cities: generation, the people who live in them, their daily round, and what trouble does. */
@GameTestHolder(VtmMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CityGameTests {

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(CityGameTests.class);
    }

    private static final ResourceLocation CAMARILLA = VtmMod.id("camarilla");

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    /** A city with a few beds and workplaces around the arena, registered without any structure. */
    private static City makeCity(GameTestHelper helper, String name) {
        City layout = new City(java.util.UUID.randomUUID(), name, Level.OVERWORLD, helper.absolutePos(new BlockPos(4, 2, 4)), CAMARILLA);
        for (int i = 0; i < 8; i++) {
            layout.homes.add(helper.absolutePos(new BlockPos(1 + i, 2, 1)));
        }
        layout.works.add(new City.Work(Job.SHOPKEEPER, helper.absolutePos(new BlockPos(1, 2, 7))));
        layout.works.add(new City.Work(Job.BARTENDER, helper.absolutePos(new BlockPos(3, 2, 7))));
        layout.works.add(new City.Work(Job.WATCHMAN, helper.absolutePos(new BlockPos(5, 2, 7))));
        layout.works.add(new City.Work(Job.WATCHMAN, helper.absolutePos(new BlockPos(7, 2, 7))));
        layout.plaza = helper.absolutePos(new BlockPos(4, 2, 4));
        layout.club = helper.absolutePos(new BlockPos(7, 2, 4));
        return CityRegistry.register(helper.getLevel(), layout.center, name, CAMARILLA, CityRegistry.write(layout));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return NemesisGameTests.player(helper);
    }

    // ---------------------------------------------------------------- generation

    @GameTest(template = "empty", batch = "city_gen", timeoutTicks = 400)
    public static void citiesAreBuiltAndRegistered(GameTestHelper helper) {
        BlockPos arena = helper.absolutePos(new BlockPos(4, 2, 4));
        // The structure starts at the chunk of the given position and its Domain Stone is 32 blocks in: aim so it lands here.
        BlockPos at = arena.offset(-32, 0, -32);
        var level = helper.getLevel();
        var source = server(helper).createCommandSourceStack().withLevel(level)
                .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(at)).withPermission(4);
        server(helper).getCommands().performPrefixedCommand(source, "place structure vtm:city " + at.getX() + " " + at.getY() + " " + at.getZ());

        helper.succeedWhen(() -> {
            City city = CityData.get(server(helper)).all().stream()
                    .filter(c -> Math.abs(c.center.getX() - arena.getX()) <= 16 && Math.abs(c.center.getZ() - arena.getZ()) <= 16)
                    .findFirst().orElse(null);
            helper.assertTrue(city != null, "the Domain Stone registers the city once its chunk is live");
            helper.assertTrue(city.homes.size() >= 10, "with beds for its people: " + city.homes.size());
            helper.assertTrue(city.works.stream().anyMatch(w -> w.job() == Job.SHOPKEEPER)
                    && city.works.stream().anyMatch(w -> w.job() == Job.BARTENDER)
                    && city.works.stream().filter(w -> w.job() == Job.WATCHMAN).count() == 2, "and jobs for them");
            helper.assertTrue(city.plaza != null && city.club != null && city.watch != null && city.haven != null, "a plaza, a club, a watch station and a haven");
            helper.assertTrue(!city.name.isEmpty() && city.owner != null, "named, and ruled by someone");
            for (BlockPos bed : city.homes) {
                helper.assertTrue(level.getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock, "a bed at " + bed);
            }
            City.Work shop = city.works.stream().filter(w -> w.job() == Job.SHOPKEEPER).findFirst().orElseThrow();
            helper.assertTrue(level.getBlockState(shop.pos().above(0)).isAir() && !level.getBlockState(shop.pos().below()).isAir(),
                    "the shopkeeper's place is a floor with room above it");
            helper.assertTrue(level.getBlockState(city.center).is(com.epi0.vtm.registry.ModBlocks.CITY_HEART.get()), "the Domain Stone stands in the plaza");
        });
    }

    // ---------------------------------------------------------------- people

    @GameTest(template = "empty", batch = "city_people")
    public static void peopleMoveInAndKeepAnHourlyRound(GameTestHelper helper) {
        City city = makeCity(helper, "Testford");
        var level = helper.getLevel();
        for (int i = 0; i < 4; i++) {
            CityLife.populate(level, city);
        }
        List<CivilianNpc> residents = CityLife.residents(level, city);
        helper.assertTrue(residents.size() >= 6, "the city fills up: " + residents.size());
        helper.assertTrue(residents.stream().anyMatch(r -> r.job() == Job.SHOPKEEPER && r.work() != null)
                && residents.stream().anyMatch(r -> r.job() == Job.BARTENDER)
                && residents.stream().filter(r -> r.job() == Job.WATCHMAN).count() == 2, "every workplace is staffed");
        helper.assertTrue(residents.stream().filter(r -> r.job() == Job.WATCHMAN).allMatch(r -> r.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD)),
                "the watch is armed");
        int before = residents.size();
        CityLife.populate(level, city);
        helper.assertTrue(CityLife.residents(level, city).size() <= Math.max(before, CityLife.targetPopulation(city) + city.works.size()),
                "and it does not overflow");

        CivilianNpc shopkeeper = residents.stream().filter(r -> r.job() == Job.SHOPKEEPER).findFirst().orElseThrow();
        CivilianNpc bartender = residents.stream().filter(r -> r.job() == Job.BARTENDER).findFirst().orElseThrow();
        CivilianNpc watchman = residents.stream().filter(r -> r.job() == Job.WATCHMAN).findFirst().orElseThrow();
        CivilianNpc plain = residents.stream().filter(r -> r.job() == Job.RESIDENT).findFirst().orElseThrow();

        helper.setDayTime(3000);
        helper.assertTrue(shopkeeper.anchor().equals(shopkeeper.work()) && plain.anchor().equals(city.plaza) && !plain.shouldRestNow(), "morning: to work");
        helper.setDayTime(10500);
        helper.assertTrue(plain.anchor().equals(city.plaza) && shopkeeper.anchor().equals(shopkeeper.work()), "late afternoon: to the square");
        helper.setDayTime(14000);
        helper.assertTrue(bartender.anchor().equals(bartender.work()), "evening: the bartender is at the bar");
        helper.assertTrue(plain.anchor().equals(city.club) || plain.anchor().equals(plain.home()), "and everyone else is out or home");
        helper.assertTrue(!plain.shouldRestNow() && !watchman.shouldRestNow(), "nobody sleeps yet");
        helper.setDayTime(20000);
        helper.assertTrue(plain.anchor().equals(plain.home()) && plain.shouldRestNow(), "late: home to bed");
        helper.assertTrue(watchman.shouldRestNow() || watchman.anchor().equals(watchman.work()), "the watch keeps its own hours");
        helper.setDayTime(23000);
        helper.assertTrue(watchman.anchor().equals(watchman.work()) && !watchman.shouldRestNow(), "and is back on duty before dawn");
        helper.succeed();
    }

    // ---------------------------------------------------------------- trouble

    @GameTest(template = "empty", batch = "city_heat")
    public static void troubleMakesTheWatchHostileAndThePeopleAfraid(GameTestHelper helper) {
        City city = makeCity(helper, "Hotbed");
        var level = helper.getLevel();
        helper.setDayTime(3000);
        ServerPlayer player = player(helper);
        CivilianNpc watchman = CityLife.spawn(level, city, city.homes.get(0), city.homes.get(0), Job.WATCHMAN, null);
        CivilianNpc citizen = CityLife.spawn(level, city, city.homes.get(1), city.homes.get(1), Job.RESIDENT, null);
        helper.assertTrue(watchman.attitudeTo(player) == Attitude.NEUTRAL && citizen.attitudeTo(player) == Attitude.NEUTRAL, "a clean record");

        CityLife.crime(player, city, 35);
        helper.assertTrue(watchman.attitudeTo(player) == Attitude.WARY && citizen.attitudeTo(player) == Attitude.NEUTRAL, "35: the watch is suspicious, the people are not");
        CityLife.crime(player, city, 10);
        helper.assertTrue(citizen.attitudeTo(player) == Attitude.WARY, "45: the people take fright");
        CityLife.crime(player, city, 20);
        helper.assertTrue(watchman.attitudeTo(player) == Attitude.HOSTILE, "65: the watch attacks on sight");

        // Hurting a citizen, feeding in front of one, and killing one all count.
        int base = city.heatOf(player.getUUID());
        citizen.hurt(level.damageSources().playerAttack(player), 1F);
        helper.assertTrue(city.heatOf(player.getUUID()) == base + 8, "assault is remembered");
        base = city.heatOf(player.getUUID());
        player.moveTo(citizen.position());
        NpcWitness.broadcast(player, NpcMemory.Event.WITNESSED_FEEDING, 16);
        helper.assertTrue(city.heatOf(player.getUUID()) == base + 12, "feeding in front of a citizen is worse: " + base + " -> " + city.heatOf(player.getUUID()));
        city.heat.clear(); // (heat is capped at 100, so start the last one from a clean slate)
        base = 0;
        CivilianNpc victim = CityLife.spawn(level, city, city.homes.get(2), city.homes.get(2), Job.RESIDENT, null);
        victim.hurt(level.damageSources().playerAttack(player), 1000F);
        helper.assertTrue(city.heatOf(player.getUUID()) >= base + 30, "murder is worst of all");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "city_reinforce", timeoutTicks = 200)
    public static void wantedPlayersAreHuntedAndTroubleFades(GameTestHelper helper) {
        City city = makeCity(helper, "Watchtown");
        var level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(4, 1, 4));
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                level.setBlock(centre.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        ServerPlayer player = player(helper);
        CityLife.crime(player, city, 100);
        helper.assertTrue(city.heatOf(player.getUUID()) == 100, "wanted");
        CityLife.tick(server(helper));
        helper.assertTrue(city.heatOf(player.getUUID()) == 99, "time cools it, a little");
        long guards = level.getEntitiesOfClass(CivilianNpc.class, player.getBoundingBox().inflate(60),
                c -> c.job() == Job.WATCHMAN && c.getTarget() == player).size();
        helper.assertTrue(guards >= 1, "reinforcements come for a wanted player: " + guards);
        long before = city.lastReinforcement;
        CityLife.tick(server(helper));
        helper.assertTrue(city.lastReinforcement == before, "but not again straight away");
        helper.succeed();
    }

    // ---------------------------------------------------------------- conversation and persistence

    @GameTest(template = "empty", batch = "city_talk")
    public static void tradesTalkLikeTheirTrades(GameTestHelper helper) {
        City city = makeCity(helper, "Chatham");
        var level = helper.getLevel();
        helper.setDayTime(3000);
        ServerPlayer player = player(helper);
        for (Job job : new Job[]{Job.WATCHMAN, Job.SHOPKEEPER, Job.BARTENDER}) {
            CivilianNpc npc = CityLife.spawn(level, city, city.homes.get(0), city.homes.get(0), job, null);
            String tree = Dialogues.select(player, npc).orElseThrow().id().getPath();
            helper.assertTrue(tree.equals("civilian_" + job.id()), job + " talks as a " + job + ", not " + tree);
            helper.assertTrue(com.epi0.vtm.dialogue.DialogueManager.open(player, npc) || npc.state() != com.epi0.vtm.npc.VtmNpc.State.NORMAL, "and will talk");
            com.epi0.vtm.dialogue.DialogueManager.end(player);
        }
        CivilianNpc plain = CityLife.spawn(level, city, city.homes.get(1), city.homes.get(1), Job.RESIDENT, null);
        helper.assertTrue(Dialogues.select(player, plain).orElseThrow().id().getPath().equals("civilian"), "residents have no trade");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "city_save")
    public static void citiesSurviveASaveAndReload(GameTestHelper helper) {
        City city = makeCity(helper, "Saveford");
        city.strength = 71;
        city.owner = CAMARILLA;
        city.addHeat(java.util.UUID.nameUUIDFromBytes(new byte[]{1}), 44);
        city.club = null;
        City copy = City.load(city.save());
        helper.assertTrue(copy.name.equals("Saveford") && copy.strength == 71 && copy.owner.equals(CAMARILLA), "identity");
        helper.assertTrue(copy.homes.equals(city.homes) && copy.works.equals(city.works), "homes and workplaces");
        helper.assertTrue(copy.plaza.equals(city.plaza) && copy.club == null, "places");
        helper.assertTrue(copy.heatOf(java.util.UUID.nameUUIDFromBytes(new byte[]{1})) == 44, "and who is wanted");
        helper.succeed();
    }
}
