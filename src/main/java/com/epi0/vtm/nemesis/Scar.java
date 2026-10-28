package com.epi0.vtm.nemesis;

import java.util.Locale;
import java.util.Optional;

/** A mark a defeat leaves. Visible to anyone who meets the vampire, and the source of an epithet. */
public enum Scar {
    BURNED, SUNBLASTED, PIERCED, MAULED, SHATTERED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Scar> byId(String id) {
        for (Scar scar : values()) {
            if (scar.id().equals(id)) {
                return Optional.of(scar);
            }
        }
        return Optional.empty();
    }
}
