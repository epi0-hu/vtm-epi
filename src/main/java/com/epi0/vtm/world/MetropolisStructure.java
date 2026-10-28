package com.epi0.vtm.world;

import com.epi0.vtm.registry.ModStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A metropolis: a city of districts, avenues and towers, 228 blocks across. It is levelled onto one pad, so it
 * tolerates more slope than a town, but not water.
 */
public class MetropolisStructure extends Structure {
    public static final MapCodec<MetropolisStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            ResourceLocation.CODEC.listOf().fieldOf("factions").forGetter(s -> s.factions),
            Codec.INT.optionalFieldOf("max_slope", 12).forGetter(s -> s.maxSlope)
    ).apply(i, MetropolisStructure::new));

    private static final int SAMPLES = 7;

    private final List<ResourceLocation> factions;
    private final int maxSlope;

    public MetropolisStructure(StructureSettings settings, List<ResourceLocation> factions, int maxSlope) {
        super(settings);
        this.factions = factions;
        this.maxSlope = maxSlope;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMinBlockX();
        int z = context.chunkPos().getMinBlockZ();
        int size = CityPlan.size();
        List<Integer> heights = new ArrayList<>();
        int wet = 0;
        for (int i = 0; i < SAMPLES; i++) {
            for (int j = 0; j < SAMPLES; j++) {
                int sx = x + i * (size - 1) / (SAMPLES - 1);
                int sz = z + j * (size - 1) / (SAMPLES - 1);
                int h = context.chunkGenerator().getFirstFreeHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());
                var column = context.chunkGenerator().getBaseColumn(sx, sz, context.heightAccessor(), context.randomState());
                if (!column.getBlock(h - 1).getFluidState().isEmpty()) {
                    wet++;
                }
                heights.add(h);
            }
        }
        java.util.Collections.sort(heights);
        // A little water is a pond to fill; a lot is a lake.
        if (wet > SAMPLES * SAMPLES / 10 || heights.get(heights.size() - 1) - heights.get(0) > maxSlope * 2) {
            return Optional.empty();
        }
        // Level to the median, so that the hills are cut as much as the valleys are filled.
        int ground = Math.max(context.heightAccessor().getMinBuildHeight() + MetropolisPiece.DEPTH_BELOW + 1, heights.get(heights.size() / 2));
        if (heights.get(heights.size() - 1) - ground > maxSlope || ground - heights.get(0) > maxSlope) {
            return Optional.empty();
        }
        long seed = context.random().nextLong();
        ResourceLocation faction = factions.get(Math.floorMod((int) (seed >> 7), factions.size()));
        String name = CityStructure.nameFor(seed);
        return Optional.of(new GenerationStub(new BlockPos(x, ground, z),
                builder -> builder.addPiece(new MetropolisPiece(x, ground, z, seed, faction, name))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.METROPOLIS.get();
    }
}
