package com.epi0.vtm.client;

import com.epi0.vtm.VtmMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client half of Share the Senses: swaps the camera into the host creature and back. */
@EventBusSubscriber(modid = VtmMod.MOD_ID, value = Dist.CLIENT)
public final class SenseLinkClient {
    private static boolean linked;

    public static boolean isLinked() {
        return linked;
    }

    /** @param entityId the host's entity id, or a negative number to return to your own eyes */
    public static void apply(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && entityId >= 0) {
            Entity host = mc.level.getEntity(entityId);
            if (host != null) {
                mc.setCameraEntity(host);
                linked = true;
                return;
            }
        }
        reset(mc);
    }

    private static void reset(Minecraft mc) {
        if (linked) {
            linked = false;
            if (mc.player != null) {
                mc.setCameraEntity(mc.player);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (linked && mc.player != null) {
            Entity camera = mc.getCameraEntity();
            if (camera == null || camera == mc.player || camera.isRemoved()) {
                reset(mc);
            } else {
                mc.player.displayClientMessage(Component.translatable("gui.vtm.sensing", camera.getDisplayName()), true);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        linked = false;
    }

    private SenseLinkClient() {}
}
