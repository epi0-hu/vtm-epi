package com.epi0.vtm.world;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Nosferatu warren: a brick sewer set into the cave system, a channel of black water between two walkways, a nest
 * at the far end where the occupant sleeps, a tunnel that runs off into the dark, and a shaft up to a manhole in the
 * world above.
 */
final class SewerLair {
    static final int WIDTH = 21;
    static final int DEPTH = 13;

    /** Longest shaft worth building; deeper than this the lair is reached through the caves alone. */
    private static final int MAX_SHAFT = 70;

    private static BlockState brick(Canvas c, int x, int y, int z) {
        return switch (c.roll(x, y, z, 5)) {
            case 0 -> Canvas.state(Blocks.MOSSY_STONE_BRICKS);
            case 1 -> Canvas.state(Blocks.CRACKED_STONE_BRICKS);
            default -> Canvas.state(Blocks.STONE_BRICKS);
        };
    }

    /** {@code fy} is the level of the walkways; the channel runs one block below it. */
    static void build(Canvas c, int x0, int fy, int z0, int surface, ResourceLocation faction, ResourceLocation clan, RandomSource random) {
        int x1 = x0 + WIDTH - 1;
        int z1 = z0 + DEPTH - 1;
        int midZ = z0 + 6;
        // The shell: a solid block of brick, carved out below.
        for (int x = x0; x <= x1; x++) {
            for (int y = fy - 3; y <= fy + 5; y++) {
                for (int z = z0; z <= z1; z++) {
                    c.set(x, y, z, brick(c, x, y, z));
                }
            }
        }
        // The hall: walkways at fy-1 either side of a trench of water at fy-2.
        c.clear(x0 + 9, fy - 1, z0 + 3, x1 - 1, fy + 3, z0 + 9);
        c.fill(x0 + 9, fy - 2, z0 + 5, x1 - 1, fy - 2, z0 + 7, Canvas.state(Blocks.WATER));
        for (int x = x0 + 9; x <= x1 - 1; x++) {
            for (int z : new int[]{z0 + 3, z0 + 4, z0 + 8, z0 + 9}) {
                c.set(x, fy - 1, z, brick(c, x, fy - 1, z));
            }
        }
        // Arches every four blocks, lamps between them.
        for (int x = x0 + 11; x <= x1 - 2; x += 4) {
            c.fill(x, fy, z0 + 3, x, fy + 3, z0 + 3, Canvas.state(Blocks.STONE_BRICK_WALL));
            c.fill(x, fy, z0 + 9, x, fy + 3, z0 + 9, Canvas.state(Blocks.STONE_BRICK_WALL));
            c.set(x, fy + 3, z0 + 4, Canvas.stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, true));
            c.set(x, fy + 3, z0 + 8, Canvas.stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, true));
            c.set(x + 2, fy + 3, midZ, Canvas.state(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }
        // Grates onto nothing, and the outfall pipe at the end of the channel.
        for (int x = x0 + 12; x <= x1 - 3; x += 4) {
            c.set(x, fy + 1, z0 + 2, Canvas.state(Blocks.IRON_BARS));
        }
        c.clear(x1, fy - 1, z0 + 5, x1, fy, z0 + 7);
        // The nest: a wide room at the west end, entered through an arch in the dividing wall.
        c.clear(x0 + 1, fy - 1, z0 + 1, x0 + 7, fy + 4, z1 - 1);
        c.clear(x0 + 8, fy - 1, z0 + 5, x0 + 8, fy + 1, z0 + 7);
        c.set(x0 + 8, fy + 2, z0 + 5, Canvas.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
        c.set(x0 + 8, fy + 2, z0 + 7, Canvas.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
        c.fill(x0 + 8, fy - 2, z0 + 5, x0 + 8, fy - 2, z0 + 7, Canvas.state(Blocks.WATER));
        c.fill(x0 + 1, fy - 2, z0 + 1, x0 + 7, fy - 2, z1 - 1, Canvas.state(Blocks.STONE_BRICKS));
        c.fill(x0 + 1, fy - 1, z0 + 1, x0 + 7, fy - 1, z1 - 1, Canvas.state(Blocks.COBBLESTONE));
        for (int x = x0 + 1; x <= x0 + 7; x++) { // the floor is old and uneven
            for (int z = z0 + 1; z <= z1 - 1; z++) {
                if (c.chance(x, fy, z, 25)) {
                    c.set(x, fy - 1, z, Canvas.state(c.roll(x, fy, z, 2) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.GRAVEL));
                }
            }
        }
        // Where the sleeper lies: a dais, the coffin, candles, and the collected leavings of years of hiding.
        c.fill(x0 + 2, fy, z0 + 4, x0 + 5, fy, z0 + 8, Canvas.state(Blocks.DEEPSLATE_BRICKS));
        c.coffin(x0 + 3, fy + 1, z0 + 6, Direction.EAST, faction, clan);
        c.set(x0 + 3, fy + 1, z0 + 4, Canvas.state(Blocks.CANDLE));
        c.set(x0 + 3, fy + 1, z0 + 8, Canvas.state(Blocks.CANDLE));
        c.set(x0 + 5, fy + 1, z0 + 4, Canvas.state(Blocks.SKELETON_SKULL));
        c.chest(x0 + 1, fy, z0 + 2, Direction.SOUTH, LairPiece.LOOT, random);
        c.chest(x0 + 1, fy, z1 - 2, Direction.NORTH, LairPiece.LOOT, random);
        for (int i = 0; i < 14; i++) { // bones, scraps and cobweb
            int x = x0 + 1 + c.roll(i, 1, 1, 7);
            int z = z0 + 1 + c.roll(i, 2, 2, 11);
            if (c.at(x, fy, z).isAir() && c.at(x, fy - 1, z).isSolid()) {
                c.set(x, fy, z, Canvas.state(switch (c.roll(i, 3, 3, 4)) {
                    case 0 -> Blocks.BONE_BLOCK;
                    case 1 -> Blocks.COBWEB;
                    case 2 -> Blocks.BROWN_MUSHROOM;
                    default -> Blocks.COBBLESTONE_SLAB;
                }));
            }
        }
        c.set(x0 + 4, fy + 4, z0 + 2, Canvas.state(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        c.set(x0 + 4, fy + 4, z1 - 2, Canvas.state(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        c.fill(x0 + 7, fy, z0 + 1, x0 + 7, fy + 2, z0 + 3, Canvas.state(Blocks.COBWEB));
        // A side tunnel from the south wall of the hall that ends in a cave-in (what lies beyond, lava included, stays put).
        c.clear(x0 + 13, fy - 1, z0 + 10, x0 + 15, fy + 2, z1 - 1);
        c.clear(x0 + 14, fy + 3, z0 + 10, x0 + 14, fy + 3, z1 - 1);
        c.fill(x0 + 13, fy - 1, z1 - 1, x0 + 15, fy + 1, z1 - 1, Canvas.state(Blocks.MOSSY_COBBLESTONE));
        c.fill(x0 + 13, fy + 2, z1 - 2, x0 + 15, fy + 2, z1 - 1, Canvas.state(Blocks.COBWEB));
        // And the way up.
        int sx = x1 - 2;
        int sz = z0 + 3;
        if (surface - fy <= MAX_SHAFT && surface - fy > 6) {
            BlockState ladder = Canvas.state(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.SOUTH);
            for (int y = fy; y <= surface - 2; y++) {
                c.set(sx, y, sz, ladder);
                if (y > fy + 3) {
                    c.set(sx, y, sz - 1, Canvas.state(Blocks.STONE_BRICKS));
                }
            }
            c.set(sx, surface - 1, sz, Canvas.state(Blocks.SPRUCE_TRAPDOOR)
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.NORTH)
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true));
        }
        // Rats' work: a few missing bricks high in the walls.
        for (int i = 0; i < 20; i++) {
            int x = x0 + c.roll(i, 5, 5, WIDTH);
            int y = fy + 2 + c.roll(i, 6, 6, 3);
            int z = c.roll(i, 7, 7, 2) == 0 ? z0 : z1;
            if (c.chance(x, y, z, 30) && !c.at(x, y, z).isAir()) {
                c.set(x, y, z, Canvas.state(Blocks.COBBLESTONE));
            }
        }
    }

    private SewerLair() {}
}
