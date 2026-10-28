package com.epi0.vtm.client;

import com.epi0.vtm.vampire.VampireData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Blood bar drawn where the food bar normally sits. */
final class BloodHud {
    private static final int WIDTH = 81;
    private static final int HEIGHT = 9;

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        VampireData data = VampireData.of(mc.player);
        if (!data.isVampire()) {
            return;
        }
        int x = graphics.guiWidth() / 2 + 10;
        int y = graphics.guiHeight() - 39;
        float fraction = Math.max(0F, Math.min(1F, data.blood() / data.maxBlood()));
        int filled = Math.round(WIDTH * fraction);
        // Turns darker and more menacing as hunger rises.
        int fill = data.hunger() >= 4 ? 0xFF6B0F0F : 0xFFB01E1E;

        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xFF000000);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF2A0A0A);
        graphics.fill(x, y, x + filled, y + HEIGHT, fill);
        Component label = Component.translatable("gui.vtm.blood_short",
                String.format("%.1f", data.blood()), Math.round(data.maxBlood()));
        graphics.drawCenteredString(mc.font, label, x + WIDTH / 2, y + 1, 0xFFFFFF);
    }

    private BloodHud() {}
}
