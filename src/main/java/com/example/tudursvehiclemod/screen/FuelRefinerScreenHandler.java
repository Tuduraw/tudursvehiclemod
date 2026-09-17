package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.block.FuelRefinerBlockEntity;
import com.example.tudursvehiclemod.item.FuelCanItem;
import com.example.tudursvehiclemod.block.FuelValues;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.MathHelper;

/** See block.FuelRefinerBlockEntity's own doc for what slot 0/1 do. */
public class FuelRefinerScreenHandler extends ScreenHandler {

	private final Inventory inventory;
	private final PropertyDelegate propertyDelegate;

	/** Client-side constructor (see registry.ModScreenHandlers' own factory registration). */
	public FuelRefinerScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(2), new ArrayPropertyDelegate(1));
	}

	public FuelRefinerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory,
			PropertyDelegate propertyDelegate) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.FUEL_REFINER, syncId);
		this.inventory = inventory;
		this.propertyDelegate = propertyDelegate;
		inventory.onOpen(playerInventory.player);

		this.addSlot(new Slot(inventory, 0, 62, 24) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return FuelValues.isFuel(stack);
			}
		});
		this.addSlot(new Slot(inventory, 1, 62, 52) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof FuelCanItem;
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}

		this.addProperties(propertyDelegate);
	}

	/** 0.0-1.0, for the screen's own stored-burn-time progress bar. */
	public float getStoredBurnTimeRatio() {
		return MathHelper.clamp(this.propertyDelegate.get(0) / (float) FuelRefinerBlockEntity.MAX_STORED_BURN_TIME, 0f, 1f);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return this.inventory.canPlayerUse(player);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasStack()) {
			ItemStack original = slot.getStack();
			result = original.copy();
			if (index < 2) {
				// From the device's own slots into the player's inventory.
				if (!this.insertItem(original, 2, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				// From the player's inventory into whichever device slot fits.
				if (FuelValues.isFuel(original)) {
					if (!this.insertItem(original, 0, 1, false)) {
						return ItemStack.EMPTY;
					}
				} else if (original.getItem() instanceof FuelCanItem) {
					if (!this.insertItem(original, 1, 2, false)) {
						return ItemStack.EMPTY;
					}
				} else {
					return ItemStack.EMPTY;
				}
			}
			if (original.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}
		}
		return result;
	}
}
