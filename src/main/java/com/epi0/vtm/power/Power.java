package com.epi0.vtm.power;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/**
 * One discipline power. Display text uses the lang keys {@code power.<ns>.<name>} and {@code .desc}.
 *
 * @param level       discipline level required (1-5)
 * @param bloodCost   blood spent when the power succeeds
 * @param cooldownTicks per-power cooldown
 */
public record Power(ResourceLocation id, ResourceLocation discipline, int level, PowerKind kind,
                    int bloodCost, int cooldownTicks, PowerAction action, Predicate<ServerPlayer> engaged) {

    /** Toggle powers (forms, sense link) can always be released; their cooldown starts on release. */
    public boolean isToggle() {
        return engaged != null;
    }

    public boolean isEngagedFor(ServerPlayer player) {
        return engaged != null && engaged.test(player);
    }

    public Component name() {
        return Component.translatable(Util.makeDescriptionId("power", id));
    }

    public Component description() {
        return Component.translatable(Util.makeDescriptionId("power", id) + ".desc");
    }
}
