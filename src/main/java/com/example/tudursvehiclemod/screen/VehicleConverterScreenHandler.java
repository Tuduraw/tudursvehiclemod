package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.block.VehicleConverterBlockEntity;
import com.example.tudursvehiclemod.item.TieredVehicleBaseItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/** See block.VehicleConverterBlockEntity's own doc for what slot 0 and the button IDs below do. */
public class VehicleConverterScreenHandler extends ScreenHandler {

	/** Per VehicleConverterBlockEntity's own doc: onButtonClick()'s own button IDs - matching this project's own convention elsewhere of small, self-documenting int constants rather than magic numbers scattered across both the client screen (which sends these) and this class (which receives them). */
	public static final int BUTTON_PREVIOUS_PAGE = 0;
	public static final int BUTTON_NEXT_PAGE = 1;
	public static final int BUTTON_CONFIRM = 2;

	private final Inventory inventory;
	private final VehicleConverterBlockEntity blockEntity;
	private final PropertyDelegate propertyDelegate;

	/** Client-side constructor (see registry.ModScreenHandlers' own factory registration). */
	public VehicleConverterScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, null, new ArrayPropertyDelegate(1));
	}

	public VehicleConverterScreenHandler(int syncId, PlayerInventory playerInventory, VehicleConverterBlockEntity blockEntity,
			PropertyDelegate propertyDelegate) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.VEHICLE_CONVERTER, syncId);
		this.blockEntity = blockEntity;
		this.inventory = blockEntity != null ? blockEntity : new SimpleInventory(1);
		this.propertyDelegate = propertyDelegate;
		this.inventory.onOpen(playerInventory.player);

		this.addSlot(new Slot(this.inventory, 0, 80, 35) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof TieredVehicleBaseItem;
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

	/** For the screen's own display - see item.VehicleCategory's own values(). */
	public int getSelectedCategoryIndex() {
		return this.propertyDelegate.get(0);
	}

	/** Per VehicleConverterBlockEntity's own doc: the standard vanilla button-click mechanism (same as an enchanting table/loom) - no custom network payload needed at all. Server-side only; a client press is a no-op if this.blockEntity is null (the client-side constructor's own placeholder, never actually used for real interaction - see that constructor's own doc). */
	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (this.blockEntity == null) {
			return false;
		}
		switch (id) {
			case BUTTON_PREVIOUS_PAGE -> this.blockEntity.tudursvehiclemod$cyclePage(-1);
			case BUTTON_NEXT_PAGE -> this.blockEntity.tudursvehiclemod$cyclePage(1);
			case BUTTON_CONFIRM -> this.blockEntity.tudursvehiclemod$tryConvert(player);
			default -> {
				return false;
			}
		}
		return true;
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
				if (!this.insertItem(original, 1, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				if (original.getItem() instanceof TieredVehicleBaseItem) {
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
