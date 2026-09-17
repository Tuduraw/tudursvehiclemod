package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/** A throwaway grouping of VehicleDefinition's own hud/engineSound/damage/ fuel/inventory/ammo-part fields, purely for RecordCodecBuilder's 16-field group() limit. */
public record VehicleExtras(
		Optional<String> hud,
		Optional<String> engineSound,
		// MaxHP - this vehicle's own health pool (see AbstractVehicleEntity's own damage()/health tracking).
		float maxHealth,
		// ArmorDamageFactor.
		float armorDamageFactor,
		// ArmorMinDamage - if damage after armorDamageFactor is below this threshold, the vehicle takes NO damage at all from that hit..
		float armorMinDamage,
		// ArmorMaxDamage - caps the reduced damage at this value (damage exceeding it is rounded DOWN to it, per MC Heli's own documented example) - Float.MAX_VALUE..
		float armorMaxDamage,
		// DamageFactor - multiplies incoming damage to a PASSENGER while mounted (1.0 = no reduction).
		float damageFactor,
		// MaxFuel - this vehicle's own maximum internal fuel pool (see AbstractVehicleEntity's own fuel tracking / item.FuelCanItem) - ported directly from MC Heli's own..
		float maxFuel,
		// FuelConsumption.
		float fuelConsumptionPerSecond,
		// InventorySize - this vehicle's own persistent inventory slot count (see AbstractVehicleEntity's own vehicle-inventory tracking) - MC Heli's own documented..
		int inventorySize,
		// AddPartWeaponMissile - see AmmoPart's own doc.
		List<AmmoPart> ammoParts,
		// SubmergedDamageHeight - how far below the water surface (blocks)
		// this vehicle can go before taking damage each tick - 0 (MC Heli's
		// own default before this setting existed) means any submersion at
		// all damages it; see AbstractVehicleEntity's own
		// tudursvehiclemod$updateSubmergedDamage() for where this is
		// actually applied.
		float submergedDamageHeight,
		// force_bounding_box - see AbstractVehicleEntity's own getDimensions()
		// for where this is actually applied. A simple choice between two
		// collision-box sources, deliberately never derived from the
		// model's own real geometry (which, unlike this box, is meant to
		// visually match the vehicle's actual size, including things like
		// wingspan/hull length that would make a terrible collision box):
		// - false (default) - CarEntity's own same fixed, small
		// mod-standard box (see that class's own getDimensions() doc for
		// the exact values and reasoning) - this is the mod-wide
		// default unless a vehicle's own definition opts out.
		// - true - this vehicle's own hand-specified width/height (above)
		// is the actual collision box instead, for the rare vehicle that
		// genuinely needs a different size than the mod-standard box.
		boolean forceBoundingBox,
		// stall_speed - AircraftEntity-specific: the fraction of effectiveMaxSpeed below which this aircraft stalls (see that class's own STALL_ENGAGE_FRACTION doc, which this now configures rather than hardcodes) - 0.3 (the pre-existing hardcoded value) remains the default.
		float stallSpeedFraction,
		// HideEntity/EntityWidth/EntityHeight - see PassengerDisplay's own doc.
		PassengerDisplay passengerDisplay,
		// Float - AircraftEntity-specific (see that class's own doc for
		// where this is actually applied): true means this is a seaplane/
		// flying boat that can land/take off from water and taxi on it
		// just like on the ground; false (MC Heli's own default when this
		// directive is omitted) means landing on water instead disables
		// control and starts a slow, gradual sink - a regular aircraft was
		// never designed to survive a water landing at all.
		boolean isFloatCapable
) {
	public static final MapCodec<VehicleExtras> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.optionalFieldOf("hud").forGetter(VehicleExtras::hud),
			Codec.STRING.optionalFieldOf("engine_sound").forGetter(VehicleExtras::engineSound),
			Codec.FLOAT.optionalFieldOf("max_health", 100.0f).forGetter(VehicleExtras::maxHealth),
			Codec.FLOAT.optionalFieldOf("armor_damage_factor", 1.0f).forGetter(VehicleExtras::armorDamageFactor),
			Codec.FLOAT.optionalFieldOf("armor_min_damage", 0.0f).forGetter(VehicleExtras::armorMinDamage),
			Codec.FLOAT.optionalFieldOf("armor_max_damage", Float.MAX_VALUE).forGetter(VehicleExtras::armorMaxDamage),
			Codec.FLOAT.optionalFieldOf("damage_factor", 1.0f).forGetter(VehicleExtras::damageFactor),
			Codec.FLOAT.optionalFieldOf("max_fuel", 600.0f).forGetter(VehicleExtras::maxFuel),
			Codec.FLOAT.optionalFieldOf("fuel_consumption", 0.5f).forGetter(VehicleExtras::fuelConsumptionPerSecond),
			Codec.INT.optionalFieldOf("inventory_size", 0).forGetter(VehicleExtras::inventorySize),
			AmmoPart.CODEC.listOf().optionalFieldOf("ammo_parts", List.of()).forGetter(VehicleExtras::ammoParts),
			Codec.FLOAT.optionalFieldOf("submerged_damage_height", 0.0f).forGetter(VehicleExtras::submergedDamageHeight),
			Codec.BOOL.optionalFieldOf("force_bounding_box", false).forGetter(VehicleExtras::forceBoundingBox),
			Codec.FLOAT.optionalFieldOf("stall_speed", 0.3f).forGetter(VehicleExtras::stallSpeedFraction),
			PassengerDisplay.CODEC.optionalFieldOf("passenger_display", PassengerDisplay.DEFAULT).forGetter(VehicleExtras::passengerDisplay),
			Codec.BOOL.optionalFieldOf("float_capable", false).forGetter(VehicleExtras::isFloatCapable)
	).apply(instance, VehicleExtras::new));
}
