package com.example.tudursvehiclemod.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

/** A refillable fuel container. */
public class FuelCanItem extends Item {

	/** Maximum fuel a single can can hold. */
	public static final int MAX_FUEL = 10000;

	public FuelCanItem(Settings settings) {
		super(settings);
	}

	/** True for item.CreativeFuelCanItem (a separate, uncraftable, creative-only variant). */
	public boolean isInfinite() {
		return false;
	}

	public static int getFuel(ItemStack stack) {
		if (stack.getItem() instanceof FuelCanItem fuelCanItem && fuelCanItem.isInfinite()) {
			return MAX_FUEL;
		}
		return stack.getOrDefault(ModComponents.FUEL_AMOUNT, 0);
	}

	public static void setFuel(ItemStack stack, int fuel) {
		if (stack.getItem() instanceof FuelCanItem fuelCanItem && fuelCanItem.isInfinite()) {
			return; // never actually changes - see isInfinite()'s own doc
		}
		stack.set(ModComponents.FUEL_AMOUNT, MathHelper.clamp(fuel, 0, MAX_FUEL));
	}

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		// Always visible (even at 0 fuel).
		return true;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		return Math.round(13.0f * getFuel(stack) / MAX_FUEL);
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		// A warm orange/fuel-like color, distinct from vanilla durability's own green-to-red gradient, so it doesn't read as "about to break".
		return 0xF29D3D;
	}
}
