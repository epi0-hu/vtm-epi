package com.epi0.vtm.power;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One discipline power. Display text uses the lang keys {@code power.<ns>.<name>} and {@code .desc}.
 *
 * @param level       discipline level required (1-5)
 * @param bloodCost   blood spent when the power succeeds
 * @param cooldownTicks per-power cooldown
 */
public record Power(ResourceLocation id, ResourceLocation discipline, int level, PowerKind kind,
                    int bloodCost, int cooldownTicks, PowerAction action) {

    public Component name() {
        return Component.translatable(Util.makeDescriptionId("power", id));
    }

    public Component description() {
        return Component.translatable(Util.makeDescriptionId("power", id) + ".desc");
    }
}
