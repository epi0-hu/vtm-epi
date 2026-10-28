package com.epi0.vtm.world;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A vampire's lair, built block by block. All three themes hide a coffin that belongs to the lair's faction, a chest
 * of what the occupant has gathered over the years, and signs of a long residence.
 */
public class LairPiece extends StructurePiece {
    public static final ResourceLocation LOOT = VtmMod.id("chests/lair");

    public enum Theme {
        CRYPT(13, 13, 6), MANOR(15, 11, 9), WAREHOUSE(17, 11, 7),
        /** Set into the cave system, not on the surface. */
        SEWER(SewerLair.WIDTH, SewerLair.DEPTH, 6, true),
        ASYLUM(AsylumClub.WIDTH, AsylumClub.DEPTH + AsylumClub.FORECOURT + 1, 12);

        final int width;
        final int depth;
        final int height;
        final boolean underground;

        Theme(int width, int depth, int height) {
            this(width, depth, height, false);
        }

        Theme(int width, int depth, int height, boolean underground) {
            this.width = width;
            this.depth = depth;
            this.height = height;
            this.underground = underground;
        }
    }

    private final Theme theme;
    private final ResourceLocation faction;
    @org.jetbrains.annotations.Nullable
    private final ResourceLocation clan;
    private final int groundY;
    private final int surfaceY;
    private final long seed;

    /**
     * {@code groundY} is the level of the lair's floor; {@code surfaceY} the surface above it (only underground lairs
     * care, for their shaft up).
     */
    public LairPiece(Theme theme, ResourceLocation faction, @org.jetbrains.annotations.Nullable ResourceLocation clan,
                     int minX, int groundY, int minZ, long seed, int surfaceY) {
        super(ModStructures.LAIR_PIECE.get(), 0, new BoundingBox(minX, groundY - (theme.underground ? 4 : 8), minZ,
                minX + theme.width - 1, Math.max(groundY + theme.height + 2, theme.underground ? surfaceY + 1 : 0), minZ + theme.depth - 1));
        this.theme = theme;
        this.faction = faction;
        this.clan = clan;
        this.groundY = groundY;
        this.surfaceY = surfaceY;
        this.seed = seed;
    }

    public LairPiece(CompoundTag tag) {
        super(ModStructures.LAIR_PIECE.get(), tag);
        this.theme = Theme.valueOf(tag.getString("Theme"));
        this.faction = ResourceLocation.parse(tag.getString("Faction"));
        this.groundY = tag.getInt("GroundY");
        this.surfaceY = tag.getInt("SurfaceY");
        this.clan = tag.contains("Clan") ? ResourceLocation.parse(tag.getString("Clan")) : null;
        this.seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Theme", theme.name());
        tag.putString("Faction", faction.toString());
        tag.putInt("GroundY", groundY);
        tag.putInt("SurfaceY", surfaceY);
        if (clan != null) {
            tag.putString("Clan", clan.toString());
        }
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        Canvas c = new Canvas(level, box, seed);
        int x0 = boundingBox.minX();
        int z0 = boundingBox.minZ();
        switch (theme) {
            case CRYPT -> crypt(c, x0, groundY, z0, random);
            case MANOR -> manor(c, x0, groundY, z0, faction, random);
            case WAREHOUSE -> warehouse(c, x0, groundY, z0, random);
            case SEWER -> SewerLair.build(c, x0, groundY, z0, surfaceY, faction, clan, random);
            case ASYLUM -> AsylumClub.build(c, x0, groundY, z0, faction, clan, random);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static BlockState brick(Canvas c, int x, int y, int z) {
        return switch (c.roll(x, y, z, 6)) {
            case 0 -> Canvas.state(Blocks.CRACKED_STONE_BRICKS);
            case 1 -> Canvas.state(Blocks.MOSSY_STONE_BRICKS);
            default -> Canvas.state(Blocks.STONE_BRICKS);
        };
    }

    /** Weathers the walls of a box: some blocks are cracked or mossy, some missing, some hung with cobweb. */
    private static void weather(Canvas c, int x1, int y1, int z1, int x2, int y2, int z2, int decayPercent) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    if (!c.inside(x, y, z)) {
                        continue;
                    }
                    BlockState here = c.at(x, y, z);
                    if (!here.isAir() && c.chance(x, y, z, decayPercent) && !here.is(Blocks.CHEST) && !here.is(Blocks.CHAIN) && !here.is(com.epi0.vtm.registry.ModBlocks.COFFIN.get())) {
                        c.set(x, y, z, Blocks.AIR.defaultBlockState());
                    } else if (here.isAir() && c.chance(x + 7, y, z - 3, decayPercent / 2 + 1) && !c.at(x, y + 1, z).isAir()) {
                        c.set(x, y, z, Canvas.state(Blocks.COBWEB));
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- crypt

    /**
     * A mausoleum above ground; a stair down to a vaulted chamber where the dead are kept in order, and one of them
     * is not dead.
     */
    private void crypt(Canvas c, int x0, int y0, int z0, RandomSource random) {
        int x1 = x0 + 12;
        int z1 = z0 + 12;
        c.clear(x0, y0, z0, x1, y0 + 11, z1); // trees, tall grass, and whatever else grew here
        // Underground chamber: interior y0-5..y0-2, floor y0-6, ceiling y0-1.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0 - 6; y <= y0 - 1; y++) {
                    boolean shell = x == x0 || x == x1 || z == z0 || z == z1 || y == y0 - 6 || y == y0 - 1;
                    c.set(x, y, z, shell ? (y == y0 - 6 ? Canvas.state(Blocks.POLISHED_DEEPSLATE) : brick(c, x, y, z))
                            : Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int[] corner : new int[][]{{x0 + 2, z0 + 2}, {x1 - 2, z0 + 2}, {x0 + 2, z1 - 2}, {x1 - 2, z1 - 2}}) {
            c.fill(corner[0], y0 - 5, corner[1], corner[0], y0 - 2, corner[1], Canvas.state(Blocks.STONE_BRICK_WALL));
            c.set(corner[0], y0 - 2, corner[1], Canvas.state(Blocks.CHISELED_STONE_BRICKS));
        }
        // Coffins along the north wall; the middle one is occupied.
        for (int i = 0; i < 3; i++) {
            int x = x0 + 3 + i * 3;
            c.coffin(x, y0 - 5, z0 + 1, Direction.SOUTH, i == 1 ? faction : null);
            c.set(x, y0 - 4, z0 + 1, Canvas.state(Blocks.CANDLE));
        }
        c.chest(x1 - 1, y0 - 5, z1 - 1, Direction.WEST, LOOT, random);
        c.set(x0 + 1, y0 - 5, z1 - 1, Canvas.state(Blocks.BARREL));
        c.set(x0 + 1, y0 - 5, z1 - 2, Canvas.state(Blocks.BOOKSHELF));
        c.set(x0 + 1, y0 - 4, z1 - 2, Canvas.state(Blocks.BOOKSHELF));
        c.set(x0 + 6, y0 - 2, z0 + 6, Canvas.state(Blocks.SOUL_LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        c.set(x0 + 6, y0 - 1, z0 + 6, Canvas.state(Blocks.CHAIN));
        // Mausoleum above (x0+3..x0+9), door facing south, stair down inside.
        int mx1 = x0 + 3;
        int mx2 = x0 + 9;
        int mz1 = z0 + 3;
        int mz2 = z0 + 9;
        c.room(mx1, y0, mz1, mx2, y0 + 5, mz2, Canvas.state(Blocks.STONE_BRICKS));
        c.walls(mx1, y0, mz1, mx2, y0 + 4, mz2, Canvas.state(Blocks.STONE_BRICKS));
        c.fill(mx1, y0 + 5, mz1, mx2, y0 + 5, mz2, Canvas.state(Blocks.STONE_BRICKS));
        for (int i = 0; i < 4; i++) { // a pitched roof of stone stairs, ridge along the east-west axis
            c.fill(mx1 - 1, y0 + 6 + i, mz1 - 1 + i, mx2 + 1, y0 + 6 + i, mz1 - 1 + i, Canvas.stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
            c.fill(mx1 - 1, y0 + 6 + i, mz2 + 1 - i, mx2 + 1, y0 + 6 + i, mz2 + 1 - i, Canvas.stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
        }
        c.fill(mx1 - 1, y0 + 9, mz1 + 3, mx2 + 1, y0 + 9, mz1 + 3, Canvas.state(Blocks.STONE_BRICK_SLAB));
        c.fill(x0 + 5, y0, mz2 + 1, x0 + 5, y0 + 2, mz2 + 1, Canvas.state(Blocks.STONE_BRICK_WALL));
        c.fill(x0 + 7, y0, mz2 + 1, x0 + 7, y0 + 2, mz2 + 1, Canvas.state(Blocks.STONE_BRICK_WALL));
        c.set(x0 + 5, y0 + 3, mz2 + 1, Canvas.state(Blocks.SOUL_LANTERN));
        c.set(x0 + 7, y0 + 3, mz2 + 1, Canvas.state(Blocks.SOUL_LANTERN));
        c.clear(x0 + 6, y0, mz2, x0 + 6, y0 + 1, mz2); // doorway
        c.set(x0 + 6, y0 + 2, mz2, Canvas.state(Blocks.CHISELED_STONE_BRICKS));
        for (int i = 0; i < 5; i++) {
            int z = z0 + 5 + i;
            c.clear(x0 + 5, y0 - 1 - i, z, x0 + 7, y0 + 2 - i, z);
            c.set(x0 + 6, y0 - 1 - i, z, Canvas.stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
            c.set(x0 + 5, y0 - 1 - i, z, Canvas.state(Blocks.STONE_BRICKS));
            c.set(x0 + 7, y0 - 1 - i, z, Canvas.state(Blocks.STONE_BRICKS));
        }
        c.set(x0 + 4, y0 + 1, z0 + 6, Canvas.state(Blocks.CHISELED_STONE_BRICKS));
        c.set(x0 + 8, y0 + 1, z0 + 6, Canvas.state(Blocks.CHISELED_STONE_BRICKS));
        c.set(x0 + 4, y0 + 2, z0 + 6, Canvas.state(Blocks.SOUL_LANTERN));
        weather(c, x0, y0 - 5, z0, x1, y0 + 4, z1, 3);
    }

    // ---------------------------------------------------------------- manor

    /** A ruined townhouse with a cellar, where the family silver is buried and the family is not. */
    public static void manor(Canvas c, int x0, int y0, int z0, ResourceLocation faction, RandomSource random) {
        int x1 = x0 + 14;
        int z1 = z0 + 10;
        c.clear(x0, y0, z0, x1, y0 + 12, z1);
        c.foundation(x0, z0, x1, z1, y0, Canvas.state(Blocks.COBBLESTONE));
        BlockState frame = Canvas.state(Blocks.DARK_OAK_LOG);
        BlockState plank = Canvas.state(Blocks.DARK_OAK_PLANKS);
        BlockState stone = Canvas.state(Blocks.STONE_BRICKS);
        // Cellar: interior y0-4..y0-1.
        c.room(x0 + 2, y0 - 5, z0 + 2, x1 - 2, y0 - 1, z1 - 2, stone);
        // Ground floor and upper floor shells.
        c.fill(x0, y0 - 1, z0, x1, y0 - 1, z1, Canvas.state(Blocks.SPRUCE_PLANKS));
        c.fill(x0, y0, z0, x1, y0 + 3, z1, plank);
        c.clear(x0 + 1, y0, z0 + 1, x1 - 1, y0 + 3, z1 - 1);
        c.fill(x0, y0 + 4, z0, x1, y0 + 4, z1, Canvas.state(Blocks.SPRUCE_PLANKS));
        c.fill(x0, y0 + 5, z0, x1, y0 + 8, z1, plank);
        c.clear(x0 + 1, y0 + 5, z0 + 1, x1 - 1, y0 + 8, z1 - 1);
        for (int x : new int[]{x0, x1}) {
            for (int z : new int[]{z0, z1}) {
                c.fill(x, y0, z, x, y0 + 8, z, frame);
            }
        }
        // Windows and a door.
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            for (int y : new int[]{y0 + 1, y0 + 6}) {
                c.set(x, y, z0, Canvas.state(Blocks.GLASS_PANE));
                c.set(x, y, z1, Canvas.state(Blocks.GLASS_PANE));
            }
        }
        c.clear(x0 + 7, y0, z0, x0 + 7, y0 + 1, z0);
        c.set(x0 + 7, y0 + 2, z0, Canvas.state(Blocks.DARK_OAK_PLANKS));
        // Roof: stepped dark oak stairs along the long axis.
        for (int i = 0; i < 6; i++) {
            c.fill(x0 - 1, y0 + 9 + i, z0 + i - 1, x1 + 1, y0 + 9 + i, z0 + i - 1, Canvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
            c.fill(x0 - 1, y0 + 9 + i, z1 - i + 1, x1 + 1, y0 + 9 + i, z1 - i + 1, Canvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
        }
        c.fill(x0 - 1, y0 + 14, z0 + 5, x1 + 1, y0 + 14, z0 + 5, Canvas.state(Blocks.DARK_OAK_SLAB));
        // Stair from hall to the upper floor and a ladder down to the cellar.
        for (int i = 0; i < 4; i++) {
            c.set(x1 - 2, y0 + i, z0 + 2 + i, Canvas.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        }
        c.clear(x1 - 2, y0 + 4, z0 + 5, x1 - 2, y0 + 4, z0 + 7);
        c.clear(x0 + 3, y0 - 1, z0 + 3, x0 + 3, y0 - 1, z0 + 3);
        for (int y = y0 - 4; y <= y0; y++) {
            c.set(x0 + 3, y, z0 + 3, Canvas.state(Blocks.LADDER).setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
        }
        // Furnishings: a hall table, bookshelves, bedrooms above.
        c.fill(x0 + 5, y0, z0 + 4, x0 + 9, y0, z0 + 4, Canvas.state(Blocks.DARK_OAK_SLAB));
        c.fill(x0 + 1, y0, z1 - 1, x0 + 4, y0 + 2, z1 - 1, Canvas.state(Blocks.BOOKSHELF));
        c.set(x0 + 11, y0, z1 - 1, Canvas.state(Blocks.FURNACE));
        c.set(x0 + 2, y0 + 5, z0 + 2, Canvas.state(Blocks.RED_BED).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH));
        c.set(x0 + 2, y0 + 5, z0 + 3, Canvas.state(Blocks.RED_BED).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        c.set(x0 + 11, y0 + 5, z1 - 1, Canvas.state(Blocks.BOOKSHELF));
        // Cellar: the coffin, a chest, wine.
        c.coffin(x0 + 8, y0 - 4, z0 + 4, Direction.SOUTH, faction);
        c.set(x0 + 8, y0 - 3, z0 + 4, Canvas.state(Blocks.CANDLE));
        c.chest(x1 - 3, y0 - 4, z1 - 3, Direction.NORTH, LOOT, random);
        c.fill(x0 + 4, y0 - 4, z1 - 3, x0 + 6, y0 - 4, z1 - 3, Canvas.state(Blocks.BARREL));
        c.set(x0 + 7, y0 - 2, z0 + 6, Canvas.state(Blocks.SOUL_LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        c.set(x0 + 7, y0 - 1, z0 + 6, Canvas.state(Blocks.CHAIN));
        weather(c, x0, y0 + 1, z0, x1, y0 + 13, z1, 9);
    }

    // ---------------------------------------------------------------- warehouse

    /** A disused warehouse taken over by a gang: crates, a loft office, and a coffin behind the manager's desk. */
    private void warehouse(Canvas c, int x0, int y0, int z0, RandomSource random) {
        int x1 = x0 + 16;
        int z1 = z0 + 10;
        c.clear(x0, y0, z0, x1, y0 + 8, z1);
        c.foundation(x0, z0, x1, z1, y0, Canvas.state(Blocks.STONE));
        c.fill(x0, y0 - 1, z0, x1, y0 - 1, z1, Canvas.state(Blocks.SMOOTH_STONE));
        BlockState wall = Canvas.state(Blocks.STONE_BRICKS);
        for (int y = y0; y <= y0 + 5; y++) {
            c.walls(x0, y, z0, x1, y, z1, y >= y0 + 4 ? Canvas.state(Blocks.GRAY_CONCRETE) : wall);
        }
        c.fill(x0, y0 + 6, z0, x1, y0 + 6, z1, Canvas.state(Blocks.SMOOTH_STONE_SLAB));
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            c.set(x, y0 + 4, z0, Canvas.state(Blocks.IRON_BARS));
            c.set(x, y0 + 4, z1, Canvas.state(Blocks.IRON_BARS));
        }
        c.clear(x0 + 8, y0, z0, x0 + 9, y0 + 2, z0); // big door
        c.clear(x1, y0, z0 + 5, x1, y0 + 1, z0 + 5); // back door
        // Graffiti: coloured patches on the long walls.
        for (int x = x0 + 1; x < x1; x++) {
            for (int y = y0 + 1; y <= y0 + 3; y++) {
                if (c.chance(x, y, 1, 9) && c.at(x, y, z1).is(Blocks.STONE_BRICKS)) {
                    c.set(x, y, z1, Canvas.state(c.roll(x, y, 2, 2) == 0 ? Blocks.RED_TERRACOTTA : Blocks.BLACK_TERRACOTTA));
                }
            }
        }
        // Crates and barrels on the floor.
        for (int i = 0; i < 10; i++) {
            int x = x0 + 2 + c.roll(i, 1, 1, 8);
            int z = z0 + 2 + c.roll(i, 2, 2, 7);
            c.set(x, y0, z, Canvas.state(c.roll(i, 3, 3, 2) == 0 ? Blocks.BARREL : Blocks.SPRUCE_PLANKS));
            if (c.chance(i, 4, 4, 40)) {
                c.set(x, y0 + 1, z, Canvas.state(Blocks.BARREL));
            }
        }
        // Loft office at the back (x1-6..x1-1, y0+3 floor).
        c.fill(x1 - 6, y0 + 3, z0 + 1, x1 - 1, y0 + 3, z1 - 1, Canvas.state(Blocks.SPRUCE_PLANKS));
        c.fill(x1 - 6, y0 + 4, z0 + 1, x1 - 6, y0 + 5, z1 - 1, Canvas.state(Blocks.SPRUCE_FENCE));
        for (int y = y0; y <= y0 + 3; y++) {
            c.set(x1 - 7, y, z0 + 2, Canvas.state(Blocks.LADDER).setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
        }
        c.clear(x1 - 7, y0 + 3, z0 + 2, x1 - 6, y0 + 3, z0 + 2);
        c.coffin(x1 - 3, y0 + 4, z1 - 2, Direction.WEST, faction);
        c.set(x1 - 3, y0 + 5, z1 - 2, Canvas.state(Blocks.CANDLE));
        c.chest(x1 - 2, y0 + 4, z0 + 2, Direction.WEST, LOOT, random);
        c.set(x1 - 4, y0 + 4, z0 + 3, Canvas.state(Blocks.CRAFTING_TABLE));
        c.set(x1 - 3, y0 + 5, z0 + 3, Canvas.state(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, false));
        c.set(x0 + 4, y0 + 5, z0 + 4, Canvas.state(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        c.set(x0 + 4, y0 + 6, z0 + 4, Canvas.state(Blocks.CHAIN));
        weather(c, x0, y0 + 1, z0, x1, y0 + 5, z1, 2);
    }
}
