package com.example.tudursvehiclemod.registry;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.screen.FuelRefinerScreenHandler;
import com.example.tudursvehiclemod.screen.VehicleMenuScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/** ScreenHandlerType's own constructor is private in vanilla, but Fabric Loom's own transitive access widener (bundled with Fabric API) widens it to be callable.. */
public class ModScreenHandlers {

	public static ScreenHandlerType<FuelRefinerScreenHandler> FUEL_REFINER;
	public static ScreenHandlerType<VehicleMenuScreenHandler> VEHICLE_MENU;
	public static ScreenHandlerType<com.example.tudursvehiclemod.screen.DroneCenterScreenHandler> DRONE_CENTER;
	public static ScreenHandlerType<com.example.tudursvehiclemod.screen.DroneCenterFormationScreenHandler> DRONE_CENTER_FORMATION;
	public static ScreenHandlerType<com.example.tudursvehiclemod.screen.StationScreenHandler> STATION;
	public static ScreenHandlerType<com.example.tudursvehiclemod.screen.VehicleConverterScreenHandler> VEHICLE_CONVERTER;

	public static void register() {
		FUEL_REFINER = Registry.register(
				Registries.SCREEN_HANDLER,
				Identifier.of(VehicleMod.MOD_ID, "fuel_refiner"),
				new ScreenHandlerType<>(FuelRefinerScreenHandler::new, FeatureSet.empty()));

		VEHICLE_MENU = Registry.register(
				Registries.SCREEN_HANDLER,
				Identifier.of(VehicleMod.MOD_ID, "vehicle_menu"),
				new ScreenHandlerType<>(VehicleMenuScreenHandler::new, FeatureSet.empty()));

		DRONE_CENTER = Registry.register(
				Registries.SCREEN_HANDLER,
				Identifier.of(VehicleMod.MOD_ID, "drone_center"),
				new ScreenHandlerType<>(com.example.tudursvehiclemod.screen.DroneCenterScreenHandler::new, FeatureSet.empty()));

		DRONE_CENTER_FORMATION = Registry.register(
				Registries.SCREEN_HANDLER,
				Identifier.of(VehicleMod.MOD_ID, "drone_center_formation"),
				new ScreenHandlerType<>(com.example.tudursvehiclemod.screen.DroneCenterFormationScreenHandler::new, FeatureSet.empty()));

		STATION = Registry.register(
				Registries.SCREEN_HANDLER,
				Identifier.of(VehicleMod.MOD_ID, "station"),
				new ScreenHandlerType<>(com.example.tudursvehiclemod.screen.StationScreenHandler::new, FeatureSet.empty()));

		VEHICLE_CONVERTER = Registry.register(
				Registries.SCREEN_HANDLER,
				Identifier.of(VehicleMod.MOD_ID, "vehicle_converter"),
				new ScreenHandlerType<>(com.example.tudursvehiclemod.screen.VehicleConverterScreenHandler::new, FeatureSet.empty()));
	}
}
