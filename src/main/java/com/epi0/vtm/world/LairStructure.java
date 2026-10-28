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

import java.util.Optional;

/** A vampire's lair of one theme, belonging to one faction. Set up in {@code data/<ns>/worldgen/structure/*.json}. */
public class LairStructure extends Structure {
    public static final MapCodec<LairStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.STRING.fieldOf("theme").forGetter(s -> s.theme.name().toLowerCase(java.util.Locale.ROOT)),
            ResourceLocation.CODEC.fieldOf("faction").forGetter(s -> s.faction),
            ResourceLocation.CODEC.optionalFieldOf("clan").forGetter(s -> Optional.ofNullable(s.clan))
    ).apply(i, (settings, theme, faction, clan) -> new LairStructure(settings,
            LairPiece.Theme.valueOf(theme.toUpperCase(java.util.Locale.ROOT)), faction, clan.orElse(null))));

    private final LairPiece.Theme theme;
    private final ResourceLocation faction;
    @org.jetbrains.annotations.Nullable
    private final ResourceLocation clan;

    public LairStructure(StructureSettings settings, LairPiece.Theme theme, ResourceLocation faction,
                         @org.jetbrains.annotations.Nullable ResourceLocation clan) {
        super(settings);
        this.theme = theme;
        this.faction = faction;
        this.clan = clan;
    }

    /**
     * Underground lairs go where the caves are: the highest cave floor with room to stand, well below the surface.
     * Where there is no cave (flat or shallow worlds) the lair is simply dug out at depth.
     */
    private Optional<GenerationStub> undergroundPoint(GenerationContext context) {
        int x = context.chunkPos().getMinBlockX() + 1;
        int z = context.chunkPos().getMinBlockZ() + 1;
        var generator = context.chunkGenerator();
        int surface = generator.getFirstFreeHeight(x + theme.width / 2, z + theme.depth / 2, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        int minY = context.heightAccessor().getMinBuildHeight() + 8;
        var column = generator.getBaseColumn(x + theme.width / 2, z + theme.depth / 2, context.heightAccessor(), context.randomState());
        int floor = Integer.MIN_VALUE;
        for (int y = surface - 14; y >= minY; y--) {
            if (column.getBlock(y).isAir() && column.getBlock(y + 1).isAir() && column.getBlock(y + 2).isAir()
                    && column.getBlock(y - 1).isSolid() && column.getBlock(y - 1).getFluidState().isEmpty()) {
                floor = y;
                break;
            }
        }
        if (floor == Integer.MIN_VALUE) {
            floor = Math.max(minY, surface - 24);
        }
        int fy = floor;
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(new BlockPos(x, fy, z),
                builder -> builder.addPiece(new LairPiece(theme, faction, clan, x, fy, z, seed, surface))));
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (theme.underground) {
            return undergroundPoint(context);
        }
        int x = context.chunkPos().getMinBlockX() + 1;
        int z = context.chunkPos().getMinBlockZ() + 1;
        // Never so low that the cellar would fall out of the world (only matters in unusually shallow worlds).
        int floor = context.heightAccessor().getMinBuildHeight() + 9;
        int terrain = context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        int ground = Math.max(floor, terrain);
        // Not on water, and not where the terrain falls away on one side.
        var column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
        if (!column.getBlock(ground - 1).getFluidState().isEmpty()) {
            return Optional.empty();
        }
        for (int[] offset : new int[][]{{theme.width - 1, 0}, {0, theme.depth - 1}, {theme.width - 1, theme.depth - 1}}) {
            int h = context.chunkGenerator().getFirstFreeHeight(x + offset[0], z + offset[1], Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
            if (Math.abs(h - terrain) > 4) {
                return Optional.empty();
            }
        }
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(new BlockPos(x, ground, z),
                builder -> builder.addPiece(new LairPiece(theme, faction, clan, x, ground, z, seed, ground))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.LAIR.get();
    }
}
