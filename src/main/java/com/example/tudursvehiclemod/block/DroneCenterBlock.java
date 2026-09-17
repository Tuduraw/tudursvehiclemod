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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Target drone control center block (/ block.DroneCenterBlockEntity's own doc). */
public class DroneCenterBlock extends BlockWithEntity {

	public static final MapCodec<DroneCenterBlock> CODEC = createCodec(DroneCenterBlock::new);

	public DroneCenterBlock(Settings settings) {
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
		return new DroneCenterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, com.example.tudursvehiclemod.registry.ModBlockEntities.DRONE_CENTER, DroneCenterBlockEntity::tick);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, net.minecraft.entity.LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		if (world.getBlockEntity(pos) instanceof DroneCenterBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$updateChunkForceLoading(true);
			if (world instanceof ServerWorld serverWorld) {
				blockEntity.tudursvehiclemod$onRedstonePowerChanged(serverWorld, world.isReceivingRedstonePower(pos));
			}
		}
	}

	/** Reacts to any redstone power change at this position - a genuine no-op (via tudursvehiclemod$onRedstonePowerChanged()'s own early return) unless the block entity's own redstone-control mode is currently enabled. */
	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, net.minecraft.block.Block sourceBlock,
			net.minecraft.world.block.WireOrientation wireOrientation, boolean notify) {
		super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
		if (world instanceof ServerWorld serverWorld && world.getBlockEntity(pos) instanceof DroneCenterBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$onRedstonePowerChanged(serverWorld, world.isReceivingRedstonePower(pos));
		}
	}

	@Override
	public void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		if (world.getBlockEntity(pos) instanceof DroneCenterBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$updateChunkForceLoading(false);
			blockEntity.tudursvehiclemod$releaseForcedChunk(world);
			if (DroneCenterBlockEntity.tudursvehiclemod$findBoundVehicle(world, blockEntity.tudursvehiclemod$getBoundVehicleId())
					instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
				vehicle.tudursvehiclemod$setDroneLink(null);
			}
			// Scatters both slots (stick, route book) rather than just the one that used to exist.
			for (int slot = 0; slot < blockEntity.size(); slot++) {
				ItemStack stack = blockEntity.getStack(slot);
				if (!stack.isEmpty()) {
					net.minecraft.util.ItemScatterer.spawn(world, pos.getX(), pos.getY() + 1, pos.getZ(), stack);
				}
			}
		}
		super.onStateReplaced(state, world, pos, moved);
	}

	/** Right-click (sneaking or not, identically) always opens this block's own config screen (client.screen.DroneCenterConfigScreen - altitude/speed/turn-radius adjustment, an activate/deactivate toggle, and an "Item Slots" button that opens the slot UI instead - see that screen's own doc). */
	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		return this.tudursvehiclemod$handleUse(state, world, pos, player, hit);
	}

	/** This block's own interaction (opening either the slot UI or the config screen - see onUse()'s own doc) only ever worked while empty-handed: Minecraft calls THIS method first whenever the interacting player is holding any item at all, only falling through to the plain onUse() above when empty-handed - without overriding it too, held-item interactions never reached this block's own logic at all. Delegates to the exact same handling regardless of whatever item (if any) is held, since none of that logic actually depends on the held stack. */
	@Override
	protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, net.minecraft.util.Hand hand, BlockHitResult hit) {
		return this.tudursvehiclemod$handleUse(state, world, pos, player, hit);
	}

	private ActionResult tudursvehiclemod$handleUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		// A remnant of the earlier "reach this handler even while sneaking" fix (see tudursvehiclemod$registerUseBlockCallback()'s own doc) that no longer applies.
		if (player.isSneaking()) {
			return ActionResult.PASS;
		}
		if (!(world instanceof ServerWorld serverWorld) || !(world.getBlockEntity(pos) instanceof DroneCenterBlockEntity blockEntity)) {
			return ActionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.SUCCESS;
		}

		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
				new com.example.tudursvehiclemod.network.DroneCenterConfigOpenPayload(
						pos.getX(), pos.getY(), pos.getZ(),
						blockEntity.tudursvehiclemod$getSpeedFraction(),
						(float) blockEntity.tudursvehiclemod$getOrbitAltitude(),
						blockEntity.tudursvehiclemod$getRadiusMultiplier(),
						blockEntity.tudursvehiclemod$isActive(),
						blockEntity.tudursvehiclemod$isRedstoneControlEnabled(),
						blockEntity.tudursvehiclemod$encodeFormationConfig()));
		return ActionResult.SUCCESS;
	}

	/** Confirmed via decompiling ServerPlayerInteractionManager for this exact Minecraft version: sneaking while holding any item at all - even in the off-hand - bypasses onUse()/onUseWithItem() entirely, unconditionally, regardless of block or item type: net.fabricmc.fabric.api.event.player.UseBlockCallback is injected at the very head of that same vanilla method, before this bypass check, so registering a listener there reaches this block's own interaction in every case. Called once from VehicleMod's own onInitialize(). */
	public static void tudursvehiclemod$registerUseBlockCallback() {
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof DroneCenterBlock droneCenterBlock)) {
				return ActionResult.PASS;
			}
			return droneCenterBlock.tudursvehiclemod$handleUse(state, world, pos, player, hitResult);
		});
	}
}
