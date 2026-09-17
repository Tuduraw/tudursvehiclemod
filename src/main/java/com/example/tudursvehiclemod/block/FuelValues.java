package com.example.tudursvehiclemod.block;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

import java.util.Map;

/** A small, self-contained table of furnace-style fuel burn times (in ticks), covering the common cases. */
public final class FuelValues {

	private static final Map<Item, Integer> VALUES = Map.ofEntries(
			Map.entry(Items.LAVA_BUCKET, 20000),
			Map.entry(Items.COAL_BLOCK, 16000),
			Map.entry(Items.DRIED_KELP_BLOCK, 4000),
			Map.entry(Items.BLAZE_ROD, 2400),
			Map.entry(Items.COAL, 1600),
			Map.entry(Items.CHARCOAL, 1600),
			Map.entry(Items.BAMBOO_MOSAIC, 300),
			Map.entry(Items.STICK, 100),
			Map.entry(Items.BOWL, 100)
	);

	private FuelValues() {
	}

	public static boolean isFuel(ItemStack stack) {
		return getFuelTicks(stack) > 0;
	}

	public static int getFuelTicks(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		Item item = stack.getItem();
		Integer explicit = VALUES.get(item);
		if (explicit != null) {
			return explicit;
		}
		// Broad tag-based fallback for the many wood-family items (planks, logs, slabs, doors, boats, etc.).
		if (stack.isIn(ItemTags.PLANKS)) {
			return 300;
		}
		if (stack.isIn(ItemTags.LOGS) || stack.isIn(ItemTags.WOODEN_SLABS)) {
			return 300;
		}
		if (stack.isIn(ItemTags.SAPLINGS)) {
			return 100;
		}
		return 0;
	}
}
