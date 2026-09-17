package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.item.DroneControlStickItem;
import com.example.tudursvehiclemod.item.DroneRouteBookItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** See block.DroneCenterBlockEntity's own doc for what slot 0/1 do (binding stick / route book respectively). */
public class DroneCenterScreenHandler extends ScreenHandler {

	private final Inventory inventory;
	/** Null client-side when this screen was opened without a known block position yet (shouldn't normally happen, but avoids a hard crash if it does) - the config-screen-opening button just does nothing in that case. */
	private final BlockPos blockPos;

	/** Client-side constructor (see registry.ModScreenHandlers' own factory registration). */
	public DroneCenterScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(2), null);
	}

	public DroneCenterScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, BlockPos blockPos) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.DRONE_CENTER, syncId);
		this.inventory = inventory;
		this.blockPos = blockPos;
		inventory.onOpen(playerInventory.player);

		this.addSlot(new Slot(inventory, 0, 62, 24) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof DroneControlStickItem;
			}
		});
		this.addSlot(new Slot(inventory, 1, 62, 52) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof DroneRouteBookItem;
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

	/** Block position this screen was opened for - used by the client's own "Open Config" button to know which Drone Center to request settings for. Null only if this screen handler was somehow constructed without one (defensive; shouldn't normally happen). */
	public BlockPos tudursvehiclemod$getBlockPos() {
		return this.blockPos;
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
				if (original.getItem() instanceof DroneControlStickItem) {
					if (!this.insertItem(original, 0, 1, false)) {
						return ItemStack.EMPTY;
					}
				} else if (original.getItem() instanceof DroneRouteBookItem) {
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
