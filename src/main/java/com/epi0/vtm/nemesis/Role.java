package com.epi0.vtm.nemesis;

import java.util.Locale;
import java.util.Optional;

/** A seat in a faction's court. Titles are the lang keys {@code role.<ns>.<faction>.<role>}. */
public enum Role {
    LEADER(1, 2, 3), LIEUTENANT(2, 1, 2), ENFORCER(3, 1, 1), AGENT(Integer.MAX_VALUE, 0, 0);

    private final int seats;
    private final int rank;
    private final int power;

    Role(int seats, int rank, int power) {
        this.seats = seats;
        this.rank = rank;
        this.power = power;
    }

    /** Seats of this kind in one faction's court. */
    public int seats() {
        return seats;
    }

    /** Sect rank the role carries (see {@code Faction.ranks}). */
    public int rank() {
        return rank;
    }

    /** Extra weight the role adds to a vampire's power. */
    public int power() {
        return power;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Role> byId(String id) {
        for (Role role : values()) {
            if (role.id().equals(id)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
