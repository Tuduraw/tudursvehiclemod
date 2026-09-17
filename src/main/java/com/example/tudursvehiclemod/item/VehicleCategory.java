package com.example.tudursvehiclemod.item;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.util.Identifier;

/** The 5 broad movement categories a TieredVehicleSpawnerItem is scoped to.
 *
 * Implements VehicleConverterTarget so the converter block can treat these and addon-registered
 * targets identically - see VehicleConverterTargets' own doc. */
public enum VehicleCategory implements VehicleConverterTarget {
	CAR("car", "item.tudursvehiclemod.category.car"),
	SHIP("ship", "item.tudursvehiclemod.category.ship"),
	SUBMARINE("submarine", "item.tudursvehiclemod.category.submarine"),
	AIRCRAFT("aircraft", "item.tudursvehiclemod.category.aircraft"),
	HELICOPTER("helicopter", "item.tudursvehiclemod.category.helicopter"),
	/** VtolEntity gets the same tiered spawner item treatment as every other category. */
	VTOL("vtol", "item.tudursvehiclemod.category.vtol"),
	/** The new movement-less entity (a stationary turret/emplacement - see entity.StaticEmplacementEntity's own doc) gets the same tiered spawner item treatment as every other category. */
	STATIC_EMPLACEMENT("static_emplacement", "item.tudursvehiclemod.category.static_emplacement");

	private final Identifier entityTypeId;
	private final String translationKey;

	VehicleCategory(String entityTypePath, String translationKey) {
		this.entityTypeId = Identifier.of(VehicleMod.MOD_ID, entityTypePath);
		this.translationKey = translationKey;
	}

	public Identifier entityTypeId() {
		return entityTypeId;
	}

	public String translationKey() {
		return translationKey;
	}

	/** This mod's own namespace plus the category name - can never collide with an addon's own
	 * target id, which uses that addon's own namespace. */
	@Override
	public Identifier id() {
		return Identifier.of(VehicleMod.MOD_ID, this.name().toLowerCase(java.util.Locale.ROOT));
	}

	/** The built-in spawner items, which ModItems registers at init - not duplicated here. */
	@Override
	public net.minecraft.item.Item[] tieredSpawnerItems() {
		net.minecraft.item.Item[] tiers = ModItems.TIERED_SPAWNERS.get(this);
		return tiers == null ? new net.minecraft.item.Item[0] : tiers;
	}

	/** Reverse lookup - used when packing a vehicle back up into an item (see AbstractVehicleEntity#tryPackUp), to find which category's tiered spawner item to hand.. */
	public static java.util.Optional<VehicleCategory> fromEntityTypeId(Identifier entityTypeId) {
		for (VehicleCategory category : values()) {
			if (category.entityTypeId.equals(entityTypeId)) {
				return java.util.Optional.of(category);
			}
		}
		return java.util.Optional.empty();
	}
}
