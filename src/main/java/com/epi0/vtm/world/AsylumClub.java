package com.epi0.vtm.world;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Asylum: a derelict sanatorium that a Malkavian turned into the city's most talked-about nightclub. Black
 * concrete outside with red neon, a dance floor in the old day room, a VIP gallery looking down on it, and below,
 * the padded cells, which the staff still keep locked, and the owner's quarters.
 */
final class AsylumClub {
    static final int WIDTH = 21;
    static final int DEPTH = 17;
    /** The forecourt in front of the building, so the entrance is clear of whatever grows on the site. */
    static final int FORECOURT = 7;

    private static BlockState s(net.minecraft.world.level.block.Block b) {
        return Canvas.state(b);
    }

    static void build(Canvas c, int x0, int y0, int z0, ResourceLocation faction, ResourceLocation clan, RandomSource random) {
        int x1 = x0 + WIDTH - 1;
        int z1 = z0 + DEPTH - 1;
        int mx = x0 + 10;
        c.clear(x0, y0, z0, x1, y0 + 13, z1 + FORECOURT);
        c.foundation(x0, z0, x1, z1 + FORECOURT, y0, s(Blocks.STONE_BRICKS));
        forecourt(c, x0, y0, z1, x1, mx);

        // ---- Basement: cells to the north, the owner to the south, a stair up at the east end.
        c.room(x0 + 1, y0 - 6, z0 + 1, x1 - 1, y0 - 1, z1 - 1, s(Blocks.DEEPSLATE_BRICKS));
        c.fill(x0 + 2, y0 - 5, z0 + 2, x1 - 2, y0 - 5, z1 - 2, s(Blocks.WHITE_CONCRETE));
        for (int i = 0; i < 3; i++) {
            int cx = x0 + 3 + i * 5; // each cell is 4 wide inside, walls between
            c.fill(cx - 1, y0 - 5, z0 + 2, cx - 1, y0 - 2, z0 + 6, s(Blocks.WHITE_WOOL));
            c.fill(cx + 4, y0 - 5, z0 + 2, cx + 4, y0 - 2, z0 + 6, s(Blocks.WHITE_WOOL));
            c.fill(cx, y0 - 5, z0 + 2, cx + 3, y0 - 5, z0 + 5, s(Blocks.WHITE_WOOL));
            c.fill(cx, y0 - 4, z0 + 2, cx + 3, y0 - 2, z0 + 2, s(Blocks.WHITE_WOOL));
            c.fill(cx, y0 - 4, z0 + 6, cx + 3, y0 - 2, z0 + 6, s(Blocks.IRON_BARS));
            if (i != 1) { // one cell stands open; the others do not
                c.clear(cx + 1, y0 - 4, z0 + 6, cx + 2, y0 - 3, z0 + 6);
            }
            c.set(cx + 1, y0 - 4, z0 + 3, s(Blocks.WHITE_BED).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH));
            c.set(cx + 1, y0 - 4, z0 + 2, s(Blocks.WHITE_BED).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            if (c.chance(i, 1, 1, 100)) {
                c.set(cx + 2, y0 - 4, z0 + 5, s(Blocks.CHAIN));
            }
        }
        c.fill(x0 + 2, y0 - 2, z0 + 2, x1 - 2, y0 - 2, z0 + 2, s(Blocks.WHITE_WOOL));
        // The owner's quarters, behind a door in the south of the basement.
        c.fill(x0 + 2, y0 - 5, z0 + 9, x1 - 2, y0 - 2, z0 + 9, s(Blocks.DEEPSLATE_BRICKS));
        c.clear(mx, y0 - 4, z0 + 9, mx + 1, y0 - 3, z0 + 9);
        c.fill(x0 + 2, y0 - 5, z0 + 10, x1 - 2, y0 - 5, z1 - 2, s(Blocks.DARK_OAK_PLANKS));
        c.fill(x0 + 5, y0 - 4, z0 + 11, x1 - 6, y0 - 4, z1 - 3, s(Blocks.RED_CARPET));
        c.coffin(mx, y0 - 4, z1 - 3, Direction.NORTH, faction, clan);
        c.set(mx - 1, y0 - 4, z1 - 3, s(Blocks.CANDLE));
        c.set(mx + 1, y0 - 4, z1 - 3, s(Blocks.CANDLE));
        c.fill(x0 + 2, y0 - 4, z1 - 2, x0 + 5, y0 - 2, z1 - 2, s(Blocks.BOOKSHELF));
        c.fill(x1 - 6, y0 - 4, z1 - 2, x1 - 4, y0 - 2, z1 - 2, s(Blocks.BOOKSHELF));
        c.chest(x1 - 5, y0 - 4, z0 + 11, Direction.WEST, LairPiece.LOOT, random);
        c.set(x0 + 3, y0 - 4, z0 + 11, s(Blocks.LECTERN));
        c.set(mx, y0 - 2, z0 + 13, s(Blocks.SOUL_LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        c.set(mx, y0 - 1, z0 + 13, s(Blocks.CHAIN));
        // The stair from the quarters up into the club, along the east wall.
        for (int i = 0; i < 5; i++) {
            int z = z1 - 2 - i;
            c.clear(x1 - 3, y0 - 4 + i, z, x1 - 2, y0 - 1 + i, z);
            c.fill(x1 - 3, y0 - 5 + i, z, x1 - 2, y0 - 5 + i, z, s(Blocks.DEEPSLATE_BRICKS));
            c.set(x1 - 3, y0 - 4 + i, z, Canvas.stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH, false));
            c.set(x1 - 2, y0 - 4 + i, z, Canvas.stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH, false));
        }

        // ---- The club: ground floor, 5 high; a gallery above.
        for (int x = x0; x <= x1; x++) { // the day room floor, a clinical chequer
            for (int z = z0; z <= z1; z++) {
                if (x >= x1 - 3 && x <= x1 - 2 && z >= z1 - 6 && z <= z1 - 2) {
                    continue; // the stairwell from the quarters below
                }
                c.set(x, y0 - 1, z, (x + z) % 2 == 0 ? s(Blocks.WHITE_CONCRETE) : s(Blocks.BLACK_CONCRETE));
            }
        }
        for (int y = y0; y <= y0 + 4; y++) {
            c.walls(x0, y, z0, x1, y, z1, y <= y0 + 1 ? s(Blocks.SMOOTH_QUARTZ) : s(Blocks.BLACK_CONCRETE));
        }
        c.fill(x0, y0 + 5, z0, x1, y0 + 5, z1, s(Blocks.POLISHED_BLACKSTONE));
        for (int y = y0 + 6; y <= y0 + 9; y++) {
            c.walls(x0, y, z0, x1, y, z1, s(Blocks.BLACK_CONCRETE));
        }
        c.fill(x0, y0 + 10, z0, x1, y0 + 10, z1, s(Blocks.POLISHED_BLACKSTONE));
        for (int x = x0; x <= x1; x += 2) { // parapet
            c.set(x, y0 + 11, z0, s(Blocks.POLISHED_BLACKSTONE_WALL));
            c.set(x, y0 + 11, z1, s(Blocks.POLISHED_BLACKSTONE_WALL));
        }
        for (int z = z0; z <= z1; z += 2) {
            c.set(x0, y0 + 11, z, s(Blocks.POLISHED_BLACKSTONE_WALL));
            c.set(x1, y0 + 11, z, s(Blocks.POLISHED_BLACKSTONE_WALL));
        }
        // Neon windows, ground and upper.
        for (int x = x0 + 3; x <= x1 - 3; x += 4) {
            for (int y : new int[]{y0 + 2, y0 + 7}) {
                c.set(x, y, z1, s(Blocks.RED_STAINED_GLASS));
                c.set(x + 1, y, z1, s(Blocks.RED_STAINED_GLASS));
                c.set(x, y, z0, s(Blocks.RED_STAINED_GLASS));
                c.set(x + 1, y, z0, s(Blocks.RED_STAINED_GLASS));
            }
        }
        for (int z = z0 + 3; z <= z1 - 3; z += 4) {
            c.set(x0, y0 + 2, z, s(Blocks.PURPLE_STAINED_GLASS));
            c.set(x1, y0 + 2, z, s(Blocks.PURPLE_STAINED_GLASS));
        }
        // The door, with steps and a sign either side.
        c.clear(mx, y0, z1, mx + 1, y0 + 2, z1);
        c.door(mx, y0, z1, Direction.SOUTH, Blocks.IRON_DOOR);
        c.door(mx + 1, y0, z1, Direction.SOUTH, Blocks.IRON_DOOR);
        c.fill(mx - 1, y0 + 3, z1, mx + 2, y0 + 3, z1, s(Blocks.CHISELED_POLISHED_BLACKSTONE));
        c.sign(mx - 2, y0 + 2, z1 + 1, Direction.SOUTH, "", "THE", "ASYLUM", "");
        c.sign(mx + 3, y0 + 2, z1 + 1, Direction.SOUTH, "", "ADMISSION", "BY INVITATION", "");

        // ---- Inside: bar along the west wall, DJ stage at the east, dance floor between.
        c.fill(x0 + 2, y0, z0 + 3, x0 + 2, y0, z1 - 3, s(Blocks.DEEPSLATE_BRICKS));
        c.fill(x0 + 2, y0 + 1, z0 + 3, x0 + 2, y0 + 1, z1 - 3, s(Blocks.DEEPSLATE_BRICK_SLAB));
        c.fill(x0 + 1, y0, z0 + 3, x0 + 1, y0 + 3, z1 - 3, s(Blocks.DARK_OAK_PLANKS));
        for (int z = z0 + 4; z <= z1 - 4; z += 2) {
            c.set(x0 + 1, y0 + 1, z, s(Blocks.BARREL));
            c.set(x0 + 1, y0 + 2, z, s(Blocks.POTTED_RED_MUSHROOM));
            c.set(x0 + 3, y0, z, Canvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST, false));
        }
        c.fill(x1 - 4, y0, z0 + 3, x1 - 1, y0, z0 + 9, s(Blocks.POLISHED_BLACKSTONE)); // stage
        c.fill(x1 - 1, y0 + 1, z0 + 4, x1 - 1, y0 + 1, z0 + 5, s(Blocks.NOTE_BLOCK));
        c.set(x1 - 2, y0 + 1, z0 + 6, s(Blocks.JUKEBOX));
        c.fill(x1 - 5, y0, z0 + 3, x1 - 5, y0, z0 + 9, s(Blocks.POLISHED_BLACKSTONE_SLAB));
        for (int x = x0 + 6; x <= x1 - 7; x += 3) { // strobes over the floor
            for (int z = z0 + 5; z <= z1 - 5; z += 3) {
                c.set(x, y0 + 4, z, s(Blocks.SEA_LANTERN));
            }
        }
        for (int[] t : new int[][]{{x0 + 4, z0 + 2}, {x0 + 4, z1 - 2}, {x1 - 6, z0 + 2}, {x1 - 6, z1 - 2}}) {
            c.set(t[0], y0, t[1], s(Blocks.DARK_OAK_FENCE));
            c.set(t[0], y0 + 1, t[1], s(Blocks.DARK_OAK_PRESSURE_PLATE));
        }
        // Stair from the club up to the gallery, along the north wall.
        c.clear(x0 + 4, y0 + 5, z0 + 1, x0 + 9, y0 + 5, z0 + 2);
        for (int i = 0; i < 6; i++) {
            c.set(x0 + 4 + i, y0 + i, z0 + 1, Canvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST, false));
            c.fill(x0 + 4 + i, y0, z0 + 1, x0 + 4 + i, y0 + i - 1, z0 + 1, s(Blocks.DARK_OAK_PLANKS));
        }

        // ---- The gallery: the floor above is open over the dance floor and railed.
        c.clear(x0 + 4, y0 + 5, z0 + 4, x1 - 6, y0 + 5, z1 - 4);
        c.fill(x0 + 4, y0 + 6, z0 + 4, x1 - 6, y0 + 6, z0 + 4, s(Blocks.IRON_BARS));
        c.fill(x0 + 4, y0 + 6, z1 - 4, x1 - 6, y0 + 6, z1 - 4, s(Blocks.IRON_BARS));
        c.fill(x0 + 4, y0 + 6, z0 + 4, x0 + 4, y0 + 6, z1 - 4, s(Blocks.IRON_BARS));
        c.fill(x1 - 6, y0 + 6, z0 + 4, x1 - 6, y0 + 6, z1 - 4, s(Blocks.IRON_BARS));
        for (int z = z0 + 2; z <= z1 - 2; z += 3) { // VIP booths, red velvet
            c.set(x0 + 2, y0 + 6, z, Canvas.stairs(Blocks.CRIMSON_STAIRS, Direction.EAST, false));
            c.set(x0 + 1, y0 + 6, z, s(Blocks.RED_WOOL));
            c.set(x1 - 2, y0 + 6, z, Canvas.stairs(Blocks.CRIMSON_STAIRS, Direction.WEST, false));
            c.set(x1 - 1, y0 + 6, z, s(Blocks.RED_WOOL));
        }
        for (int x = x0 + 5; x <= x1 - 7; x += 3) {
            c.set(x, y0 + 9, z0 + 2, s(Blocks.SEA_LANTERN));
            c.set(x, y0 + 9, z1 - 2, s(Blocks.SEA_LANTERN));
        }
        c.chest(x0 + 1, y0 + 6, z1 - 1, Direction.NORTH, LairPiece.LOOT, random);
    }

    /** Paving, a red carpet to the door, velvet ropes, lamp posts, and a marquee over the entrance. */
    private static void forecourt(Canvas c, int x0, int y0, int z1, int x1, int mx) {
        int zEnd = z1 + FORECOURT;
        c.fill(x0, y0 - 1, z1 + 1, x1, y0 - 1, zEnd, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
        for (int x = x0; x <= x1; x++) {
            for (int z = z1 + 1; z <= zEnd; z++) {
                if ((x + z) % 5 == 0) {
                    c.set(x, y0 - 1, z, s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS));
                }
            }
        }
        c.fill(mx, y0 - 1, z1 + 1, mx + 1, y0 - 1, zEnd, s(Blocks.RED_CONCRETE));
        for (int z = z1 + 2; z <= zEnd - 1; z += 2) { // ropes
            for (int x : new int[]{mx - 1, mx + 2}) {
                c.set(x, y0, z, s(Blocks.CRIMSON_FENCE));
            }
        }
        for (int x : new int[]{x0 + 1, x1 - 1}) { // lamp posts
            c.fill(x, y0, zEnd, x, y0 + 3, zEnd, s(Blocks.POLISHED_BLACKSTONE_WALL));
            c.set(x, y0 + 4, zEnd, s(Blocks.SOUL_LANTERN));
        }
        // The marquee: a canopy on posts, lit from beneath.
        c.fill(mx - 2, y0 + 4, z1 + 1, mx + 3, y0 + 4, z1 + 3, s(Blocks.POLISHED_BLACKSTONE_SLAB));
        c.fill(mx - 2, y0 + 5, z1 + 1, mx + 3, y0 + 5, z1 + 3, s(Blocks.RED_CONCRETE));
        for (int x : new int[]{mx - 2, mx + 3}) {
            c.fill(x, y0, z1 + 3, x, y0 + 3, z1 + 3, s(Blocks.POLISHED_BLACKSTONE_WALL));
        }
        c.set(mx, y0 + 3, z1 + 2, s(Blocks.SEA_LANTERN));
        c.set(mx + 1, y0 + 3, z1 + 2, s(Blocks.SEA_LANTERN));
    }

    private AsylumClub() {}
}
