package com.epi0.vtm.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/** A vertical scroll area: pages draw their content at {@link #top()} and the area clips it and draws the bar. */
public final class Scroll {
    private Rect view = new Rect(0, 0, 0, 0);
    private int content;
    private int offset;

    /** Starts the area. Everything drawn until {@link #end} is clipped to {@code view}. */
    public void begin(Ui ui, Rect view, int contentHeight) {
        this.view = view;
        this.content = contentHeight;
        this.offset = Math.max(0, Math.min(offset, Math.max(0, contentHeight - view.h())));
        ui.pushClip(view);
    }

    public int top() {
        return view.y() - offset;
    }

    public Rect view() {
        return view;
    }

    public void end(Ui ui) {
        ui.popClip();
        GuiGraphics g = ui.g();
        if (content > view.h()) {
            int trackX = view.right() - 3;
            g.fill(trackX, view.y(), trackX + 2, view.bottom(), Theme.alpha(0xFFFFFF, 0x10));
            int thumb = Math.max(12, view.h() * view.h() / content);
            int y = view.y() + (int) ((view.h() - thumb) * (offset / (double) (content - view.h())));
            g.fill(trackX, y, trackX + 2, y + thumb, Theme.MUTED);
        }
    }

    /** Scrolls by mouse-wheel notches. Returns whether there was anything to scroll. */
    public boolean wheel(double notches) {
        if (content <= view.h()) {
            return false;
        }
        offset = Math.max(0, Math.min(content - view.h(), offset - (int) Math.round(notches * 18)));
        return true;
    }

    public void scrollTo(int y) {
        offset = Math.max(0, y);
    }

    public void reset() {
        offset = 0;
    }

    public boolean overflowing() {
        return content > view.h();
    }
}
