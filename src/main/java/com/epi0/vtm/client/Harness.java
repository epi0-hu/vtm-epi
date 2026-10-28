package com.epi0.vtm.client;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.power.PowerManager;
import com.epi0.vtm.power.Powers;
import com.epi0.vtm.vampire.Frenzy;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only scripted walkthrough that drives the real client and saves screenshots to {@code run/screenshots}.
 * Inert unless the JVM is started with {@code -Dvtm.harness=true} (see the {@code harness} Gradle property).
 */
@EventBusSubscriber(modid = VtmMod.MOD_ID, value = Dist.CLIENT)
public final class Harness {
    private record Step(int delay, Runnable action) {}

    private static final String SCENARIO = System.getProperty("vtm.harness", "");
    private static final boolean ENABLED = !SCENARIO.isEmpty();
    private static final List<Step> STEPS = new ArrayList<>();
    private static int joined = -1;
    private static int clock;
    private static int index;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            return;
        }
        // The harness world remembers where it was last left, which can be somewhere that kills; start alive and safe.
        if (mc.player.isDeadOrDying()) {
            mc.player.respawn();
            mc.setScreen(null);
            joined = -1;
            return;
        }
        if (joined < 0) {
            joined = mc.player.tickCount;
            var server = mc.getSingleplayerServer();
            server.execute(() -> server.getPlayerList().getPlayers().forEach(p -> p.setGameMode(net.minecraft.world.level.GameType.CREATIVE)));
        }
        if (mc.player.tickCount - joined < 120) {
            return;
        }
        if (STEPS.isEmpty()) {
            if (SCENARIO.equals("natural")) {
                naturalScript(mc);
            } else if (SCENARIO.equals("locate")) {
                locateScript(mc);
            } else if (SCENARIO.equals("court")) {
                courtScript(mc);
            } else if (SCENARIO.equals("domains")) {
                domainsScript(mc);
            } else if (SCENARIO.equals("city")) {
                cityScript(mc);
            } else if (SCENARIO.equals("metropolis")) {
                metropolisScript(mc);
            } else if (SCENARIO.equals("sewer")) {
                structureScript(mc, "nosferatu_sewer", "nosferatu", new int[]{3, 1, 6}, new int[][]{
                        {17, 1, 4, 9, 1, 6}, {12, 1, 6, 3, 1, 6}, {7, 2, 3, 3, 0, 7}, {14, 1, 8, 14, 1, 13}, {18, 1, 4, 18, 9, 3}}, 0, "sewer");
            } else if (SCENARIO.equals("asylum")) {
                structureScript(mc, "the_asylum", "malkavian", new int[]{10, -4, 13}, new int[][]{
                        {10, 7, 33, 10, 2, 17}, {30, 9, 30, 10, 3, 14}, {10, 1, 15, 10, 1, 0}, {6, 7, 15, 12, 1, 3}, {16, 8, 14, 9, 1, 4},
                        {10, -3, 8, 10, -3, 0}, {10, -3, 9, 10, -3, 16}}, 2, "asylum");
            } else if (SCENARIO.equals("lairs")) {
                lairScript(mc);
            } else if (SCENARIO.equals("nemesis")) {
                nemesisScript(mc);
            } else if (SCENARIO.equals("sect")) {
                sectScript(mc);
            } else if (SCENARIO.equals("dialogue")) {
                dialogueScript(mc);
            } else if (SCENARIO.equals("npc")) {
                npcScript(mc);
            } else {
                script(mc);
            }
        }
        if (index >= STEPS.size()) {
            return;
        }
        if (++clock >= STEPS.get(index).delay) {
            clock = 0;
            STEPS.get(index++).action.run();
        }
    }

    private static void then(int delayTicks, Runnable action) {
        STEPS.add(new Step(delayTicks, action));
    }

    private static void server(Minecraft mc, int delay, java.util.function.Consumer<ServerPlayer> action) {
        then(delay, () -> {
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> action.accept(server.getPlayerList().getPlayers().get(0)));
        });
    }

    private static void cmd(Minecraft mc, int delay, String command) {
        server(mc, delay, player -> {
            MinecraftServer server = player.getServer();
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command);
        });
    }

    private static void shot(Minecraft mc, String name) {
        then(12, () -> {
            Window window = mc.getWindow();
            VtmMod.LOGGER.info("HARNESS screenshot {} ({}x{})", name, window.getWidth(), window.getHeight());
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> {});
        });
    }

    private static final List<com.epi0.vtm.npc.VtmNpc> LINEUP = new ArrayList<>();

    private static void shotAfter(Minecraft mc, int delay, String name) {
        then(delay, () -> {
            mc.gui.getChat().clearMessages(true);
            VtmMod.LOGGER.info("HARNESS screenshot {}", name);
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> {});
        });
    }

    private static com.epi0.vtm.npc.VtmNpc talker;

    private static void choose(Minecraft mc, String textKey) {
        server(mc, 4, p -> {
            var session = com.epi0.vtm.dialogue.DialogueManager.session(p);
            if (session == null) {
                VtmMod.LOGGER.info("HARNESS no session when choosing {}", textKey);
                return;
            }
            var options = com.epi0.vtm.dialogue.Dialogues.byId(session.tree()).orElseThrow().tree().nodes().get(session.node()).options();
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).text().equals(textKey)) {
                    com.epi0.vtm.dialogue.DialogueManager.choose(p, i);
                    return;
                }
            }
            VtmMod.LOGGER.info("HARNESS option {} not in {}", textKey, session.node());
        });
    }

    /** Visits structures generated by the world itself (seed 1337 world generated by a dedicated server). */
    private static void naturalScript(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 6000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "weather clear", "gamemode creative", "vtm embrace @s ventrue",
                "effect give @s minecraft:night_vision infinite 0 true"}) {
            cmd(mc, 3, c);
        }
        int[][] places = {{-3968 + 32, 176 + 32, 0}, {-512, 496, 1}, {-544, 48, 2}, {-320, 480, 3}};
        String[] names = {"city", "crypt", "manor", "warehouse"};
        for (int n = 0; n < places.length; n++) {
            int[] at = places[n];
            String name = names[n];
            cmd(mc, 10, "forceload add " + (at[0] - 48) + " " + (at[1] - 48) + " " + (at[0] + 48) + " " + (at[1] + 48));
            server(mc, 20, p -> {
                int y = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, at[0], at[1]);
                p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),
                        "tp @s " + at[0] + " " + (y + 40) + " " + at[1]);
            });
            then(400, () -> {});
            server(mc, 5, p -> {
                int y = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, at[0], at[1]);
                double dist = at[2] == 0 ? 52 : 26;
                p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),
                        "tp @s " + (at[0] - dist) + " " + (y + (at[2] == 0 ? 40 : 14)) + " " + (at[1] - dist) + " facing " + at[0] + " " + y + " " + at[1]);
            });
            shotAfter(mc, 60, "natural_" + name);
        }
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    /** Looks for each structure in a naturally generated world, and photographs what it finds. */
    private static void locateScript(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 6000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "weather clear", "gamemode creative", "vtm embrace @s ventrue"}) {
            cmd(mc, 3, c);
        }
        String[] structures = {"nosferatu_sewer", "the_asylum"};
        for (String name : structures) {
            server(mc, 20, p -> {
                var server = p.getServer();
                var structure = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                        .getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, VtmMod.id(name)));
                if (structure.isEmpty()) {
                    VtmMod.LOGGER.info("HARNESS locate {} : structure not registered", name);
                    return;
                }
                var found = p.serverLevel().getChunkSource().getGenerator().findNearestMapStructure(p.serverLevel(),
                        net.minecraft.core.HolderSet.direct(structure.get()), p.blockPosition(), 100, false);
                VtmMod.LOGGER.info("HARNESS locate {} : {}", name, found == null ? "none within 100 chunks" : found.getFirst());
            });
            then(300, () -> {}); // the search can take a while on the server thread
        }
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    /** The Court tab before and after taking a throne. */
    private static void courtScript(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 18000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "gamemode creative", "vtm embrace @s ventrue", "vtm sect join @s vtm:camarilla"}) {
            cmd(mc, 3, c);
        }
        server(mc, 10, p -> {
            var server = p.getServer();
            var random = server.overworld().random;
            var nem = com.epi0.vtm.nemesis.NemesisManager.data(server);
            nem.reset();
            com.epi0.vtm.court.CourtData.get(server).clear();
            com.epi0.vtm.city.CityData.get(server).clear();
            com.epi0.vtm.nemesis.NemesisSimulator.populate(server, nem, random);
            com.epi0.vtm.nemesis.NemesisSimulator.succession(server, nem);
            var cities = com.epi0.vtm.city.CityData.get(server);
            for (int i = 0; i < 2; i++) {
                var layout = new com.epi0.vtm.city.City(java.util.UUID.randomUUID(), i == 0 ? "Ashford" : "Blackmoor", net.minecraft.world.level.Level.OVERWORLD,
                        new net.minecraft.core.BlockPos(100 * i - 80, -53, 60 * i), VtmMod.id("camarilla"));
                var city = com.epi0.vtm.city.CityRegistry.register(p.serverLevel(), layout.center, layout.name, layout.owner, com.epi0.vtm.city.CityRegistry.write(layout));
                city.strength = 55 + 15 * i;
            }
            com.epi0.vtm.sect.SectManager.set(p, com.epi0.vtm.sect.Membership.DEFAULT.withFaction(java.util.Optional.of(VtmMod.id("camarilla")), 1)
                    .withStanding(VtmMod.id("camarilla"), 55));
            com.epi0.vtm.nemesis.NemesisActions.send(p);
        });
        then(15, () -> mc.setScreen(new com.epi0.vtm.client.ui.Codex(com.epi0.vtm.client.ui.PageId.COURT)));
        shot(mc, "court_before");
        then(5, () -> mc.setScreen(null));
        server(mc, 5, p -> {
            var server = p.getServer();
            var nem = com.epi0.vtm.nemesis.NemesisManager.data(server);
            var prince = nem.inRole(VtmMod.id("camarilla"), com.epi0.vtm.nemesis.Role.LEADER).get(0);
            com.epi0.vtm.nemesis.NemesisManager.destroy(server, prince, p, null);
            com.epi0.vtm.court.Court.claim(p);
            var reign = com.epi0.vtm.court.CourtData.get(server).reignOf(p.getUUID());
            reign.gain(60);
            com.epi0.vtm.court.Court.toggleDecree(p, com.epi0.vtm.court.Decree.STRICT_MASQUERADE);
            com.epi0.vtm.court.Court.toggleDecree(p, com.epi0.vtm.court.Decree.TITHES);
            var lieutenant = nem.inRole(VtmMod.id("camarilla"), com.epi0.vtm.nemesis.Role.LIEUTENANT).get(0);
            com.epi0.vtm.court.Court.dismiss(p, lieutenant.id);
            nem.inRole(VtmMod.id("camarilla"), com.epi0.vtm.nemesis.Role.ENFORCER).forEach(r -> r.courtLoyalty = 20 + r.level * 6 % 70);
            var random = server.overworld().random;
            for (int i = 0; i < 6; i++) {
                com.epi0.vtm.court.Court.spawnPetition(server, nem, reign, server.overworld().getGameTime(), random);
            }
            reign.tribute = 6;
            com.epi0.vtm.nemesis.NemesisActions.send(p);
        });
        then(15, () -> mc.setScreen(new com.epi0.vtm.client.ui.Codex(com.epi0.vtm.client.ui.PageId.COURT)));
        shot(mc, "court_ruling");
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    /** The Domains tab with several cities of different rulers on the map. */
    private static void domainsScript(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 18000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "gamemode creative", "vtm embrace @s toreador", "vtm sect join @s vtm:camarilla"}) {
            cmd(mc, 3, c);
        }
        server(mc, 10, p -> {
            var server = p.getServer();
            var random = server.overworld().random;
            var nem = com.epi0.vtm.nemesis.NemesisManager.data(server);
            nem.records().clear();
            nem.chronicle().clear();
            com.epi0.vtm.nemesis.NemesisSimulator.populate(server, nem, random);
            com.epi0.vtm.nemesis.NemesisSimulator.succession(server, nem);
            var cities = com.epi0.vtm.city.CityData.get(server);
            cities.clear();
            String[] names = {"Ashford", "Blackmoor", "Ravenhollow", "Saltport", "Kingsbridge"};
            String[] owners = {"camarilla", "sabbat", "anarch", "camarilla", "sabbat"};
            int[][] at = {{-120, 90}, {260, -40}, {40, 310}, {-300, -180}, {420, 220}};
            for (int i = 0; i < names.length; i++) {
                var layout = new com.epi0.vtm.city.City(java.util.UUID.randomUUID(), names[i], net.minecraft.world.level.Level.OVERWORLD,
                        new net.minecraft.core.BlockPos(at[i][0], -53, at[i][1]), VtmMod.id(owners[i]));
                var city = com.epi0.vtm.city.CityRegistry.register(p.serverLevel(), layout.center, names[i], layout.owner, com.epi0.vtm.city.CityRegistry.write(layout));
                city.strength = 30 + i * 14;
                com.epi0.vtm.city.Territory.seatWarden(server, nem, city);
                com.epi0.vtm.city.Territory.discover(p, city);
                if (i == 1) {
                    city.heat.put(p.getUUID(), 72);
                }
                if (i == 0) {
                    com.epi0.vtm.nemesis.Intel.knowFully(p, nem.get(city.warden));
                }
            }
            com.epi0.vtm.nemesis.NemesisActions.send(p);
        });
        then(15, () -> mc.setScreen(new com.epi0.vtm.client.ui.Codex(com.epi0.vtm.client.ui.PageId.DOMAINS)));
        shot(mc, "nem_domains");
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    private static com.epi0.vtm.city.City harnessCity;

    private static void look(Minecraft mc, int delay, java.util.function.Function<com.epi0.vtm.city.City, double[]> view) {
        server(mc, delay, p -> {
            double[] v = view.apply(harnessCity);
            // City coordinates are relative to the plaza; lift them onto the ground this world actually has.
            double dy = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    harnessCity.center.getX(), harnessCity.center.getZ()) - harnessCity.center.getY();
            v[1] += dy;
            v[4] += dy;
            p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),
                    "tp @s " + v[0] + " " + v[1] + " " + v[2] + " facing " + v[3] + " " + v[4] + " " + v[5]);
        });
    }

    /** Generates a city, lets its people move in, and photographs it by day and by night. */
    private static void cityScript(Minecraft mc) {
        harnessCity = null;
        mc.options.pauseOnLostFocus = false;
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 3000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "weather clear", "gamemode creative", "vtm embrace @s toreador",
                "effect give @s minecraft:night_vision infinite 0 true"}) {
            cmd(mc, 3, c);
        }
        // `place structure` refuses biomes outside #vtm:has_cities, so visit the natural city this seed has and let it generate.
        cmd(mc, 10, "forceload add -4016 128 -3920 256");
        server(mc, 20, p -> {
            int y = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, -3936, 208);
            p.teleportTo(p.serverLevel(), -3936, y + 40, 208, 0F, 0F);
        });
        then(400, () -> {});
        server(mc, 5, p -> {
            harnessCity = com.epi0.vtm.city.CityData.get(p.getServer()).all().stream()
                    .min(java.util.Comparator.comparingDouble(c -> c.center.distSqr(new net.minecraft.core.BlockPos(-3936, c.center.getY(), 208)))).orElse(null);
            VtmMod.LOGGER.info("HARNESS city {}", harnessCity == null ? null : harnessCity.name + " at " + harnessCity.center);
        });
        then(120, () -> {});
        server(mc, 5, p -> {
            var data = com.epi0.vtm.city.CityData.get(p.getServer());
            VtmMod.LOGGER.info("HARNESS city {}", harnessCity == null ? null : harnessCity.name + " homes=" + harnessCity.homes.size());
            for (int i = 0; i < 6 && harnessCity != null; i++) {
                com.epi0.vtm.city.CityLife.populate(p.serverLevel(), harnessCity);
            }
            if (harnessCity != null) {
                harnessCity.addHeat(p.getUUID(), 70);
            }
        });
        then(40, () -> {});
        // Walk out of the city and back in, so the arrival banner can be photographed.
        look(mc, 5, c -> new double[]{c.center.getX() + 130, c.center.getY() + 10, c.center.getZ(), c.center.getX(), c.center.getY(), c.center.getZ()});
        then(80, () -> {});
        look(mc, 5, c -> new double[]{c.center.getX() - 2, c.center.getY() + 34, c.center.getZ() - 40, c.center.getX(), c.center.getY(), c.center.getZ()});
        shotAfter(mc, 45, "city_banner");
        shotAfter(mc, 60, "city_aerial");
        look(mc, 5, c -> new double[]{c.center.getX() + 0.5, c.center.getY() + 0, c.center.getZ() - 28.5, c.center.getX(), c.center.getY() + 2, c.center.getZ()});
        shotAfter(mc, 40, "city_street");
        look(mc, 5, c -> new double[]{c.center.getX() + 7.5, c.center.getY() - 1, c.center.getZ() - 8.5, c.center.getX(), c.center.getY(), c.center.getZ()});
        shotAfter(mc, 40, "city_plaza");
        cmd(mc, 5, "time set 14000");
        look(mc, 5, c -> new double[]{c.club.getX() + 0.5, c.club.getY() + 1, c.club.getZ() + 3.5, c.club.getX() + 0.5, c.club.getY() + 1.5, c.club.getZ() - 5});
        shotAfter(mc, 50, "city_club");
        look(mc, 5, c -> new double[]{c.center.getX() + 0.5, c.center.getY() + 2, c.center.getZ() - 20.5, c.center.getX(), c.center.getY() + 1, c.center.getZ()});
        shotAfter(mc, 50, "city_night");
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    private static final List<net.minecraft.core.BlockPos> LAIRS = new ArrayList<>();

    /** Places each lair on a raised lawn (the harness world is flat and low) and photographs it from outside and in. */
    private static net.minecraft.core.BlockPos metroOrigin;
    private static com.epi0.vtm.world.CityPlan metroPlan;
    private static int metroY;

    /** Finds the nearest metropolis, lets all of it generate, and photographs it from the air and from its streets. */
    private static void metropolisScript(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(18);
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 6000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "weather clear", "gamemode spectator", "vtm embrace @s ventrue"}) {
            cmd(mc, 3, c);
        }
        // The harness world is hilly, and a metropolis refuses hills; so level a site by hand, then ask for the city.
        metroOrigin = null;
        int size = com.epi0.vtm.world.CityPlan.size();
        int x = 600;
        int z = 0;
        server(mc, 5, p -> {
            for (int cx = (x >> 4) - 1; cx <= ((x + size) >> 4) + 1; cx++) {
                for (int cz = (z >> 4) - 1; cz <= ((z + size) >> 4) + 1; cz++) {
                    p.serverLevel().setChunkForced(cx, cz, true);
                }
            }
            p.teleportTo(p.serverLevel(), x + size / 2.0, 150, z + size / 2.0, 0F, 90F);
        });
        then(700, () -> {});
        server(mc, 5, p -> {
            var level = p.serverLevel();
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x + size / 2, z + size / 2);
            var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
            for (int wx = x - 8; wx < x + size + 8; wx++) {
                for (int wz = z - 8; wz < z + size + 8; wz++) {
                    for (int y = ground - 6; y < ground; y++) {
                        level.setBlock(pos.set(wx, y, wz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                    }
                    level.setBlock(pos.set(wx, ground - 1, wz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int y = ground; y < ground + 40; y++) {
                        level.setBlock(pos.set(wx, y, wz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            // Cities only stand in certain biomes; the harness world's are not among them, so repaint the site as plains.
            for (int bx = x - 16; bx < x + size + 16; bx += 32) {
                for (int bz = z - 16; bz < z + size + 16; bz += 32) {
                    try {
                        p.getServer().getCommands().getDispatcher().execute("fillbiome " + bx + " " + (ground - 8) + " " + bz + " " + (bx + 31) + " " + (ground + 7) + " " + (bz + 31) + " minecraft:plains",
                                p.createCommandSourceStack().withPermission(4));
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        VtmMod.LOGGER.info("HARNESS fillbiome refused: {}", e.getMessage());
                    }
                }
            }
            try {
                p.getServer().getCommands().getDispatcher().execute("place structure vtm:metropolis " + x + " " + ground + " " + z,
                        p.createCommandSourceStack().withPermission(4).withPosition(net.minecraft.world.phys.Vec3.atCenterOf(new net.minecraft.core.BlockPos(x, ground, z))));
                metroOrigin = new net.minecraft.core.BlockPos(x & ~15, ground, z & ~15);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                VtmMod.LOGGER.info("HARNESS metropolis refused: {}", e.getMessage());
            }
            VtmMod.LOGGER.info("HARNESS metropolis origin {} ground {}", metroOrigin, ground);
        });
        then(900, () -> {});
        server(mc, 5, p -> {
            var level = p.serverLevel();
            var structure = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .get(VtmMod.id("metropolis"));
            var start = level.structureManager().getStructureAt(metroOrigin.offset(100, 0, 100), structure);
            metroPlan = start.isValid() && start.getPieces().get(0) instanceof com.epi0.vtm.world.MetropolisPiece piece ? piece.plan() : null;
            var city = com.epi0.vtm.city.CityData.get(p.getServer()).all().stream()
                    .filter(c -> c.radius > 100).findFirst().orElse(null);
            metroY = city == null ? 64 : city.center.getY() - 2;
            VtmMod.LOGGER.info("HARNESS metropolis plan {} ground {} city {}", metroPlan != null, metroY, city == null ? null : city.name + " homes=" + city.homes.size() + " works=" + city.works.size());
        });
        // Views: camera x, y, z then target x, y, z, all relative to the city's north-west corner and ground level.
        int mid = com.epi0.vtm.world.CityPlan.size() / 2;
        int avenue = com.epi0.vtm.world.CityPlan.lotStart(com.epi0.vtm.world.CityPlan.CENTRE - 1) + com.epi0.vtm.world.CityPlan.LOT + 3;
        int[][] fixed = {
                {mid, 230, mid + 4, mid, 0, mid},
                {-90, 110, -90, mid, 10, mid},
                {mid + 130, 70, mid + 130, mid, 10, mid},
                {avenue, 3, 12, avenue, 10, mid},
                {mid - 12, 4, mid + 16, mid, 3, mid},
                {avenue, 6, -22, avenue, 5, 2},
        };
        for (int n = 0; n < fixed.length; n++) {
            int[] v = fixed[n];
            int index = n;
            server(mc, 5, p -> p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),
                    "tp @s " + (metroOrigin.getX() + v[0] + 0.5) + " " + (metroY + v[1]) + " " + (metroOrigin.getZ() + v[2] + 0.5)
                            + " facing " + (metroOrigin.getX() + v[3] + 0.5) + " " + (metroY + v[4]) + " " + (metroOrigin.getZ() + v[5] + 0.5)));
            shotAfter(mc, 80, "metro_" + index);
        }
        String[] kinds = {"TOWER", "CHURCH", "MARKET", "PARK", "ASYLUM", "TENEMENT", "DEPOT", "CLUB", "HAVEN"};
        for (String kind : kinds) {
            server(mc, 5, p -> {
                if (metroPlan == null) {
                    return;
                }
                var lot = metroPlan.lots.stream().filter(l -> l.kind().name().equals(kind)).min(java.util.Comparator.comparingInt(l -> Math.abs(l.i() - 5) + Math.abs(l.j() - 5))).orElse(null);
                if (lot == null) {
                    return;
                }
                int h = lot.kind() == com.epi0.vtm.world.CityPlan.Kind.TOWER ? lot.floors() * 5 / 2 : 4;
                int back = lot.kind() == com.epi0.vtm.world.CityPlan.Kind.TOWER ? 34 : lot.kind() == com.epi0.vtm.world.CityPlan.Kind.ASYLUM ? 40 : 24;
                double cx = metroOrigin.getX() + lot.centreX() + 0.5 - 16;
                double cz = metroOrigin.getZ() + lot.centreZ() + 0.5 + back;
                p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),
                        "tp @s " + cx + " " + (metroY + (lot.kind() == com.epi0.vtm.world.CityPlan.Kind.TOWER ? 14 : 12)) + " " + cz
                                + " facing " + (metroOrigin.getX() + lot.centreX() + 0.5) + " " + (metroY + h) + " " + (metroOrigin.getZ() + lot.centreZ() + 0.5));
            });
            shotAfter(mc, 60, "metro_" + kind.toLowerCase());
        }
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    private static int[] structureOrigin = new int[3];

    /**
     * Builds {@code structure} wherever the world lets it, finds its clan's coffin to learn where it stands, and
     * photographs it from {@code views}: camera and target, each relative to the origin (x0, ground y, z0).
     */
    private static void structureScript(Minecraft mc, String structure, String clan, int[] coffinAt, int[][] views, int exterior, String prefix) {
        mc.options.pauseOnLostFocus = false;
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 18000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "weather clear", "gamemode creative", "vtm embrace @s ventrue",
                "effect give @s minecraft:night_vision infinite 0 true"}) {
            cmd(mc, 3, c);
        }
        // `place structure` only works on loaded chunks, so load every candidate spot and let it generate.
        for (int i = 0; i < 6; i++) {
            int x = 300 + (i % 3) * 170;
            int z = (i / 3) * 170;
            cmd(mc, 5, "forceload add " + (x - 32) + " " + (z - 32) + " " + (x + 48) + " " + (z + 48));
        }
        then(500, () -> {});
        server(mc, 10, p -> {
            var level = p.serverLevel();
            boolean found = false;
            for (int i = 0; i < 6 && !found; i++) {
                int x = 300 + (i % 3) * 170;
                int z = (i / 3) * 170;
                int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z);
                try {
                    p.getServer().getCommands().getDispatcher().execute("place structure vtm:" + structure + " " + x + " " + surface + " " + z,
                            p.createCommandSourceStack().withPermission(4).withPosition(net.minecraft.world.phys.Vec3.atCenterOf(new net.minecraft.core.BlockPos(x, surface, z))));
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    VtmMod.LOGGER.info("HARNESS place {} at {} {} failed: {}", structure, x, z, e.getMessage());
                    continue;
                }
                for (int cx = (x >> 4) - 2; cx <= (x >> 4) + 2 && !found; cx++) {
                    for (int cz = (z >> 4) - 2; cz <= (z >> 4) + 2 && !found; cz++) {
                        for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
                            if (be instanceof com.epi0.vtm.block.CoffinBlockEntity coffin && coffin.clan() != null && coffin.clan().getPath().equals(clan)) {
                                structureOrigin = new int[]{be.getBlockPos().getX() - coffinAt[0], be.getBlockPos().getY() - coffinAt[1], be.getBlockPos().getZ() - coffinAt[2]};
                                found = true;
                                break;
                            }
                        }
                    }
                }
            }
            VtmMod.LOGGER.info("HARNESS {} origin {} found={}", structure, java.util.Arrays.toString(structureOrigin), found);
        });
        then(60, () -> {});
        for (int n = 0; n < views.length; n++) {
            int[] v = views[n];
            int index = n;
            if (n == 0 || n == exterior) {
                cmd(mc, 3, n == 0 ? "gamemode spectator" : "gamemode creative"); // fly for the outside views
            }
            server(mc, 5, p -> p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),
                    "tp @s " + (structureOrigin[0] + v[0] + 0.5) + " " + (structureOrigin[1] + v[1]) + " " + (structureOrigin[2] + v[2] + 0.5)
                            + " facing " + (structureOrigin[0] + v[3] + 0.5) + " " + (structureOrigin[1] + v[4] + 0.5) + " " + (structureOrigin[2] + v[5] + 0.5)));
            shotAfter(mc, 50, prefix + "_" + index);
        }
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    private static void lairScript(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 6000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "weather clear", "gamemode creative", "vtm embrace @s ventrue",
                "effect give @s minecraft:night_vision infinite 0 true"}) {
            cmd(mc, 3, c);
        }
        String[] structures = {"crypt_lair", "manor_haven", "warehouse_squat"};
        // Where the occupied coffin sits relative to the structure origin (x0, y0 = ground, z0), and where to look from.
        int[][] coffin = {{6, -5, 1}, {8, -4, 4}, {13, 4, 8}};
        int[][] centre = {{6, 3, 6}, {7, 5, 5}, {8, 3, 5}};
        int[][] camera = {{-9, 7, -9}, {-9, 9, -10}, {-9, 7, -10}};
        int[][] stand = {{6, -5, 8}, {8, -4, 6}, {13, 4, 4}};
        for (int n = 0; n < structures.length; n++) {
            String name = structures[n];
            int index = n;
            server(mc, 10, p -> {
                net.minecraft.core.BlockPos base = new net.minecraft.core.BlockPos(160 * (index + 1), -55, 0);
                p.teleportTo(p.serverLevel(), base.getX(), base.getY() + 3, base.getZ(), 0F, 0F);
            });
            then(60, () -> {});
            server(mc, 5, p -> {
                net.minecraft.core.BlockPos base = p.blockPosition().atY(-55);
                var level = p.serverLevel();
                for (int dx = -40; dx <= 40; dx++) {
                    for (int dz = -40; dz <= 40; dz++) {
                        level.setBlock(base.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                        level.setBlock(base.offset(dx, -2, dz), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
                        for (int dy = 0; dy < 20; dy++) {
                            level.setBlock(base.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
                p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4)
                        .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(base)),
                        "place structure vtm:" + name + " " + base.getX() + " " + base.getY() + " " + base.getZ());
                net.minecraft.core.BlockPos found = null;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        var chunk = level.getChunk((base.getX() >> 4) + dx, (base.getZ() >> 4) + dz);
                        for (var be : chunk.getBlockEntities().values()) {
                            if (be instanceof com.epi0.vtm.block.CoffinBlockEntity c && c.faction() != null) {
                                found = c.getBlockPos();
                            }
                        }
                    }
                }
                LAIRS.add(found);
                VtmMod.LOGGER.info("HARNESS lair {} coffin at {}", name, found);
            });
        }
        for (int n = 0; n < structures.length; n++) {
            int index = n;
            String name = structures[n];
            server(mc, 20, p -> {
                var c = LAIRS.get(index);
                int[] k = coffin[index];
                int x0 = c.getX() - k[0];
                int y0 = c.getY() - k[1];
                int z0 = c.getZ() - k[2];
                int[] cam = camera[index];
                int[] mid = centre[index];
                p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),
                        "tp @s " + (x0 + cam[0]) + " " + (y0 + cam[1]) + " " + (z0 + cam[2]) + " facing "
                                + (x0 + mid[0]) + " " + (y0 + mid[1]) + " " + (z0 + mid[2]));
            });
            shotAfter(mc, 50, "lair_" + name + "_outside");
        }
        for (int n = 0; n < structures.length; n++) {
            int index = n;
            String name = structures[n];
            server(mc, 10, p -> {
                var c = LAIRS.get(index);
                int[] k = coffin[index];
                int x0 = c.getX() - k[0];
                int y0 = c.getY() - k[1];
                int z0 = c.getZ() - k[2];
                int[] at = stand[index];
                p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),
                        "tp @s " + (x0 + at[0] + 0.5) + " " + (y0 + at[1] + 1) + " " + (z0 + at[2] + 0.5) + " facing "
                                + (c.getX() + 0.5) + " " + (c.getY() + 0.8) + " " + (c.getZ() + 0.5));
            });
            shotAfter(mc, 40, "lair_" + name + "_inside");
        }
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    /** The Nemesis screen after a stretch of simulated history, a named vampire in the world, and a verdict conversation. */
    private static void nemesisScript(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 18000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "weather clear", "effect give @s minecraft:night_vision infinite 0 true",
                "execute at @s run fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 minecraft:smooth_stone",
                "execute at @s run fill ~-12 ~ ~-12 ~12 ~8 ~12 minecraft:air", "tp @s ~ ~ ~ 0 8",
                "vtm embrace @s toreador", "vtm set @s blood 14", "vtm discipline @s dominate 3", "vtm discipline @s presence 2"}) {
            cmd(mc, 3, c);
        }
        server(mc, 10, p -> {
            var server = p.getServer();
            var random = server.overworld().random;
            var data = com.epi0.vtm.nemesis.NemesisManager.data(server);
            data.records().clear();
            data.chronicle().clear();
            for (int i = 0; i < 40; i++) {
                com.epi0.vtm.nemesis.NemesisSimulator.step(server, random);
                server.overworld().setDayTime(server.overworld().getDayTime() + 1200);
            }
            server.overworld().setDayTime(18000);
            // The player has met a few of them, and been told of others.
            var all = new java.util.ArrayList<>(data.all());
            java.util.Collections.shuffle(all, new java.util.Random(4));
            int n = 0;
            for (var r : all) {
                if (!r.alive()) {
                    continue;
                }
                if (n < 4) {
                    com.epi0.vtm.nemesis.Intel.knowFully(p, r);
                } else if (n < 11) {
                    com.epi0.vtm.nemesis.Intel.reveal(p, r, random);
                    com.epi0.vtm.nemesis.Intel.reveal(p, r, random);
                    com.epi0.vtm.nemesis.Intel.reveal(p, r, random);
                } else if (n < 16) {
                    com.epi0.vtm.nemesis.Intel.reveal(p, r, random);
                }
                n++;
            }
            for (var r : data.all()) {
                if (r.role == com.epi0.vtm.nemesis.Role.LEADER) {
                    com.epi0.vtm.nemesis.Intel.reveal(p, r, random);
                }
            }
            // One of them holds a grudge and has scars; two are bound to the player.
            var foes = data.all().stream().filter(r -> r.alive() && r.status != com.epi0.vtm.nemesis.Status.BOUND).limit(6).toList();
            var foe = foes.get(0);
            foe.scars.add(com.epi0.vtm.nemesis.Scar.BURNED);
            foe.deaths = 1;
            foe.feud(p.getUUID()).enmity = 72;
            foe.feud(p.getUUID()).defeats = 1;
            foe.feud(p.getUUID()).respect = 22;
            com.epi0.vtm.nemesis.Intel.knowFully(p, foe);
            for (int i = 1; i <= 2; i++) {
                var thrall = foes.get(i);
                var body = com.epi0.vtm.nemesis.NemesisManager.manifest(p.serverLevel(), thrall, p.position().add(-4 + i * 4, 0, 5));
                body.yieldTo(p, p.level().getGameTime() + 9000);
                com.epi0.vtm.nemesis.NemesisManager.bind(p, body, thrall);
                body.setNoAi(true);
            }
            com.epi0.vtm.nemesis.NemesisActions.send(p);
        });
        then(15, () -> mc.setScreen(new com.epi0.vtm.client.ui.Codex(com.epi0.vtm.client.ui.PageId.DOSSIERS)));
        shot(mc, "nem_dossiers");
        then(5, () -> mc.setScreen(new com.epi0.vtm.client.ui.Codex(com.epi0.vtm.client.ui.PageId.COURTS)));
        shot(mc, "nem_courts");
        then(5, () -> mc.setScreen(new com.epi0.vtm.client.ui.Codex(com.epi0.vtm.client.ui.PageId.CHRONICLE)));
        shot(mc, "nem_chronicle");
        then(5, () -> mc.setScreen(new com.epi0.vtm.client.ui.Codex(com.epi0.vtm.client.ui.PageId.COTERIE)));
        shot(mc, "nem_coterie");
        then(5, () -> mc.setScreen(null));
        server(mc, 5, p -> {
            var data = com.epi0.vtm.nemesis.NemesisManager.data(p.getServer());
            var r = data.all().stream().filter(x -> x.status == com.epi0.vtm.nemesis.Status.ACTIVE).findFirst().orElseThrow();
            r.traits.remove(com.epi0.vtm.nemesis.Trait.PROUD);
            r.kills = 1;
            var body = com.epi0.vtm.nemesis.NemesisManager.manifest(p.serverLevel(), r, p.position().add(0, 0, 3));
            body.setNoAi(true);
            body.setYRot(180F);
            body.yHeadRot = 180F;
            body.yBodyRot = 180F;
            body.yieldTo(p, p.level().getGameTime() + 9000);
            com.epi0.vtm.dialogue.DialogueManager.open(p, body);
        });
        shotAfter(mc, 80, "nem_verdict");
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    /** The Sect tab for a member, and a conversation with a higher-ranked member of the same sect. */
    private static void sectScript(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 18000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "weather clear", "effect give @s minecraft:night_vision infinite 0 true",
                "execute at @s run fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 minecraft:smooth_stone",
                "execute at @s run fill ~-12 ~ ~-12 ~12 ~8 ~12 minecraft:air", "tp @s ~ ~ ~ 0 8",
                "vtm embrace @s toreador", "vtm sect join @s vtm:camarilla", "vtm sect standing @s vtm:camarilla 31",
                "vtm sect standing @s vtm:sabbat -45", "vtm sect standing @s vtm:anarch 12"}) {
            cmd(mc, 3, c);
        }
        then(10, () -> mc.setScreen(new com.epi0.vtm.client.ui.Codex(com.epi0.vtm.client.ui.PageId.SECT)));
        shot(mc, "sect_tab");
        then(10, () -> mc.setScreen(null));
        server(mc, 10, p -> {
            var npc = com.epi0.vtm.npc.NpcSpawner.vampire(p.serverLevel(), p.position().add(0, 0, 2.5), VtmMod.id("ventrue"));
            npc.setRank(2);
            npc.setNoAi(true);
            npc.setYRot(180F);
            npc.yHeadRot = 180F;
            npc.yBodyRot = 180F;
            npc.setCustomName(net.minecraft.network.chat.Component.literal("Lucian Ashworth"));
            com.epi0.vtm.dialogue.DialogueManager.open(p, npc);
        });
        shotAfter(mc, 70, "sect_dialogue");
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    /** Opens real conversations with a civilian and a vampire and walks through a few branches. */
    private static void dialogueScript(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        // Leave any city (and its grudges) the harness world was last saved in.
        server(mc, 3, p -> {
            com.epi0.vtm.city.CityData.get(p.getServer()).all().forEach(c -> c.heat.clear());
        });
        cmd(mc, 3, "forceload add 0 0");
        then(40, () -> {});
        cmd(mc, 3, "fill -14 199 -14 14 199 14 minecraft:smooth_stone");
        cmd(mc, 3, "tp @s 0.5 200 0.5");
        then(20, () -> {});
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 18000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "weather clear", "effect give @s minecraft:night_vision infinite 0 true",
                "execute at @s run fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 minecraft:smooth_stone",
                "execute at @s run fill ~-12 ~ ~-12 ~12 ~8 ~12 minecraft:air", "tp @s ~ ~ ~ 0 8",
                "vtm embrace @s gangrel", "vtm discipline @s presence 2", "vtm set @s blood 6"}) {
            cmd(mc, 3, c);
        }
        server(mc, 10, p -> {
            talker = com.epi0.vtm.npc.NpcSpawner.civilian(p.serverLevel(), p.position().add(0, 0, 2.5));
            talker.setNoAi(true);
            talker.setYRot(180F);
            talker.yHeadRot = 180F;
            talker.yBodyRot = 180F;
            talker.setCustomName(net.minecraft.network.chat.Component.literal("Mara Quinn"));
            com.epi0.vtm.dialogue.DialogueManager.open(p, talker);
        });
        shotAfter(mc, 70, "dlg_civilian");
        choose(mc, "dialogue.vtm.opt.charm");
        shotAfter(mc, 70, "dlg_civilian_charm");
        choose(mc, "dialogue.vtm.opt.else");
        choose(mc, "dialogue.vtm.opt.rumors");
        shotAfter(mc, 70, "dlg_civilian_rumors");
        server(mc, 5, p -> {
            com.epi0.vtm.dialogue.DialogueManager.end(p);
            talker.discard();
            talker = com.epi0.vtm.npc.NpcSpawner.vampire(p.serverLevel(), p.position().add(0, 0, 2.5), VtmMod.id("ventrue"));
            talker.setNoAi(true);
            talker.setYRot(180F);
            talker.yHeadRot = 180F;
            talker.yBodyRot = 180F;
            talker.setCustomName(net.minecraft.network.chat.Component.literal("Lucian Ashworth"));
            com.epi0.vtm.dialogue.DialogueManager.open(p, talker);
        });
        shotAfter(mc, 70, "dlg_vampire");
        choose(mc, "dialogue.vtm.opt.introduce");
        choose(mc, "dialogue.vtm.opt.else");
        choose(mc, "dialogue.vtm.opt.ask_clan");
        shotAfter(mc, 90, "dlg_vampire_clan");
        choose(mc, "dialogue.vtm.opt.else");
        choose(mc, "dialogue.vtm.opt.ask_sect");
        choose(mc, "dialogue.vtm.opt.else");
        shotAfter(mc, 50, "dlg_vampire_trust");
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    /** NPCs in a row, each frozen in a different animation. */
    private static void npcScript(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        for (String c : new String[]{"gamerule sendCommandFeedback false", "time set 18000", "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false", "weather clear", "effect give @s minecraft:night_vision infinite 0 true",
                "execute at @s run fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 minecraft:smooth_stone",
                "execute at @s run fill ~-12 ~ ~-12 ~12 ~8 ~12 minecraft:air", "tp @s ~ ~ ~ 0 8",
                "vtm embrace @s ventrue"}) {
            cmd(mc, 3, c);
        }
        server(mc, 10, p -> {
            String[] clans = {"brujah", "ventrue", "nosferatu", "toreador", "malkavian", "tremere"};
            net.minecraft.world.phys.Vec3 base = p.position();
            for (int i = 0; i < 9; i++) {
                net.minecraft.world.phys.Vec3 pos = new net.minecraft.world.phys.Vec3(base.x - 6 + i * 1.5, base.y, base.z + 4);
                com.epi0.vtm.npc.VtmNpc npc = i % 3 == 0
                        ? com.epi0.vtm.npc.NpcSpawner.civilian(p.serverLevel(), pos)
                        : com.epi0.vtm.npc.NpcSpawner.vampire(p.serverLevel(), pos,
                                VtmMod.id(clans[(i - 1 - i / 3) % clans.length]));
                npc.setNoAi(true);
                npc.setYRot(180F);
                npc.yBodyRot = 180F;
                npc.yHeadRot = 180F;
                npc.setRemainingFireTicks(0);
                LINEUP.add(npc);
            }
        });
        shotAfter(mc, 40, "npc_lineup_idle");

        String[] poses = {"walk", "run", "attack", "bite", "talk"};
        for (String pose : poses) {
            server(mc, 5, p -> LINEUP.forEach(n -> n.triggerAnim(com.epi0.vtm.npc.VtmNpc.CONTROLLER_ACTION, pose)));
            then(2, () -> {
                var first = mc.level.getEntity(LINEUP.get(0).getId());
                if (first instanceof software.bernie.geckolib.animatable.GeoEntity g) {
                    var ctrl = g.getAnimatableInstanceCache().getManagerForId(first.getId())
                            .getAnimationControllers().get(com.epi0.vtm.npc.VtmNpc.CONTROLLER_ACTION);
                    VtmMod.LOGGER.info("HARNESS {} after server trigger: current={} state={}", pose,
                            ctrl.getCurrentRawAnimation() == null ? null : ctrl.getCurrentRawAnimation().getAnimationStages().size(),
                            ctrl.getAnimationState());
                }
            });
            then(1, () -> {
                // Also trigger on the client copy directly, to tell a networking problem from a controller problem.
                for (com.epi0.vtm.npc.VtmNpc n : LINEUP) {
                    var e = mc.level.getEntity(n.getId());
                    if (e instanceof software.bernie.geckolib.animatable.GeoEntity g) {
                        g.triggerAnim(com.epi0.vtm.npc.VtmNpc.CONTROLLER_ACTION, pose);
                    }
                }
            });
            shotAfter(mc, 4, "npc_pose_" + pose);
        }
        server(mc, 5, p -> {
            for (int i = 0; i < LINEUP.size(); i++) {
                LINEUP.get(i).stopTriggeredAnim(com.epi0.vtm.npc.VtmNpc.CONTROLLER_ACTION, "talk");
                LINEUP.get(i).setState(i % 2 == 0 ? com.epi0.vtm.npc.VtmNpc.State.SLEEPING : com.epi0.vtm.npc.VtmNpc.State.COWERING);
            }
        });
        shotAfter(mc, 25, "npc_states");
        then(10, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    private static void script(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = false;
        for (String c : new String[]{
                "time set 18000", "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "weather clear",
                "execute at @s run fill ~-8 ~-1 ~-8 ~8 ~-1 ~8 minecraft:smooth_stone",
                "execute at @s run fill ~-8 ~ ~-8 ~8 ~6 ~8 minecraft:air",
                "tp @s ~ ~ ~ 0 12",
                "vtm embrace @s ventrue", "vtm set @s xp 60", "vtm set @s potency 2", "vtm set @s blood 11",
                "vtm discipline @s dominate 3", "vtm discipline @s fortitude 2", "vtm discipline @s presence 1",
                "vtm discipline @s protean 5", "vtm discipline @s auspex 5", "vtm discipline @s animalism 3",
                "vtm discipline @s celerity 4",
                "execute at @s run summon minecraft:cow ~ ~ ~5", "execute at @s run summon minecraft:villager ~2 ~ ~5"}) {
            cmd(mc, 3, c);
        }
        then(40, () -> PowerSelection.select(Powers.get(VtmMod.id("blink")).orElseThrow()));

        for (com.epi0.vtm.client.ui.PageId tab : com.epi0.vtm.client.ui.PageId.values()) {
            then(10, () -> mc.setScreen(new com.epi0.vtm.client.ui.Codex(tab)));
            shot(mc, "gui_" + tab.name().toLowerCase());
        }
        then(10, () -> mc.setScreen(null));
        cmd(mc, 3, "gamemode survival");
        shot(mc, "hud");
        then(5, () -> CityNotice.receive(new com.epi0.vtm.network.CityNoticePayload(java.util.Optional.of("Ashgrove"), VtmMod.id("camarilla"), 72)));
        shotAfter(mc, 25, "hud_city");
        shotAfter(mc, 120, "hud_city_later");

        // Shapechange, seen from outside.
        then(5, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        for (String power : new String[]{"shapechange", "bat_form", "mist_form"}) {
            cmd(mc, 5, "vtm set @s blood 14");
            server(mc, 10, p -> PowerManager.activate(p, VtmMod.id(power)));
            then(30, () -> {});
            shot(mc, "form_" + power);
        }
        server(mc, 15, p -> PowerManager.activate(p, VtmMod.id("mist_form")));
        cmd(mc, 3, "vtm set @s blood 14");
        then(30, () -> {});
        shot(mc, "form_human");

        // Share the Senses: camera moves into the cow.
        then(5, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        cmd(mc, 5, "vtm set @s blood 14");
        cmd(mc, 3, "effect clear @s");
        cmd(mc, 3, "tp @s ~ ~ ~ 0 12");
        cmd(mc, 3, "kill @e[type=minecraft:cow]");
        cmd(mc, 3, "execute at @s run summon minecraft:cow ~ ~ ~5");
        server(mc, 25, p -> {
            boolean ok = PowerManager.activate(p, VtmMod.id("share_the_senses"));
            VtmMod.LOGGER.info("HARNESS sense activate -> {} frenzied={} blood={}", ok,
                    Frenzy.isFrenzied(p), com.epi0.vtm.vampire.VampireData.of(p).blood());
        });
        then(30, () -> {});
        shot(mc, "sense_link");
        then(1, () -> VtmMod.LOGGER.info("HARNESS camera is player: {}", mc.getCameraEntity() == mc.player));
        server(mc, 5, p -> PowerManager.activate(p, VtmMod.id("share_the_senses")));
        then(20, () -> VtmMod.LOGGER.info("HARNESS camera is player after release: {}", mc.getCameraEntity() == mc.player));

        // Frenzy.
        server(mc, 10, p -> Frenzy.start(p, 60));
        then(30, () -> {});
        shot(mc, "frenzy");

        then(20, () -> {
            VtmMod.LOGGER.info("HARNESS DONE");
            mc.stop();
        });
    }

    private Harness() {}
}
