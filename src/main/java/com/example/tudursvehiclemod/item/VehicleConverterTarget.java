package com.example.tudursvehiclemod.item;

import net.minecraft.util.Identifier;

/** One selectable target of the vehicle converter (block.VehicleConverterBlockEntity) - a vehicle
 * kind a TieredVehicleBaseItem can be converted into a spawner item for.
 *
 * The built-in VehicleCategory enum implements this, and an ADDON MOD can register its own
 * implementations through VehicleConverterTargets - see that class's own doc for why registration
 * is opt-in rather than automatic.
 *
 * An addon that wants its own vehicle kind to stay OUT of the converter entirely (because it
 * intends a costlier or otherwise different acquisition route of its own) simply never registers
 * one, and nothing else about its vehicle changes. */
public interface VehicleConverterTarget {

	/** The entity type a vehicle JSON's own "entity_type" must name for a spawner item scoped to
	 * this target to accept it. */
	Identifier entityTypeId();

	/** Translation key for this target's own display name, shown on the converter screen and used
	 * by each spawner item's own name. */
	String translationKey();

	/** Stable id used for persistence and for ordering the converter's own page list. For the
	 * built-in categories this is this mod's own namespace plus the category name; an addon uses
	 * its own namespace, so the two can never collide. */
	Identifier id();

	/** The spawner items for this target, indexed [tier - 1]. Length is normally 5 (tiers 1-5).
	 *
	 * An addon registers its own already-registered items here - this framework never creates
	 * items on an addon's behalf, so the addon keeps full control over each item's own settings,
	 * model, and recipe (if any). */
	net.minecraft.item.Item[] tieredSpawnerItems();
}
