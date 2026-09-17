package com.example.tudursvehiclemod.item;

import net.minecraft.item.Item;

/** One of these exists per tier (1-5), with no VehicleCategory of its own and no summon function at all (unlike TieredVehicleSpawnerItem, this has no use() override - right-clicking it does nothing). Craftable directly (see this project's own recipe/base_item_t*.json files, each reusing the exact same ingredients every vehicle category's own T1-T5 recipe already used identically before this change), and converted into a specific (category, tier)-matched TieredVehicleSpawnerItem at block.VehicleConverterBlockEntity - see that class's own doc for the full conversion flow. Every individual TieredVehicleSpawnerItem itself has no recipe of its own anymore at all (per the same direct request, "個々のスポーンアイテムはレシピを持たないものとします") - this base item plus the converter block is now the ONLY way to obtain one. */
public class TieredVehicleBaseItem extends Item {

	private final int tier;

	public TieredVehicleBaseItem(Settings settings, int tier) {
		super(settings);
		this.tier = tier;
	}

	public int getTier() {
		return tier;
	}
}
