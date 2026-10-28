package com.epi0.vtm.npc.goal;

import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Job;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Only the watch fights; everyone else in the city runs. */
public class WatchmanAttackGoal extends MeleeAttackGoal {
    private final CivilianNpc npc;

    public WatchmanAttackGoal(CivilianNpc npc, double speed) {
        super(npc, speed, true);
        this.npc = npc;
    }

    @Override
    public boolean canUse() {
        return npc.job() == Job.WATCHMAN && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return npc.job() == Job.WATCHMAN && super.canContinueToUse();
    }
}
