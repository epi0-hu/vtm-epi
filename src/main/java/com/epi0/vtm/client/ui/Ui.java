package com.epi0.vtm.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Immediate-mode drawing for the codex: pages draw themselves every frame and, as they draw buttons and rows, register
 * the places that can be clicked. The same zones serve the mouse, tab-key focus and tooltips, and respect scrolling
 * areas (a zone half hidden by a scroll area can only be clicked where it shows).
 */
public final class Ui {
    public enum Style { NORMAL, PRIMARY, DANGER, QUIET, TOGGLE_ON }

    private record Zone(Rect rect, Rect clip, Runnable action, boolean enabled) {}

    private final List<Zone> zones = new ArrayList<>();
    private final Deque<Rect> clips = new ArrayDeque<>();
    private GuiGraphics g;
    private Font font;
    private int mouseX;
    private int mouseY;
    private int focus = -1;
    @Nullable
    private Component tooltip;
    private Rect tooltipArea;

    // ---------------------------------------------------------------- frame

    public void begin(GuiGraphics graphics, int mouseX, int mouseY) {
        this.g = graphics;
        this.font = Minecraft.getInstance().font;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.zones.clear();
        this.clips.clear();
        this.tooltip = null;
    }

    /** Draws the tooltip of whatever the mouse is over, last, so it lies on top. */
    public void end() {
        if (tooltip != null) {
            g.renderTooltip(font, font.split(tooltip, 200), mouseX, mouseY);
        }
    }

    public GuiGraphics g() {
        return g;
    }

    public Font font() {
        return font;
    }

    public int mouseX() {
        return mouseX;
    }

    public int mouseY() {
        return mouseY;
    }

    // ---------------------------------------------------------------- clipping

    public void pushClip(Rect r) {
        Rect clipped = clips.isEmpty() ? r : r.clip(clips.peek());
        clips.push(clipped);
        g.enableScissor(clipped.x(), clipped.y(), clipped.right(), clipped.bottom());
    }

    public void popClip() {
        clips.pop();
        g.disableScissor();
    }

    private Rect currentClip() {
        return clips.isEmpty() ? new Rect(0, 0, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2) : clips.peek();
    }

    // ---------------------------------------------------------------- hit zones

    public boolean hovered(Rect r) {
        return r.contains(mouseX, mouseY) && currentClip().contains(mouseX, mouseY);
    }

    /** Registers a clickable area. Returns whether the mouse is over it. */
    public boolean zone(Rect r, Runnable action, @Nullable Component tip) {
        return zone(r, action, tip, true);
    }

    public boolean zone(Rect r, Runnable action, @Nullable Component tip, boolean enabled) {
        zones.add(new Zone(r, currentClip(), action, enabled));
        boolean hover = hovered(r);
        if (hover && tip != null) {
            tooltip = tip;
        }
        if (focus >= 0 && focus < zones.size() && focus == zones.size() - 1) {
            g.renderOutline(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, Theme.GOLD);
        }
        return hover;
    }

    /** A tooltip for an area that is not clickable. */
    public void tooltip(Rect r, Component tip) {
        if (hovered(r)) {
            tooltip = tip;
        }
    }

    public boolean click(double x, double y) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            if (z.rect.contains(x, y) && z.clip.contains(x, y)) {
                if (z.enabled) {
                    focus = -1;
                    z.action.run();
                }
                return true;
            }
        }
        return false;
    }

    /** Moves keyboard focus to the next (or previous) enabled zone. */
    public void cycleFocus(boolean backwards) {
        if (zones.isEmpty()) {
            return;
        }
        for (int step = 1; step <= zones.size(); step++) {
            int i = Math.floorMod(focus + (backwards ? -step : step), zones.size());
            if (zones.get(i).enabled) {
                focus = i;
                return;
            }
        }
    }

    public boolean activateFocus() {
        if (focus >= 0 && focus < zones.size() && zones.get(focus).enabled) {
            zones.get(focus).action.run();
            return true;
        }
        return false;
    }

    public void clearFocus() {
        focus = -1;
    }

    // ---------------------------------------------------------------- widgets

    /** A button. Draws it and registers its zone; returns whether the mouse is over it. */
    public boolean button(Rect r, Component label, Style style, boolean enabled, Runnable action, @Nullable Component tip) {
        boolean hover = zone(r, action, tip, enabled) && enabled;
        int fill;
        int edge;
        int text;
        switch (style) {
            case PRIMARY -> {
                fill = hover ? 0xFF8E2024 : Theme.ACCENT_DIM;
                edge = Theme.ACCENT;
                text = Theme.TEXT;
            }
            case DANGER -> {
                fill = hover ? 0xFF6E1A1A : 0xFF3E1214;
                edge = Theme.BAD;
                text = 0xFFFFC9C6;
            }
            case QUIET -> {
                fill = hover ? Theme.PANEL_HI : 0x00000000;
                edge = Theme.BORDER_DIM;
                text = Theme.MUTED;
            }
            case TOGGLE_ON -> {
                fill = hover ? 0xFF7B2A2C : 0xFF5A1C1F;
                edge = Theme.GOLD;
                text = Theme.TEXT;
            }
            default -> {
                fill = hover ? 0xFF3A2C34 : Theme.PANEL_HI;
                edge = Theme.BORDER_DIM;
                text = Theme.TEXT;
            }
        }
        if (!enabled) {
            fill = 0xFF18121A;
            edge = 0xFF2A2024;
            text = Theme.DIM;
        }
        g.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        g.renderOutline(r.x(), r.y(), r.w(), r.h(), edge);
        g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, Theme.alpha(0xFFFFFF, enabled ? 0x14 : 0x06));
        String s = label.getString();
        int available = r.w() - 6;
        Component shown = font.width(s) <= available ? label
                : Component.literal(font.plainSubstrByWidth(s, available - font.width("...")) + "...");
        g.drawString(font, shown, r.x() + (r.w() - font.width(shown)) / 2, r.y() + (r.h() - font.lineHeight) / 2 + 1, text, false);
        return hover;
    }

    public boolean button(Rect r, Component label, Runnable action) {
        return button(r, label, Style.NORMAL, true, action, null);
    }

    /** A row that can be picked from a list: highlights on hover and when selected. */
    public boolean row(Rect r, boolean selected, Runnable action, @Nullable Component tip) {
        boolean hover = zone(r, action, tip);
        if (selected) {
            g.fill(r.x(), r.y(), r.right(), r.bottom(), Theme.SELECT);
            g.fill(r.x(), r.y(), r.x() + 2, r.bottom(), Theme.ACCENT);
        } else if (hover) {
            g.fill(r.x(), r.y(), r.right(), r.bottom(), Theme.HOVER);
        }
        return hover;
    }

    // ---------------------------------------------------------------- text

    public void text(Component t, int x, int y, int color) {
        g.drawString(font, t, x, y, color, false);
    }

    public void shadow(Component t, int x, int y, int color) {
        g.drawString(font, t, x, y, color, true);
    }

    public void centred(Component t, int cx, int y, int color) {
        g.drawString(font, t, cx - font.width(t) / 2, y, color, false);
    }

    public void right(Component t, int rx, int y, int color) {
        g.drawString(font, t, rx - font.width(t), y, color, false);
    }

    public int paragraph(Component t, int x, int y, int w, int color) {
        return Draw.paragraph(g, font, t, x, y, w, color);
    }

    public int measure(Component t, int w) {
        return Draw.measure(font, t, w);
    }

    /** Text cut to a width; a tooltip shows the whole of it if it was cut. */
    public void clipped(Component t, int x, int y, int w, int color) {
        if (Draw.clipped(g, font, t, x, y, w, color)) {
            tooltip(new Rect(x, y, w, font.lineHeight), t);
        }
    }

    public int chip(Component t, int x, int y, int color) {
        return Draw.chip(g, font, t, x, y, color);
    }

    /** A section heading: small capitals-ish label with a rule beneath it. Returns the height. */
    public int heading(Component t, int x, int y, int w) {
        g.drawString(font, t, x, y, Theme.GOLD, false);
        Draw.divider(g, x, y + 10, w);
        return 14;
    }

    /** A "label ........ value" row. */
    public void stat(Component label, Component value, int x, int y, int w, int valueColor) {
        g.drawString(font, label, x, y, Theme.MUTED, false);
        g.drawString(font, value, x + w - font.width(value), y, valueColor, false);
    }
}
