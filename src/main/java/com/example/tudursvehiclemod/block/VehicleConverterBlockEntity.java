package com.example.tudursvehiclemod.block;

import com.example.tudursvehiclemod.item.TieredVehicleBaseItem;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/** Converts a tier-based item.TieredVehicleBaseItem (slot 0, not scoped to any one vehicle kind) into a specific spawner item of the SAME tier, for whichever converter target the player has currently paged to (selectedCategoryIndex, synced to the client via PropertyDelegate so screen.VehicleConverterScreenHandler's own screen can display the current selection). The selectable targets are the built-in categories plus whatever an addon has registered - see item.VehicleConverterTargets' own doc. The actual conversion only happens on an explicit confirm (screen.VehicleConverterScreenHandler's own onButtonClick(), the same vanilla mechanism an enchanting table/loom already use for their own button-driven actions - see that class's own doc) - paging alone never touches the slot's own contents at all. */
public class VehicleConverterBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);
	private int selectedCategoryIndex;

	public VehicleConverterBlockEntity(BlockPos pos, net.minecraft.block.BlockState state) {
		super(com.example.tudursvehiclemod.registry.ModBlockEntities.VEHICLE_CONVERTER, pos, state);
	}

	/** Per this class's own doc: cycles the currently-paged target - called from the screen handler's own onButtonClick(), never touches the input slot at all. Wraps around in both directions. Covers addon-registered targets too (see item.VehicleConverterTargets' own doc), not just the built-in categories. */
	public void tudursvehiclemod$cyclePage(int direction) {
		int targetCount = Math.max(1, com.example.tudursvehiclemod.item.VehicleConverterTargets.count());
		this.selectedCategoryIndex = Math.floorMod(this.selectedCategoryIndex + direction, targetCount);
		this.markDirty();
	}

	public com.example.tudursvehiclemod.item.VehicleConverterTarget tudursvehiclemod$getSelectedCategory() {
		return com.example.tudursvehiclemod.item.VehicleConverterTargets.byIndex(this.selectedCategoryIndex);
	}

	/** Per this class's own doc: the actual conversion, only ever called from the screen handler's own onButtonClick() confirm action. Does nothing at all (silently) if slot 0 doesn't actually hold a TieredVehicleBaseItem - the confirm button is always visible/clickable regardless of slot contents, so this validates for itself rather than trusting the client. Consumes exactly 1 of the base item and gives the player 1 matching-tier spawner item for whichever target is currently selected - directly into their own inventory (or dropped at their feet if that's somehow full), matching how a crafting-table result is normally handed to the player rather than left in a slot for them to manually retrieve. */
	public void tudursvehiclemod$tryConvert(PlayerEntity player) {
		ItemStack inputStack = this.items.get(0);
		if (!(inputStack.getItem() instanceof TieredVehicleBaseItem baseItem)) {
			return;
		}
		com.example.tudursvehiclemod.item.VehicleConverterTarget target = this.tudursvehiclemod$getSelectedCategory();
		if (target == null) {
			return;
		}
		int tier = baseItem.getTier();
		Item[] tierArray = target.tieredSpawnerItems();
		if (tierArray == null || tier < 1 || tier > tierArray.length) {
			return;
		}
		Item resultItem = tierArray[tier - 1];
		if (resultItem == null) {
			return;
		}
		inputStack.decrement(1);
		this.markDirty();
		ItemStack resultStack = new ItemStack(resultItem);
		if (!player.getInventory().insertStack(resultStack)) {
			player.dropItem(resultStack, false);
		}
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
		return stack.getItem() instanceof TieredVehicleBaseItem;
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
		this.selectedCategoryIndex = view.getInt("SelectedCategoryIndex", 0);
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		Inventories.writeData(view, this.items, true);
		view.putInt("SelectedCategoryIndex", this.selectedCategoryIndex);
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.tudursvehiclemod.vehicle_converter");
	}

	@Override
	public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, PlayerEntity player) {
		return new com.example.tudursvehiclemod.screen.VehicleConverterScreenHandler(syncId, playerInventory, this,
				this.tudursvehiclemod$createPropertyDelegate());
	}

	private PropertyDelegate tudursvehiclemod$createPropertyDelegate() {
		return new PropertyDelegate() {
			@Override
			public int get(int index) {
				return index == 0 ? VehicleConverterBlockEntity.this.selectedCategoryIndex : 0;
			}

			@Override
			public void set(int index, int value) {
				if (index == 0) {
					VehicleConverterBlockEntity.this.selectedCategoryIndex = value;
				}
			}

			@Override
			public int size() {
				return 1;
			}
		};
	}
}
