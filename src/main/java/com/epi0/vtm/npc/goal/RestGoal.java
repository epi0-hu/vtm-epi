package com.epi0.vtm.npc.goal;

import com.epi0.vtm.npc.VtmNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Walks to the NPC's rest spot when it is their time to sleep, then holds still until it is time to wake. */
public class RestGoal extends Goal {
    private static final int GIVE_UP_TICKS = 600;
    private static final int REPATH_TICKS = 40;

    private final VtmNpc npc;
    private int giveUp;
    private int repath;

    public RestGoal(VtmNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.shouldRestNow() && npc.state() != VtmNpc.State.FEEDING && npc.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return npc.shouldRestNow() && npc.getTarget() == null;
    }

    @Override
    public void start() {
        giveUp = GIVE_UP_TICKS;
        repath = 0;
    }

    @Override
    public void tick() {
        if (npc.isResting()) {
            return;
        }
        BlockPos spot = npc.restSpot();
        boolean arrived = spot == null || npc.distanceToSqr(Vec3.atBottomCenterOf(spot)) < 6.0;
        if (arrived || --giveUp <= 0) {
            npc.getNavigation().stop();
            npc.setState(VtmNpc.State.SLEEPING);
            return;
        }
        if (--repath <= 0) {
            npc.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
            repath = REPATH_TICKS;
        }
    }

    @Override
    public void stop() {
        if (npc.isResting()) {
            npc.setState(VtmNpc.State.NORMAL);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
