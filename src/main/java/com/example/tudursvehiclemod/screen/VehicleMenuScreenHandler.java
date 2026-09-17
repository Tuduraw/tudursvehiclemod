package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.item.FuelCanItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/** Opened only while riding a vehicle (see the C2S open-request handling in network.ModNetworking / client.VehicleModClient's own keybinding). */
public class VehicleMenuScreenHandler extends ScreenHandler {

	private final AbstractVehicleEntity vehicle;
	/** How many of this screen's own slots (starting at 0) belong to the vehicle, before the player's own inventory slots begin. */
	private final int vehicleSlotCount;
	/** Caps the visible cargo grid to a 5-row x 9-column page (matching the creative inventory's own layout), rather than showing a very large inventorySize() all at once (which overflowed the screen off the bottom entirely). */
	public static final int PAGE_ROWS = 5;
	public static final int PAGE_SIZE = PAGE_ROWS * 9;
	/** "Next page"/"previous page" cargo control - separate ID ranges from REPAIR_BUTTON_ID_OFFSET's own so the three never collide. */
	public static final int PAGE_NEXT_BUTTON_ID = 2000;
	public static final int PAGE_PREV_BUTTON_ID = 2001;

	/** An earlier "close and reopen the whole screen on page change" design caused severe bugs (item duplication/cross-contamination, and separately a "reopen does nothing visible" symptom): this Slot objects are now permanently fixed for the lifetime of this screen (never recreated) - what changes on a page switch is only WHICH backing cargo indices this proxy inventory's own getStack()/setStack()/etc. calls actually read from/write to (pageOffset below), then this ScreenHandler's own normal per-tick content-sync (inherited from ScreenHandler itself, already running regardless) picks up the change and pushes the new page's own contents to the client automatically - no reopening, no re-creating Slot objects, no risk of the previous screen's own teardown ever being incomplete. */
	private static final class PagedCargoView implements Inventory {
		private final AbstractVehicleEntity vehicle;
		private int pageOffset;

		private PagedCargoView(AbstractVehicleEntity vehicle) {
			this.vehicle = vehicle;
		}

		private void setPageOffset(int offset) {
			this.pageOffset = offset;
		}

		/** Cargo index 0 is always this vehicle's own fuel slot (a separate, fixed, non-paginated Slot - see this screen's own constructor) - +1 shifts every cargo-page index past it. */
		private int realIndex(int viewIndex) {
			return 1 + this.pageOffset + viewIndex;
		}

		boolean inRange(int viewIndex) {
			return this.realIndex(viewIndex) < this.vehicle.size();
		}

		@Override
		public int size() {
			return PAGE_SIZE;
		}

		@Override
		public boolean isEmpty() {
			for (int i = 0; i < PAGE_SIZE; i++) {
				if (!this.getStack(i).isEmpty()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public ItemStack getStack(int viewIndex) {
			return this.inRange(viewIndex) ? this.vehicle.getStack(this.realIndex(viewIndex)) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeStack(int viewIndex, int amount) {
			return this.inRange(viewIndex) ? this.vehicle.removeStack(this.realIndex(viewIndex), amount) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeStack(int viewIndex) {
			return this.inRange(viewIndex) ? this.vehicle.removeStack(this.realIndex(viewIndex)) : ItemStack.EMPTY;
		}

		@Override
		public void setStack(int viewIndex, ItemStack stack) {
			if (this.inRange(viewIndex)) {
				this.vehicle.setStack(this.realIndex(viewIndex), stack);
			}
		}

		@Override
		public void markDirty() {
			this.vehicle.markDirty();
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return this.vehicle.canPlayerUse(player);
		}

		@Override
		public void clear() {
			for (int i = 0; i < PAGE_SIZE; i++) {
				this.removeStack(i);
			}
		}
	}

	private final PagedCargoView pagedCargoView;

	/** Client-side constructor (see registry.ModScreenHandlers' own factory registration). */
	public VehicleMenuScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, tudursvehiclemod$resolveClientVehicle(playerInventory));
	}

	public VehicleMenuScreenHandler(int syncId, PlayerInventory playerInventory, AbstractVehicleEntity vehicle) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.VEHICLE_MENU, syncId);
		this.vehicle = vehicle;

		Inventory fuelBacking = vehicle != null ? vehicle : new SimpleInventory(1);
		int cargoSize = vehicle != null ? vehicle.getDefinition().inventorySize() : 0;

		this.addSlot(new Slot(fuelBacking, 0, 26, 24) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof FuelCanItem;
			}
		});

		if (vehicle != null) {
			this.pagedCargoView = new PagedCargoView(vehicle);
			this.pagedCargoView.setPageOffset(vehicle.tudursvehiclemod$getCargoPage() * PAGE_SIZE);
		} else {
			this.pagedCargoView = null;
		}
		Inventory cargoBacking = vehicle != null ? this.pagedCargoView : new SimpleInventory(0);
		int visibleSlots = Math.min(PAGE_SIZE, cargoSize);
		// Always the SAME slot COUNT for this vehicle regardless of which page is currently shown (min(PAGE_SIZE, cargoSize) - for any vehicle with more than 45 cargo slots this is always exactly PAGE_SIZE, so a sparse last page still shows a full, consistently-sized grid; for a smaller vehicle it's that vehicle's own actual size, matching this vehicle's own single, un-paginated page exactly). An item placed into a slot beyond the vehicle's own actual inventorySize() (a partial last page, e.g. inventorySize=54 leaves only 9 real slots on page 2 out of the full 45 shown) simply vanished - canInsert() now explicitly rejects any such out-of-range placement (consulting PagedCargoView's own inRange() check, which already knows exactly which view-indices are real for the CURRENT page), rather than silently accepting the click and then discarding the item once setStack() found nowhere real to put it.
		PagedCargoView view = this.pagedCargoView;
		for (int i = 0; i < visibleSlots; i++) {
			int col = i % 9;
			int row = i / 9;
			int slotIndex = i;
			this.addSlot(new Slot(cargoBacking, i, 8 + col * 18, 50 + row * 18) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return view == null || view.inRange(slotIndex);
				}
			});
		}
		this.vehicleSlotCount = 1 + visibleSlots;

		int actualCargoRows = cargoSize == 0 ? 0 : Math.min(PAGE_ROWS, (cargoSize + 8) / 9);
		int playerInvY = 50 + actualCargoRows * 18 + 14;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, playerInvY + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, playerInvY + 58));
		}
	}

	private static AbstractVehicleEntity tudursvehiclemod$resolveClientVehicle(PlayerInventory playerInventory) {
		if (playerInventory.player.getVehicle() instanceof AbstractVehicleEntity vehicle) {
			return vehicle;
		}
		return null;
	}

	public AbstractVehicleEntity tudursvehiclemod$getVehicle() {
		return this.vehicle;
	}

	/** Repair button IDs are offset by this much so they can never collide with a real weapon index (see AbstractVehicleEntity's own REPAIR_TIER_IRON_COST for the 3 available tiers, 0-2). */
	public static final int REPAIR_BUTTON_ID_OFFSET = 1000;

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (this.vehicle == null) {
			return false;
		}
		if (id == PAGE_NEXT_BUTTON_ID || id == PAGE_PREV_BUTTON_ID) {
			// Just moves this SAME screen's own already-open cargo view to a different backing offset, then forces every cargo slot to re-sync (this ScreenHandler's own normal per-tick sync only pushes a slot's own contents when it NOTICES a change - since the Slot objects themselves haven't changed at all, only what their shared backing Inventory returns has, an explicit sendContentUpdates() call here guarantees the client picks up the new page's own contents immediately rather than waiting for some unrelated future change to trigger it).
			int cargoSize = this.vehicle.getDefinition().inventorySize();
			int maxPage = Math.max(0, (cargoSize - 1) / PAGE_SIZE);
			int newPage = Math.max(0, Math.min(maxPage, this.vehicle.tudursvehiclemod$getCargoPage() + (id == PAGE_NEXT_BUTTON_ID ? 1 : -1)));
			this.vehicle.tudursvehiclemod$setCargoPage(newPage);
			this.pagedCargoView.setPageOffset(newPage * PAGE_SIZE);
			this.sendContentUpdates();
			return true;
		}
		if (id >= REPAIR_BUTTON_ID_OFFSET) {
			return this.vehicle.tudursvehiclemod$tryRepair(player, id - REPAIR_BUTTON_ID_OFFSET);
		}
		return this.vehicle.tudursvehiclemod$tryResupplyWeapon(player, id);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return this.vehicle != null && player.getVehicle() == this.vehicle;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasStack()) {
			ItemStack original = slot.getStack();
			result = original.copy();
			if (index < this.vehicleSlotCount) {
				if (!this.insertItem(original, this.vehicleSlotCount, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				if (!this.insertItem(original, 0, this.vehicleSlotCount, false)) {
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
