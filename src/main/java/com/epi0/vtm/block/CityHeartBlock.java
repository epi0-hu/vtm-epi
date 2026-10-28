package com.epi0.vtm.block;

import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.registry.ModBlocks;
import com.epi0.vtm.registry.VtmRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** The monument at the centre of a city. Touching it tells you whose city it is. */
public class CityHeartBlock extends BaseEntityBlock {
    public static final MapCodec<CityHeartBlock> CODEC = simpleCodec(CityHeartBlock::new);

    public CityHeartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CityHeartBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlocks.CITY_HEART_ENTITY.get(), CityHeartBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            City city = CityData.get(server.getServer()).at(level, pos);
            if (city != null) {
                player.displayClientMessage(Component.translatable("message.vtm.city.stone", city.name,
                        VtmRegistries.name("faction", city.owner), city.strength), false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
