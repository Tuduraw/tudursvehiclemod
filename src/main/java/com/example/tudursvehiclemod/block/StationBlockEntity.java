package com.example.tudursvehiclemod.block;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.UUID;

	/** UAV control station (project-specific, not from MC Heli).
 * Has a real inventory slot (see screen.StationScreenHandler) for the
 * binding stick, matching block.DroneCenterBlockEntity's own equivalent redesign - rather
 * than the earlier sneak-click insert/extract mechanic. This station's own "bound vehicle"
 * is derived directly from whatever registered stick currently sits in slot 0 (see
 * tudursvehiclemod$getBoundVehicleId()) rather than tracked as a separate field that needed
 * manual bookkeeping to stay in sync with the slot. */
public class StationBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, Inventory {

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);

	/** Bound vehicle's last-known chunk, persisted to disk - the only thing ever force-loaded now. */
	private ChunkPos lastKnownVehicleChunk;

	/** Player currently waiting on a pending control attempt to resolve, or null if none in progress. */
	private UUID pendingControlPlayerId;
	/** Ticks waited so far for the pending attempt's temporarily-forced chunk to load the vehicle back in. */
	private int pendingControlTicksWaited;
	/** Chunk temporarily force-loaded for the currently-pending attempt - remembered separately from lastKnownVehicleChunk so it can be un-forced correctly even if that's since changed. */
	private ChunkPos pendingControlForcedChunk;
	/** How long a pending control attempt is given before giving up (100 ticks = 5 real seconds). */
	private static final int PENDING_CONTROL_TIMEOUT_TICKS = 100;

	public StationBlockEntity(BlockPos pos, net.minecraft.block.BlockState state) {
		super(com.example.tudursvehiclemod.registry.ModBlockEntities.STATION, pos, state);
	}

	/** Derived directly from whatever registered stick currently sits in slot 0 (see this class's own doc) - null if slot 0 is empty, holds an unregistered stick, or holds something else entirely. */
	public UUID tudursvehiclemod$getBoundVehicleId() {
		ItemStack stick = this.items.get(0);
		return stick.getItem() instanceof com.example.tudursvehiclemod.item.DroneControlStickItem
				? com.example.tudursvehiclemod.item.DroneControlStickItem.getRegisteredVehicleId(stick) : null;
	}

	/** Called from StationBlock's onUse() when a player tries to control the bound vehicle. Mounts immediately if already found; otherwise force-loads lastKnownVehicleChunk and returns PENDING - tick() resolves it within PENDING_CONTROL_TIMEOUT_TICKS. */
	public Result tudursvehiclemod$tryStartControlAttempt(ServerWorld world, ServerPlayerEntity player, BlockPos stationPos) {
		UUID boundVehicleId = this.tudursvehiclemod$getBoundVehicleId();
		if (boundVehicleId == null) {
			return Result.NO_VEHICLE_BOUND;
		}
		if (this.pendingControlPlayerId != null) {
			return Result.ALREADY_PENDING;
		}
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle = tudursvehiclemod$findBoundVehicle(world, boundVehicleId);
		if (vehicle != null) {
			this.tudursvehiclemod$rememberVehicleChunk(vehicle);
			return vehicle.tudursvehiclemod$tryEnterRemoteControl(player, stationPos) ? Result.SUCCESS : Result.CONTROL_UNAVAILABLE;
		}
		if (this.lastKnownVehicleChunk == null) {
			return Result.NEVER_SEEN;
		}
		world.setChunkForced(this.lastKnownVehicleChunk.x, this.lastKnownVehicleChunk.z, true);
		this.pendingControlForcedChunk = this.lastKnownVehicleChunk;
		this.pendingControlPlayerId = player.getUuid();
		this.pendingControlTicksWaited = 0;
		return Result.PENDING;
	}

	/** Outcomes of tudursvehiclemod$tryStartControlAttempt() - StationBlock's onUse() maps each to a player message. PENDING gets a follow-up message once tick() resolves it. */
	public enum Result {
		SUCCESS, CONTROL_UNAVAILABLE, NO_VEHICLE_BOUND, NEVER_SEEN, PENDING, ALREADY_PENDING
	}

	private void tudursvehiclemod$rememberVehicleChunk(com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
		ChunkPos current = new ChunkPos(vehicle.getBlockPos());
		if (!current.equals(this.lastKnownVehicleChunk)) {
			this.lastKnownVehicleChunk = current;
			this.markDirty();
		}
	}

	/** Public so AbstractVehicleEntity's removePassenger() can update lastKnownVehicleChunk directly on dismount, recording the vehicle's actual final position (rememberVehicleChunk() above only runs when a control attempt starts, not ends). */
	public void tudursvehiclemod$updateLastKnownVehicleChunk(ChunkPos chunk) {
		if (!chunk.equals(this.lastKnownVehicleChunk)) {
			this.lastKnownVehicleChunk = chunk;
			this.markDirty();
		}
	}

	public static void tick(net.minecraft.world.World world, BlockPos pos, net.minecraft.block.BlockState state, StationBlockEntity blockEntity) {
		if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
			return;
		}
		if (blockEntity.pendingControlPlayerId == null) {
			return;
		}
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle =
				tudursvehiclemod$findBoundVehicle(serverWorld, blockEntity.tudursvehiclemod$getBoundVehicleId());
		if (vehicle != null) {
			blockEntity.tudursvehiclemod$rememberVehicleChunk(vehicle);
			blockEntity.tudursvehiclemod$finishPendingControlAttempt(serverWorld, serverPlayer ->
					serverPlayer.sendMessage(Text.translatable(vehicle.tudursvehiclemod$tryEnterRemoteControl(serverPlayer, blockEntity.getPos())
							? "block.tudursvehiclemod.station.control_started"
							: "block.tudursvehiclemod.station.control_unavailable"), true));
			return;
		}
		blockEntity.pendingControlTicksWaited++;
		if (blockEntity.pendingControlTicksWaited >= PENDING_CONTROL_TIMEOUT_TICKS) {
			blockEntity.tudursvehiclemod$finishPendingControlAttempt(serverWorld, serverPlayer ->
					serverPlayer.sendMessage(Text.translatable("block.tudursvehiclemod.station.vehicle_not_found"), true));
		}
	}

	private void tudursvehiclemod$finishPendingControlAttempt(ServerWorld world, java.util.function.Consumer<ServerPlayerEntity> notify) {
		if (this.pendingControlForcedChunk != null) {
			world.setChunkForced(this.pendingControlForcedChunk.x, this.pendingControlForcedChunk.z, false);
			this.pendingControlForcedChunk = null;
		}
		UUID playerId = this.pendingControlPlayerId;
		this.pendingControlPlayerId = null;
		this.pendingControlTicksWaited = 0;
		if (world.getServer().getPlayerManager().getPlayer(playerId) instanceof ServerPlayerEntity player) {
			notify.accept(player);
		}
	}

	/** Resolves this station's own bound vehicle UUID (see tudursvehiclemod$getBoundVehicleId()'s own doc) against world's own entity list, or null if unbound / that vehicle no longer actually exists. Server-only (a UUID lookup needs the server's own full entity list, not just whatever's client-side loaded/visible right now). */
	public static com.example.tudursvehiclemod.entity.AbstractVehicleEntity tudursvehiclemod$findBoundVehicle(ServerWorld world, UUID vehicleId) {
		if (vehicleId == null) {
			return null;
		}
		Entity entity = world.getEntity(vehicleId);
		return entity instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle ? vehicle : null;
	}

	/** Called when this station block is removed - releases any pending control attempt's temporarily-forced chunk, since tick() won't run again to do it. Usually a no-op (pending attempts only last a few ticks). */
	public void tudursvehiclemod$releasePendingControlChunk(ServerWorld world) {
		if (this.pendingControlForcedChunk != null) {
			world.setChunkForced(this.pendingControlForcedChunk.x, this.pendingControlForcedChunk.z, false);
			this.pendingControlForcedChunk = null;
		}
		this.pendingControlPlayerId = null;
		this.pendingControlTicksWaited = 0;
	}

	/** This station's own chunk stays loaded permanently for as long as the block entity exists, regardless of binding/control state. */
	public void tudursvehiclemod$updateChunkForceLoading(boolean forced) {
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			ChunkPos ownChunk = new ChunkPos(this.getPos());
			serverWorld.setChunkForced(ownChunk.x, ownChunk.z, forced);
		}
	}

	// --- NamedScreenHandlerFactory / Inventory (see this class's own doc) ---

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.tudursvehiclemod.station");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new com.example.tudursvehiclemod.screen.StationScreenHandler(syncId, playerInventory, this);
	}

	@Override
	public int size() {
		return this.items.size();
	}

	@Override
	public boolean isEmpty() {
		return this.items.get(0).isEmpty();
	}

	@Override
	public ItemStack getStack(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack result = net.minecraft.inventory.Inventories.splitStack(this.items, slot, amount);
		if (!result.isEmpty()) {
			this.markDirty();
		}
		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return net.minecraft.inventory.Inventories.removeStack(this.items, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		if (stack.getCount() > this.getMaxCountPerStack()) {
			stack.setCount(this.getMaxCountPerStack());
		}
		this.markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return this.getWorld() != null && this.getWorld().getBlockEntity(this.getPos()) == this
				&& player.squaredDistanceTo(this.getPos().getX() + 0.5, this.getPos().getY() + 0.5, this.getPos().getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clear() {
		this.items.clear();
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		Inventories.readData(view, this.items);
		int lastChunkX = view.getInt("LastKnownVehicleChunkX", Integer.MIN_VALUE);
		int lastChunkZ = view.getInt("LastKnownVehicleChunkZ", Integer.MIN_VALUE);
		this.lastKnownVehicleChunk = lastChunkX == Integer.MIN_VALUE ? null : new ChunkPos(lastChunkX, lastChunkZ);
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		Inventories.writeData(view, this.items, true);
		if (this.lastKnownVehicleChunk != null) {
			view.putInt("LastKnownVehicleChunkX", this.lastKnownVehicleChunk.x);
			view.putInt("LastKnownVehicleChunkZ", this.lastKnownVehicleChunk.z);
		}
	}
}
