package com.epi0.vtm.client.ui;

/** The look of every screen: one palette, one set of spacings. */
public final class Theme {
    public static final int BACKDROP = 0xA0050304;
    public static final int PANEL = 0xF2161014;
    public static final int PANEL_ALT = 0xF2201820;
    public static final int PANEL_HI = 0xF22C2028;
    public static final int BORDER = 0xFF5C1A1E;
    public static final int BORDER_DIM = 0xFF3A2A2C;
    public static final int ACCENT = 0xFFB01E1E;
    public static final int ACCENT_DIM = 0xFF6B1414;
    public static final int TEXT = 0xFFEDE6E6;
    public static final int MUTED = 0xFFA39A9A;
    public static final int DIM = 0xFF645A5C;
    public static final int GOOD = 0xFF6FBF73;
    public static final int WARN = 0xFFD9A441;
    public static final int BAD = 0xFFE0524D;
    public static final int GOLD = 0xFFE6C070;
    public static final int HOVER = 0x28FFFFFF;
    public static final int SELECT = 0x3CB01E1E;

    public static final int PAD = 8;
    public static final int GAP = 4;
    public static final int ROW = 12;
    public static final int BUTTON_HEIGHT = 16;

    /** Opaque, and lifted towards white if the colour would be hard to read on the dark panel. */
    public static int readable(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        double luma = 0.299 * r + 0.587 * g + 0.114 * b;
        if (luma < 125) {
            double mix = (125 - luma) / (255 - luma);
            r += (int) ((255 - r) * mix);
            g += (int) ((255 - g) * mix);
            b += (int) ((255 - b) * mix);
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static int alpha(int argb, int alpha) {
        return (alpha & 0xFF) << 24 | argb & 0xFFFFFF;
    }

    /** Linear blend of two ARGB colours. */
    public static int mix(int a, int b, float t) {
        int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    private Theme() {}
}
