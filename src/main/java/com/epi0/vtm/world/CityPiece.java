package com.epi0.vtm.world;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.block.CityHeartBlockEntity;
import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityRegistry;
import com.epi0.vtm.npc.Job;
import com.epi0.vtm.registry.ModBlocks;
import com.epi0.vtm.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A city of nine plots on a flat plateau: a plaza with a fountain and the Domain Stone in the middle, and around it
 * four houses, a shop, a club, a watch station and the ruler's haven, joined by lamplit streets.
 */
public class CityPiece extends StructurePiece {
    public static final int ROAD = 4;
    public static final int LOT = 16;
    public static final int SIZE = 4 * ROAD + 3 * LOT; // 64
    public static final int HEIGHT_ABOVE = 16;
    public static final int DEPTH_BELOW = 8;

    enum Lot { HOUSE, SHOP, CLUB, WATCH, HAVEN }

    private static final int[][] SLOTS = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}};

    private final int groundY;
    private final long seed;
    private final ResourceLocation faction;
    private final String name;

    public CityPiece(int minX, int groundY, int minZ, long seed, ResourceLocation faction, String name) {
        super(ModStructures.CITY_PIECE.get(), 0, new BoundingBox(minX, groundY - DEPTH_BELOW, minZ,
                minX + SIZE - 1, groundY + HEIGHT_ABOVE, minZ + SIZE - 1));
        this.groundY = groundY;
        this.seed = seed;
        this.faction = faction;
        this.name = name;
    }

    public CityPiece(CompoundTag tag) {
        super(ModStructures.CITY_PIECE.get(), tag);
        this.groundY = tag.getInt("GroundY");
        this.seed = tag.getLong("Seed");
        this.faction = ResourceLocation.parse(tag.getString("Faction"));
        this.name = tag.getString("Name");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("GroundY", groundY);
        tag.putLong("Seed", seed);
        tag.putString("Faction", faction.toString());
        tag.putString("Name", name);
    }

    private static int lotOrigin(int index) {
        return ROAD + index * (LOT + ROAD);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        Canvas c = new Canvas(level, box, seed);
        int ox = boundingBox.minX();
        int oz = boundingBox.minZ();
        int y0 = groundY;
        City layout = new City(UUID.nameUUIDFromBytes(Long.toString(seed).getBytes()), name, Level.OVERWORLD,
                new BlockPos(ox + SIZE / 2, y0 + 2, oz + SIZE / 2), faction);

        // The plateau: lawn over everything, air above, earth below.
        c.clear(ox, y0, oz, ox + SIZE - 1, y0 + HEIGHT_ABOVE, oz + SIZE - 1);
        c.foundation(ox, oz, ox + SIZE - 1, oz + SIZE - 1, y0 - 1, Canvas.state(Blocks.DIRT));
        c.fill(ox, y0 - 1, oz, ox + SIZE - 1, y0 - 1, oz + SIZE - 1, Canvas.state(Blocks.GRASS_BLOCK));
        streets(c, ox, oz, y0);

        // The lots, in an order fixed by the seed.
        List<Lot> lots = new ArrayList<>(List.of(Lot.HOUSE, Lot.HOUSE, Lot.HOUSE, Lot.HOUSE, Lot.SHOP, Lot.CLUB, Lot.WATCH, Lot.HAVEN));
        java.util.Collections.shuffle(lots, new java.util.Random(seed));
        for (int i = 0; i < SLOTS.length; i++) {
            int lx = ox + lotOrigin(SLOTS[i][0]);
            int lz = oz + lotOrigin(SLOTS[i][1]);
            Direction facing = frontFor(SLOTS[i][0], SLOTS[i][1]);
            Frame frame = new Frame(c, lx + 1, y0, lz + 1, facing);
            switch (lots.get(i)) {
                case HOUSE -> CityBuildings.house(frame, layout, i, seed);
                case SHOP -> CityBuildings.shop(frame, layout);
                case CLUB -> CityBuildings.club(frame, layout);
                case WATCH -> CityBuildings.watch(frame, layout);
                case HAVEN -> {
                    LairPiece.manor(c, lx, y0, lz + 2, faction, random);
                    layout.haven = new BlockPos(lx + 8, y0 - 3, lz + 2 + 4);
                }
            }
        }
        plaza(c, ox + lotOrigin(1), oz + lotOrigin(1), y0, layout);

        BlockPos heart = layout.center;
        if (c.inside(heart.getX(), heart.getY(), heart.getZ())
                && c.blockEntity(heart.getX(), heart.getY(), heart.getZ()) instanceof CityHeartBlockEntity entity) {
            entity.describe(name, faction, CityRegistry.write(layout));
        }
    }

    /** Facing for the building on lot (i, j): towards the plaza along the street it stands on. */
    private static Direction frontFor(int i, int j) {
        int dx = i - 1;
        int dz = j - 1;
        if (dz != 0) {
            return dz < 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return dx < 0 ? Direction.EAST : Direction.WEST;
    }

    private void streets(Canvas c, int ox, int oz, int y0) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean road = x % (LOT + ROAD) < ROAD || z % (LOT + ROAD) < ROAD || x >= SIZE - ROAD || z >= SIZE - ROAD;
                if (road) {
                    int wx = ox + x;
                    int wz = oz + z;
                    BlockState paving = switch (c.roll(wx, 0, wz, 7)) {
                        case 0 -> Canvas.state(Blocks.GRAVEL);
                        case 1 -> Canvas.state(Blocks.ANDESITE);
                        case 2 -> Canvas.state(Blocks.COBBLESTONE);
                        default -> Canvas.state(Blocks.STONE);
                    };
                    c.set(wx, y0 - 1, wz, paving);
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                int x = ox + i * (LOT + ROAD) + (i == 3 ? ROAD - 1 : 0);
                int z = oz + j * (LOT + ROAD) + (j == 3 ? ROAD - 1 : 0);
                c.fill(x, y0, z, x, y0 + 2, z, Canvas.state(Blocks.COBBLESTONE_WALL));
                c.set(x, y0 + 3, z, Canvas.state(Blocks.LANTERN));
            }
        }
    }

    /** The plaza: paving, a fountain around the Domain Stone, benches and a pair of trees. */
    static void plaza(Canvas c, int x0, int z0, int y0, City layout) {
        int cx = x0 + LOT / 2;
        int cz = z0 + LOT / 2;
        for (int x = x0; x < x0 + LOT; x++) {
            for (int z = z0; z < z0 + LOT; z++) {
                c.set(x, y0 - 1, z, (x + z) % 2 == 0 ? Canvas.state(Blocks.POLISHED_ANDESITE) : Canvas.state(Blocks.STONE_BRICKS));
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                if (rim) {
                    c.set(cx + dx, y0 - 1, cz + dz, Canvas.state(Blocks.STONE_BRICKS));
                    c.set(cx + dx, y0, cz + dz, Canvas.state(Blocks.STONE_BRICK_SLAB));
                } else {
                    c.set(cx + dx, y0 - 1, cz + dz, Canvas.state(Blocks.WATER));
                }
            }
        }
        c.set(cx, y0 - 1, cz, Canvas.state(Blocks.STONE_BRICKS));
        c.set(cx, y0, cz, Canvas.state(Blocks.STONE_BRICKS));
        c.set(cx, y0 + 1, cz, Canvas.state(Blocks.CHISELED_STONE_BRICKS));
        c.set(cx, y0 + 2, cz, ModBlocks.CITY_HEART.get().defaultBlockState());
        for (int[] bench : new int[][]{{0, -6, 1}, {0, 6, 3}, {-6, 0, 0}, {6, 0, 2}}) {
            Direction face = new Direction[]{Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST}[bench[2]];
            c.set(cx + bench[0], y0, cz + bench[1], Canvas.stairs(Blocks.OAK_STAIRS, face, false));
        }
        for (int[] tree : new int[][]{{-6, -6}, {6, 6}}) {
            int tx = cx + tree[0];
            int tz = cz + tree[1];
            c.fill(tx, y0, tz, tx, y0 + 3, tz, Canvas.state(Blocks.OAK_LOG));
            c.fill(tx - 2, y0 + 3, tz - 2, tx + 2, y0 + 4, tz + 2, Canvas.state(Blocks.OAK_LEAVES));
            c.fill(tx - 1, y0 + 5, tz - 1, tx + 1, y0 + 5, tz + 1, Canvas.state(Blocks.OAK_LEAVES));
            c.set(tx, y0 + 3, tz, Canvas.state(Blocks.OAK_LOG));
            c.set(tx, y0 + 4, tz, Canvas.state(Blocks.OAK_LOG));
        }
        layout.plaza = new BlockPos(cx + 4, y0, cz + 4);
    }
}
