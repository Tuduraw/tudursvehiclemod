package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.item.DroneControlStickItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/** See block.StationBlockEntity's own doc for what slot 0 does (binding stick). */
public class StationScreenHandler extends ScreenHandler {

	private final Inventory inventory;

	/** Client-side constructor (see registry.ModScreenHandlers' own factory registration). */
	public StationScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(1));
	}

	public StationScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.STATION, syncId);
		this.inventory = inventory;
		inventory.onOpen(playerInventory.player);

		this.addSlot(new Slot(inventory, 0, 80, 35) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof DroneControlStickItem;
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
			if (index == 0) {
				// From the device's own slot into the player's inventory.
				if (!this.insertItem(original, 1, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				// From the player's inventory into the device's own slot, if it fits.
				if (original.getItem() instanceof DroneControlStickItem) {
					if (!this.insertItem(original, 0, 1, false)) {
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
