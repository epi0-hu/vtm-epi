package com.epi0.vtm.npc;

import java.util.Locale;
import java.util.UUID;

/**
 * Who a mortal is, besides their name: how they take to strangers, and where they are in life. Worked out from the
 * NPC's identity, so it is always the same person and nothing has to be saved.
 */
public record Persona(Temperament temperament, Age age) {

    public enum Temperament {
        CHEERFUL, NERVOUS, BRAVE, SUSPICIOUS, LONELY, DEVOUT, CURIOUS;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Age {
        YOUNG, ADULT, ELDER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static Persona of(UUID id) {
        long h = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits();
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        Temperament[] temperaments = Temperament.values();
        int roll = (int) Math.floorMod(h >>> 8, 10L);
        Age age = roll < 3 ? Age.YOUNG : roll < 8 ? Age.ADULT : Age.ELDER;
        return new Persona(temperaments[(int) Math.floorMod(h, (long) temperaments.length)], age);
    }

    /** The trouble in a city at which this person starts to be afraid of a player. */
    public int fearHeat() {
        return switch (temperament) {
            case NERVOUS -> 25;
            case BRAVE -> 55;
            default -> CivilianNpc.FRIGHTENED;
        };
    }

    /** The rapport at which this person counts a player as a friend. */
    public int friendRapport() {
        return switch (temperament) {
            case CHEERFUL, LONELY -> 2;
            case SUSPICIOUS -> 5;
            default -> CivilianNpc.FRIENDLY_RAPPORT;
        };
    }

    /** What a conversation moves rapport by, for this person: the suspicious are quick to take offence. */
    public int scaleRapport(int delta) {
        return temperament == Temperament.SUSPICIOUS && delta < 0 ? delta * 2 : delta;
    }
}
