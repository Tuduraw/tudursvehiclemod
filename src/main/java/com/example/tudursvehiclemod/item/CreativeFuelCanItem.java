package com.example.tudursvehiclemod.item;

/** A creative-only variant of FuelCanItem with infinite fuel. */
public class CreativeFuelCanItem extends FuelCanItem {

	public CreativeFuelCanItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean isInfinite() {
		return true;
	}
}
