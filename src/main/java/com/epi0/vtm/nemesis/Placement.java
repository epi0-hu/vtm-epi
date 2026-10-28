package com.epi0.vtm.nemesis;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Finds somewhere a vampire can stand, in terrain that is already loaded. */
public final class Placement {

    /** A spot between {@code min} and {@code max} blocks from {@code center}, or null if none was found. */
    @Nullable
    public static Vec3 spot(ServerLevel level, Vec3 center, double min, double max, RandomSource random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = min + random.nextDouble() * (max - min);
            int x = (int) Math.floor(center.x + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.z + Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (level.getBlockState(feet.below()).isSolid() && level.getFluidState(feet).isEmpty()
                    && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                    && Math.abs(y - center.y) < 24) {
                return new Vec3(x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    private Placement() {}
}
