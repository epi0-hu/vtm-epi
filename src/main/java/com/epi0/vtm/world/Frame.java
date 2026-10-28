package com.epi0.vtm.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A 14 x 14 plot seen from the front door. Buildings are drawn as if the door faced south ({@code u} runs west to
 * east, {@code v} from the back wall (0) to the front (13), {@code h} up from the floor); the frame turns that into
 * world coordinates for whichever way the door really faces, rotating stairs, beds and doors with it.
 */
public final class Frame {
    public static final int SIZE = 14;

    private final Canvas canvas;
    private final int x0;
    private final int y0;
    private final int z0;
    private final Rotation rotation;

    /** @param facing the direction the front door looks */
    public Frame(Canvas canvas, int x0, int y0, int z0, Direction facing) {
        this.canvas = canvas;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.rotation = switch (facing) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public int worldX(int u, int v) {
        return switch (rotation) {
            case NONE -> x0 + u;
            case CLOCKWISE_90 -> x0 + SIZE - 1 - v;
            case CLOCKWISE_180 -> x0 + SIZE - 1 - u;
            case COUNTERCLOCKWISE_90 -> x0 + v;
        };
    }

    public int worldZ(int u, int v) {
        return switch (rotation) {
            case NONE -> z0 + v;
            case CLOCKWISE_90 -> z0 + u;
            case CLOCKWISE_180 -> z0 + SIZE - 1 - v;
            case COUNTERCLOCKWISE_90 -> z0 + SIZE - 1 - u;
        };
    }

    public BlockPos pos(int u, int h, int v) {
        return new BlockPos(worldX(u, v), y0 + h, worldZ(u, v));
    }

    public Direction turn(Direction local) {
        return rotation.rotate(local);
    }

    public void set(int u, int h, int v, BlockState state) {
        canvas.set(worldX(u, v), y0 + h, worldZ(u, v), state.rotate(rotation));
    }

    public void set(int u, int h, int v, Block block) {
        set(u, h, v, block.defaultBlockState());
    }

    public void fill(int u1, int h1, int v1, int u2, int h2, int v2, BlockState state) {
        for (int u = u1; u <= u2; u++) {
            for (int h = h1; h <= h2; h++) {
                for (int v = v1; v <= v2; v++) {
                    set(u, h, v, state);
                }
            }
        }
    }

    public void fill(int u1, int h1, int v1, int u2, int h2, int v2, Block block) {
        fill(u1, h1, v1, u2, h2, v2, block.defaultBlockState());
    }

    public void clear(int u1, int h1, int v1, int u2, int h2, int v2) {
        fill(u1, h1, v1, u2, h2, v2, Blocks.AIR.defaultBlockState());
    }

    /** Four walls of a rectangle, floor to {@code h2}, with nothing inside or above. */
    public void walls(int u1, int h1, int v1, int u2, int h2, int v2, BlockState state) {
        for (int h = h1; h <= h2; h++) {
            fill(u1, h, v1, u2, h, v1, state);
            fill(u1, h, v2, u2, h, v2, state);
            fill(u1, h, v1, u1, h, v2, state);
            fill(u2, h, v1, u2, h, v2, state);
        }
    }

    public void walls(int u1, int h1, int v1, int u2, int h2, int v2, Block block) {
        walls(u1, h1, v1, u2, h2, v2, block.defaultBlockState());
    }

    /** A door whose front looks south in the frame (towards the street), two blocks tall at {@code (u, v)}. */
    public void door(int u, int v, Block door) {
        net.minecraft.world.level.block.state.BlockState lower = door.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.SOUTH);
        set(u, 0, v, lower);
        set(u, 1, v, lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
    }

    /** A bed with its foot at {@code (u, v)} and its head one block further back (towards v - 1). */
    public void bed(int u, int v, Block bed) {
        BlockState foot = bed.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH);
        set(u, 0, v, foot);
        set(u, 0, v - 1, foot.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD));
    }

    public Canvas canvas() {
        return canvas;
    }

    public BlockState stairs(Block block, Direction ascending, boolean upsideDown) {
        return Canvas.stairs(block, ascending, upsideDown);
    }
}
