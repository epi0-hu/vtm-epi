package com.epi0.vtm.npc.goal;

import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.npc.VtmNpc;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/** A hungry vampire stalks a civilian, bites, and lets the world believe nothing happened. */
public class FeedOnPreyGoal extends net.minecraft.world.entity.ai.goal.Goal {
    private static final int BITE_TICKS = 30;

    private final VampireNpc vampire;
    @Nullable
    private CivilianNpc prey;
    private int biteTimer;

    public FeedOnPreyGoal(VampireNpc vampire) {
        this.vampire = vampire;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!vampire.isHungry() || vampire.shouldRestNow() || vampire.getTarget() != null) {
            return false;
        }
        prey = vampire.findPrey();
        return prey != null;
    }

    @Override
    public boolean canContinueToUse() {
        return prey != null && prey.isAlive() && vampire.blood() < 8F && vampire.getTarget() == null;
    }

    @Override
    public void start() {
        biteTimer = 0;
        vampire.getNavigation().moveTo(prey, 1.3);
    }

    @Override
    public void tick() {
        vampire.getLookControl().setLookAt(prey, 30F, 30F);
        if (biteTimer > 0) {
            if (--biteTimer == 0) {
                vampire.feedOn(prey);
                vampire.setState(VtmNpc.State.NORMAL);
                prey = null;
            }
            return;
        }
        if (vampire.distanceToSqr(prey) < 3.0) {
            vampire.getNavigation().stop();
            vampire.setState(VtmNpc.State.FEEDING);
            vampire.triggerAnim(VtmNpc.CONTROLLER_ACTION, "bite");
            biteTimer = BITE_TICKS;
        } else if (vampire.getNavigation().isDone()) {
            vampire.getNavigation().moveTo(prey, 1.3);
        }
    }

    @Override
    public void stop() {
        if (vampire.state() == VtmNpc.State.FEEDING) {
            vampire.setState(VtmNpc.State.NORMAL);
        }
        prey = null;
    }
}
