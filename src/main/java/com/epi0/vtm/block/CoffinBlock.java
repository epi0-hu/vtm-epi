package com.epi0.vtm.block;

import com.epi0.vtm.npc.VtmNpc;
import com.epi0.vtm.registry.ModBlocks;
import com.epi0.vtm.vampire.VampireData;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A vampire's bed. A vampire who lies down in one by day sleeps until dusk, and wakes with it as their haven.
 * Structures place them with a faction, which makes them the resting place of a named vampire.
 */
public class CoffinBlock extends BaseEntityBlock {
    public static final MapCodec<CoffinBlock> CODEC = simpleCodec(CoffinBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape NORTH_SOUTH = box(2, 0, 0, 14, 9, 16);
    private static final VoxelShape EAST_WEST = box(0, 0, 2, 16, 9, 14);

    public CoffinBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoffinBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlocks.COFFIN_ENTITY.get(), CoffinBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel server)) {
            return InteractionResult.CONSUME;
        }
        if (!VampireData.of(player).isVampire()) {
            player.displayClientMessage(Component.translatable("message.vtm.coffin.mortal"), true);
            return InteractionResult.CONSUME;
        }
        serverPlayer.setRespawnPosition(level.dimension(), pos, 0F, false, true);
        if (VtmNpc.isNight(level)) {
            player.displayClientMessage(Component.translatable("message.vtm.coffin.night"), true);
        } else if (!level.getEntitiesOfClass(Monster.class, new net.minecraft.world.phys.AABB(pos).inflate(8)).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.vtm.coffin.unsafe"), true);
        } else {
            server.setDayTime(server.getDayTime() - server.getDayTime() % 24000L + 12500L);
            player.displayClientMessage(Component.translatable("message.vtm.coffin.slept"), true);
        }
        return InteractionResult.CONSUME;
    }
}
