package com.epi0.vtm.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Stateless drawing helpers shared by every screen. */
public final class Draw {

    /** A framed panel: dark fill, a bright outer edge and a dim inner one. */
    public static void panel(GuiGraphics g, Rect r, int fill) {
        g.fill(r.x() - 1, r.y() - 1, r.right() + 1, r.bottom() + 1, Theme.BORDER);
        g.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, Theme.alpha(0xFFFFFF, 0x10));
    }

    public static void panel(GuiGraphics g, Rect r) {
        panel(g, r, Theme.PANEL);
    }

    /** A thin inset box, for grouping things inside a panel. */
    public static void inset(GuiGraphics g, Rect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), Theme.PANEL_ALT);
        g.renderOutline(r.x(), r.y(), r.w(), r.h(), Theme.BORDER_DIM);
    }

    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, Theme.BORDER_DIM);
    }

    /** A labelled meter: a dark track, a filled part, and the value written over it if there is room. */
    public static void bar(GuiGraphics g, Rect r, float fraction, int color) {
        g.fill(r.x() - 1, r.y() - 1, r.right() + 1, r.bottom() + 1, 0xFF000000);
        g.fill(r.x(), r.y(), r.right(), r.bottom(), 0xFF2A1A1C);
        int filled = Math.round(r.w() * Math.max(0F, Math.min(1F, fraction)));
        g.fill(r.x(), r.y(), r.x() + filled, r.bottom(), color);
        g.fill(r.x(), r.y(), r.x() + filled, r.y() + 1, Theme.alpha(0xFFFFFF, 0x30));
    }

    /** A bar that grows both ways from the middle, for values from -max to +max. */
    public static void centredBar(GuiGraphics g, Rect r, float fraction, int positive, int negative) {
        g.fill(r.x() - 1, r.y() - 1, r.right() + 1, r.bottom() + 1, 0xFF000000);
        g.fill(r.x(), r.y(), r.right(), r.bottom(), 0xFF2A1A1C);
        int mid = r.x() + r.w() / 2;
        int end = mid + Math.round(fraction * r.w() / 2F);
        g.fill(Math.min(mid, end), r.y(), Math.max(mid, end), r.bottom(), fraction >= 0 ? positive : negative);
        g.fill(mid, r.y() - 1, mid + 1, r.bottom() + 1, Theme.MUTED);
    }

    public static void pips(GuiGraphics g, int x, int y, int filled, int total, int color) {
        for (int i = 0; i < total; i++) {
            int px = x + i * 9;
            g.fill(px, y, px + 7, y + 7, 0xFF000000);
            g.fill(px + 1, y + 1, px + 6, y + 6, i < filled ? color : 0xFF2A1A1C);
        }
    }

    /** Text cut to a width, with an ellipsis if it did not fit. Returns true if it was cut. */
    public static boolean clipped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        String s = text.getString();
        if (font.width(s) <= width) {
            g.drawString(font, text, x, y, color, false);
            return false;
        }
        g.drawString(font, font.plainSubstrByWidth(s, Math.max(0, width - font.width("..."))) + "...", x, y, color, false);
        return true;
    }

    /** Wrapped text. Returns the height it took. */
    public static int paragraph(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(8, width));
        int h = 0;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x, y + h, color, false);
            h += font.lineHeight + 1;
        }
        return h;
    }

    /** How tall a paragraph would be, without drawing it. */
    public static int measure(Font font, Component text, int width) {
        return font.split(text, Math.max(8, width)).size() * (font.lineHeight + 1);
    }

    /** A small rounded-looking tag: coloured text on a tinted ground. Returns its width. */
    public static int chip(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        int w = font.width(text) + 8;
        g.fill(x, y, x + w, y + 11, Theme.alpha(color, 0x38));
        g.renderOutline(x, y, w, 11, Theme.alpha(color, 0x90));
        g.drawString(font, text, x + 4, y + 2, color, false);
        return w;
    }

    private Draw() {}
}
