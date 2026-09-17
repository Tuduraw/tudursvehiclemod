package com.example.tudursvehiclemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FuelRefinerBlock extends BlockWithEntity {

	public static final MapCodec<FuelRefinerBlock> CODEC = createCodec(FuelRefinerBlock::new);

	public FuelRefinerBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected net.minecraft.block.BlockRenderType getRenderType(BlockState state) {
		return net.minecraft.block.BlockRenderType.MODEL;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new FuelRefinerBlockEntity(pos, state);
	}

	// Reuses BlockWithEntity's own protected static validateTicker(..) helper (defining another one here with the same erasure would conflict with it).
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, com.example.tudursvehiclemod.registry.ModBlockEntities.FUEL_REFINER,
				FuelRefinerBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!(world instanceof ServerWorld)) {
			return ActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof FuelRefinerBlockEntity blockEntity
				&& player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
			serverPlayer.openHandledScreen(blockEntity);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos, net.minecraft.util.math.Direction direction) {
		if (world.getBlockEntity(pos) instanceof FuelRefinerBlockEntity blockEntity) {
			return Math.round(blockEntity.getStoredBurnTimeRatio() * 15f);
		}
		return 0;
	}
}
