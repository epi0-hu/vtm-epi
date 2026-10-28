package com.epi0.vtm.npc;

/** How an NPC regards a given creature right now. */
public enum Attitude {
    FRIENDLY,
    NEUTRAL,
    /** Afraid: keeps its distance. */
    WARY,
    /** Will attack on sight. */
    HOSTILE
}
