package com.epi0.vtm.nemesis;

import java.util.Locale;
import java.util.Optional;

/** Errands a bound vampire can be sent on; resolved by the simulation while they are away. */
public enum MissionType {
    SPY(24000L), HUNT(48000L), TRIBUTE(24000L);

    private final long duration;

    MissionType(long duration) {
        this.duration = duration;
    }

    public long duration() {
        return duration;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<MissionType> byId(String id) {
        for (MissionType t : values()) {
            if (t.id().equals(id)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
