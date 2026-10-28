package com.epi0.vtm.npc.goal;

import com.epi0.vtm.npc.VampireNpc;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** A bound vampire fights whatever hurt, or was hurt by, the player who owns them. */
public class AssistOwnerGoal extends TargetGoal {
    private final VampireNpc vampire;
    private LivingEntity victim;
    private int timestamp;

    public AssistOwnerGoal(VampireNpc vampire) {
        super(vampire, false);
        this.vampire = vampire;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (vampire.boundOwner() == null || vampire.isYielding()) {
            return false;
        }
        Player owner = vampire.level().getPlayerByUUID(vampire.boundOwner());
        if (owner == null) {
            return false;
        }
        LivingEntity hurtBy = owner.getLastHurtByMob();
        LivingEntity hurt = owner.getLastHurtMob();
        if (hurtBy != null && owner.getLastHurtByMobTimestamp() != timestamp && valid(hurtBy)) {
            victim = hurtBy;
            timestamp = owner.getLastHurtByMobTimestamp();
            return true;
        }
        if (hurt != null && owner.getLastHurtMobTimestamp() != timestamp && valid(hurt)) {
            victim = hurt;
            timestamp = owner.getLastHurtMobTimestamp();
            return true;
        }
        return false;
    }

    private boolean valid(LivingEntity entity) {
        return entity != vampire && entity.isAlive() && !(entity instanceof Player p && p.getUUID().equals(vampire.boundOwner()))
                && !(entity instanceof VampireNpc other && vampire.boundOwner().equals(other.boundOwner()));
    }

    @Override
    public void start() {
        mob.setTarget(victim);
        super.start();
    }
}
