package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** A throwaway single-field grouping, matching GroundVehicleParts' own pattern for the same reason (VehicleDefinition's own top-level group is at RecordCodecBuilder's 16-field limit, and GroundVehicleParts - despite having a little headroom left - is a semantically unrelated bundle that this field has no real reason to join). Reserved for auxiliary equipment that doesn't fit any of the other existing bundles; currently searchLightParts, navLightParts, the supply ranges, regeneration, and defaultFreelook. */
public record AuxiliaryParts(
		// AddSearchLight/AddFixedSearchLight/AddSteeringSearchLight - see SearchLightPart's own doc.
		List<SearchLightPart> searchLightParts,
		// Simpler point-light feature (navigation/aviation lights) - see NavLightPart's own doc.
		List<NavLightPart> navLightParts,
		// FuelSupplyRange/AmmoSupplyRange, plus a durability equivalent requested alongside them - see VehicleDefinition's own accessors for the full semantics.
		float fuelSupplyRange,
		float ammoSupplyRange,
		float healthSupplyRange,
		/** Regeneration - MC Heli's own "2番席以降のモブが自動回復する" (non-pilot passengers auto-heal). implemented via this project's own direct tudursvehiclemod$heal() calls on a timer (this project's own "方式A") rather than applying vanilla's own REGENERATION status effect, avoiding that effect's own particle/HUD-icon presentation. See AbstractVehicleEntity's own tudursvehiclemod$updatePassengerRegeneration() doc for the full mechanism. */
		boolean regeneration,
		/** DefaultFreelook - MC Heli's own "機体の乗った直後からフリールックにするかどうか" (start in free-look immediately on mount). a vehicle with this set defaults to a FIXED (non-free-look) view on mount, and HOLDING the free-look key switches it to the ordinary fixed/locked view instead of the usual free-look - the opposite of every other vehicle's own behavior. See AbstractVehicleEntity's own tudursvehiclemod$isEffectiveFreeLook() doc for the full mechanism. */
		boolean defaultFreelook,
		/** Whether any occupant of any seat on this vehicle can eject via Alt+Space at all. See AbstractVehicleEntity's own tudursvehiclemod$tryEjectSeat() doc for the full mechanism. */
		boolean enableEjectionSeat,
		/** Supports MC Heli's own MobDropOption - see asset.MobDropOption's own doc for the full semantics. Empty (the default) means this vehicle has no mob-drop capability at all. */
		java.util.Optional<MobDropOption> mobDropOption
) {
	public static final MapCodec<AuxiliaryParts> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			SearchLightPart.CODEC.listOf().optionalFieldOf("search_light_parts", List.of()).forGetter(AuxiliaryParts::searchLightParts),
			NavLightPart.CODEC.listOf().optionalFieldOf("nav_light_parts", List.of()).forGetter(AuxiliaryParts::navLightParts),
			Codec.FLOAT.optionalFieldOf("fuel_supply_range", 0f).forGetter(AuxiliaryParts::fuelSupplyRange),
			Codec.FLOAT.optionalFieldOf("ammo_supply_range", 0f).forGetter(AuxiliaryParts::ammoSupplyRange),
			Codec.FLOAT.optionalFieldOf("health_supply_range", 0f).forGetter(AuxiliaryParts::healthSupplyRange),
			Codec.BOOL.optionalFieldOf("regeneration", false).forGetter(AuxiliaryParts::regeneration),
			Codec.BOOL.optionalFieldOf("default_freelook", false).forGetter(AuxiliaryParts::defaultFreelook),
			Codec.BOOL.optionalFieldOf("enable_ejection_seat", false).forGetter(AuxiliaryParts::enableEjectionSeat),
			MobDropOption.CODEC.optionalFieldOf("mob_drop_option").forGetter(AuxiliaryParts::mobDropOption)
	).apply(instance, AuxiliaryParts::new));
}
