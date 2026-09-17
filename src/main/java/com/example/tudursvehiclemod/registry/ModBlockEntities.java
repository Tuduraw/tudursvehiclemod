package com.example.tudursvehiclemod.registry;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.block.FuelRefinerBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

	public static BlockEntityType<FuelRefinerBlockEntity> FUEL_REFINER;
	public static BlockEntityType<com.example.tudursvehiclemod.block.StationBlockEntity> STATION;
	public static BlockEntityType<com.example.tudursvehiclemod.block.DroneCenterBlockEntity> DRONE_CENTER;
	public static BlockEntityType<com.example.tudursvehiclemod.block.VehicleConverterBlockEntity> VEHICLE_CONVERTER;

	public static void register() {
		FUEL_REFINER = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(VehicleMod.MOD_ID, "fuel_refiner"),
				FabricBlockEntityTypeBuilder.create(FuelRefinerBlockEntity::new, ModBlocks.FUEL_REFINER).build());

		STATION = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(VehicleMod.MOD_ID, "station"),
				FabricBlockEntityTypeBuilder.create(com.example.tudursvehiclemod.block.StationBlockEntity::new, ModBlocks.STATION).build());

		DRONE_CENTER = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(VehicleMod.MOD_ID, "drone_center"),
				FabricBlockEntityTypeBuilder.create(com.example.tudursvehiclemod.block.DroneCenterBlockEntity::new, ModBlocks.DRONE_CENTER).build());

		VEHICLE_CONVERTER = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(VehicleMod.MOD_ID, "vehicle_converter"),
				FabricBlockEntityTypeBuilder.create(com.example.tudursvehiclemod.block.VehicleConverterBlockEntity::new, ModBlocks.VEHICLE_CONVERTER).build());
	}
}
