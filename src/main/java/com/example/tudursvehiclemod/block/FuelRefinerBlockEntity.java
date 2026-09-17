package com.example.tudursvehiclemod.block;

import com.example.tudursvehiclemod.item.FuelCanItem;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/** Slot 0: any furnace-style fuel item (coal, a lava bucket,..). */
public class FuelRefinerBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {

	/** A generous cap - a stack of 64 coal (1600 ticks each) is 102,400, so this comfortably holds a full stack's worth without needing to be emptied constantly. */
	public static final int MAX_STORED_BURN_TIME = 128_000;
	/** How much stored burn time converts to 1 unit of can fuel per tick. */
	private static final int BURN_TIME_PER_FUEL_UNIT = 4;
	/** Multiplies the fuel units actually produced per unit of burn time consumed, without changing how much burn time gets consumed for that output. */
	private static final int FUEL_YIELD_MULTIPLIER = 10;
	/** How many units of can fuel transfer per tick while a can sits in slot 1. */
	private static final int FUEL_TRANSFER_PER_TICK = 20;

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);
	private int storedBurnTime;

	public FuelRefinerBlockEntity(BlockPos pos, net.minecraft.block.BlockState state) {
		super(com.example.tudursvehiclemod.registry.ModBlockEntities.FUEL_REFINER, pos, state);
	}

	public static void tick(net.minecraft.world.World world, BlockPos pos, net.minecraft.block.BlockState state,
			FuelRefinerBlockEntity blockEntity) {
		if (world.isClient()) {
			return;
		}
		boolean dirty = false;

		ItemStack fuelSlot = blockEntity.items.get(0);
		if (blockEntity.storedBurnTime < MAX_STORED_BURN_TIME && !fuelSlot.isEmpty()
				&& FuelValues.isFuel(fuelSlot)) {
			int fuelTime = FuelValues.getFuelTicks(fuelSlot);
			if (fuelTime > 0) {
				blockEntity.storedBurnTime = Math.min(MAX_STORED_BURN_TIME, blockEntity.storedBurnTime + fuelTime);
				fuelSlot.decrement(1);
				dirty = true;
			}
		}

		ItemStack canSlot = blockEntity.items.get(1);
		if (blockEntity.storedBurnTime > 0 && !canSlot.isEmpty() && canSlot.getItem() instanceof FuelCanItem) {
			int canFuel = FuelCanItem.getFuel(canSlot);
			if (canFuel < FuelCanItem.MAX_FUEL) {
				int burnTimeAvailable = Math.min(blockEntity.storedBurnTime, FUEL_TRANSFER_PER_TICK * BURN_TIME_PER_FUEL_UNIT);
				int fuelUnitsToAdd = (burnTimeAvailable / BURN_TIME_PER_FUEL_UNIT) * FUEL_YIELD_MULTIPLIER;
				fuelUnitsToAdd = Math.min(fuelUnitsToAdd, FuelCanItem.MAX_FUEL - canFuel);
				if (fuelUnitsToAdd > 0) {
					// Burn time consumed is based on the UNMULTIPLIED fuel units (fuelUnitsToAdd / FUEL_YIELD_MULTIPLIER, i.e.
					blockEntity.storedBurnTime -= (fuelUnitsToAdd / FUEL_YIELD_MULTIPLIER) * BURN_TIME_PER_FUEL_UNIT;
					FuelCanItem.setFuel(canSlot, canFuel + fuelUnitsToAdd);
					dirty = true;
				}
			}
		}

		if (dirty) {
			blockEntity.markDirty();
		}
	}

	/** 0.0-1.0, for the screen's own progress bar. */
	public float getStoredBurnTimeRatio() {
		return MathHelper.clamp((float) this.storedBurnTime / MAX_STORED_BURN_TIME, 0f, 1f);
	}

	@Override
	public int size() {
		return this.items.size();
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStack(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack result = Inventories.splitStack(this.items, slot, amount);
		if (!result.isEmpty()) {
			this.markDirty();
		}
		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(this.items, slot);
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
	public boolean isValid(int slot, ItemStack stack) {
		if (slot == 0) {
			return FuelValues.isFuel(stack);
		}
		if (slot == 1) {
			return stack.getItem() instanceof FuelCanItem;
		}
		return false;
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return this.world != null && this.world.getBlockEntity(this.pos) == this
				&& player.squaredDistanceTo(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clear() {
		this.items.clear();
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		Inventories.readData(view, this.items);
		this.storedBurnTime = view.getInt("StoredBurnTime", 0);
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		Inventories.writeData(view, this.items, true);
		view.putInt("StoredBurnTime", this.storedBurnTime);
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.tudursvehiclemod.fuel_refiner");
	}

	@Override
	public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, PlayerEntity player) {
		return new com.example.tudursvehiclemod.screen.FuelRefinerScreenHandler(syncId, playerInventory, this,
				this.tudursvehiclemod$createPropertyDelegate());
	}

	private PropertyDelegate tudursvehiclemod$createPropertyDelegate() {
		return new PropertyDelegate() {
			@Override
			public int get(int index) {
				return index == 0 ? FuelRefinerBlockEntity.this.storedBurnTime : 0;
			}

			@Override
			public void set(int index, int value) {
				if (index == 0) {
					FuelRefinerBlockEntity.this.storedBurnTime = value;
				}
			}

			@Override
			public int size() {
				return 1;
			}
		};
	}
}
