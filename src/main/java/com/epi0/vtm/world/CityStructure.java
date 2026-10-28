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

import java.util.List;
import java.util.Optional;

/**
 * A city on flat ground, ruled by one of the listed factions. Set up in {@code data/<ns>/worldgen/structure/*.json}.
 */
public class CityStructure extends Structure {
    public static final MapCodec<CityStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            ResourceLocation.CODEC.listOf().fieldOf("factions").forGetter(s -> s.factions),
            Codec.INT.optionalFieldOf("max_slope", 3).forGetter(s -> s.maxSlope)
    ).apply(i, CityStructure::new));

    private static final String[] FIRST = {"Ash", "Black", "Cold", "Dun", "East", "Fair", "Gray", "High", "Iron", "Kings", "Low",
            "Mill", "North", "Old", "Raven", "Salt", "Thorn", "West", "Wolf", "Mourn"};
    private static final String[] LAST = {"ford", "bridge", "haven", "moor", "wick", "stead", "gate", "field", "hollow", "port",
            "mere", "ton", "cross", "fell", "watch"};

    private final List<ResourceLocation> factions;
    private final int maxSlope;

    public CityStructure(StructureSettings settings, List<ResourceLocation> factions, int maxSlope) {
        super(settings);
        this.factions = factions;
        this.maxSlope = maxSlope;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMinBlockX();
        int z = context.chunkPos().getMinBlockZ();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int sx = x + i * (CityPiece.SIZE - 1) / 2;
                int sz = z + j * (CityPiece.SIZE - 1) / 2;
                int h = context.chunkGenerator().getFirstFreeHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());
                var column = context.chunkGenerator().getBaseColumn(sx, sz, context.heightAccessor(), context.randomState());
                if (!column.getBlock(h - 1).getFluidState().isEmpty()) {
                    return Optional.empty();
                }
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        if (max - min > maxSlope) {
            return Optional.empty();
        }
        int ground = Math.max(context.heightAccessor().getMinBuildHeight() + CityPiece.DEPTH_BELOW + 1, (min + max) / 2);
        long seed = context.random().nextLong();
        ResourceLocation faction = factions.get(Math.floorMod((int) (seed >> 7), factions.size()));
        String finalName = nameFor(seed);
        return Optional.of(new GenerationStub(new BlockPos(x, ground, z),
                builder -> builder.addPiece(new CityPiece(x, ground, z, seed, faction, finalName))));
    }

    /** A place name drawn from the seed. */
    static String nameFor(long seed) {
        var random = new java.util.Random(seed);
        String name = FIRST[random.nextInt(FIRST.length)] + LAST[random.nextInt(LAST.length)];
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.CITY.get();
    }
}
