package com.example.tudursvehiclemod.item;

import net.minecraft.item.Item;

/** Equippable in the chest slot (Item.Settings' own equippable(EquipmentSlot.CHEST), applied where this is constructed in ModItems), deployable via Space while falling - see client.VehicleModClient's own key-handling doc and entity.ParachuteEntity's own doc for the actual deploy/render mechanism, both entirely separate from this class. This class exists mainly as a distinct, identifiable type (instanceof-checkable) rather than for any behavior of its own - a plain Item with the right components would work identically for equipping/dyeing purposes alone. */
public class ParachuteItem extends Item {

	public ParachuteItem(Settings settings) {
		super(settings);
	}
}
