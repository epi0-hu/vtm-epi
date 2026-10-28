package com.epi0.vtm.npc.goal;

import com.epi0.vtm.npc.VtmNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Ambles around, but keeps the walk anchored to home so NPCs stay in "their" neighbourhood. */
public class HomeStrollGoal extends WaterAvoidingRandomStrollGoal {
    private static final int RADIUS = 14;

    private final VtmNpc npc;

    public HomeStrollGoal(VtmNpc npc, double speed) {
        super(npc, speed);
        this.npc = npc;
    }

    @Nullable
    @Override
    protected Vec3 getPosition() {
        BlockPos home = npc.strollCentre();
        if (home != null && npc.distanceToSqr(Vec3.atBottomCenterOf(home)) > RADIUS * RADIUS * 0.5) {
            Vec3 back = LandRandomPos.getPosTowards(npc, 8, 4, Vec3.atBottomCenterOf(home));
            if (back != null) {
                return back;
            }
        }
        return super.getPosition();
    }
}
