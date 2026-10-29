package com.epi0.vtm.npc.goal;

import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Job;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Only the watch (and a ghoul in their master's service) fights; everyone else in the city runs. */
public class WatchmanAttackGoal extends MeleeAttackGoal {
    private final CivilianNpc npc;

    public WatchmanAttackGoal(CivilianNpc npc, double speed) {
        super(npc, speed, true);
        this.npc = npc;
    }

    private boolean fights() {
        return npc.job() == Job.WATCHMAN || npc.job() == Job.HUNTER || npc.ghoulOwner() != null;
    }

    @Override
    public boolean canUse() {
        return fights() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return fights() && super.canContinueToUse();
    }
}
