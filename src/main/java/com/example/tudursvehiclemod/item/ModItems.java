package com.example.tudursvehiclemod.item;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

public class ModItems {

	/** [category][tier-1]. */
	public static final Map<VehicleCategory, Item[]> TIERED_SPAWNERS = new EnumMap<>(VehicleCategory.class);
	/** [tier-1], no VehicleCategory of its own - see item.TieredVehicleBaseItem's own doc. */
	public static final Item[] TIERED_BASE_ITEMS = new Item[5];
	public static Item FUEL_CAN;
	public static Item CREATIVE_FUEL_CAN;
	public static Item DRONE_CONTROL_STICK;
	public static Item DRONE_ROUTE_BOOK;
	public static Item PARACHUTE;

	public static void register() {
		RegistryKey<Item> fuelCanKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VehicleMod.MOD_ID, "fuel_can"));
		FUEL_CAN = Registry.register(Registries.ITEM, fuelCanKey,
				new com.example.tudursvehiclemod.item.FuelCanItem(new Item.Settings().registryKey(fuelCanKey).maxCount(1)));

		// Deliberately no recipe at all.
		RegistryKey<Item> creativeFuelCanKey =
				RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VehicleMod.MOD_ID, "creative_fuel_can"));
		CREATIVE_FUEL_CAN = Registry.register(Registries.ITEM, creativeFuelCanKey,
				new com.example.tudursvehiclemod.item.CreativeFuelCanItem(
						new Item.Settings().registryKey(creativeFuelCanKey).maxCount(1)));

		// The dedicated stick that registers/binds a UAV vehicle to a StationBlock - see DroneControlStickItem's own doc. maxCount(1) - it isn't meaningfully stackable (each stack can only ever be registered to ONE vehicle at a time).
		RegistryKey<Item> droneControlStickKey =
				RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VehicleMod.MOD_ID, "drone_control_stick"));
		DRONE_CONTROL_STICK = Registry.register(Registries.ITEM, droneControlStickKey,
				new com.example.tudursvehiclemod.item.DroneControlStickItem(
						new Item.Settings().registryKey(droneControlStickKey).maxCount(1)));

		// A dedicated waypoint-recording tool for a Drone Center's own patrol route - see DroneRouteBookItem's own doc. maxCount(1), same reasoning as the control stick just above (each stack holds its own distinct recorded route).
		RegistryKey<Item> droneRouteBookKey =
				RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VehicleMod.MOD_ID, "drone_route_book"));
		DRONE_ROUTE_BOOK = Registry.register(Registries.ITEM, droneRouteBookKey,
				new com.example.tudursvehiclemod.item.DroneRouteBookItem(
						new Item.Settings().registryKey(droneRouteBookKey).maxCount(1)));

		// Equippable in the chest slot and dyeable like leather armor (default white, DyedColorComponent's own default meaning is "undyed" but this project's own item model always applies SOME tint, so an explicit default of white - 0xFFFFFF - is set here rather than leaving it to whatever DyedColorComponent's own DEFAULT_COLOR happens to be, matching "デフォルトは白" exactly). See ParachuteItem's own doc for why it's equippable/dyeable but otherwise has no behavior of its own.
		RegistryKey<Item> parachuteKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VehicleMod.MOD_ID, "parachute"));
		PARACHUTE = Registry.register(Registries.ITEM, parachuteKey,
				new com.example.tudursvehiclemod.item.ParachuteItem(new Item.Settings().registryKey(parachuteKey).maxCount(1)
						.equippable(net.minecraft.entity.EquipmentSlot.CHEST)
						.component(net.minecraft.component.DataComponentTypes.DYED_COLOR,
								new net.minecraft.component.type.DyedColorComponent(0xFFFFFF))));

		for (VehicleCategory category : VehicleCategory.values()) {
			Item[] tiers = new Item[5];
			for (int tier = 1; tier <= 5; tier++) {
				String path = category.name().toLowerCase() + "_spawner_t" + tier;
				RegistryKey<Item> tierKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VehicleMod.MOD_ID, path));
				Item item = new TieredVehicleSpawnerItem(
						new Item.Settings().registryKey(tierKey).maxCount(1), category, tier);
				tiers[tier - 1] = Registry.register(Registries.ITEM, tierKey, item);
			}
			TIERED_SPAWNERS.put(category, tiers);
		}

		// One per tier, no VehicleCategory - see item.TieredVehicleBaseItem's own doc. Named "vehicle_base_tN" - distinct from any of the "<category>_spawner_tN" names above, since this isn't scoped to any one category at all.
		for (int tier = 1; tier <= 5; tier++) {
			String path = "vehicle_base_t" + tier;
			RegistryKey<Item> baseItemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VehicleMod.MOD_ID, path));
			Item item = new TieredVehicleBaseItem(new Item.Settings().registryKey(baseItemKey).maxCount(64), tier);
			TIERED_BASE_ITEMS[tier - 1] = Registry.register(Registries.ITEM, baseItemKey, item);
		}
	}
}
