package com.epi0.vtm.npc;

import java.util.Locale;
import java.util.Optional;

/** What a civilian does for a living. Titles are the lang keys {@code job.vtm.<id>}. */
public enum Job {
    RESIDENT, SHOPKEEPER, BARTENDER, WATCHMAN, PRIEST, CLERK, DETECTIVE, HUNTER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Job of(int ordinal) {
        return values()[Math.floorMod(ordinal, values().length)];
    }

    public static Optional<Job> byId(String id) {
        for (Job job : values()) {
            if (job.id().equals(id)) {
                return Optional.of(job);
            }
        }
        return Optional.empty();
    }
}
