package com.epi0.vtm.client;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = VtmMod.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (ClientModEvents.OPEN_MENU.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new VampireScreen());
            }
        }
    }

    /** Vampires don't eat, so the blood bar replaces the food bar. */
    @SubscribeEvent
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)
                && mc.player != null
                && VampireData.of(mc.player).isVampire()) {
            event.setCanceled(true);
        }
    }

    private ClientGameEvents() {}
}
