package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.city.CityLife;
import com.epi0.vtm.city.CityRegistry;
import com.epi0.vtm.city.Territory;
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

    private static void reset(GameTestHelper helper) {
        CityData.get(server(helper)).clear();
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

    // ---------------------------------------------------------------- metropolis

    @GameTest(template = "empty", batch = "metro_plan")
    public static void aMetropolisPlanIsAPureFunctionOfItsSeed(GameTestHelper helper) {
        for (long seed : new long[]{1L, 42L, -7L, 123456789L, 99L}) {
            var plan = com.epi0.vtm.world.CityPlan.of(seed);
            var again = new java.util.ArrayList<>(com.epi0.vtm.world.CityPlan.of(seed).lots);
            helper.assertTrue(plan.lots.equals(again), "the same seed gives the same plan (" + seed + ")");
            helper.assertTrue(plan.size == com.epi0.vtm.world.CityPlan.size() && plan.size > 200, "a big city: " + plan.size);
            var kinds = new java.util.EnumMap<com.epi0.vtm.world.CityPlan.Kind, Integer>(com.epi0.vtm.world.CityPlan.Kind.class);
            long cells = 0;
            for (var lot : plan.lots) {
                kinds.merge(lot.kind(), 1, Integer::sum);
                cells += (long) lot.span() * lot.span();
                helper.assertTrue(lot.x() >= 0 && lot.z() >= 0 && lot.x() + lot.size() <= plan.size && lot.z() + lot.size() <= plan.size, "every lot is inside the city");
            }
            helper.assertTrue(cells == (long) com.epi0.vtm.world.CityPlan.N * com.epi0.vtm.world.CityPlan.N, "and every cell of the grid is built on exactly once: " + cells);
            for (var a : plan.lots) {
                for (var b : plan.lots) {
                    if (a != b) {
                        boolean apart = a.x() + a.size() <= b.x() || b.x() + b.size() <= a.x() || a.z() + a.size() <= b.z() || b.z() + b.size() <= a.z();
                        helper.assertTrue(apart, "lots do not overlap");
                    }
                }
            }
            helper.assertTrue(kinds.getOrDefault(com.epi0.vtm.world.CityPlan.Kind.PLAZA, 0) == 1
                    && kinds.getOrDefault(com.epi0.vtm.world.CityPlan.Kind.HAVEN, 0) == 1
                    && kinds.getOrDefault(com.epi0.vtm.world.CityPlan.Kind.ASYLUM, 0) == 1
                    && kinds.getOrDefault(com.epi0.vtm.world.CityPlan.Kind.CHURCH, 0) >= 1
                    && kinds.getOrDefault(com.epi0.vtm.world.CityPlan.Kind.WATCH, 0) >= 2
                    && kinds.getOrDefault(com.epi0.vtm.world.CityPlan.Kind.TOWER, 0) >= 5
                    && kinds.getOrDefault(com.epi0.vtm.world.CityPlan.Kind.HOUSE, 0) >= 5, "one plaza, haven and asylum, a church, two watch stations, towers and houses: " + kinds);
            var plaza = plan.first(com.epi0.vtm.world.CityPlan.Kind.PLAZA);
            helper.assertTrue(plaza.centreX() == plan.size / 2 || Math.abs(plaza.centreX() - plan.size / 2) <= 1, "the plaza is in the middle");
        }
        helper.assertTrue(!com.epi0.vtm.world.CityPlan.of(1L).lots.equals(com.epi0.vtm.world.CityPlan.of(2L).lots), "different seeds give different cities");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "metro_scale")
    public static void bigCitiesHoldMorePeople(GameTestHelper helper) {
        City town = new City(java.util.UUID.randomUUID(), "Town", helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 2, 1)), CAMARILLA);
        City metropolis = new City(java.util.UUID.randomUUID(), "Metro", helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 2, 1)), CAMARILLA);
        metropolis.radius = 120;
        for (int i = 0; i < 200; i++) {
            town.homes.add(new BlockPos(i, 2, 0));
            metropolis.homes.add(new BlockPos(i, 2, 0));
        }
        helper.assertTrue(CityLife.targetPopulation(town) == CityLife.MAX_POPULATION, "a town holds sixteen");
        helper.assertTrue(CityLife.targetPopulation(metropolis) >= 60, "a metropolis holds far more: " + CityLife.targetPopulation(metropolis));
        helper.assertTrue(metropolis.contains(helper.getLevel(), metropolis.center.offset(110, 0, -110)) && !town.contains(helper.getLevel(), town.center.offset(110, 0, 0)),
                "and reaches further");
        var copy = City.load(metropolis.save());
        helper.assertTrue(copy.radius == 120, "the radius is remembered");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "metro_gen", timeoutTicks = 1800)
    public static void metropolisesAreBuiltAndRegistered(GameTestHelper helper) {
        BlockPos arena = helper.absolutePos(new BlockPos(4, 2, 4));
        int centreOffset = com.epi0.vtm.world.CityPlan.lotStart(com.epi0.vtm.world.CityPlan.CENTRE) + com.epi0.vtm.world.CityPlan.LOT / 2;
        BlockPos at = arena.offset(-centreOffset, 0, -centreOffset);
        var level = helper.getLevel();
        int minCx = (at.getX() >> 4) - 1;
        int minCz = (at.getZ() >> 4) - 1;
        int chunks = com.epi0.vtm.world.CityPlan.size() / 16 + 4;
        java.util.List<net.minecraft.world.level.ChunkPos> forced = new java.util.ArrayList<>();
        for (int cx = 0; cx < chunks; cx++) {
            for (int cz = 0; cz < chunks; cz++) {
                level.setChunkForced(minCx + cx, minCz + cz, true);
                forced.add(new net.minecraft.world.level.ChunkPos(minCx + cx, minCz + cz));
            }
        }
        helper.runAfterDelay(120, () -> {
            var source = server(helper).createCommandSourceStack().withLevel(level)
                    .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(at)).withPermission(4);
            server(helper).getCommands().performPrefixedCommand(source, "place structure vtm:metropolis " + at.getX() + " " + at.getY() + " " + at.getZ());
        });
        helper.succeedWhen(() -> {
            City city = CityData.get(server(helper)).all().stream()
                    .filter(c -> c.radius > 100 && Math.abs(c.center.getX() - arena.getX()) <= 16 && Math.abs(c.center.getZ() - arena.getZ()) <= 16)
                    .findFirst().orElse(null);
            helper.assertTrue(city != null, "the Domain Stone registers the metropolis once its chunk is live");
            forced.forEach(p -> level.setChunkForced(p.x, p.z, false));
            helper.assertTrue(city.homes.size() >= 150, "with beds for a great many people: " + city.homes.size());
            helper.assertTrue(city.works.stream().filter(w -> w.job() == Job.SHOPKEEPER).count() >= 6
                    && city.works.stream().anyMatch(w -> w.job() == Job.BARTENDER)
                    && city.works.stream().filter(w -> w.job() == Job.WATCHMAN).count() >= 4, "and work: " + city.works.size());
            helper.assertTrue(city.plaza != null && city.club != null && city.watch != null && city.haven != null, "a plaza, a club, a watch station and a haven");
            helper.assertTrue(level.getBlockState(city.center).is(com.epi0.vtm.registry.ModBlocks.CITY_HEART.get()), "the Domain Stone stands in the plaza");
            int checked = 0;
            for (BlockPos bed : city.homes) {
                if (level.isLoaded(bed)) {
                    helper.assertTrue(level.getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock, "a bed at " + bed);
                    checked++;
                }
            }
            helper.assertTrue(checked >= 150, "and the beds are really there: " + checked);
            // The wall stands at the edge, and the avenue is open through it.
            BlockPos wall = city.center.offset(-centreOffset + 1, -1, -centreOffset + 20);
            var wallBlock = level.getBlockState(wall);
            helper.assertTrue(wallBlock.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS) || wallBlock.is(net.minecraft.world.level.block.Blocks.CRACKED_STONE_BRICKS)
                    || wallBlock.is(net.minecraft.world.level.block.Blocks.MOSSY_STONE_BRICKS), "a wall rings the city, found " + wallBlock + " at " + wall);
            // The other tests share this ground: forget the city so that none of them finds itself inside it.
            CityData.get(server(helper)).clear();
        });
    }

    // ---------------------------------------------------------------- people

    @GameTest(template = "empty", batch = "city_people")
    public static void peopleMoveInAndKeepAnHourlyRound(GameTestHelper helper) {
        reset(helper);
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
        reset(helper);
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
        reset(helper);
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
        reset(helper);
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
        reset(helper);
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

    // ---------------------------------------------------------------- territory

    private static final ResourceLocation SABBAT = VtmMod.id("sabbat");

    private static com.epi0.vtm.nemesis.NemesisData nemeses(GameTestHelper helper) {
        com.epi0.vtm.court.CourtData.get(server(helper)).clear();
        var data = com.epi0.vtm.nemesis.NemesisManager.data(server(helper));
        data.reset();
        return data;
    }

    private static City makeCity(GameTestHelper helper, String name, ResourceLocation owner) {
        City city = makeCity(helper, name);
        city.owner = owner;
        city.warden = null;
        city.strength = Territory.HELD_STRENGTH;
        city.haven = helper.absolutePos(new BlockPos(6, 2, 6));
        return city;
    }

    private static void platform(GameTestHelper helper, int radius) {
        BlockPos centre = helper.absolutePos(new BlockPos(4, 1, 4));
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                helper.getLevel().setBlock(centre.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
    }

    @GameTest(template = "empty", batch = "terr_warden")
    public static void wardensHoldCitiesAndTheirLossWeakensThem(GameTestHelper helper) {
        var nem = nemeses(helper);
        var random = net.minecraft.util.RandomSource.create(5L);
        com.epi0.vtm.nemesis.NemesisSimulator.populate(server(helper), nem, random);
        com.epi0.vtm.nemesis.NemesisSimulator.succession(server(helper), nem);
        City city = makeCity(helper, "Wardenford", CAMARILLA);
        Territory.seatWarden(server(helper), nem, city);
        var warden = nem.get(city.warden);
        helper.assertTrue(warden != null && warden.faction.equals(CAMARILLA), "a Camarilla vampire holds a Camarilla city");
        helper.assertTrue(city.haven.equals(warden.lair) && warden.role != com.epi0.vtm.nemesis.Role.LEADER, "from the haven, and not the Prince");

        city.strength = 40;
        Territory.drift(nem, city);
        helper.assertTrue(city.strength == 41, "a held city firms up: " + city.strength + " warden " + city.warden);

        ServerPlayer player = player(helper);
        com.epi0.vtm.nemesis.NemesisManager.destroy(server(helper), warden, player, null);
        helper.assertTrue(city.warden == null && city.strength == 16, "killing the warden shakes the city: " + city.strength);
        helper.assertTrue(nem.chronicle().stream().anyMatch(c -> c.key().equals("city_weakened")), "and is chronicled");
        Territory.drift(nem, city);
        helper.assertTrue(city.strength == 15, "and without a warden it slips further");
        Territory.seatWarden(server(helper), nem, city);
        helper.assertTrue(city.warden != null && !city.warden.equals(warden.id), "until a replacement is found");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "terr_war")
    public static void factionsAtWarTakeCitiesFromEachOther(GameTestHelper helper) {
        var nem = nemeses(helper);
        var random = net.minecraft.util.RandomSource.create(9L);
        com.epi0.vtm.nemesis.NemesisSimulator.populate(server(helper), nem, random);
        com.epi0.vtm.nemesis.NemesisSimulator.succession(server(helper), nem);
        City city = makeCity(helper, "Contested", CAMARILLA);
        city.strength = 10;
        nem.inFaction(SABBAT).forEach(r -> r.level = 18);
        var band = nem.inFaction(SABBAT).stream().limit(3).toList();

        boolean taken = false;
        for (int i = 0; i < 60 && !taken; i++) {
            nem.inFaction(SABBAT).forEach(r -> {
                if (r.status != com.epi0.vtm.nemesis.Status.ACTIVE) {
                    r.status = com.epi0.vtm.nemesis.Status.ACTIVE;
                }
            });
            taken = Territory.resolveAssault(server(helper), nem, city, SABBAT, band, random);
        }
        helper.assertTrue(taken && city.owner.equals(SABBAT), "a strong enough assault takes the city");
        helper.assertTrue(city.strength == Territory.CAPTURED_STRENGTH && city.warden == null, "which starts out weakly held");
        helper.assertTrue(nem.chronicle().stream().anyMatch(c -> c.key().equals("city_taken") && c.publicNews()), "and everyone hears of it");

        // Only enemies attack: the Anarch have no quarrel with a Camarilla city, so over many wars it only ever changes hands to the Sabbat.
        City peaceful = makeCity(helper, "Peacetown", CAMARILLA);
        peaceful.owner = CAMARILLA;
        for (int i = 0; i < 400; i++) {
            nem.inFaction(ANARCH).forEach(r -> r.status = com.epi0.vtm.nemesis.Status.ACTIVE);
            Territory.war(server(helper), nem, CityData.get(server(helper)), random, 5.0);
            if (!peaceful.owner.equals(CAMARILLA)) {
                break;
            }
        }
        helper.assertTrue(peaceful.owner.equals(CAMARILLA) || peaceful.owner.equals(SABBAT), "the Anarch do not march on the Camarilla: " + peaceful.owner);
        helper.succeed();
    }

    private static final ResourceLocation ANARCH = VtmMod.id("anarch");

    @GameTest(template = "empty", batch = "terr_patrol", timeoutTicks = 200)
    public static void rulersPatrolTheirStreetsAtNightAndEnforceTheMasquerade(GameTestHelper helper) {
        reset(helper);
        nemeses(helper);
        City city = makeCity(helper, "Patrolton", CAMARILLA);
        platform(helper, 30);
        var level = helper.getLevel();
        helper.setDayTime(18000);
        ServerPlayer player = player(helper);
        int spawned = CityLife.patrol(level, city);
        helper.assertTrue(spawned == 1 + city.strength / 40, "a patrol walks the streets: " + spawned);
        helper.assertTrue(CityLife.patrol(level, city) == 0, "and is not topped up while it is there");
        var patrol = level.getEntitiesOfClass(com.epi0.vtm.npc.VampireNpc.class, new net.minecraft.world.phys.AABB(city.center).inflate(60),
                v -> city.id.equals(v.patrol())).get(0);
        helper.assertTrue(patrol.factionId().equals(CAMARILLA), "of the ruling faction");
        helper.assertTrue(patrol.attitudeTo(player) != Attitude.HOSTILE, "who leave a quiet player alone");
        city.heat.put(player.getUUID(), 70);
        helper.assertTrue(patrol.attitudeTo(player) == Attitude.HOSTILE, "but hunt one who has broken the Masquerade");

        city.owner = SABBAT;
        helper.assertTrue(!Territory.enforcesMasquerade(level.registryAccess(), city), "the Sabbat do not care");
        city.owner = CAMARILLA;

        com.epi0.vtm.sect.SectManager.set(player, com.epi0.vtm.sect.Membership.DEFAULT);
        Territory.masqueradeBreach(player, city);
        helper.assertTrue(com.epi0.vtm.sect.Membership.of(player).standingWith(CAMARILLA) == com.epi0.vtm.sect.SectManager.MASQUERADE_BREACH,
                "even a stranger loses standing with a ruler who keeps the Masquerade");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "terr_know")
    public static void playersLearnCitiesByVisitingAndAsking(GameTestHelper helper) {
        var nem = nemeses(helper);
        CityData cities = CityData.get(server(helper));
        City here = makeCity(helper, "Hereford", CAMARILLA);
        ServerPlayer player = player(helper);
        helper.assertTrue(com.epi0.vtm.nemesis.NemesisViews.build(server(helper), player).domains().stream().noneMatch(d -> d.id().equals(here.id)),
                "a city you have not been to is not on your map");
        com.epi0.vtm.city.CityEvents.announce(player);
        helper.assertTrue(com.epi0.vtm.city.CityKnowledge.of(player).knows(here.id), "walking in discovers it");
        var domain = com.epi0.vtm.nemesis.NemesisViews.build(server(helper), player).domains().stream().filter(d -> d.id().equals(here.id)).findFirst().orElseThrow();
        helper.assertTrue(domain.owner().equals(CAMARILLA) && domain.strength() == here.strength && domain.warden().isEmpty(), "with who rules it, and no warden named");

        var random = net.minecraft.util.RandomSource.create(2L);
        com.epi0.vtm.nemesis.NemesisSimulator.populate(server(helper), nem, random);
        Territory.seatWarden(server(helper), nem, here);
        var warden = nem.get(here.warden);
        com.epi0.vtm.nemesis.Intel.reveal(player, warden, random);
        domain = com.epi0.vtm.nemesis.NemesisViews.build(server(helper), player).domains().stream().filter(d -> d.id().equals(here.id)).findFirst().orElseThrow();
        helper.assertTrue(domain.warden().isPresent(), "once you know the warden, the map names them");

        long unknown = cities.all().stream().filter(c -> !com.epi0.vtm.city.CityKnowledge.of(player).knows(c.id)).count();
        for (long i = 0; i < unknown; i++) {
            helper.assertTrue(Territory.revealCity(player, random).isPresent(), "asking around turns up another city");
        }
        helper.assertTrue(Territory.revealCity(player, random).isEmpty(), "until there are none left");
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        var snap = com.epi0.vtm.nemesis.NemesisViews.build(server(helper), player);
        com.epi0.vtm.network.NemesisViewsPayload.STREAM_CODEC.encode(buf, new com.epi0.vtm.network.NemesisViewsPayload(snap));
        helper.assertTrue(com.epi0.vtm.network.NemesisViewsPayload.STREAM_CODEC.decode(buf).snapshot().domains().size() == snap.domains().size(), "the map survives the wire");
        helper.succeed();
    }
}
