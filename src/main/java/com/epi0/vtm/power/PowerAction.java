package com.epi0.vtm.power;

@FunctionalInterface
public interface PowerAction {
    /** @return false if the power fizzled (e.g. no target); the player is then not charged. */
    boolean run(PowerContext ctx);
}
