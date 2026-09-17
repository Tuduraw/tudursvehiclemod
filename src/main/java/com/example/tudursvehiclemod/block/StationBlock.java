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
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** UAV control station block (/ "右クリック時の挙動"). */
public class StationBlock extends BlockWithEntity {

	public static final MapCodec<StationBlock> CODEC = createCodec(StationBlock::new);

	public StationBlock(Settings settings) {
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
		return new StationBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, com.example.tudursvehiclemod.registry.ModBlockEntities.STATION, StationBlockEntity::tick);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, net.minecraft.entity.LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		if (world.getBlockEntity(pos) instanceof StationBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$updateChunkForceLoading(true);
		}
	}

	@Override
	public void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		if (world.getBlockEntity(pos) instanceof StationBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$updateChunkForceLoading(false);
			// Checks this specific release path
			// carefully (it's hard to verify during actual play whether
			// a chunk got left permanently force-loaded or not) - see
			// StationBlockEntity's own tudursvehiclemod$releasePendingControlChunk()
			// doc for exactly why this explicit call, right here, is
			// required at all (this block entity's own tick() - where
			// that same release would otherwise eventually happen
			// automatically - never runs again after this point).
			blockEntity.tudursvehiclemod$releasePendingControlChunk(world);
			if (StationBlockEntity.tudursvehiclemod$findBoundVehicle(world, blockEntity.tudursvehiclemod$getBoundVehicleId())
					instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
				vehicle.tudursvehiclemod$tryExitRemoteControl();
			}
			// Real slot UI (see StationBlockEntity's own doc).
			ItemStack insertedStick = blockEntity.getStack(0);
			if (!insertedStick.isEmpty()) {
				net.minecraft.util.ItemScatterer.spawn(world, pos.getX(), pos.getY() + 1, pos.getZ(), insertedStick);
			}
		}
		super.onStateReplaced(state, world, pos, moved);
	}

	/** The previous sneak-right-click access to the item slot container UI was removed entirely (sneaking while holding any item bypasses block interaction unconditionally in vanilla Minecraft - see tudursvehiclemod$registerUseBlockCallback()'s own doc), consolidating the remote-control toggle and the item slots into one reachable screen: right-click (sneaking or not, identically) always opens client.screen.StationMenuScreen, which offers a "Toggle Remote Control" button (running the exact logic that used to fire immediately on right-click - see tudursvehiclemod$handleToggleRemoteControl()'s own doc) and an "Item Slots" button. */
	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		return this.tudursvehiclemod$handleUse(state, world, pos, player, hit);
	}

	/** This block's own interaction (opening the station menu screen - see onUse()'s own doc) only ever worked while empty-handed: Minecraft calls THIS method first whenever the interacting player is holding any item at all, only falling through to the plain onUse() above when empty-handed - without overriding it too, held-item interactions never reached this block's own logic at all. Delegates to the exact same handling regardless of whatever item (if any) is held, since none of that logic actually depends on the held stack. */
	@Override
	protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, net.minecraft.util.Hand hand, BlockHitResult hit) {
		return this.tudursvehiclemod$handleUse(state, world, pos, player, hit);
	}

	private ActionResult tudursvehiclemod$handleUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		// A remnant of the earlier "reach this handler even while sneaking" fix (see tudursvehiclemod$registerUseBlockCallback()'s own doc) that no longer applies.
		if (player.isSneaking()) {
			return ActionResult.PASS;
		}
		if (!(world instanceof ServerWorld) || !(world.getBlockEntity(pos) instanceof StationBlockEntity)) {
			return ActionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.SUCCESS;
		}
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
				new com.example.tudursvehiclemod.network.StationMenuOpenPayload(pos.getX(), pos.getY(), pos.getZ()));
		return ActionResult.SUCCESS;
	}

	/** Runs the same remote-control-toggle logic this block's own right-click used to run immediately, before that was replaced with client.screen.StationMenuScreen's own "Toggle Remote Control" button (see this class's own onUse() doc) - called from the network.StationToggleRemoteControlPayload's own handler. Re-validates the block entity and world from scratch rather than trusting anything about the client's own screen state. */
	public static void tudursvehiclemod$handleToggleRemoteControl(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, BlockPos pos) {
		if (!(serverWorld.getBlockEntity(pos) instanceof StationBlockEntity blockEntity)) {
			return;
		}

		java.util.UUID boundVehicleId = blockEntity.tudursvehiclemod$getBoundVehicleId();
		if (boundVehicleId == null) {
			serverPlayer.sendMessage(Text.translatable("block.tudursvehiclemod.station.no_vehicle_bound"), true);
			return;
		}

		// Per this class's own doc: the SAME button toggles both directions - exiting if this exact player is ALREADY remote-controlling the bound vehicle through this exact station right now.
		if (StationBlockEntity.tudursvehiclemod$findBoundVehicle(serverWorld, boundVehicleId)
				instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity alreadyControlledVehicle
				&& alreadyControlledVehicle.tudursvehiclemod$getRemoteControllerId() != null
				&& alreadyControlledVehicle.tudursvehiclemod$getRemoteControllerId().equals(serverPlayer.getUuid())) {
			alreadyControlledVehicle.tudursvehiclemod$tryExitRemoteControl();
			serverPlayer.sendMessage(Text.translatable("block.tudursvehiclemod.station.control_ended"), true);
			return;
		}

		// Rather than only ever trying the bound
		// vehicle's own CURRENT in-memory status once and giving up
		// immediately if it's not already found (which is exactly why
		// a distant vehicle could never actually be reached at all
		// before this change) - see StationBlockEntity's own
		// tudursvehiclemod$tryStartControlAttempt() doc for the full
		// on-demand chunk-loading/retry design this now goes through
		// instead.
		StationBlockEntity.Result result = blockEntity.tudursvehiclemod$tryStartControlAttempt(serverWorld, serverPlayer, pos);
		switch (result) {
			case SUCCESS -> serverPlayer.sendMessage(Text.translatable("block.tudursvehiclemod.station.control_started"), true);
			case CONTROL_UNAVAILABLE -> serverPlayer.sendMessage(Text.translatable("block.tudursvehiclemod.station.control_unavailable"), true);
			case NO_VEHICLE_BOUND -> serverPlayer.sendMessage(Text.translatable("block.tudursvehiclemod.station.no_vehicle_bound"), true);
			case NEVER_SEEN -> serverPlayer.sendMessage(Text.translatable("block.tudursvehiclemod.station.vehicle_not_found"), true);
			case PENDING -> serverPlayer.sendMessage(Text.translatable("block.tudursvehiclemod.station.control_pending"), true);
			case ALREADY_PENDING -> serverPlayer.sendMessage(Text.translatable("block.tudursvehiclemod.station.control_pending"), true);
		}
	}

	/** Confirmed via decompiling ServerPlayerInteractionManager for this exact Minecraft version: sneaking while holding any item at all - even in the off-hand - bypasses onUse()/onUseWithItem() entirely, unconditionally, regardless of block or item type. net.fabricmc.fabric.api.event.player.UseBlockCallback is injected at the very head of that same vanilla method, before this bypass check, so registering a listener there reaches this block's own interaction in every case. Called once from VehicleMod's own onInitialize(). */
	public static void tudursvehiclemod$registerUseBlockCallback() {
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof StationBlock stationBlock)) {
				return ActionResult.PASS;
			}
			return stationBlock.tudursvehiclemod$handleUse(state, world, pos, player, hitResult);
		});
	}
}
