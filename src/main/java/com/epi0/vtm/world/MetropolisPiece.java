package com.epi0.vtm.world;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.block.CityHeartBlockEntity;
import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityRegistry;
import com.epi0.vtm.registry.ModStructures;
import net.minecraft.core.BlockPos;
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

import java.util.UUID;

/**
 * A metropolis, drawn one chunk at a time. Everything about it comes from the {@link CityPlan} of its seed, so a
 * chunk draws only the ground, streets, wall and lots that overlap it; the records the game needs (beds, workplaces,
 * places) are taken from the buildings once, in the chunk that holds the Domain Stone.
 */
public class MetropolisPiece extends StructurePiece {
    public static final int DEPTH_BELOW = 8;
    /** Taller than the tallest tower (thirteen floors) and its mast. */
    public static final int HEIGHT_ABOVE = 80;
    /** Whatever grows on the site, to this height, is cleared; towers clear their own way. */
    private static final int CLEARED = 24;
    private static final int WALL = 5;

    private final int groundY;
    private final long seed;
    private final ResourceLocation faction;
    private final String name;

    public MetropolisPiece(int minX, int groundY, int minZ, long seed, ResourceLocation faction, String name) {
        super(ModStructures.METROPOLIS_PIECE.get(), 0, new BoundingBox(minX, groundY - DEPTH_BELOW, minZ,
                minX + CityPlan.size() - 1, groundY + HEIGHT_ABOVE, minZ + CityPlan.size() - 1));
        this.groundY = groundY;
        this.seed = seed;
        this.faction = faction;
        this.name = name;
    }

    public MetropolisPiece(CompoundTag tag) {
        super(ModStructures.METROPOLIS_PIECE.get(), tag);
        this.groundY = tag.getInt("GroundY");
        this.seed = tag.getLong("Seed");
        this.faction = ResourceLocation.parse(tag.getString("Faction"));
        this.name = tag.getString("Name");
    }

    /** The plan this city was drawn from (tools and tests). */
    public CityPlan plan() {
        return CityPlan.of(seed);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("GroundY", groundY);
        tag.putLong("Seed", seed);
        tag.putString("Faction", faction.toString());
        tag.putString("Name", name);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        Canvas c = new Canvas(level, box, seed);
        CityPlan plan = CityPlan.of(seed);
        int ox = boundingBox.minX();
        int oz = boundingBox.minZ();
        int y0 = groundY;
        int size = plan.size;

        // The pad: levelled, with lawn over it.
        c.clear(ox, y0, oz, ox + size - 1, y0 + CLEARED, oz + size - 1);
        c.foundation(ox, oz, ox + size - 1, oz + size - 1, y0 - 1, Canvas.state(Blocks.DIRT));
        c.fill(ox, y0 - 1, oz, ox + size - 1, y0 - 1, oz + size - 1, Canvas.state(Blocks.GRASS_BLOCK));
        streets(c, plan, ox, oz, y0);
        wall(c, plan, ox, oz, y0);

        // Only the lots that reach into this chunk are drawn (into a scratch record: the real one is taken below).
        City scratch = layoutFor(plan, ox, oz, y0);
        for (CityPlan.Lot lot : plan.lots) {
            int x = ox + lot.x();
            int z = oz + lot.z();
            if (x + lot.size() - 1 < box.minX() || x > box.maxX() || z + lot.size() - 1 < box.minZ() || z > box.maxZ()) {
                continue;
            }
            draw(c, lot, scratch, x, z, y0, random);
        }
        CityPlan.Lot centre = plan.first(CityPlan.Kind.PLAZA);
        CityPiece.plaza(c, ox + centre.x(), oz + centre.z(), y0, scratch);

        // The Domain Stone's chunk describes the whole city: what every building holds, seen without drawing a block.
        BlockPos heart = scratch.center;
        if (c.inside(heart.getX(), heart.getY(), heart.getZ())
                && c.blockEntity(heart.getX(), heart.getY(), heart.getZ()) instanceof CityHeartBlockEntity entity) {
            City layout = layoutFor(plan, ox, oz, y0);
            Canvas nowhere = new Canvas(level, new BoundingBox(ox - 4000, 0, oz - 4000, ox - 4000, 0, oz - 4000), seed);
            for (CityPlan.Lot lot : plan.lots) {
                draw(nowhere, lot, layout, ox + lot.x(), oz + lot.z(), y0, random);
            }
            CityPiece.plaza(nowhere, ox + centre.x(), oz + centre.z(), y0, layout);
            if (layout.club == null) {
                CityPlan.Lot asylum = plan.first(CityPlan.Kind.ASYLUM);
                layout.club = new BlockPos(ox + asylum.centreX(), y0, oz + asylum.z() + 8);
            }
            entity.describe(name, faction, CityRegistry.write(layout));
        }
    }

    private City layoutFor(CityPlan plan, int ox, int oz, int y0) {
        CityPlan.Lot centre = plan.first(CityPlan.Kind.PLAZA);
        City layout = new City(UUID.nameUUIDFromBytes(Long.toString(seed).getBytes()), name, Level.OVERWORLD,
                new BlockPos(ox + centre.centreX(), y0 + 2, oz + centre.centreZ()), faction);
        layout.radius = plan.size / 2 + 6;
        return layout;
    }

    // ---------------------------------------------------------------- lots

    private void draw(Canvas c, CityPlan.Lot lot, City layout, int lx, int lz, int y0, RandomSource random) {
        Frame frame = new Frame(c, lx + 1, y0, lz + 1, lot.facing());
        switch (lot.kind()) {
            case PLAZA -> { } // drawn once, below
            case HOUSE -> CityBuildings.house(frame, layout, lot.i() * CityPlan.N + lot.j(), lot.seed());
            case SHOP -> CityBuildings.shop(frame, layout);
            case CLUB -> CityBuildings.club(frame, layout);
            case WATCH -> CityBuildings.watch(frame, layout);
            case MARKET -> CityKit.market(frame, layout);
            case TOWER -> CityKit.tower(frame, layout, lot.floors(), lot.seed());
            case TENEMENT -> CityKit.tenement(frame, layout, lot.floors(), lot.seed());
            case CHURCH -> CityKit.church(frame, layout);
            case PARK -> CityKit.park(frame, lot.seed());
            case DEPOT -> CityKit.depot(frame, lot.seed());
            case HAVEN -> {
                LairPiece.manor(c, lx, y0, lz + 2, faction, random);
                layout.haven = new BlockPos(lx + 8, y0 - 3, lz + 2 + 4);
            }
            case ASYLUM -> AsylumClub.build(c, lx + (lot.size() - AsylumClub.WIDTH) / 2, y0, lz + (lot.size() - AsylumClub.DEPTH - AsylumClub.FORECOURT) / 2,
                    VtmMod.id("camarilla"), VtmMod.id("malkavian"), random);
        }
    }

    // ---------------------------------------------------------------- streets

    private static BlockState asphalt(Canvas c, int x, int z) {
        return Canvas.state(switch (c.roll(x, 0, z, 9)) {
            case 0 -> Blocks.COBBLED_DEEPSLATE;
            case 1 -> Blocks.GRAVEL;
            default -> Blocks.BLACKSTONE;
        });
    }

    private void streets(Canvas c, CityPlan plan, int ox, int oz, int y0) {
        BoundingBox box = c.box();
        int size = plan.size;
        for (int wx = Math.max(ox, box.minX()); wx <= Math.min(ox + size - 1, box.maxX()); wx++) {
            int dx = wx - ox;
            int[] bandX = CityPlan.axisIsStreet(dx) ? CityPlan.streetAt(dx) : null;
            for (int wz = Math.max(oz, box.minZ()); wz <= Math.min(oz + size - 1, box.maxZ()); wz++) {
                int dz = wz - oz;
                int[] bandZ = CityPlan.axisIsStreet(dz) ? CityPlan.streetAt(dz) : null;
                if (bandX == null && bandZ == null) {
                    continue;
                }
                BlockState paving;
                if (bandX != null && bandZ != null) {
                    paving = asphalt(c, wx, wz); // an intersection
                } else {
                    // A street running along one axis: kerbs on its edges, a line down the middle of an avenue.
                    int[] band = bandX != null ? bandX : bandZ;
                    int across = (bandX != null ? dx : dz) - band[1];
                    int along = bandX != null ? dz : dx;
                    if (across == 0 || across == band[2] - 1) {
                        paving = Canvas.state(Blocks.STONE_BRICKS);
                    } else if (band[2] == CityPlan.AVENUE && (across == 2 || across == 3) && along % 6 < 3) {
                        paving = Canvas.state(across == 2 ? Blocks.YELLOW_CONCRETE : Blocks.WHITE_CONCRETE);
                    } else {
                        paving = asphalt(c, wx, wz);
                    }
                }
                c.set(wx, y0 - 1, wz, paving);
            }
        }
        // A lamp at the corner of every lot, on the pavement.
        for (int i = 0; i < CityPlan.N; i++) {
            for (int j = 0; j < CityPlan.N; j++) {
                int x = ox + CityPlan.lotStart(i);
                int z = oz + CityPlan.lotStart(j);
                for (int[] corner : new int[][]{{x - 1, z - 1}, {x + CityPlan.LOT, z + CityPlan.LOT}}) {
                    if (c.inside(corner[0], y0, corner[1]) && !insideAsylum(plan, corner[0] - ox, corner[1] - oz)) {
                        c.fill(corner[0], y0, corner[1], corner[0], y0 + 2, corner[1], Canvas.state(Blocks.COBBLESTONE_WALL));
                        c.set(corner[0], y0 + 3, corner[1], Canvas.state(Blocks.LANTERN));
                    }
                }
            }
        }
    }

    private static boolean insideAsylum(CityPlan plan, int dx, int dz) {
        CityPlan.Lot a = plan.first(CityPlan.Kind.ASYLUM);
        return dx >= a.x() && dx < a.x() + a.size() && dz >= a.z() && dz < a.z() + a.size();
    }

    // ---------------------------------------------------------------- the wall

    /** Where the wall is open: the two avenues in each direction (offset along the wall, and width). */
    private static int[][] gates() {
        return new int[][]{{CityPlan.lotStart(CityPlan.CENTRE - 1) + CityPlan.LOT, CityPlan.AVENUE},
                {CityPlan.lotStart(CityPlan.CENTRE) + CityPlan.LOT, CityPlan.AVENUE}};
    }

    private void wall(Canvas c, CityPlan plan, int ox, int oz, int y0) {
        BoundingBox box = c.box();
        int size = plan.size;
        int[][] gates = gates();
        for (int wx = Math.max(ox, box.minX()); wx <= Math.min(ox + size - 1, box.maxX()); wx++) {
            int dx = wx - ox;
            for (int wz = Math.max(oz, box.minZ()); wz <= Math.min(oz + size - 1, box.maxZ()); wz++) {
                int dz = wz - oz;
                boolean ns = dz < 2 || dz >= size - 2;
                boolean ew = dx < 2 || dx >= size - 2;
                if (!ns && !ew) {
                    continue;
                }
                // Position along the wall this column is part of (a corner belongs to both; use the longer run).
                int along = ns ? dx : dz;
                int height = WALL;
                boolean opening = false;
                for (int[] gate : gates) {
                    if (along >= gate[0] && along < gate[0] + gate[1]) {
                        opening = true;
                    } else if (along >= gate[0] - 3 && along < gate[0] + gate[1] + 3) {
                        height = 11; // the gate towers
                    }
                }
                if (ns && ew && (dx < 2 || dx >= size - 2) && (dz < 2 || dz >= size - 2)) {
                    height = 9; // corner towers
                    opening = false;
                }
                if (opening) {
                    c.set(wx, y0 + WALL, wz, Canvas.state(Blocks.STONE_BRICKS)); // the arch over the gate
                    c.set(wx, y0 + WALL + 1, wz, Canvas.state(Blocks.STONE_BRICK_SLAB));
                    continue;
                }
                for (int y = 0; y < height; y++) {
                    c.set(wx, y0 + y, wz, brick(c, wx, y0 + y, wz));
                }
                if (height == WALL && (dx + dz) % 2 == 0) {
                    c.set(wx, y0 + WALL, wz, Canvas.state(Blocks.STONE_BRICK_WALL));
                }
                if (height > WALL && (dx + dz) % 2 == 0) {
                    c.set(wx, y0 + height, wz, Canvas.state(Blocks.STONE_BRICK_WALL));
                }
                if (height > WALL && c.roll(wx, 3, wz, 5) == 0) {
                    c.set(wx, y0 + 7, wz, Canvas.state(Blocks.IRON_BARS));
                }
            }
        }
    }

    private static BlockState brick(Canvas c, int x, int y, int z) {
        return Canvas.state(switch (c.roll(x, y, z, 6)) {
            case 0 -> Blocks.CRACKED_STONE_BRICKS;
            case 1 -> Blocks.MOSSY_STONE_BRICKS;
            default -> Blocks.STONE_BRICKS;
        });
    }
}
