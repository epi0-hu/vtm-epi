package com.epi0.vtm.court;

import java.util.Locale;
import java.util.Optional;

/**
 * A standing order of a player-run court. At most {@link Reign#MAX_DECREES} are in force at once. Names and
 * descriptions are the lang keys {@code decree.vtm.<id>} and {@code decree.vtm.<id>.desc}.
 */
public enum Decree {
    /** Cities enforce the Masquerade and cool faster; breaches cost standing. */
    STRICT_MASQUERADE,
    /** Cities no longer police feeding; income is higher, but the cities slowly lose their hold. */
    OPEN_HUNTING,
    /** Tribute is a half as large again; seat-holders resent it. */
    TITHES,
    /** The faction fights harder and more often; enforcers like it. */
    WAR_FOOTING,
    /** The faction does not attack, and no one is looking for a fight; the ambitious get restless. */
    PEACE,
    /** Spends influence to keep the court loyal. */
    PATRONAGE;

    public static final int COST = 10;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean conflictsWith(Decree other) {
        return this == STRICT_MASQUERADE && other == OPEN_HUNTING || this == OPEN_HUNTING && other == STRICT_MASQUERADE
                || this == WAR_FOOTING && other == PEACE || this == PEACE && other == WAR_FOOTING;
    }

    public static Optional<Decree> byId(String id) {
        for (Decree d : values()) {
            if (d.id().equals(id)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }
}
