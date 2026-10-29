package com.epi0.vtm.client;

import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.power.Power;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The blood bar, drawn where the food bar normally sits: ten segments, a row of hunger pips above its right end
 * that darken as the Beast stirs, and the armed power on the left.
 */
final class BloodHud {
    private static final int WIDTH = 81;
    private static final int HEIGHT = 9;
    private static final int SEGMENTS = 10;

    static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        VampireData data = VampireData.of(mc.player);
        if (!data.isVampire()) {
            return;
        }
        int x = g.guiWidth() / 2 + 10;
        int y = g.guiHeight() - 39;
        float fraction = Math.max(0F, Math.min(1F, data.blood() / data.maxBlood()));
        int hunger = Math.min(5, data.hunger());
        int fill = hunger >= 4 ? 0xFF7A1212 : hunger >= 2 ? 0xFFA21C1C : 0xFFC22626;
        boolean throb = hunger >= 4 && (mc.level.getGameTime() / 8) % 2 == 0;

        g.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, throb ? Theme.BAD : 0xFF000000);
        g.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF2A0A0A);
        int filled = Math.round(WIDTH * fraction);
        g.fill(x, y, x + filled, y + HEIGHT, fill);
        g.fill(x, y, x + filled, y + 2, Theme.alpha(0xFFFFFF, 0x28));
        for (int i = 1; i < SEGMENTS; i++) {
            int sx = x + i * WIDTH / SEGMENTS;
            g.fill(sx, y, sx + 1, y + HEIGHT, 0x80000000);
        }
        Component label = Component.translatable("gui.vtm.blood_short", String.format("%.1f", data.blood()), Math.round(data.maxBlood()));
        g.drawCenteredString(mc.font, label, x + WIDTH / 2, y + 1, 0xFFFFFF);

        // Hunger pips above the right end of the bar.
        int pipColor = hunger >= 4 ? Theme.BAD : hunger >= 2 ? Theme.WARN : Theme.MUTED;
        if (hunger > 0) {
            Draw.pips(g, x + WIDTH - 5 * 9 + 2, y - 10, hunger, 5, pipColor);
        }

        Power power = PowerSelection.current(data);
        if (power != null) {
            Component armed = Component.literal("\u25B6 ").append(power.name()).append(" " + com.epi0.vtm.power.PowerManager.scaledCost(net.minecraft.client.Minecraft.getInstance().player, data, power));
            Draw.chip(g, mc.font, Component.literal(mc.font.plainSubstrByWidth(armed.getString(), WIDTH - 8)), x, y - 24, Theme.GOLD);
        }
    }

    private BloodHud() {}
}
