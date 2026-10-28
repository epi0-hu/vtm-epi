package com.epi0.vtm.dialogue;

import net.minecraft.util.RandomSource;

/**
 * The V5 dice pool: d10s, 6 or more is a success, and every pair of 10s adds two further successes.
 * A vampire replaces some dice with Hunger dice. A 10 on one of those makes a win "messy" (the Beast shows),
 * and a 1 on one makes a failure "bestial".
 */
public record Roll(int pool, int hunger, int successes, boolean messyCritical, boolean bestialFailure) {

    public static Roll make(RandomSource random, int pool, int hunger, int difficulty) {
        pool = Math.max(1, pool);
        hunger = Math.max(0, Math.min(hunger, pool));
        int successes = 0;
        int tens = 0;
        boolean hungerTen = false;
        boolean hungerOne = false;
        for (int i = 0; i < pool; i++) {
            int die = random.nextInt(10) + 1;
            boolean hungerDie = i < hunger;
            if (die >= 6) {
                successes++;
            }
            if (die == 10) {
                tens++;
                hungerTen |= hungerDie;
            }
            if (die == 1) {
                hungerOne |= hungerDie;
            }
        }
        successes += 2 * (tens / 2);
        boolean critical = tens >= 2;
        boolean win = successes >= difficulty;
        return new Roll(pool, hunger, successes, win && critical && hungerTen, !win && hungerOne);
    }

    public boolean succeeded(int difficulty) {
        return successes >= difficulty;
    }
}
