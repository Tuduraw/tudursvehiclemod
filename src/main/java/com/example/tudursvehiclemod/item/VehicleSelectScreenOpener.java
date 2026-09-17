package com.example.tudursvehiclemod.item;

/** TieredVehicleSpawnerItem lives in the common (main) sourceSet, so it can't reference the
 * client-only selection screen directly - VehicleModClient supplies this at startup instead.
 *
 * Takes a VehicleConverterTarget rather than the VehicleCategory enum, so the same screen serves
 * an addon's own vehicle kinds too. */
public interface VehicleSelectScreenOpener {
	void open(VehicleConverterTarget category, int tier);
}
