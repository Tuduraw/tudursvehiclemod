package com.example.tudursvehiclemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Fire Support Center - see FireSupportCenterBlockEntity's own doc. Right-click opens its screen (unit and book slots plus every setting); sneak-right-click with a Fire Support Designator links the designator to it instead (FireSupportDesignatorItem.useOnBlock()). */
public class FireSupportCenterBlock extends BlockWithEntity {

	public static final MapCodec<FireSupportCenterBlock> CODEC = createCodec(FireSupportCenterBlock::new);

	public FireSupportCenterBlock(Settings settings) {
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
		return new FireSupportCenterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient() ? null
				: validateTicker(type, com.example.tudursvehiclemod.registry.ModBlockEntities.FIRE_SUPPORT_CENTER, FireSupportCenterBlockEntity::tick);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, net.minecraft.entity.LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		if (world instanceof ServerWorld serverWorld && world.getBlockEntity(pos) instanceof FireSupportCenterBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$onRedstonePowerChanged(serverWorld, world.isReceivingRedstonePower(pos));
		}
	}

	/** Redstone control - a no-op unless the center has it enabled (see FireSupportCenterBlockEntity.tudursvehiclemod$onRedstonePowerChanged()). */
	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, net.minecraft.block.Block sourceBlock,
			net.minecraft.world.block.WireOrientation wireOrientation, boolean notify) {
		super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
		if (world instanceof ServerWorld serverWorld && world.getBlockEntity(pos) instanceof FireSupportCenterBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$onRedstonePowerChanged(serverWorld, world.isReceivingRedstonePower(pos));
		}
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		return this.tudursvehiclemod$openScreen(world, pos, player);
	}

	/** Called (instead of onUse()) while holding any item. Sneaking with an item never gets here at all - vanilla hands that straight to the item, which is how the designator links itself. */
	@Override
	protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		return this.tudursvehiclemod$openScreen(world, pos, player);
	}

	private ActionResult tudursvehiclemod$openScreen(World world, BlockPos pos, PlayerEntity player) {
		if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer
				&& world.getBlockEntity(pos) instanceof FireSupportCenterBlockEntity blockEntity) {
			serverPlayer.openHandledScreen(blockEntity);
			com.example.tudursvehiclemod.network.FireSupportNetworking.tudursvehiclemod$sendState(serverPlayer, serverWorld, blockEntity);
		}
		return ActionResult.SUCCESS;
	}
}
