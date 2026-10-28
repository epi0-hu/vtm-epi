package com.epi0.vtm.npc.goal;

import com.epi0.vtm.npc.VampireNpc;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** A bound vampire keeps close to the player who owns them, and catches up if left behind. */
public class FollowOwnerGoal extends Goal {
    private static final double START = 7.0;
    private static final double STOP = 3.0;
    private static final double TELEPORT = 36.0;

    private final VampireNpc vampire;
    private Player owner;
    private int repath;

    public FollowOwnerGoal(VampireNpc vampire) {
        this.vampire = vampire;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (vampire.boundOwner() == null || vampire.getTarget() != null || vampire.isYielding()) {
            return false;
        }
        owner = vampire.level().getPlayerByUUID(vampire.boundOwner());
        return owner != null && !owner.isSpectator() && vampire.distanceToSqr(owner) > START * START;
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null && owner.isAlive() && vampire.getTarget() == null
                && vampire.distanceToSqr(owner) > STOP * STOP;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void tick() {
        vampire.getLookControl().setLookAt(owner, 10F, vampire.getMaxHeadXRot());
        if (vampire.distanceToSqr(owner) > TELEPORT * TELEPORT) {
            vampire.teleportTo(owner.getX() + vampire.getRandom().nextInt(5) - 2, owner.getY(), owner.getZ() + vampire.getRandom().nextInt(5) - 2);
            vampire.getNavigation().stop();
            return;
        }
        if (--repath <= 0) {
            repath = 10;
            vampire.getNavigation().moveTo(owner, 1.2);
        }
    }

    @Override
    public void stop() {
        vampire.getNavigation().stop();
    }
}
