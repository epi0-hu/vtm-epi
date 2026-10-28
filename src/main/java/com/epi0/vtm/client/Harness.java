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
            if (SCENARIO.equals("nemesis")) {
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
        then(15, () -> mc.setScreen(new NemesisScreen(NemesisScreen.Tab.DOSSIERS)));
        shot(mc, "nem_dossiers");
        then(5, () -> mc.setScreen(new NemesisScreen(NemesisScreen.Tab.COURTS)));
        shot(mc, "nem_courts");
        then(5, () -> mc.setScreen(new NemesisScreen(NemesisScreen.Tab.CHRONICLE)));
        shot(mc, "nem_chronicle");
        then(5, () -> mc.setScreen(new NemesisScreen(NemesisScreen.Tab.COTERIE)));
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
        then(10, () -> mc.setScreen(new VampireScreen(VampireScreen.Tab.SECT)));
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
