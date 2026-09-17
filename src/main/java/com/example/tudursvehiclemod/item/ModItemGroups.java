package com.example.tudursvehiclemod.item;

import com.example.tudursvehiclemod.VehicleMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** A dedicated creative tab listing all 25 tiered spawner items (5 categories x 5 tiers). */
public class ModItemGroups {

	public static final RegistryKey<ItemGroup> VEHICLES =
			RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(VehicleMod.MOD_ID, "vehicles"));

	public static void register() {
		Registry.register(Registries.ITEM_GROUP, VEHICLES, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.TIERED_SPAWNERS.get(VehicleCategory.CAR)[0]))
				.displayName(Text.translatable("itemGroup.tudursvehiclemod.vehicles"))
				.build());

		ItemGroupEvents.modifyEntriesEvent(VEHICLES).register(entries -> {
			for (VehicleCategory category : VehicleCategory.values()) {
				for (Item item : ModItems.TIERED_SPAWNERS.get(category)) {
					entries.add(item);
				}
			}
			for (Item item : ModItems.TIERED_BASE_ITEMS) {
				entries.add(item);
			}
			entries.add(ModItems.FUEL_CAN);
			entries.add(ModItems.CREATIVE_FUEL_CAN);
			entries.add(com.example.tudursvehiclemod.registry.ModBlocks.FUEL_REFINER);
			// These two were missing from this
			// manual list entirely - see item.DroneControlStickItem's/
			// block.StationBlock's own doc.
			entries.add(ModItems.DRONE_CONTROL_STICK);
			entries.add(ModItems.DRONE_ROUTE_BOOK);
			entries.add(ModItems.PARACHUTE);
			entries.add(com.example.tudursvehiclemod.registry.ModBlocks.STATION);
			entries.add(com.example.tudursvehiclemod.registry.ModBlocks.DRONE_CENTER);
			entries.add(com.example.tudursvehiclemod.registry.ModBlocks.VEHICLE_CONVERTER);
		});
	}
}
