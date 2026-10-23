package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.block.FireSupportCenterBlockEntity;
import com.example.tudursvehiclemod.item.DroneControlStickItem;
import com.example.tudursvehiclemod.item.DroneRouteBookItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** Fire Support Center screen: UNIT_SLOTS stick slots (one linked vehicle each) and the route-book slot, above the player's inventory. Everything else on the screen (settings, statuses, the last notice) comes from network.FireSupportStatePayload into tudursvehiclemod$getState(), and goes back as network.FireSupportActionPayload. */
public class FireSupportScreenHandler extends ScreenHandler {

	public static final int UNIT_SLOT_X = 8;
	public static final int UNIT_SLOT_Y = 18;
	public static final int UNIT_SLOT_SPACING = 20;
	public static final int BOOK_SLOT_X = 8;
	public static final int BOOK_SLOT_Y = 142;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 168;
	public static final int HOTBAR_Y = 226;

	private final Inventory inventory;
	/** Known server-side from the start; client-side once the first state payload arrives. */
	private BlockPos blockPos;
	/** Client side: the latest state from the server (empty until the first one arrives). */
	private NbtCompound state = new NbtCompound();

	/** Client-side constructor (registry.ModScreenHandlers). */
	public FireSupportScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(FireSupportCenterBlockEntity.UNIT_SLOTS + 1), null);
	}

	public FireSupportScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, BlockPos blockPos) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.FIRE_SUPPORT_CENTER, syncId);
		this.inventory = inventory;
		this.blockPos = blockPos;
		inventory.onOpen(playerInventory.player);
		for (int unit = 0; unit < FireSupportCenterBlockEntity.UNIT_SLOTS; unit++) {
			this.addSlot(new Slot(inventory, unit, UNIT_SLOT_X, UNIT_SLOT_Y + unit * UNIT_SLOT_SPACING) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return stack.getItem() instanceof DroneControlStickItem;
				}

				@Override
				public int getMaxItemCount() {
					return 1;
				}
			});
		}
		this.addSlot(new Slot(inventory, FireSupportCenterBlockEntity.BOOK_SLOT, BOOK_SLOT_X, BOOK_SLOT_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof DroneRouteBookItem;
			}

			@Override
			public int getMaxItemCount() {
				return 1;
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
		}
	}

	public BlockPos tudursvehiclemod$getBlockPos() {
		return this.blockPos;
	}

	public NbtCompound tudursvehiclemod$getState() {
		return this.state;
	}

	/** Client side, from network.FireSupportStatePayload. */
	public void tudursvehiclemod$applyState(BlockPos pos, NbtCompound newState) {
		this.blockPos = pos;
		this.state = newState;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return this.inventory.canPlayerUse(player);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		int ownSlots = FireSupportCenterBlockEntity.UNIT_SLOTS + 1;
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getStack();
		ItemStack result = original.copy();
		if (index < ownSlots) {
			if (!this.insertItem(original, ownSlots, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (original.getItem() instanceof DroneControlStickItem) {
			if (!this.insertItem(original, 0, FireSupportCenterBlockEntity.UNIT_SLOTS, false)) {
				return ItemStack.EMPTY;
			}
		} else if (original.getItem() instanceof DroneRouteBookItem) {
			if (!this.insertItem(original, FireSupportCenterBlockEntity.BOOK_SLOT, ownSlots, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (original.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return result;
	}
}
