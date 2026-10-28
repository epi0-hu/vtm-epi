package com.epi0.vtm.nemesis;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What makes one named vampire different from another. Strengths and weaknesses change how the vampire fights, and
 * are not known to a player until they have seen them in action or learned of them. Names and descriptions are the
 * lang keys {@code trait.vtm.<id>} and {@code trait.vtm.<id>.desc}.
 */
public enum Trait {
    IRON_WILL(Kind.STRENGTH), BLOODTHIRSTY(Kind.STRENGTH), SWIFT(Kind.STRENGTH), BRUTE(Kind.STRENGTH),
    DEFLECTOR(Kind.STRENGTH), UNDYING(Kind.STRENGTH), FIRE_HARDENED(Kind.STRENGTH), COMMANDER(Kind.STRENGTH),

    SUN_SCARRED(Kind.WEAKNESS), FLAMMABLE(Kind.WEAKNESS), BRITTLE(Kind.WEAKNESS), GLASS_JAW(Kind.WEAKNESS),
    COWARD(Kind.WEAKNESS), HUNGER_BOUND(Kind.WEAKNESS),

    VENGEFUL(Kind.QUIRK), PATIENT(Kind.QUIRK), PROUD(Kind.QUIRK), RECKLESS(Kind.QUIRK), GRATEFUL(Kind.QUIRK);

    public enum Kind { STRENGTH, WEAKNESS, QUIRK }

    private static final Set<Set<Trait>> CONFLICTS = Set.of(
            Set.of(DEFLECTOR, BRITTLE), Set.of(FIRE_HARDENED, FLAMMABLE), Set.of(FIRE_HARDENED, SUN_SCARRED),
            Set.of(BRUTE, SWIFT), Set.of(UNDYING, COWARD), Set.of(PROUD, COWARD), Set.of(IRON_WILL, GLASS_JAW));

    private final Kind kind;

    Trait(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return "trait.vtm." + id();
    }

    public String descriptionKey() {
        return nameKey() + ".desc";
    }

    public boolean conflictsWith(Trait other) {
        return this != other && CONFLICTS.contains(Set.of(this, other));
    }

    public static Optional<Trait> byId(String id) {
        for (Trait trait : values()) {
            if (trait.id().equals(id)) {
                return Optional.of(trait);
            }
        }
        return Optional.empty();
    }
}
