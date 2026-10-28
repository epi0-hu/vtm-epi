package com.epi0.vtm.client.ui;

/** A rectangle on the screen, in GUI pixels. */
public record Rect(int x, int y, int w, int h) {
    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public Rect inset(int d) {
        return new Rect(x + d, y + d, Math.max(0, w - 2 * d), Math.max(0, h - 2 * d));
    }

    public Rect inset(int horizontal, int vertical) {
        return new Rect(x + horizontal, y + vertical, Math.max(0, w - 2 * horizontal), Math.max(0, h - 2 * vertical));
    }

    public Rect withY(int newY, int newH) {
        return new Rect(x, newY, w, newH);
    }

    /** The overlap of two rectangles (empty if they do not meet). */
    public Rect clip(Rect other) {
        int nx = Math.max(x, other.x);
        int ny = Math.max(y, other.y);
        int nr = Math.min(right(), other.right());
        int nb = Math.min(bottom(), other.bottom());
        return new Rect(nx, ny, Math.max(0, nr - nx), Math.max(0, nb - ny));
    }

    public boolean empty() {
        return w <= 0 || h <= 0;
    }
}
