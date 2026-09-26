package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/** Everything needed to spawn, simulate and render one *kind* of vehicle. */
public record VehicleDefinition(
		Identifier entityType,
		Identifier model,
		Identifier texture,
		float scale,
		float width,
		float height,
		float maxSpeed,
		float acceleration,
		float turnSpeed,
		float stepHeight,
		float gravity,
		float onGroundPitch,
		// Minimum (most negative) throttle fraction this vehicle can reach in reverse - e.g. -0.2 means 20% reverse speed, 0 means no reverse at all. See mcheli_convert.py's own per-vehicle-type default handling.
		float reverseThrottle,
		// dive_max_speed - see MovementStats's own doc / SubmarineEntity's own tudursvehiclemod$getDiveMaxSpeed().
		Optional<Float> diveMaxSpeed,
		// engine_sound_volume - see MovementStats's own doc.
		float engineSoundVolume,
		// pivot_turn_throttle - see MovementStats's own doc.
		float pivotTurnThrottle,
		// wheel_rotation_speed - see MovementStats's own doc.
		float wheelRotationSpeed,
		// throttle_up_down - see MovementStats's own doc.
		Optional<Float> throttleUpDown,
		// weight_type - see MovementStats's own doc / WeightType's own doc.
		WeightType weightType,
		// throttle_switch_hold_ticks - see MovementStats's own doc.
		Optional<Integer> throttleSwitchHoldTicks,
		List<SeatDefinition> seats,
		List<WeaponDefinition> weapons,
		List<PartAnimation> spinningParts,
		List<TogglePart> toggleParts,
		Optional<SpawnItemDefinition> spawnItem,
		// Which HUD script (see client.hud.HudScriptLoader) this vehicle uses.
		Optional<String> hud,
		// Sound name (matches assets/<namespace>/sound(s)/<name>.ogg in any namespace.
		Optional<String> engineSound,
		// Named OBJ parts that rotate to track whoever is aiming an associated weapon's seat.
		List<WeaponPart> weaponParts,
		// MaxHP/ArmorDamageFactor/ArmorMinDamage/ArmorMaxDamage/DamageFactor/ MaxFuel/InventorySize.
		float maxHealth,
		float armorDamageFactor,
		float armorMinDamage,
		float armorMaxDamage,
		float damageFactor,
		float maxFuel,
		float fuelConsumptionPerSecond,
		int inventorySize,
		// AddPartWeaponMissile - see AmmoPart's own doc.
		List<AmmoPart> ammoParts,
		// SubmergedDamageHeight - see VehicleExtras's own doc.
		float submergedDamageHeight,
		// force_bounding_box - see VehicleExtras's own doc / AbstractVehicleEntity's own getDimensions().
		boolean forceBoundingBox,
		// stall_speed - see VehicleExtras's own doc / AircraftEntity's own STALL_ENGAGE_FRACTION.
		float stallSpeedFraction,
		// HideEntity/EntityWidth/EntityHeight - see PassengerDisplay's own doc.
		boolean hideEntity,
		float entityWidth,
		float entityHeight,
		// Float - see VehicleExtras's own doc / AircraftEntity's own water-landing handling.
		boolean isFloatCapable,
		// AddPartWheel - see WheelPart's own doc / GroundVehicleParts's own doc for why this is bundled through a nested codec.
		List<WheelPart> wheelParts,
		// AddPartSteeringWheel - see SteeringWheelPart's own doc.
		List<SteeringWheelPart> steeringWheelParts,
		// AddCrawlerTrack - see CrawlerTrackPart's own doc.
		List<CrawlerTrackPart> crawlerTracks,
		// AddTrackRoller - see TrackRollerPart's own doc.
		List<TrackRollerPart> trackRollerParts,
		// AddPartRotor - see VtolRotorPart's own doc.
		List<VtolRotorPart> vtolRotorParts,
		// IsUAV - see GroundVehicleParts's own doc for why this landed in that particular bundle.
		boolean isUav,
		// IsTargetDrone - see GroundVehicleParts's own doc for why this landed in that particular bundle.
		boolean isTargetDrone,
		// VtolHoverSpeedFraction - see GroundVehicleParts's own doc for why this landed in that particular bundle.
		float vtolHoverSpeedFraction,
		// runways - see RunwayDefinition's own doc, and GroundVehicleParts' own doc for why this landed in that bundle too. A List rather than a single Optional, supporting multiple independent runways on one vehicle (ordinary decks and multi-deck carriers alike).
		List<RunwayDefinition> runways,
		// wake_trail_spread_distance/wake_trail_duration_ticks - see GroundVehicleParts's own doc for why these landed in that particular bundle.
		Optional<Float> wakeTrailSpreadDistance,
		int wakeTrailDurationTicks,
		// FlareType - see GroundVehicleParts's own doc for why this landed in that particular bundle, and AbstractVehicleEntity's own tudursvehiclemod$deployFlares() doc for what this actually does.
		int flareType,
		// AddSearchLight/AddFixedSearchLight/AddSteeringSearchLight - see SearchLightPart's own doc, and AuxiliaryParts' own doc for why this rides in its own separate codec group.
		List<SearchLightPart> searchLightParts,
		// Simpler point-light feature (navigation/aviation lights) - see NavLightPart's own doc.
		List<NavLightPart> navLightParts,
		/** Radius, in blocks, within which this vehicle continuously refuels OTHER vehicles. 0 disables it. Supplying costs this vehicle nothing, and it never supplies itself - both per MC Heli's own documented behaviour. */
		float fuelSupplyRange,
		/** For weapon ammunition instead. */
		float ammoSupplyRange,
		/** Same semantics again, restoring OTHER vehicles' health. Has no MC Heli counterpart - it is this project's own addition. */
		float healthSupplyRange,
		// regeneration/default_freelook - see AuxiliaryParts's own doc for the full semantics of each (default_freelook in particular is intentionally REVERSED from MC Heli's own).
		boolean regeneration,
		boolean defaultFreelook,
		/** Supports MC Heli's own EnableEjectionSeat - see AuxiliaryParts's own doc for the full semantics. */
		boolean enableEjectionSeat,
		/** Supports MC Heli's own MobDropOption - see asset.MobDropOption's own doc for the full semantics. */
		Optional<MobDropOption> mobDropOption
) {

	/** A NEGATIVE turnSpeed silently mirrors every steering input for that vehicle, in every vehicle type that uses it - Aircraft, Car, Helicopter, Ship, Submarine and Vtol all multiply their own sideways input by it directly, so the sign flows straight through to setYaw().
	 *
	 * It has no legitimate meaning: turnSpeed comes from MC Heli's own MobilityYaw, documented as "larger = better maneuverability", so the value is a RATE and its sign was never part of the format. Nothing rejected one, though, which is exactly why the symptom appeared per-VEHICLE rather than per-type and looked so arbitrary.
	 *
	 * Normalised here, in the record itself, so it is impossible for any vehicle type to receive one - and so ALREADY-CONVERTED definition files are fixed on load without needing to be regenerated. mcheli_convert.py guards the same value at conversion time as well; this is the backstop. Zero is left alone, since that legitimately means "cannot turn at all". */
	public VehicleDefinition {
		if (turnSpeed < 0f) {
			turnSpeed = Math.abs(turnSpeed);
		}
		// ReverseThrottle is passed straight into updateThrottle() as its own `min` parameter - the LOWER bound throttle is clamped to - so it has to be negative for "reverse" to mean anything at all. A POSITIVE value clamps throttle to stay positive, so asking for reverse drives the vehicle FORWARD instead, which is exactly the reported symptom.
		//
		// Like turnSpeed, the sign was never meaningfully part of the source format (MC Heli's own ReverseThrottle is a magnitude), and nothing rejected a positive one - so this too appeared per-VEHICLE rather than per-type, depending purely on how each addon happened to write it. Normalised here in the record for the same reason: already-converted definition files are corrected on load, without needing regeneration. Zero is left alone, since that legitimately means "cannot reverse at all".
		if (reverseThrottle > 0f) {
			reverseThrottle = -reverseThrottle;
		}
	}

	// NOTE: RecordCodecBuilder's group() only supports up to 16 fields.
	public static final Codec<VehicleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("entity_type").forGetter(VehicleDefinition::entityType),
			Identifier.CODEC.fieldOf("model").forGetter(VehicleDefinition::model),
			Identifier.CODEC.fieldOf("texture").forGetter(VehicleDefinition::texture),
			Codec.FLOAT.optionalFieldOf("scale", 1.0f).forGetter(VehicleDefinition::scale),
			Codec.FLOAT.fieldOf("width").forGetter(VehicleDefinition::width),
			Codec.FLOAT.fieldOf("height").forGetter(VehicleDefinition::height),
			// max_speed/acceleration/turn_speed/step_height/gravity/on_ground_pitch/reverse_throttle/dive_max_speed.
			MovementStats.CODEC.forGetter(def ->
					new MovementStats(def.maxSpeed(), def.acceleration(), def.turnSpeed(), def.stepHeight(),
							def.gravity(), def.onGroundPitch(), def.reverseThrottle(), def.diveMaxSpeed(), def.engineSoundVolume(),
						def.pivotTurnThrottle(), def.wheelRotationSpeed(), def.throttleUpDown(),
						def.weightType(), def.throttleSwitchHoldTicks())),
			SeatDefinition.CODEC.listOf().optionalFieldOf("seats", List.of()).forGetter(VehicleDefinition::seats),
			WeaponDefinition.CODEC.listOf().optionalFieldOf("weapons", List.of()).forGetter(VehicleDefinition::weapons),
			// Continuously-spinning named OBJ parts (rotors, propellers, wheels,..).
			PartAnimation.CODEC.listOf().optionalFieldOf("spinning_parts", List.of()).forGetter(VehicleDefinition::spinningParts),
			// Two-state (open/closed) named OBJ parts (hatches, canopies, landing gear).
			TogglePart.CODEC.listOf().optionalFieldOf("toggle_parts", List.of()).forGetter(VehicleDefinition::toggleParts),
			SpawnItemDefinition.CODEC.optionalFieldOf("spawn_item").forGetter(VehicleDefinition::spawnItem),
			WeaponPart.CODEC.listOf().optionalFieldOf("weapon_parts", List.of()).forGetter(VehicleDefinition::weaponParts),
			// hud/engine_sound/max_health/armor_damage_factor/armor_min_damage/ armor_max_damage/damage_factor/max_fuel/inventory_size/ammo_parts/ submerged_damage_height/force_bounding_box/stall_speed/passenger_display/float_capable.
			VehicleExtras.CODEC.forGetter(def ->
					new VehicleExtras(def.hud(), def.engineSound(), def.maxHealth(),
							def.armorDamageFactor(), def.armorMinDamage(), def.armorMaxDamage(), def.damageFactor(),
							def.maxFuel(), def.fuelConsumptionPerSecond(), def.inventorySize(), def.ammoParts(),
							def.submergedDamageHeight(), def.forceBoundingBox(), def.stallSpeedFraction(),
							new PassengerDisplay(def.hideEntity(), def.entityWidth(), def.entityHeight()),
							def.isFloatCapable())),
			// wheel_parts/steering_wheel_parts/crawler_tracks/track_roller_parts/vtol_rotor_parts/is_uav - see GroundVehicleParts's own doc for why these are bundled (both VehicleDefinition's own top-level group AND VehicleExtras were already right at RecordCodecBuilder's 16-field limit).
			GroundVehicleParts.CODEC.forGetter(def -> new GroundVehicleParts(def.wheelParts(), def.steeringWheelParts(), def.crawlerTracks(), def.trackRollerParts(), def.vtolRotorParts(), def.isUav(), def.isTargetDrone(), def.vtolHoverSpeedFraction(), def.runways(), def.wakeTrailSpreadDistance(), def.wakeTrailDurationTicks(), def.flareType())),
			AuxiliaryParts.CODEC.forGetter(def -> new AuxiliaryParts(def.searchLightParts(), def.navLightParts(),
					def.fuelSupplyRange(), def.ammoSupplyRange(), def.healthSupplyRange(), def.regeneration(), def.defaultFreelook(),
					def.enableEjectionSeat(), def.mobDropOption()))
	).apply(instance, (entityType, model, texture, scale, width, height, movementStats,
					   seats, weapons, spinningParts, toggleParts, spawnItem, weaponParts, extras, groundVehicleParts, auxiliaryParts) ->
			new VehicleDefinition(entityType, model, texture, scale, width, height,
					movementStats.maxSpeed(), movementStats.acceleration(), movementStats.turnSpeed(),
					movementStats.stepHeight(), movementStats.gravity(), movementStats.onGroundPitch(),
					movementStats.reverseThrottle(), movementStats.diveMaxSpeed(), movementStats.engineSoundVolume(),
				movementStats.pivotTurnThrottle(), movementStats.wheelRotationSpeed(), movementStats.throttleUpDown(),
				movementStats.weightType(), movementStats.throttleSwitchHoldTicks(),
					seats, weapons, spinningParts, toggleParts, spawnItem, extras.hud(), extras.engineSound(),
					weaponParts, extras.maxHealth(), extras.armorDamageFactor(), extras.armorMinDamage(),
					extras.armorMaxDamage(), extras.damageFactor(), extras.maxFuel(),
					extras.fuelConsumptionPerSecond(), extras.inventorySize(), extras.ammoParts(),
					extras.submergedDamageHeight(), extras.forceBoundingBox(), extras.stallSpeedFraction(),
					extras.passengerDisplay().hideEntity(), extras.passengerDisplay().entityWidth(),
					extras.passengerDisplay().entityHeight(), extras.isFloatCapable(),
					groundVehicleParts.wheelParts(), groundVehicleParts.steeringWheelParts(), groundVehicleParts.crawlerTracks(),
					groundVehicleParts.trackRollerParts(), groundVehicleParts.vtolRotorParts(), groundVehicleParts.isUav(), groundVehicleParts.isTargetDrone(), groundVehicleParts.vtolHoverSpeedFraction(), groundVehicleParts.runways(),
					groundVehicleParts.wakeTrailSpreadDistance(), groundVehicleParts.wakeTrailDurationTicks(),
					groundVehicleParts.flareType(),
					auxiliaryParts.searchLightParts(), auxiliaryParts.navLightParts(),
					auxiliaryParts.fuelSupplyRange(), auxiliaryParts.ammoSupplyRange(), auxiliaryParts.healthSupplyRange(),
					auxiliaryParts.regeneration(), auxiliaryParts.defaultFreelook(),
					auxiliaryParts.enableEjectionSeat(), auxiliaryParts.mobDropOption())));

	/** 1-5, used by the tiered spawner item system (see item.TieredVehicleSpawnerItem) to decide which vehicles a given spawner is allowed to summon. */
	public int tier() {
		return spawnItem.map(SpawnItemDefinition::tier).orElse(1);
	}

	/** Falls back to a formatted version of the vehicle's own id (e.g. */
	public String displayName(Identifier vehicleId) {
		return spawnItem.flatMap(SpawnItemDefinition::displayName).orElseGet(() -> {
			String path = vehicleId.getPath();
			String withSpaces = path.replace('_', ' ');
			return Character.toUpperCase(withSpaces.charAt(0)) + withSpaces.substring(1);
		});
	}

	/** entity_type paths (in this mod's own namespace only - an addon's own entity_type, whatever
	 * its namespace, is never one of these) whose vehicles keep the wider engine-sound range by
	 * default: aircraft/helicopter/vtol for altitude (heard well before they're visible from the
	 * ground), ship/submarine for sheer physical scale. Everything else - car, static_emplacement,
	 * and any addon-defined entity_type - gets the narrower default instead. See
	 * tudursvehiclemod$effectiveEngineSoundVolume()'s own doc for how this is actually used. */
	private static final java.util.Set<String> WIDE_ENGINE_SOUND_RANGE_PATHS =
			java.util.Set.of("aircraft", "helicopter", "vtol", "ship", "submarine");

	/** engine_sound_volume itself defaults to ENGINE_SOUND_VOLUME_UNSET (see that constant's own
	 * doc) rather than baking one fixed number straight into the codec, specifically so a vehicle
	 * that genuinely left this unset can be told apart from one that explicitly wrote whatever
	 * number this method would otherwise have defaulted to - the two cases need to resolve
	 * differently once entityType() is factored in, which the plain codec default alone can't do
	 * (it has no way to know entityType() at the point the field itself is being decoded, and
	 * folding that in there would require restructuring this whole record's own group() call for a
	 * single field's sake). Reportedly this field's default was originally meant to be a flat 1.0 for
	 * every vehicle type, and became 3.0 by mistake in some later change - restored to 1.0 here for
	 * every type EXCEPT the wide-range ones above, which keep 3.0 (the value already in wide use for
	 * them, and reasonable for both - see WIDE_ENGINE_SOUND_RANGE_PATHS's own doc for why). */
	public float tudursvehiclemod$effectiveEngineSoundVolume() {
		if (this.engineSoundVolume != MovementStats.ENGINE_SOUND_VOLUME_UNSET) {
			return this.engineSoundVolume;
		}
		boolean wideRange = com.example.tudursvehiclemod.VehicleMod.MOD_ID.equals(this.entityType.getNamespace())
				&& WIDE_ENGINE_SOUND_RANGE_PATHS.contains(this.entityType.getPath());
		return wideRange ? 3.0f : 1.0f;
	}
}
