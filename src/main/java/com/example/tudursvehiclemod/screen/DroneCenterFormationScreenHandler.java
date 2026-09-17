package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.block.DroneCenterBlockEntity;
import com.example.tudursvehiclemod.item.DroneControlStickItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** The wingman slot UI, one DroneControlStickItem per formation position (slot index + 1 = formation index - see block.DroneCenterBlockEntity's own FormationSlotsInventory doc). Mirrors screen.VehicleMenuScreenHandler's own pagination pattern closely (see that class's own doc for the "permanently fixed Slot objects, only the backing view's own offset changes" reasoning this reuses verbatim) - PAGE_SIZE=45 slots/page, identical to that screen's own page size.
 *
 * Unlike VehicleMenuScreenHandler (whose own cargoSize is already known client-side, since the player is physically riding that vehicle when opening it), this block entity's own formationSize isn't necessarily known client-side at construction time - always shows the FULL PAGE_SIZE grid regardless of the actual formationSize, with the server-authoritative canInsert() (via PagedFormationView's own inRange()) rejecting insertion into any slot beyond the real, current formationSlots size. A synced PropertyDelegate (formationSize, currentPage) lets the client-side screen still show accurate "N機編成"/greyed-out-slot feedback despite not knowing the real size ahead of time. */
public class DroneCenterFormationScreenHandler extends ScreenHandler {

	public static final int PAGE_ROWS = 5;
	public static final int PAGE_SIZE = PAGE_ROWS * 9;
	public static final int PAGE_NEXT_BUTTON_ID = 3000;
	public static final int PAGE_PREV_BUTTON_ID = 3001;

	/** Index 0 = formationSize (total including the leader), index 1 = the currently-shown page. */
	private static final int PROPERTY_FORMATION_SIZE = 0;
	private static final int PROPERTY_PAGE = 1;

	private final DroneCenterBlockEntity blockEntity;
	private final BlockPos blockPos;
	private final PagedFormationView pagedView;
	private final PropertyDelegate propertyDelegate;

	/** Same reasoning as VehicleMenuScreenHandler's own PagedCargoView - permanently fixed Slot objects for this screen's own lifetime; only which backing formation-slot indices this proxy inventory's own getStack()/setStack() actually reads from/writes to (pageOffset) changes on a page switch. */
	private static final class PagedFormationView implements Inventory {
		private final DroneCenterBlockEntity.FormationSlotsInventory backing;
		private int pageOffset;

		private PagedFormationView(DroneCenterBlockEntity.FormationSlotsInventory backing) {
			this.backing = backing;
		}

		private void setPageOffset(int offset) {
			this.pageOffset = offset;
		}

		private int realIndex(int viewIndex) {
			return this.pageOffset + viewIndex;
		}

		boolean inRange(int viewIndex) {
			return this.realIndex(viewIndex) < this.backing.size();
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
			return this.inRange(viewIndex) ? this.backing.getStack(this.realIndex(viewIndex)) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeStack(int viewIndex, int amount) {
			return this.inRange(viewIndex) ? this.backing.removeStack(this.realIndex(viewIndex), amount) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeStack(int viewIndex) {
			return this.inRange(viewIndex) ? this.backing.removeStack(this.realIndex(viewIndex)) : ItemStack.EMPTY;
		}

		@Override
		public void setStack(int viewIndex, ItemStack stack) {
			if (this.inRange(viewIndex)) {
				this.backing.setStack(this.realIndex(viewIndex), stack);
			}
		}

		@Override
		public void markDirty() {
			this.backing.markDirty();
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return this.backing.canPlayerUse(player);
		}

		@Override
		public void clear() {
			for (int i = 0; i < PAGE_SIZE; i++) {
				this.removeStack(i);
			}
		}
	}

	/** Reads formationSize/formationSlotPage LIVE from the block entity each time the client's own normal per-tick property sync checks for changes - no caching/staleness risk, unlike a one-shot ArrayPropertyDelegate snapshot taken only at screen-open time. */
	private static final class LiveFormationPropertyDelegate implements PropertyDelegate {
		private final DroneCenterBlockEntity blockEntity;

		private LiveFormationPropertyDelegate(DroneCenterBlockEntity blockEntity) {
			this.blockEntity = blockEntity;
		}

		@Override
		public int get(int index) {
			return switch (index) {
				case PROPERTY_FORMATION_SIZE -> this.blockEntity.tudursvehiclemod$getFormationSize();
				case PROPERTY_PAGE -> this.blockEntity.tudursvehiclemod$getFormationSlotPage();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			// This delegate is read-only from the client's own perspective (formationSize is configured through the separate config screen, and page changes go through onButtonClick() below, not a raw property write) - silently ignored rather than throwing, matching PropertyDelegate's own general contract tolerance for a delegate that doesn't support writes.
		}

		@Override
		public int size() {
			return 2;
		}
	}

	/** Client-side constructor (see registry.ModScreenHandlers' own factory registration). Uses a dummy, disconnected backing (never actually read from server-side) - real slot contents arrive via this ScreenHandler's own normal sync mechanism regardless of what's passed here, same convention DroneCenterScreenHandler's own client-side constructor already uses. */
	public DroneCenterFormationScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, null, null);
	}

	public DroneCenterFormationScreenHandler(int syncId, PlayerInventory playerInventory, DroneCenterBlockEntity blockEntity, BlockPos blockPos) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.DRONE_CENTER_FORMATION, syncId);
		this.blockEntity = blockEntity;
		this.blockPos = blockPos;
		this.propertyDelegate = blockEntity != null ? new LiveFormationPropertyDelegate(blockEntity) : new net.minecraft.screen.ArrayPropertyDelegate(2);
		this.addProperties(this.propertyDelegate);

		Inventory backing = blockEntity != null ? blockEntity.tudursvehiclemod$getFormationSlotsInventory() : new SimpleInventory(0);
		this.pagedView = backing instanceof DroneCenterBlockEntity.FormationSlotsInventory realBacking ? new PagedFormationView(realBacking) : null;
		if (this.pagedView != null) {
			this.pagedView.setPageOffset(blockEntity.tudursvehiclemod$getFormationSlotPage() * PAGE_SIZE);
		}
		Inventory viewInventory = this.pagedView != null ? this.pagedView : new SimpleInventory(PAGE_SIZE);
		PagedFormationView view = this.pagedView;
		for (int i = 0; i < PAGE_SIZE; i++) {
			int col = i % 9;
			int row = i / 9;
			int slotIndex = i;
			this.addSlot(new Slot(viewInventory, i, 8 + col * 18, 18 + row * 18) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return stack.getItem() instanceof DroneControlStickItem && (view == null || view.inRange(slotIndex));
				}
			});
		}

		int playerInvY = 18 + PAGE_ROWS * 18 + 14;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, playerInvY + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, playerInvY + 58));
		}
	}

	/** Total formation size (including the leader) - synced via this screen's own PropertyDelegate, so the client can show accurate "N機編成"/greyed-out-slot feedback without needing to already know this ahead of time (see this class's own doc). */
	public int tudursvehiclemod$getFormationSize() {
		return this.propertyDelegate.get(PROPERTY_FORMATION_SIZE);
	}

	public int tudursvehiclemod$getCurrentPage() {
		return this.propertyDelegate.get(PROPERTY_PAGE);
	}

	public BlockPos tudursvehiclemod$getBlockPos() {
		return this.blockPos;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (this.blockEntity == null || this.pagedView == null) {
			return false;
		}
		if (id == PAGE_NEXT_BUTTON_ID || id == PAGE_PREV_BUTTON_ID) {
			int slotCount = this.blockEntity.tudursvehiclemod$getFormationSlotsInventory().size();
			int maxPage = Math.max(0, (slotCount - 1) / PAGE_SIZE);
			int newPage = Math.max(0, Math.min(maxPage, this.blockEntity.tudursvehiclemod$getFormationSlotPage() + (id == PAGE_NEXT_BUTTON_ID ? 1 : -1)));
			this.blockEntity.tudursvehiclemod$setFormationSlotPage(newPage);
			this.pagedView.setPageOffset(newPage * PAGE_SIZE);
			this.sendContentUpdates();
			return true;
		}
		return false;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return this.pagedView == null || this.pagedView.canPlayerUse(player);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasStack()) {
			ItemStack original = slot.getStack();
			result = original.copy();
			if (index < PAGE_SIZE) {
				if (!this.insertItem(original, PAGE_SIZE, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				if (!(original.getItem() instanceof DroneControlStickItem) || !this.insertItem(original, 0, PAGE_SIZE, false)) {
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
