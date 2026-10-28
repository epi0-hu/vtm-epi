package com.epi0.vtm.world;

import com.epi0.vtm.city.City;
import com.epi0.vtm.npc.Job;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The buildings of a city. Each is drawn on a 14 x 14 {@link Frame} with its door on the south side, and records
 * where its people will sleep and work in the {@link City} it is given.
 */
final class CityBuildings {

    private static BlockState s(Block block) {
        return block.defaultBlockState();
    }

    // ---------------------------------------------------------------- houses

    /** A two-storey house with an attic bedroom and a small garden. Two people live in it. */
    static void house(Frame f, City city, int index, long seed) {
        Canvas c = f.canvas();
        int pick = Math.floorMod((int) (seed >> 3) + index * 7, 4);
        Block wall = new Block[]{Blocks.BRICKS, Blocks.SPRUCE_PLANKS, Blocks.WHITE_TERRACOTTA, Blocks.STONE_BRICKS}[pick];
        Block roof = new Block[]{Blocks.BRICK_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_STAIRS}[pick];
        Block corner = pick == 1 ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.OAK_LOG;

        f.fill(2, -1, 3, 11, -1, 11, s(Blocks.OAK_PLANKS));
        f.walls(2, 0, 3, 11, 6, 11, s(wall));
        for (int[] k : new int[][]{{2, 3}, {11, 3}, {2, 11}, {11, 11}}) {
            f.fill(k[0], 0, k[1], k[0], 6, k[1], s(corner));
        }
        f.fill(3, 4, 4, 10, 4, 10, s(Blocks.OAK_PLANKS)); // attic floor
        // Windows and the door.
        for (int u : new int[]{4, 9}) {
            f.set(u, 1, 11, Blocks.GLASS_PANE);
            f.set(u, 5, 11, Blocks.GLASS_PANE);
            f.set(u, 5, 3, Blocks.GLASS_PANE);
        }
        for (int v : new int[]{6, 9}) {
            f.set(2, 1, v, Blocks.GLASS_PANE);
            f.set(11, 1, v, Blocks.GLASS_PANE);
        }
        f.door(6, 11, Blocks.SPRUCE_DOOR);
        f.set(6, 2, 11, Blocks.GLASS_PANE);
        f.set(5, 1, 12, Blocks.LANTERN);
        // Gabled roof, ridge along the width.
        for (int i = 0; i < 5; i++) {
            f.fill(1, 7 + i, 2 + i, 12, 7 + i, 2 + i, f.stairs(roof, Direction.SOUTH, false));
            f.fill(1, 7 + i, 12 - i, 12, 7 + i, 12 - i, f.stairs(roof, Direction.NORTH, false));
            if (i > 0) {
                f.fill(2, 6 + i, 3 + i - 1, 2, 6 + i, 12 - i + 1, s(wall));
                f.fill(11, 6 + i, 3 + i - 1, 11, 6 + i, 12 - i + 1, s(wall));
            }
        }
        f.fill(1, 11, 7, 12, 11, 7, s(Blocks.SPRUCE_PLANKS));
        // Ground floor furnishings.
        f.set(3, 0, 4, Blocks.CRAFTING_TABLE);
        f.set(4, 0, 4, Blocks.FURNACE);
        f.fill(9, 0, 10, 10, 0, 10, s(Blocks.BOOKSHELF));
        f.set(10, 0, 4, Blocks.BARREL);
        f.set(5, 0, 7, Blocks.SPRUCE_FENCE);
        f.set(5, 1, 7, Blocks.SPRUCE_PRESSURE_PLATE);
        f.set(7, 0, 7, Blocks.SPRUCE_FENCE);
        f.set(7, 1, 7, Blocks.LANTERN);
        f.fill(4, 0, 8, 8, 0, 9, s(Blocks.RED_CARPET));
        // Ladder to the attic.
        for (int h = 0; h <= 4; h++) {
            f.set(10, h, 5, s(Blocks.LADDER).setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
        }
        f.clear(10, 4, 5, 10, 4, 5);
        // Attic: two beds.
        for (int u : new int[]{4, 8}) {
            bed(f, u, 5, 8, Blocks.RED_BED);
            city.homes.add(f.pos(u, 5, 8));
        }
        f.set(6, 5, 5, Blocks.LANTERN);
        // Garden.
        for (int u = 2; u <= 11; u++) {
            if (u != 6 && u != 7) {
                f.set(u, 0, 13, Blocks.OAK_FENCE);
            }
        }
        f.set(3, 0, 12, Blocks.POPPY);
        f.set(10, 0, 12, Blocks.CORNFLOWER);
        f.set(8, 0, 12, Blocks.AZURE_BLUET);
    }

    private static void bed(Frame f, int u, int h, int v, Block bed) {
        BlockState foot = bed.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH);
        f.set(u, h, v, foot);
        f.set(u, h, v - 1, foot.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD));
    }

    // ---------------------------------------------------------------- shop

    /** A corner shop: counter, shelves, an awning over the windows. The shopkeeper works behind the counter. */
    static void shop(Frame f, City city) {
        f.fill(1, -1, 2, 12, -1, 11, s(Blocks.SMOOTH_STONE));
        f.walls(1, 0, 2, 12, 1, 11, s(Blocks.STONE_BRICKS));
        f.walls(1, 2, 2, 12, 3, 11, s(Blocks.DARK_OAK_PLANKS));
        f.fill(1, 4, 2, 12, 4, 11, s(Blocks.SMOOTH_STONE_SLAB));
        for (int u = 2; u <= 11; u++) {
            if (u != 6 && u != 7) {
                f.set(u, 1, 11, Blocks.GLASS_PANE);
                f.set(u, 2, 11, Blocks.GLASS_PANE);
            }
        }
        f.door(6, 11, Blocks.DARK_OAK_DOOR);
        f.clear(7, 0, 11, 7, 1, 11);
        f.set(7, 0, 11, Blocks.DARK_OAK_PLANKS);
        for (int u = 1; u <= 12; u++) { // awning
            f.set(u, 3, 12, f.stairs(Blocks.BRICK_STAIRS, Direction.NORTH, true));
        }
        f.fill(2, 0, 8, 11, 0, 8, s(Blocks.DARK_OAK_PLANKS));
        f.fill(2, 1, 8, 11, 1, 8, s(Blocks.DARK_OAK_SLAB));
        f.set(3, 2, 8, Blocks.FLOWER_POT);
        f.set(10, 2, 8, Blocks.FLOWER_POT);
        f.fill(2, 0, 2, 11, 2, 2, s(Blocks.BOOKSHELF));
        f.fill(2, 0, 3, 3, 0, 3, s(Blocks.BARREL));
        f.fill(10, 0, 3, 11, 0, 3, s(Blocks.BARREL));
        f.set(6, 3, 6, Blocks.LANTERN);
        f.set(6, 4, 6, Blocks.CHAIN);
        f.set(1, 5, 2, Blocks.STONE_BRICK_WALL);
        city.works.add(new City.Work(Job.SHOPKEEPER, f.pos(6, 0, 6)));
    }

    // ---------------------------------------------------------------- club

    /** A nightclub: dark walls, a chequered floor, a bar along the back, and a dance floor for the evening crowd. */
    static void club(Frame f, City city) {
        for (int u = 0; u <= 13; u++) {
            for (int v = 1; v <= 12; v++) {
                f.set(u, -1, v, (u + v) % 2 == 0 ? s(Blocks.BLACK_CONCRETE) : s(Blocks.GRAY_CONCRETE));
            }
        }
        f.walls(0, 0, 1, 13, 5, 12, s(Blocks.BLACK_CONCRETE));
        f.fill(0, 6, 1, 13, 6, 12, s(Blocks.SMOOTH_STONE_SLAB));
        f.fill(1, 6, 2, 12, 6, 11, s(Blocks.BLACK_CONCRETE));
        for (int u = 2; u <= 11; u += 3) { // neon: stained glass backed by light
            f.set(u, 3, 12, Blocks.RED_STAINED_GLASS);
            f.set(u, 3, 1, Blocks.RED_STAINED_GLASS);
        }
        f.door(6, 12, Blocks.DARK_OAK_DOOR);
        f.door(7, 12, Blocks.DARK_OAK_DOOR);
        f.set(5, 3, 13, Blocks.SOUL_LANTERN);
        f.set(8, 3, 13, Blocks.SOUL_LANTERN);
        // Ceiling lights over the dance floor.
        for (int u = 4; u <= 9; u += 2) {
            for (int v = 6; v <= 10; v += 2) {
                f.set(u, 5, v, Blocks.SEA_LANTERN);
            }
        }
        // The bar.
        f.fill(2, 0, 4, 11, 0, 4, s(Blocks.DEEPSLATE_BRICKS));
        f.fill(2, 1, 4, 11, 1, 4, s(Blocks.DEEPSLATE_BRICK_SLAB));
        f.fill(2, 0, 2, 11, 3, 2, s(Blocks.DARK_OAK_PLANKS));
        for (int u = 3; u <= 10; u += 2) {
            f.set(u, 1, 2, Blocks.BARREL);
            f.set(u, 2, 3, Blocks.POTTED_RED_MUSHROOM);
        }
        for (int u = 3; u <= 10; u += 2) { // stools
            f.set(u, 0, 5, f.stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
        }
        f.set(1, 0, 3, Blocks.JUKEBOX);
        // Tables at the sides.
        for (int[] t : new int[][]{{2, 8}, {2, 10}, {11, 8}, {11, 10}}) {
            f.set(t[0], 0, t[1], Blocks.DARK_OAK_FENCE);
            f.set(t[0], 1, t[1], Blocks.DARK_OAK_PRESSURE_PLATE);
        }
        city.works.add(new City.Work(Job.BARTENDER, f.pos(6, 0, 3)));
        city.club = f.pos(7, 0, 8);
    }

    // ---------------------------------------------------------------- watch station

    /** The watch station: a stone keep with a desk, cells and two bunks. Watchmen sleep and report here. */
    static void watch(Frame f, City city) {
        f.fill(2, -1, 2, 11, -1, 11, s(Blocks.STONE_BRICKS));
        f.walls(2, 0, 2, 11, 4, 11, s(Blocks.STONE_BRICKS));
        f.fill(2, 5, 2, 11, 5, 11, s(Blocks.STONE_BRICKS));
        for (int u = 2; u <= 11; u++) { // crenellations
            if (u % 2 == 0) {
                f.set(u, 6, 2, Blocks.STONE_BRICK_WALL);
                f.set(u, 6, 11, Blocks.STONE_BRICK_WALL);
            }
        }
        for (int v = 4; v <= 10; v += 3) {
            f.set(2, 2, v, Blocks.IRON_BARS);
            f.set(11, 2, v, Blocks.IRON_BARS);
        }
        f.set(4, 2, 11, Blocks.IRON_BARS);
        f.set(9, 2, 11, Blocks.IRON_BARS);
        f.door(6, 11, Blocks.SPRUCE_DOOR);
        f.fill(5, 3, 12, 8, 3, 12, s(Blocks.RED_WOOL)); // banner
        // Desk and notice board.
        f.set(6, 0, 8, Blocks.LECTERN);
        f.fill(4, 0, 8, 4, 0, 8, s(Blocks.SPRUCE_SLAB));
        f.set(8, 0, 8, Blocks.SPRUCE_SLAB);
        // Cells at the back.
        f.fill(8, 0, 3, 11, 3, 3, s(Blocks.IRON_BARS));
        f.fill(8, 0, 3, 8, 3, 6, s(Blocks.IRON_BARS));
        f.set(10, 0, 4, Blocks.BARREL);
        // Bunks.
        bed(f, 3, 0, 6, Blocks.BLUE_BED);
        bed(f, 5, 0, 6, Blocks.BLUE_BED);
        city.homes.add(f.pos(3, 0, 6));
        city.homes.add(f.pos(5, 0, 6));
        f.set(6, 2, 4, Blocks.LANTERN);
        city.works.add(new City.Work(Job.WATCHMAN, f.pos(5, 0, 9)));
        city.works.add(new City.Work(Job.WATCHMAN, f.pos(8, 0, 9)));
        city.watch = f.pos(6, 0, 9);
    }

    private CityBuildings() {}
}
