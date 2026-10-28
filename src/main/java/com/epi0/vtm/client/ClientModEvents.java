package com.epi0.vtm.client;

import com.epi0.vtm.VtmMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

/** Mod-bus client setup: key bindings and HUD layers. */
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.vtm.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.vtm");

    public static final KeyMapping USE_POWER = new KeyMapping(
            "key.vtm.use_power", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.vtm");
    public static final KeyMapping NEXT_POWER = new KeyMapping(
            "key.vtm.next_power", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.vtm");
    public static final KeyMapping PREVIOUS_POWER = new KeyMapping(
            "key.vtm.previous_power", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.vtm");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(USE_POWER);
        event.register(NEXT_POWER);
        event.register(PREVIOUS_POWER);
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, VtmMod.id("blood"), BloodHud::render);
    }

    private ClientModEvents() {}
}
