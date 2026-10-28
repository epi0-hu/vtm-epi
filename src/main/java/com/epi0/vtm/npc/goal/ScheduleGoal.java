package com.epi0.vtm.npc.goal;

import com.epi0.vtm.npc.CivilianNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** City people go where the hour sends them: to work, to the square, to the club, home to bed. */
public class ScheduleGoal extends Goal {
    private static final double ARRIVED = 6.0;
    private static final double FAR = 12.0;
    private static final int GIVE_UP = 600;

    private final CivilianNpc npc;
    private BlockPos target;
    private int timeout;
    private int repath;

    public ScheduleGoal(CivilianNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (npc.cityId() == null || npc.isResting() || npc.state() != CivilianNpc.State.NORMAL || npc.getRandom().nextInt(20) != 0) {
            return false;
        }
        target = npc.anchor();
        return target != null && npc.distanceToSqr(Vec3.atBottomCenterOf(target)) > FAR * FAR;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && --timeout > 0 && npc.state() == CivilianNpc.State.NORMAL
                && npc.distanceToSqr(Vec3.atBottomCenterOf(target)) > ARRIVED * ARRIVED;
    }

    @Override
    public void start() {
        timeout = GIVE_UP;
        repath = 0;
    }

    @Override
    public void tick() {
        if (--repath <= 0) {
            repath = 30;
            npc.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.95);
        }
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
    }
}
