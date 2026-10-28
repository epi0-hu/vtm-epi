package com.epi0.vtm.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public final class VtmCodecs {
    /** "#RRGGBB" (the '#' is optional) <-> packed RGB int. */
    public static final Codec<Integer> HEX_COLOR = Codec.STRING.comapFlatMap(s -> {
        String hex = s.startsWith("#") ? s.substring(1) : s;
        try {
            return DataResult.success(Integer.parseInt(hex, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Invalid colour: " + s);
        }
    }, color -> String.format("#%06X", color & 0xFFFFFF));

    private VtmCodecs() {}
}
