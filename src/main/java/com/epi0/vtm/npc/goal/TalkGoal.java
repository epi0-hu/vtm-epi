package com.epi0.vtm.npc.goal;

import com.epi0.vtm.npc.VtmNpc;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Stands still and faces whoever the NPC is in conversation with. */
public class TalkGoal extends Goal {
    private final VtmNpc npc;
    private Player partner;

    public TalkGoal(VtmNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        partner = npc.talker() == null ? null : npc.level().getPlayerByUUID(npc.talker());
        return partner != null && npc.state() == VtmNpc.State.TALKING;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        npc.getLookControl().setLookAt(partner, 30F, 30F);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
