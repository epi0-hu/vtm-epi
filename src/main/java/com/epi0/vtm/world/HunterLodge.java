package com.epi0.vtm.world;

import com.epi0.vtm.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A hunters' lodge: a palisaded yard with a hall on its north side, a watchtower, a training ground, and in the middle
 * a post that flies the hunters' standard. The hall has a map room, an armoury, a trophy wall and bunks.
 */
final class HunterLodge {
    static final int WIDTH = 23;
    static final int DEPTH = 19;
    static final ResourceLocation LOOT = ResourceLocation.fromNamespaceAndPath("vtm", "chests/hunter_lodge");

    private static BlockState s(net.minecraft.world.level.block.Block b) {
        return Canvas.state(b);
    }

    static void build(Canvas c, int x0, int y0, int z0, RandomSource random) {
        int x1 = x0 + WIDTH - 1;
        int z1 = z0 + DEPTH - 1;
        int mx = x0 + WIDTH / 2;
        c.clear(x0, y0, z0, x1, y0 + 14, z1);
        c.foundation(x0, z0, x1, z1, y0, s(Blocks.COBBLESTONE));
        // The yard: hard-packed earth, gravel paths.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean path = Math.abs(x - mx) <= 1 || z >= z1 - 4 && z <= z1 - 3;
                c.set(x, y0 - 1, z, path ? s(c.roll(x, 0, z, 3) == 0 ? Blocks.COBBLESTONE : Blocks.GRAVEL) : s(c.roll(x, 1, z, 4) == 0 ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH));
            }
        }
        // The palisade, with a gate in the south.
        for (int x = x0; x <= x1; x++) {
            for (int z : new int[]{z0, z1}) {
                if (z == z1 && Math.abs(x - mx) <= 2) {
                    continue;
                }
                c.fill(x, y0, z, x, y0 + 3, z, s(Blocks.SPRUCE_LOG));
            }
        }
        for (int z = z0; z <= z1; z++) {
            for (int x : new int[]{x0, x1}) {
                c.fill(x, y0, z, x, y0 + 3, z, s(Blocks.SPRUCE_LOG));
            }
        }
        for (int x = x0; x <= x1; x += 2) { // sharpened tops
            c.set(x, y0 + 4, z0, s(Blocks.SPRUCE_FENCE));
            c.set(x, y0 + 4, z1, s(Blocks.SPRUCE_FENCE));
        }
        for (int z = z0; z <= z1; z += 2) {
            c.set(x0, y0 + 4, z, s(Blocks.SPRUCE_FENCE));
            c.set(x1, y0 + 4, z, s(Blocks.SPRUCE_FENCE));
        }
        c.fill(mx - 3, y0, z1, mx - 3, y0 + 5, z1, s(Blocks.STRIPPED_SPRUCE_LOG)); // gateposts
        c.fill(mx + 3, y0, z1, mx + 3, y0 + 5, z1, s(Blocks.STRIPPED_SPRUCE_LOG));
        c.fill(mx - 2, y0 + 4, z1, mx + 2, y0 + 4, z1, s(Blocks.SPRUCE_SLAB));
        c.set(mx - 3, y0 + 6, z1, s(Blocks.LANTERN));
        c.set(mx + 3, y0 + 6, z1, s(Blocks.LANTERN));

        // The hall, along the north wall: stone below, timber above, a pitched roof.
        int hx0 = x0 + 3;
        int hx1 = x1 - 3;
        int hz0 = z0 + 1;
        int hz1 = z0 + 8;
        c.fill(hx0, y0 - 1, hz0, hx1, y0 - 1, hz1, s(Blocks.SPRUCE_PLANKS));
        for (int y = 0; y <= 4; y++) {
            c.walls(hx0, y0 + y, hz0, hx1, y0 + y, hz1, y <= 1 ? s(Blocks.STONE_BRICKS) : s(Blocks.SPRUCE_PLANKS));
        }
        for (int[] corner : new int[][]{{hx0, hz0}, {hx1, hz0}, {hx0, hz1}, {hx1, hz1}}) {
            c.fill(corner[0], y0, corner[1], corner[0], y0 + 4, corner[1], s(Blocks.STRIPPED_SPRUCE_LOG));
        }
        for (int i = 0; i < 5; i++) { // roof, ridge along the length
            c.fill(hx0 - 1, y0 + 5 + i, hz0 - 1 + i, hx1 + 1, y0 + 5 + i, hz0 - 1 + i, Canvas.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
            c.fill(hx0 - 1, y0 + 5 + i, hz1 + 1 - i, hx1 + 1, y0 + 5 + i, hz1 + 1 - i, Canvas.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        }
        c.fill(hx0 - 1, y0 + 9, hz0 + 4, hx1 + 1, y0 + 9, hz0 + 4, s(Blocks.SPRUCE_SLAB));
        for (int x = hx0 + 2; x <= hx1 - 2; x += 3) { // windows facing the yard
            c.set(x, y0 + 2, hz1, s(Blocks.GLASS_PANE));
            c.set(x, y0 + 3, hz1, s(Blocks.GLASS_PANE));
        }
        c.clear(mx, y0, hz1, mx, y0 + 1, hz1);
        c.door(mx, y0, hz1, Direction.SOUTH, Blocks.SPRUCE_DOOR);
        c.clear(hx0 + 1, y0, hz0 + 1, hx1 - 1, y0 + 3, hz1 - 1);
        // West end: the armoury. A weapons rack of chests, barrels, a grindstone and an anvil.
        c.chest(hx0 + 1, y0, hz0 + 1, Direction.SOUTH, LOOT, random);
        c.chest(hx0 + 2, y0, hz0 + 1, Direction.SOUTH, LOOT, random);
        c.set(hx0 + 3, y0, hz0 + 1, s(Blocks.BARREL));
        c.set(hx0 + 1, y0, hz0 + 4, s(Blocks.ANVIL));
        c.set(hx0 + 1, y0, hz0 + 5, s(Blocks.GRINDSTONE));
        c.set(hx0 + 2, y0, hz0 + 6, s(Blocks.SMITHING_TABLE));
        // Middle: the long table and benches.
        c.fill(mx - 2, y0, hz0 + 3, mx + 2, y0, hz0 + 3, s(Blocks.SPRUCE_SLAB));
        for (int x = mx - 2; x <= mx + 2; x++) {
            c.set(x, y0, hz0 + 2, Canvas.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
            c.set(x, y0, hz0 + 4, Canvas.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        }
        c.set(mx, y0 + 1, hz0 + 3, s(Blocks.CANDLE));
        // East end: the map room and the trophy wall.
        c.set(hx1 - 1, y0, hz0 + 1, s(Blocks.CARTOGRAPHY_TABLE));
        c.set(hx1 - 2, y0, hz0 + 1, s(Blocks.LECTERN));
        c.set(hx1 - 1, y0, hz0 + 3, s(Blocks.BOOKSHELF));
        c.set(hx1 - 1, y0 + 1, hz0 + 3, s(Blocks.BOOKSHELF));
        c.fill(hx1 - 1, y0, hz0 + 5, hx1 - 1, y0 + 2, hz0 + 6, s(Blocks.BONE_BLOCK));
        for (int z = hz0 + 5; z <= hz0 + 6; z++) {
            c.set(hx1 - 2, y0, z, s(Blocks.SKELETON_SKULL));
            c.set(hx1 - 2, y0 + 3, z, s(Blocks.BLUE_BANNER));
        }
        // Bunks along the back wall.
        for (int x = mx - 3; x <= mx + 3; x += 3) {
            BlockState foot = s(Blocks.BLUE_BED).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH);
            c.set(x, y0, hz0 + 6, foot);
            c.set(x, y0, hz0 + 5, foot.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        }
        c.set(mx, y0 + 3, hz0 + 3, s(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        c.set(mx, y0 + 4, hz0 + 3, s(Blocks.CHAIN));

        // The standard: a post in the middle of the yard.
        int sz = z0 + 12;
        c.fill(mx, y0, sz, mx, y0 + 2, sz, s(Blocks.COBBLESTONE_WALL));
        c.set(mx, y0 + 3, sz, ModBlocks.HUNTER_STANDARD.get().defaultBlockState());
        for (int[] banner : new int[][]{{mx - 1, sz}, {mx + 1, sz}}) {
            c.fill(banner[0], y0, banner[1], banner[0], y0 + 3, banner[1], s(Blocks.SPRUCE_FENCE));
            c.set(banner[0], y0 + 4, banner[1], s(Blocks.BLUE_BANNER));
        }
        c.set(mx, y0 + 4, sz, s(Blocks.LANTERN));
        // The training ground, east of the post: straw dummies on posts, a target.
        for (int[] dummy : new int[][]{{x1 - 4, z0 + 12}, {x1 - 4, z0 + 15}, {x1 - 7, z0 + 14}}) {
            c.fill(dummy[0], y0, dummy[1], dummy[0], y0 + 1, dummy[1], s(Blocks.SPRUCE_FENCE));
            c.set(dummy[0], y0 + 2, dummy[1], s(Blocks.HAY_BLOCK));
            c.set(dummy[0], y0 + 3, dummy[1], s(Blocks.CARVED_PUMPKIN));
        }
        // West of the post: a well, a camp fire, and barrels of water and oil.
        c.fill(x0 + 4, y0, z0 + 13, x0 + 6, y0, z0 + 15, s(Blocks.COBBLESTONE));
        c.fill(x0 + 5, y0, z0 + 14, x0 + 5, y0, z0 + 14, s(Blocks.WATER));
        c.set(x0 + 4, y0 + 1, z0 + 13, s(Blocks.COBBLESTONE_WALL));
        c.set(x0 + 6, y0 + 1, z0 + 15, s(Blocks.COBBLESTONE_WALL));
        c.set(x0 + 7, y0, z0 + 11, s(Blocks.CAMPFIRE));
        c.set(x0 + 8, y0, z0 + 12, s(Blocks.BARREL));
        c.set(x0 + 8, y0, z0 + 13, s(Blocks.BARREL));
        // The watchtower in the north-east corner of the yard: a ladder up inside it, a lantern at the top.
        int tx0 = x1 - 5;
        int tz0 = z0 + 9;
        c.walls(tx0, y0, tz0, tx0 + 3, y0 + 8, tz0 + 3, s(Blocks.COBBLESTONE));
        c.clear(tx0 + 1, y0, tz0 + 1, tx0 + 2, y0 + 8, tz0 + 2);
        c.fill(tx0, y0 + 9, tz0, tx0 + 3, y0 + 9, tz0 + 3, s(Blocks.SPRUCE_PLANKS));
        for (int h = 0; h <= 8; h++) {
            c.set(tx0 + 1, y0 + h, tz0 + 1, s(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.SOUTH));
            c.set(tx0 + 1, y0 + h, tz0, s(Blocks.COBBLESTONE));
        }
        c.clear(tx0 + 1, y0 + 9, tz0 + 1, tx0 + 1, y0 + 9, tz0 + 1);
        c.clear(tx0 + 1, y0, tz0 + 3, tx0 + 1, y0 + 1, tz0 + 3); // a door
        for (int x = tx0; x <= tx0 + 3; x++) {
            for (int z : new int[]{tz0, tz0 + 3}) {
                c.set(x, y0 + 10, z, s(Blocks.SPRUCE_FENCE));
            }
        }
        for (int z = tz0; z <= tz0 + 3; z++) {
            for (int x : new int[]{tx0, tx0 + 3}) {
                c.set(x, y0 + 10, z, s(Blocks.SPRUCE_FENCE));
            }
        }
        c.set(tx0 + 2, y0 + 10, tz0 + 2, s(Blocks.LANTERN));
        c.set(tx0 + 2, y0 + 9, tz0 + 2, s(Blocks.BLUE_BANNER));
    }

    private HunterLodge() {}
}
