package com.epi0.vtm.npc.goal;

import com.epi0.vtm.item.HunterArms;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Job;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** A hunter with a crossbow or rifle in the off hand shoots from a distance and closes with the sword only when the target does. */
public class HunterRangedGoal extends Goal {
    /** Inside this many blocks, a hunter puts the weapon down and uses the sword. */
    private static final double CLOSE = 5;
    private final CivilianNpc npc;
    private int cooldown;

    public HunterRangedGoal(CivilianNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private HunterArms.Profile profile() {
        return HunterArms.profile(npc.getOffhandItem());
    }

    private boolean valid() {
        LivingEntity target = npc.getTarget();
        HunterArms.Profile profile = profile();
        return npc.job() == Job.HUNTER && profile != null && target != null && target.isAlive()
                && npc.distanceToSqr(target) <= profile.range * profile.range && npc.distanceToSqr(target) >= CLOSE * CLOSE;
    }

    @Override
    public boolean canUse() {
        return valid() && npc.hasLineOfSight(npc.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return valid();
    }

    @Override
    public void start() {
        cooldown = Math.max(cooldown, 15);
        npc.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = npc.getTarget();
        HunterArms.Profile profile = profile();
        if (target == null || profile == null) {
            return;
        }
        npc.getLookControl().setLookAt(target, 30F, 30F);
        if (!npc.hasLineOfSight(target)) {
            npc.getNavigation().moveTo(target, 1.0); // step out to where there is a shot
            return;
        }
        npc.getNavigation().stop();
        if (--cooldown <= 0 && npc.level() instanceof ServerLevel level) {
            Vec3 aim = target.getEyePosition().subtract(npc.getEyePosition());
            HunterArms.fire(level, npc, aim, profile);
            npc.swing(InteractionHand.OFF_HAND);
            cooldown = profile.cooldown + npc.getRandom().nextInt(15);
        }
    }
}
