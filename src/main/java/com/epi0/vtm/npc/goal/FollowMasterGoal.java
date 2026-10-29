package com.epi0.vtm.npc.goal;

import com.epi0.vtm.npc.CivilianNpc;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** A ghoul keeps close to the vampire whose blood they drink. */
public class FollowMasterGoal extends Goal {
    private static final double START = 8.0;
    private static final double STOP = 3.5;
    private static final double TELEPORT = 40.0;

    private final CivilianNpc ghoul;
    private Player master;
    private int repath;

    public FollowMasterGoal(CivilianNpc ghoul) {
        this.ghoul = ghoul;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ghoul.ghoulOwner() == null || ghoul.getTarget() != null || ghoul.isResting()) {
            return false;
        }
        master = ghoul.level().getPlayerByUUID(ghoul.ghoulOwner());
        return master != null && !master.isSpectator() && ghoul.distanceToSqr(master) > START * START;
    }

    @Override
    public boolean canContinueToUse() {
        return master != null && master.isAlive() && ghoul.getTarget() == null && ghoul.distanceToSqr(master) > STOP * STOP;
    }

    @Override
    public void tick() {
        ghoul.getLookControl().setLookAt(master, 10F, ghoul.getMaxHeadXRot());
        if (ghoul.distanceToSqr(master) > TELEPORT * TELEPORT) {
            ghoul.teleportTo(master.getX() + ghoul.getRandom().nextInt(5) - 2, master.getY(), master.getZ() + ghoul.getRandom().nextInt(5) - 2);
            ghoul.getNavigation().stop();
            return;
        }
        if (--repath <= 0) {
            repath = 10;
            ghoul.getNavigation().moveTo(master, 1.2);
        }
    }

    @Override
    public void stop() {
        ghoul.getNavigation().stop();
    }
}
