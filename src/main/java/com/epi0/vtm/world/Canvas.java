package com.epi0.vtm.world;

import com.epi0.vtm.block.CoffinBlock;
import com.epi0.vtm.block.CoffinBlockEntity;
import com.epi0.vtm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Draws blocks in absolute coordinates, clipped to the area being generated. Structures span chunks and are built
 * one chunk at a time, so everything here silently ignores whatever falls outside the current box.
 */
public final class Canvas {
    private final WorldGenLevel level;
    private final BoundingBox box;
    private final long seed;

    public Canvas(WorldGenLevel level, BoundingBox box, long seed) {
        this.level = level;
        this.box = box;
        this.seed = seed;
    }

    public static BlockState state(Block block) {
        return block.defaultBlockState();
    }

    public static BlockState stairs(Block block, Direction ascending, boolean upsideDown) {
        return block.defaultBlockState().setValue(StairBlock.FACING, ascending)
                .setValue(StairBlock.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
    }

    public boolean inside(int x, int y, int z) {
        return box.isInside(x, y, z);
    }

    public boolean set(int x, int y, int z, BlockState state) {
        if (!box.isInside(x, y, z)) {
            return false;
        }
        level.setBlock(new BlockPos(x, y, z), state, 2);
        return true;
    }

    public BlockEntity blockEntity(int x, int y, int z) {
        return box.isInside(x, y, z) ? level.getBlockEntity(new BlockPos(x, y, z)) : null;
    }

    public BlockState at(int x, int y, int z) {
        return box.isInside(x, y, z) ? level.getBlockState(new BlockPos(x, y, z)) : Blocks.AIR.defaultBlockState();
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.max(x1, box.minX()); x <= Math.min(x2, box.maxX()); x++) {
            for (int y = Math.max(y1, box.minY()); y <= Math.min(y2, box.maxY()); y++) {
                for (int z = Math.max(z1, box.minZ()); z <= Math.min(z2, box.maxZ()); z++) {
                    level.setBlock(new BlockPos(x, y, z), state, 2);
                }
            }
        }
    }

    public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, Blocks.AIR.defaultBlockState());
    }

    /** A hollow room: walls, floor and ceiling of {@code wall}, air inside. */
    public void room(int x1, int y1, int z1, int x2, int y2, int z2, BlockState wall) {
        fill(x1, y1, z1, x2, y2, z2, wall);
        clear(x1 + 1, y1 + 1, z1 + 1, x2 - 1, y2 - 1, z2 - 1);
    }

    /** Walls only (no floor or ceiling), air inside. */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState wall) {
        for (int y = y1; y <= y2; y++) {
            fill(x1, y, z1, x2, y, z1, wall);
            fill(x1, y, z2, x2, y, z2, wall);
            fill(x1, y, z1, x1, y, z2, wall);
            fill(x2, y, z1, x2, y, z2, wall);
        }
    }

    /** Fills downward from {@code y - 1} until solid ground, so a building does not float. */
    public void foundation(int x1, int z1, int x2, int z2, int y, BlockState state) {
        for (int x = Math.max(x1, box.minX()); x <= Math.min(x2, box.maxX()); x++) {
            for (int z = Math.max(z1, box.minZ()); z <= Math.min(z2, box.maxZ()); z++) {
                for (int dy = y - 1; dy > y - 14 && dy >= box.minY(); dy--) {
                    BlockPos pos = new BlockPos(x, dy, z);
                    if (level.getBlockState(pos).isSolid() && !level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LEAVES)) {
                        break;
                    }
                    if (dy <= box.maxY()) {
                        level.setBlock(pos, state, 2);
                    }
                }
            }
        }
    }

    public void chest(int x, int y, int z, Direction facing, ResourceLocation lootTable, RandomSource random) {
        if (set(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing))) {
            RandomizableContainer.setBlockEntityLootTable(level, random, new BlockPos(x, y, z),
                    ResourceKey.create(Registries.LOOT_TABLE, lootTable));
        }
    }

    public void coffin(int x, int y, int z, Direction facing, ResourceLocation faction) {
        if (set(x, y, z, ModBlocks.COFFIN.get().defaultBlockState().setValue(CoffinBlock.FACING, facing))) {
            BlockEntity entity = level.getBlockEntity(new BlockPos(x, y, z));
            if (entity instanceof CoffinBlockEntity coffin && faction != null) {
                coffin.setFaction(faction);
            }
        }
    }

    /** A stable pseudo-random number in [0, bound) for a place, so every chunk agrees about the same block. */
    public int roll(int x, int y, int z, int bound) {
        long h = seed ^ (x * 341873128712L) ^ (y * 132897987541L) ^ (z * 42317861L);
        h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (int) Math.floorMod(h, (long) bound);
    }

    public boolean chance(int x, int y, int z, int percent) {
        return roll(x, y, z, 100) < percent;
    }
}
