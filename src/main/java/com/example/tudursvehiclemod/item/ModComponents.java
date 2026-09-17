package com.example.tudursvehiclemod.item;

import com.example.tudursvehiclemod.VehicleMod;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** This mod's own custom ItemStack data components. */
public class ModComponents {

	/** Current stored fuel amount for a FuelCanItem stack. */
	public static final ComponentType<Integer> FUEL_AMOUNT = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			Identifier.of(VehicleMod.MOD_ID, "fuel_amount"),
			ComponentType.<Integer>builder().codec(Codec.INT).build());

	/** item.DroneControlStickItem's own registered
	 * vehicle (this vehicle's own persistent UUID, NOT its entity ID -
	 * entity IDs aren't stable across a server restart/entity reload,
	 * UUIDs are) - absent entirely on a freshly-crafted, unregistered
	 * stick. */
	public static final ComponentType<java.util.UUID> REGISTERED_VEHICLE_ID = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			Identifier.of(VehicleMod.MOD_ID, "registered_vehicle_id"),
			ComponentType.<java.util.UUID>builder().codec(net.minecraft.util.Uuids.CODEC).build());

	/** This stack's own recorded patrol route, encoded the same way block.DroneCenterBlockEntity's own internal route is (see block.DroneRouteBookWaypoint's own tudursvehiclemod$encode()/decode() doc) - absent entirely on a freshly-crafted, empty book. */
	public static final ComponentType<String> DRONE_ROUTE_WAYPOINTS = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			Identifier.of(VehicleMod.MOD_ID, "drone_route_waypoints"),
			ComponentType.<String>builder().codec(Codec.STRING).build());

	/** This stack's own coordinate offset ("x,y,z", each an int) - added to every recorded waypoint's own absolute coordinates once this book is actually read by a Drone Center (see item.DroneRouteBookItem's own tudursvehiclemod$getOffset()/setOffset() doc), letting the SAME recorded route be reused at a shifted position without re-recording it. Absent entirely (treated as 0,0,0 - no shift at all) on a freshly-crafted book. */
	public static final ComponentType<String> DRONE_ROUTE_OFFSET = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			Identifier.of(VehicleMod.MOD_ID, "drone_route_offset"),
			ComponentType.<String>builder().codec(Codec.STRING).build());

	public static void register() {
		// Referencing FUEL_AMOUNT above is enough to trigger its own static init/registration.
	}
}
