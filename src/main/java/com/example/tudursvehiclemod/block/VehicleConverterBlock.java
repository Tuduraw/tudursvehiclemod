package com.example.tudursvehiclemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Converts a tier-based item.TieredVehicleBaseItem into a specific item.TieredVehicleSpawnerItem - see block.VehicleConverterBlockEntity's own doc for the full conversion flow. Crafted from a crafting table surrounded by gunpowder (see recipe/vehicle_converter.json). */
public class VehicleConverterBlock extends BlockWithEntity {

	public static final MapCodec<VehicleConverterBlock> CODEC = createCodec(VehicleConverterBlock::new);

	public VehicleConverterBlock(Settings settings) {
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
		return new VehicleConverterBlockEntity(pos, state);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!(world instanceof ServerWorld)) {
			return ActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof VehicleConverterBlockEntity blockEntity
				&& player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
			serverPlayer.openHandledScreen(blockEntity);
		}
		return ActionResult.SUCCESS;
	}
}
