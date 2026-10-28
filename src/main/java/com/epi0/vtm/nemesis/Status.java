package com.epi0.vtm.nemesis;

import java.util.Locale;

public enum Status {
    ACTIVE, TORPOR, DESTROYED, BOUND;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Status byId(String id) {
        for (Status s : values()) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return ACTIVE;
    }
}
