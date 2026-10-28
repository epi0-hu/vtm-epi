package com.epi0.vtm.world;

import com.epi0.vtm.city.City;
import com.epi0.vtm.npc.Job;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The buildings a metropolis adds to the town set in {@link CityBuildings}: towers, tenements, a church, a market,
 * parks and depots. Like those, each is drawn on a 14 x 14 {@link Frame} with its door on the south side.
 */
final class CityKit {
    private static final int FLOOR = 5;

    private static BlockState s(Block block) {
        return block.defaultBlockState();
    }

    private static BlockState ladder() {
        return s(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.SOUTH);
    }

    private static void bed(Frame f, int u, int h, int v, Block bed) {
        BlockState foot = s(bed).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH);
        f.set(u, h, v, foot);
        f.set(u, h, v - 1, foot.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD));
    }

    // ---------------------------------------------------------------- towers

    private record Style(Block pier, Block glass, Block slab, Block trim, Block roof) {}

    private static Style style(long seed) {
        return switch (Math.floorMod((int) (seed >> 5), 3)) {
            case 0 -> new Style(Blocks.GRAY_CONCRETE, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_BLACKSTONE);
            case 1 -> new Style(Blocks.BRICKS, Blocks.GLASS_PANE, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.DEEPSLATE_TILES);
            default -> new Style(Blocks.QUARTZ_BRICKS, Blocks.GRAY_STAINED_GLASS_PANE, Blocks.POLISHED_DIORITE, Blocks.GOLD_BLOCK, Blocks.SMOOTH_QUARTZ);
        };
    }

    /**
     * A tower of {@code floors} storeys: a lobby with a front desk, offices up a ladder core, and two penthouse
     * floors where people live. It stands 12 x 12 on the plot, wrapped in a band of glass.
     */
    static void tower(Frame f, City city, int floors, long seed) {
        Style st = style(seed);
        int top = floors * FLOOR;
        f.clear(0, 0, 0, 13, top + 8, 13);
        f.fill(0, -1, 0, 13, -1, 13, s(Blocks.POLISHED_ANDESITE));
        f.fill(1, -1, 1, 12, -1, 12, s(st.slab));
        for (int k = 0; k < floors; k++) {
            int h = k * FLOOR;
            // The shell of this floor: piers at the corners and every fourth block, glass between.
            for (int u = 1; u <= 12; u++) {
                for (int v : new int[]{1, 12}) {
                    wallBlock(f, st, u, h, v, u == 1 || u == 12 || u % 4 == 1);
                }
            }
            for (int v = 2; v <= 11; v++) {
                for (int u : new int[]{1, 12}) {
                    wallBlock(f, st, u, h, v, v % 4 == 1);
                }
            }
            f.fill(1, h + 4, 1, 12, h + 4, 12, s(st.slab));
            f.set(6, h + 3, 6, s(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            f.set(7, h + 3, 6, s(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            // The ladder core and its hole in the floor above.
            for (int dh = 0; dh < FLOOR; dh++) {
                f.set(10, h + dh, 2, ladder());
            }
            if (k < floors - 1) {
                f.clear(10, h + 4, 2, 10, h + 4, 2);
            }
            if (k > 0 && k < floors - 2) { // office floors
                for (int u : new int[]{3, 6, 9}) {
                    f.fill(u, h, 7, u + 1, h, 7, s(Blocks.SPRUCE_SLAB));
                    f.set(u, h + 1, 7, Blocks.LANTERN);
                    f.set(u, h, 8, f.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
                }
                f.set(3, h, 10, Blocks.BOOKSHELF);
                f.set(4, h, 10, Blocks.BOOKSHELF);
            }
            if (k >= floors - 2 && k > 0) { // the penthouse floors
                for (int u : new int[]{3, 7}) {
                    bed(f, u, h, 10, Blocks.CYAN_BED);
                    city.homes.add(f.pos(u, h, 10));
                }
                f.fill(3, h, 4, 8, h, 5, s(Blocks.RED_CARPET));
                f.set(11, h, 10, Blocks.BARREL);
            }
        }
        // The lobby: a revolving door, a front desk, plants.
        for (int u : new int[]{6, 7}) {
            f.clear(u, 0, 1 + 11, u, 2, 12);
        }
        f.door(6, 12, Blocks.IRON_DOOR);
        f.door(7, 12, Blocks.IRON_DOOR);
        f.fill(3, 0, 9, 9, 0, 9, s(Blocks.DARK_OAK_PLANKS));
        f.fill(3, 1, 9, 9, 1, 9, s(Blocks.DARK_OAK_SLAB));
        f.set(2, 0, 3, Blocks.POTTED_FERN);
        f.set(11, 0, 3, Blocks.POTTED_FERN);
        f.fill(4, 0, 4, 9, 0, 6, s(Blocks.GRAY_CARPET));
        f.fill(5, 3, 13, 8, 3, 13, s(st.trim)); // canopy over the door
        f.fill(5, 4, 12, 8, 4, 13, s(st.slab));
        f.set(5, 3, 12, Blocks.SEA_LANTERN);
        f.set(8, 3, 12, Blocks.SEA_LANTERN);
        // The roof: a parapet, a plant room, and a mast with a warning light.
        f.fill(1, top, 1, 12, top, 12, s(st.roof));
        for (int u = 1; u <= 12; u++) {
            for (int v : new int[]{1, 12}) {
                f.set(u, top + 1, v, s(Blocks.STONE_BRICK_WALL));
            }
        }
        for (int v = 2; v <= 11; v++) {
            for (int u : new int[]{1, 12}) {
                f.set(u, top + 1, v, s(Blocks.STONE_BRICK_WALL));
            }
        }
        f.clear(10, top, 2, 10, top, 2);
        f.set(10, top - 1, 2, ladder());
        f.walls(7, top + 1, 6, 10, top + 3, 9, s(Blocks.GRAY_CONCRETE));
        f.fill(7, top + 4, 6, 10, top + 4, 9, s(Blocks.SMOOTH_STONE_SLAB));
        f.fill(3, top + 1, 3, 3, top + 6, 3, s(Blocks.IRON_BARS));
        f.set(3, top + 7, 3, Blocks.REDSTONE_LAMP);
        f.set(3, top + 8, 3, Blocks.LIGHTNING_ROD);
    }

    private static void wallBlock(Frame f, Style st, int u, int h, int v, boolean pier) {
        for (int dh = 0; dh < 4; dh++) {
            boolean glass = !pier && (dh == 1 || dh == 2) && h > 0;
            f.set(u, h + dh, v, glass ? s(st.glass) : s(pier || dh == 0 || dh == 3 ? st.pier : st.pier));
        }
    }

    // ---------------------------------------------------------------- tenement

    /** A brick block of flats, {@code floors} high, with a shop on the ground floor and a fire escape behind. */
    static void tenement(Frame f, City city, int floors, long seed) {
        Block brick = new Block[]{Blocks.BRICKS, Blocks.BROWN_TERRACOTTA, Blocks.STONE_BRICKS}[Math.floorMod((int) (seed >> 4), 3)];
        int top = floors * 4;
        f.clear(0, 0, 0, 13, top + 5, 13);
        f.fill(1, -1, 2, 12, -1, 12, s(Blocks.SMOOTH_STONE));
        for (int k = 0; k < floors; k++) {
            int h = k * 4;
            f.walls(1, h, 2, 12, h + 2, 12, s(brick));
            f.fill(1, h + 3, 2, 12, h + 3, 12, s(Blocks.SPRUCE_PLANKS));
            for (int u = 3; u <= 10; u += 3) {
                if (k > 0) {
                    f.set(u, h + 1, 12, Blocks.GLASS_PANE);
                    f.set(u, h + 1, 2, Blocks.GLASS_PANE);
                }
                f.set(u, h + 1, 12, k > 0 ? s(Blocks.GLASS_PANE) : s(Blocks.GLASS_PANE));
            }
            for (int v : new int[]{5, 9}) {
                f.set(1, h + 1, v, Blocks.GLASS_PANE);
                f.set(12, h + 1, v, Blocks.GLASS_PANE);
            }
            for (int dh = 0; dh < 4; dh++) {
                f.set(10, h + dh, 3, ladder());
            }
            if (k < floors - 1) {
                f.clear(10, h + 3, 3, 10, h + 3, 3);
            }
            f.set(6, h + 2, 7, s(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            if (k > 0) { // two rooms a floor, one bed each
                for (int u : new int[]{3, 8}) {
                    bed(f, u, h, 6, Blocks.RED_BED);
                    city.homes.add(f.pos(u, h, 6));
                }
                f.fill(5, h, 4, 6, h, 4, s(Blocks.SPRUCE_SLAB));
            }
        }
        // The corner shop on the ground floor.
        f.fill(2, 0, 5, 7, 0, 5, s(Blocks.SPRUCE_PLANKS));
        f.fill(2, 1, 5, 7, 1, 5, s(Blocks.SPRUCE_SLAB));
        f.fill(2, 0, 2, 7, 2, 2, s(Blocks.BOOKSHELF));
        f.door(9, 12, Blocks.SPRUCE_DOOR);
        f.clear(5, 0, 12, 6, 1, 12);
        f.set(5, 0, 12, Blocks.SPRUCE_PLANKS);
        city.works.add(new City.Work(Job.SHOPKEEPER, f.pos(4, 0, 6)));
        // The roof: a low parapet, water tank on legs.
        f.fill(1, top, 2, 12, top, 12, s(Blocks.STONE_BRICKS));
        for (int u = 1; u <= 12; u++) {
            f.set(u, top + 1, 2, s(Blocks.BRICK_WALL));
            f.set(u, top + 1, 12, s(Blocks.BRICK_WALL));
        }
        f.clear(10, top, 3, 10, top, 3);
        f.fill(3, top + 1, 4, 3, top + 3, 4, s(Blocks.OAK_FENCE));
        f.fill(5, top + 1, 4, 5, top + 3, 4, s(Blocks.OAK_FENCE));
        f.fill(3, top + 4, 4, 5, top + 6, 6, s(Blocks.BARREL));
        // The fire escape: bars and ladders up the back.
        for (int h = 0; h < top; h++) {
            f.set(11, h, 1, ladder().setValue(LadderBlock.FACING, Direction.SOUTH));
            if (h % 4 == 3) {
                f.fill(10, h, 1, 12, h, 1, s(Blocks.IRON_BARS));
            }
        }
        f.set(5, 3, 13, Blocks.LANTERN);
    }

    // ---------------------------------------------------------------- church

    /** A stone church: a nave of pews and a stained-glass apse, a bell tower with a spire. */
    static void church(Frame f, City city) {
        f.clear(0, 0, 0, 13, 22, 13);
        f.fill(1, -1, 1, 12, -1, 12, s(Blocks.STONE_BRICKS));
        // Nave u3..10, v4..13, with walls 8 high.
        f.walls(3, 0, 4, 10, 7, 13, s(Blocks.STONE_BRICKS));
        for (int v = 4; v <= 13; v += 3) {
            f.fill(3, 0, v, 3, 7, v, s(Blocks.CHISELED_STONE_BRICKS));
            f.fill(10, 0, v, 10, 7, v, s(Blocks.CHISELED_STONE_BRICKS));
        }
        for (int v : new int[]{6, 7, 9, 10, 12}) {
            for (int dh = 2; dh <= 5; dh++) {
                Block glass = new Block[]{Blocks.RED_STAINED_GLASS_PANE, Blocks.BLUE_STAINED_GLASS_PANE, Blocks.YELLOW_STAINED_GLASS_PANE}[v % 3];
                if (v % 3 != 0 || dh % 2 == 0) {
                    f.set(3, dh, v, glass);
                    f.set(10, dh, v, glass);
                }
            }
        }
        for (int i = 0; i < 5; i++) { // pitched roof along the nave
            f.fill(2, 8 + i, 4, 2 + i, 8 + i, 13, f.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, false));
            f.fill(11 - i, 8 + i, 4, 11, 8 + i, 13, f.stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, false));
        }
        f.fill(5, 12, 4, 8, 12, 13, s(Blocks.STONE_BRICK_SLAB));
        f.clear(4, 0, 5, 9, 6, 12);
        f.door(6, 13, Blocks.DARK_OAK_DOOR);
        f.door(7, 13, Blocks.DARK_OAK_DOOR);
        // Pews and an aisle, an altar under the east window.
        for (int v = 7; v <= 12; v += 2) {
            for (int u : new int[]{4, 5, 8, 9}) {
                f.set(u, 0, v, f.stairs(Blocks.OAK_STAIRS, Direction.NORTH, false));
            }
        }
        f.fill(5, 0, 5, 8, 0, 5, s(Blocks.QUARTZ_SLAB));
        f.set(6, 1, 5, Blocks.CANDLE);
        f.set(7, 1, 5, Blocks.CANDLE);
        f.set(6, 0, 6, Blocks.LECTERN);
        f.fill(5, 0, 4, 8, 0, 4, s(Blocks.RED_CARPET));
        f.set(6, 5, 4, Blocks.SOUL_LANTERN);
        for (int v = 6; v <= 12; v += 2) {
            f.set(4, 5, v, s(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            f.set(9, 5, v, s(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }
        // The bell tower at the back-left, a spire, a ladder up.
        f.walls(0, 0, 0, 3, 15, 3, s(Blocks.STONE_BRICKS));
        f.clear(1, 0, 1, 2, 14, 2);
        for (int h = 0; h <= 14; h++) {
            f.set(1, h, 1, ladder());
        }
        f.clear(1, 0, 3, 2, 1, 3);
        f.set(2, 12, 3, Blocks.AIR);
        for (int h = 11; h <= 13; h++) {
            for (int u : new int[]{1, 2}) {
                f.set(u, h, 3, Blocks.AIR);
                f.set(u, h, 0, Blocks.AIR);
            }
        }
        f.set(1, 12, 2, Blocks.BELL);
        for (int i = 0; i < 4; i++) {
            f.fill(i, 16 + i, i, 3 - i, 16 + i, 3 - i, s(Blocks.STONE_BRICKS));
        }
        f.fill(1, 20, 1, 2, 22, 2, s(Blocks.STONE_BRICK_WALL));
        f.set(1, 23, 1, Blocks.LIGHTNING_ROD);
        f.fill(0, 3, 0, 3, 3, 3, s(Blocks.STONE_BRICKS));
    }

    // ---------------------------------------------------------------- market

    /** An open-air market: three stalls under striped awnings, crates, a well. Each stall has its trader. */
    static void market(Frame f, City city) {
        f.clear(0, 0, 0, 13, 6, 13);
        for (int u = 0; u < 14; u++) {
            for (int v = 0; v < 14; v++) {
                f.set(u, -1, v, (u + v) % 3 == 0 ? s(Blocks.STONE_BRICKS) : s(Blocks.POLISHED_ANDESITE));
            }
        }
        Block[] awning = {Blocks.RED_WOOL, Blocks.WHITE_WOOL, Blocks.BLUE_WOOL};
        for (int i = 0; i < 3; i++) {
            int u = 1 + i * 4;
            for (int[] post : new int[][]{{u, 3}, {u + 3, 3}, {u, 8}, {u + 3, 8}}) {
                f.fill(post[0], 0, post[1], post[0], 2, post[1], s(Blocks.OAK_FENCE));
            }
            f.fill(u - 1, 3, 2, u + 4, 3, 9, s(awning[i]));
            f.fill(u, 0, 4, u + 3, 0, 4, s(Blocks.SPRUCE_PLANKS));
            f.fill(u, 1, 4, u + 3, 1, 4, s(Blocks.BARREL));
            f.set(u + 1, 2, 4, Blocks.LANTERN);
            f.set(u, 0, 7, Blocks.BARREL);
            f.set(u + 3, 0, 7, Blocks.COMPOSTER);
            city.works.add(new City.Work(Job.SHOPKEEPER, f.pos(u + 1, 0, 6)));
        }
        // A well in the middle of the back row, benches along the front.
        f.fill(5, 0, 11, 8, 0, 12, s(Blocks.COBBLESTONE));
        f.fill(6, 0, 11, 7, 0, 12, s(Blocks.WATER));
        f.fill(5, 1, 11, 5, 1, 11, s(Blocks.COBBLESTONE_WALL));
        f.fill(8, 1, 11, 8, 1, 11, s(Blocks.COBBLESTONE_WALL));
        f.fill(5, 2, 11, 8, 2, 11, s(Blocks.OAK_SLAB));
        for (int u : new int[]{2, 11}) {
            f.set(u, 0, 12, f.stairs(Blocks.OAK_STAIRS, Direction.NORTH, false));
        }
    }

    // ---------------------------------------------------------------- park

    /** A park: lawn, crossing paths, a pond, benches and trees, lamps at the corners. */
    static void park(Frame f, long seed) {
        f.clear(0, 0, 0, 13, 12, 13);
        for (int u = 0; u < 14; u++) {
            for (int v = 0; v < 14; v++) {
                boolean path = (u >= 6 && u <= 7) || (v >= 6 && v <= 7);
                f.set(u, -1, v, path ? s(Blocks.DIRT_PATH) : s(Blocks.GRASS_BLOCK));
            }
        }
        // Pond.
        for (int u = 8; u <= 11; u++) {
            for (int v = 1; v <= 4; v++) {
                f.set(u, -1, v, s(Blocks.WATER));
            }
        }
        f.set(9, 0, 2, Blocks.LILY_PAD);
        // Trees.
        int[][] spots = {{2, 2}, {3, 10}, {11, 10}, {10, 8}, {1, 5}};
        for (int i = 0; i < spots.length; i++) {
            int[] t = spots[i];
            int tall = 4 + Math.floorMod((int) (seed >> (i * 3)), 3);
            f.fill(t[0], 0, t[1], t[0], tall, t[1], s(Blocks.OAK_LOG));
            f.fill(t[0] - 2, tall - 1, t[1] - 2, t[0] + 2, tall, t[1] + 2, s(Blocks.OAK_LEAVES));
            f.fill(t[0] - 1, tall + 1, t[1] - 1, t[0] + 1, tall + 2, t[1] + 1, s(Blocks.OAK_LEAVES));
            f.set(t[0], tall, t[1], Blocks.OAK_LOG);
        }
        for (int[] bench : new int[][]{{5, 6, 3}, {8, 7, 1}, {6, 4, 0}, {7, 9, 2}}) {
            Direction face = new Direction[]{Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST}[bench[2]];
            f.set(bench[0], 0, bench[1], f.stairs(Blocks.OAK_STAIRS, face, false));
        }
        for (int[] flower : new int[][]{{1, 8}, {4, 1}, {12, 12}, {2, 12}, {12, 6}}) {
            f.set(flower[0], 0, flower[1], new Block[]{Blocks.POPPY, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.ALLIUM}[(flower[0] + flower[1]) % 4]);
        }
        for (int[] lamp : new int[][]{{0, 13}, {13, 13}}) {
            f.fill(lamp[0], 0, lamp[1], lamp[0], 2, lamp[1], s(Blocks.COBBLESTONE_WALL));
            f.set(lamp[0], 3, lamp[1], Blocks.LANTERN);
        }
    }

    // ---------------------------------------------------------------- depot

    /** A depot: a concrete shed with a roller door, crates, and a loading bay. Nobody sleeps here. */
    static void depot(Frame f, long seed) {
        f.clear(0, 0, 0, 13, 9, 13);
        f.fill(0, -1, 0, 13, -1, 13, s(Blocks.STONE));
        f.fill(1, -1, 2, 12, -1, 12, s(Blocks.SMOOTH_STONE));
        f.walls(1, 0, 2, 12, 5, 12, s(Blocks.LIGHT_GRAY_CONCRETE));
        f.fill(1, 6, 2, 12, 6, 12, s(Blocks.SMOOTH_STONE_SLAB));
        for (int u = 3; u <= 10; u += 3) {
            f.set(u, 4, 2, Blocks.IRON_BARS);
            f.set(u, 4, 12, Blocks.IRON_BARS);
        }
        f.clear(5, 0, 12, 8, 3, 12); // roller door, up
        f.fill(5, 4, 12, 8, 4, 12, s(Blocks.IRON_BARS));
        f.fill(4, 0, 13, 9, 0, 13, s(Blocks.SMOOTH_STONE_SLAB));
        for (int i = 0; i < 12; i++) {
            int u = 2 + Math.floorMod((int) (seed >> i), 10);
            int v = 3 + Math.floorMod((int) (seed >> (i + 5)), 8);
            f.set(u, 0, v, s(i % 3 == 0 ? Blocks.BARREL : Blocks.SPRUCE_PLANKS));
            if (i % 4 == 0) {
                f.set(u, 1, v, Blocks.BARREL);
            }
        }
        f.set(2, 3, 3, s(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        f.set(11, 3, 3, s(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        f.set(5, 5, 13, Blocks.LANTERN);
    }

    private CityKit() {}
}
