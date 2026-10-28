package com.epi0.vtm.nemesis;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** How one named vampire stands to another. Always stored on both sides (SIRE mirrors CHILDE). */
public record Relation(UUID other, Type type) {
    public enum Type {
        SIRE, CHILDE, RIVAL, ALLY;

        public Type mirror() {
            return switch (this) {
                case SIRE -> CHILDE;
                case CHILDE -> SIRE;
                default -> this;
            };
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Type> byId(String id) {
            for (Type t : values()) {
                if (t.id().equals(id)) {
                    return Optional.of(t);
                }
            }
            return Optional.empty();
        }
    }
}
