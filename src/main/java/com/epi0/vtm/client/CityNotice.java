package com.epi0.vtm.client;

import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.network.CityNoticePayload;
import com.epi0.vtm.registry.Faction;
import com.epi0.vtm.registry.VtmRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The city the player is in: a banner that fades in on arrival, and a small plate with their heat while they stay. */
public final class CityNotice {
    private static final int BANNER_TICKS = 90;

    private static String city;
    private static ResourceLocation owner;
    private static int heat;
    private static long arrived = Long.MIN_VALUE;

    public static void receive(CityNoticePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        String fresh = payload.city().orElse(null);
        if (fresh != null && !fresh.equals(city)) {
            arrived = mc.level == null ? 0 : mc.level.getGameTime();
        }
        city = fresh;
        owner = payload.owner();
        heat = payload.heat();
    }

    static void clear() {
        city = null;
        arrived = Long.MIN_VALUE;
    }

    static void render(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (city == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        var font = mc.font;
        int cx = g.guiWidth() / 2;
        int color = Theme.readable(VtmRegistries.faction(mc.level.registryAccess(), owner).map(Faction::color).orElse(0xAAAAAA));
        Component name = Component.literal(city);
        Component rule = Component.translatable("gui.vtm.city.ruled_by", VtmRegistries.name("faction", owner));

        // Banner
        float age = mc.level.getGameTime() - arrived + partial;
        if (age >= 0 && age < BANNER_TICKS) {
            float fade = age < 10 ? age / 10F : age > BANNER_TICKS - 25 ? (BANNER_TICKS - age) / 25F : 1F;
            int a = (int) (fade * 255) << 24;
            int w = Math.max(font.width(name) * 2, font.width(rule)) + 40;
            int y = g.guiHeight() / 5;
            g.fill(cx - w / 2, y, cx + w / 2, y + 36, Theme.alpha(0x120A0E, (int) (fade * 0xB0)));
            g.fill(cx - w / 2, y, cx + w / 2, y + 1, Theme.alpha(color, (int) (fade * 255)));
            g.fill(cx - w / 2, y + 35, cx + w / 2, y + 36, Theme.alpha(color, (int) (fade * 255)));
            if (fade > 0.05F) {
                g.pose().pushPose();
                g.pose().translate(cx, y + 6, 0);
                g.pose().scale(2F, 2F, 1F);
                g.drawString(font, name, -font.width(name) / 2, 0, (color & 0xFFFFFF) | a, true);
                g.pose().popPose();
                g.drawString(font, rule, cx - font.width(rule) / 2, y + 25, (Theme.MUTED & 0xFFFFFF) | a, false);
            }
        }

        // Heat plate
        if (heat > 0) {
            boolean hot = heat >= 60;
            boolean pulse = hot && (mc.level.getGameTime() / 6) % 2 == 0;
            int w = 70;
            Rect plate = new Rect(cx - w / 2, 4, w, 14);
            Draw.panel(g, plate, Theme.alpha(0x120A0E, 0xB0));
            g.drawString(font, Component.translatable("gui.vtm.city.heat"), plate.x() + 4, plate.y() + 3, hot ? Theme.BAD : Theme.WARN, false);
            Draw.bar(g, new Rect(plate.x() + 30, plate.y() + 4, 36, 6), heat / 100F, pulse ? 0xFFFF5A4D : hot ? Theme.BAD : Theme.WARN);
        }
    }

    private CityNotice() {}
}
