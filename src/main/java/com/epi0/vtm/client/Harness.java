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
        if (joined < 0) {
            joined = mc.player.tickCount;
        }
        if (mc.player.tickCount - joined < 120) {
            return;
        }
        if (STEPS.isEmpty()) {
            if (SCENARIO.equals("npc")) {
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

        for (VampireScreen.Tab tab : VampireScreen.Tab.values()) {
            then(10, () -> mc.setScreen(new VampireScreen(tab)));
            shot(mc, "gui_" + tab.name().toLowerCase());
        }
        then(10, () -> mc.setScreen(null));
        shot(mc, "hud");

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
