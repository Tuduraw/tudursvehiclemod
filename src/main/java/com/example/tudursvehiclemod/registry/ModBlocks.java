package com.example.tudursvehiclemod.registry;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.block.FuelRefinerBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModBlocks {

	public static Block FUEL_REFINER;
	public static Block STATION;
	public static Block DRONE_CENTER;
	public static Block VEHICLE_CONVERTER;

	public static void register() {
		RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(VehicleMod.MOD_ID, "fuel_refiner"));
		FUEL_REFINER = Registry.register(Registries.BLOCK, blockKey,
				new FuelRefinerBlock(AbstractBlock.Settings.create()
						.registryKey(blockKey)
						.mapColor(MapColor.IRON_GRAY)
						.strength(3.5f)
						.requiresTool()));

		RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VehicleMod.MOD_ID, "fuel_refiner"));
		Registry.register(Registries.ITEM, itemKey,
				new BlockItem(FUEL_REFINER, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey()));

		// This project's own UAV control station - see block.StationBlock's own doc.
		RegistryKey<Block> stationKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(VehicleMod.MOD_ID, "station"));
		STATION = Registry.register(Registries.BLOCK, stationKey,
				new com.example.tudursvehiclemod.block.StationBlock(AbstractBlock.Settings.create()
						.registryKey(stationKey)
						.mapColor(MapColor.IRON_GRAY)
						.strength(4.0f)
						.requiresTool()));

		RegistryKey<Item> stationItemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VehicleMod.MOD_ID, "station"));
		Registry.register(Registries.ITEM, stationItemKey,
				new BlockItem(STATION, new Item.Settings().registryKey(stationItemKey).useBlockPrefixedTranslationKey()));

		// This project's own target drone control center - see block.DroneCenterBlock's own doc.
		RegistryKey<Block> droneCenterKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(VehicleMod.MOD_ID, "drone_center"));
		DRONE_CENTER = Registry.register(Registries.BLOCK, droneCenterKey,
				new com.example.tudursvehiclemod.block.DroneCenterBlock(AbstractBlock.Settings.create()
						.registryKey(droneCenterKey)
						.mapColor(MapColor.IRON_GRAY)
						.strength(4.0f)
						.requiresTool()));

		RegistryKey<Item> droneCenterItemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VehicleMod.MOD_ID, "drone_center"));
		Registry.register(Registries.ITEM, droneCenterItemKey,
				new BlockItem(DRONE_CENTER, new Item.Settings().registryKey(droneCenterItemKey).useBlockPrefixedTranslationKey()));

		// Converts a tier-based base item into a specific vehicle-category spawner item - see block.VehicleConverterBlockEntity's own doc.
		RegistryKey<Block> vehicleConverterKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(VehicleMod.MOD_ID, "vehicle_converter"));
		VEHICLE_CONVERTER = Registry.register(Registries.BLOCK, vehicleConverterKey,
				new com.example.tudursvehiclemod.block.VehicleConverterBlock(AbstractBlock.Settings.create()
						.registryKey(vehicleConverterKey)
						.mapColor(MapColor.IRON_GRAY)
						.strength(3.5f)
						.requiresTool()));

		RegistryKey<Item> vehicleConverterItemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VehicleMod.MOD_ID, "vehicle_converter"));
		Registry.register(Registries.ITEM, vehicleConverterItemKey,
				new BlockItem(VEHICLE_CONVERTER, new Item.Settings().registryKey(vehicleConverterItemKey).useBlockPrefixedTranslationKey()));
	}
}
