package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** A throwaway grouping of VehicleDefinition's own AddPartWheel/AddPartSteeringWheel (and, despite the name, VtolEntity's own AddPartRotor, plus a UAV marker) fields, purely for RecordCodecBuilder's 16-field group() limit (both VehicleDefinition's own top-level group AND VehicleExtras were already right at that limit) - vtolRotorParts and isUav landed here simply because this was the bundle with room left, not because either is conceptually a "ground vehicle" part. */
public record GroundVehicleParts(
		// AddPartWheel - see WheelPart's own doc.
		List<WheelPart> wheelParts,
		// AddPartSteeringWheel - see SteeringWheelPart's own doc.
		List<SteeringWheelPart> steeringWheelParts,
		// AddCrawlerTrack - see CrawlerTrackPart's own doc.
		List<CrawlerTrackPart> crawlerTracks,
		// AddTrackRoller - see TrackRollerPart's own doc.
		List<TrackRollerPart> trackRollerParts,
		// AddPartRotor - see VtolRotorPart's own doc.
		List<VtolRotorPart> vtolRotorParts,
		// IsUAV - marks this vehicle as a UAV
		// (unmanned, remotely-piloted-only helicopter) - see
		// AbstractVehicleEntity's own tudursvehiclemod$isUav() doc for
		// what this actually gates (Readme_Weapon.txt's own Destruct
		// directive, "the pilot seat and boarding as a rider are both
		// meaningless for a UAV - it's flown exclusively through this
		// project's own station block/dedicated stick system instead").
		// false (the default, key absent) is an ordinary, crewed vehicle -
		// this project's own original behavior before this field existed.
		boolean isUav,
		// IsTargetDrone - 
		// marks this vehicle as dedicated target-drone-only (never
		// boarded/piloted normally - see AbstractVehicleEntity's own
		// tudursvehiclemod$isTargetDrone() doc for what this actually
		// gates). Like isUav, this does NOT gate whether the Drone
		// Center system itself can control a vehicle at all (any
		// vehicle can be, regardless of this flag) - only whether
		// ORDINARY boarding is also still available. false (the
		// default, key absent) is an ordinary vehicle.
		boolean isTargetDrone,
		// VtolHoverSpeedFraction - VTOL mode's
		// own WASD-driven horizontal movement (see entity.VtolEntity's
		// own tudursvehiclemod$updateHelicopterModeMovement() doc) felt
		// far too fast, using this vehicle's own full maxSpeed (the SAME
		// top speed aircraft mode itself flies at) - this is instead
		// multiplied by maxSpeed to get VTOL mode's own actual top
		// horizontal speed, a project-specific directive (not an MC
		// Heli one at all) so each vehicle can tune its own VTOL-mode
		// feel individually. 0.1 (10% of maxSpeed) is this project's
		// own default whenever this key is entirely absent, matching
		// what a direct request asked for as the default specifically.
		float vtolHoverSpeedFraction,
		// Each behaves EXACTLY as the original single runway did (see RunwayDefinition's own doc), just as one of possibly several. A multi-deck carrier authors one entry per deck, each with its own heightY.
		List<RunwayDefinition> runways,
		// wake_trail_spread_distance: manual override for the wake target width. Empty (default) means auto-detect from the hull's own measured beam at the waterline.
		java.util.Optional<Float> wakeTrailSpreadDistance,
		// wake_trail_duration_ticks: how long a wake point stays visible before aging out.
		int wakeTrailDurationTicks,
		// FlareType - 0 (the default, key absent - "未記載は「なし」") means this vehicle has no flare capability at all.
		int flareType
) {
	public static final MapCodec<GroundVehicleParts> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			WheelPart.CODEC.listOf().optionalFieldOf("wheel_parts", List.of()).forGetter(GroundVehicleParts::wheelParts),
			SteeringWheelPart.CODEC.listOf().optionalFieldOf("steering_wheel_parts", List.of()).forGetter(GroundVehicleParts::steeringWheelParts),
			CrawlerTrackPart.CODEC.listOf().optionalFieldOf("crawler_tracks", List.of()).forGetter(GroundVehicleParts::crawlerTracks),
			TrackRollerPart.CODEC.listOf().optionalFieldOf("track_roller_parts", List.of()).forGetter(GroundVehicleParts::trackRollerParts),
			VtolRotorPart.CODEC.listOf().optionalFieldOf("vtol_rotor_parts", List.of()).forGetter(GroundVehicleParts::vtolRotorParts),
			Codec.BOOL.optionalFieldOf("is_uav", false).forGetter(GroundVehicleParts::isUav),
			Codec.BOOL.optionalFieldOf("is_target_drone", false).forGetter(GroundVehicleParts::isTargetDrone),
			Codec.FLOAT.optionalFieldOf("vtol_hover_speed_fraction", 0.1f).forGetter(GroundVehicleParts::vtolHoverSpeedFraction),
			// Reads BOTH the legacy singular "runway" key (a vehicle converted/authored before this change) and the new plural "runways" array, merging them below. The legacy key's own forGetter always returns empty - there is no need to re-encode already-migrated data back out under the old key, since the plural "runways" list alone is now this record's own source of truth going forward.
			RunwayDefinition.CODEC.optionalFieldOf("runway").forGetter(parts -> java.util.Optional.empty()),
			RunwayDefinition.CODEC.listOf().optionalFieldOf("runways", List.of()).forGetter(GroundVehicleParts::runways),
			Codec.FLOAT.optionalFieldOf("wake_trail_spread_distance").forGetter(GroundVehicleParts::wakeTrailSpreadDistance),
			Codec.INT.optionalFieldOf("wake_trail_duration_ticks", 300).forGetter(GroundVehicleParts::wakeTrailDurationTicks),
			Codec.INT.optionalFieldOf("flare_type", 0).forGetter(GroundVehicleParts::flareType)
	).apply(instance, (wheelParts, steeringWheelParts, crawlerTracks, trackRollerParts, vtolRotorParts, isUav, isTargetDrone,
					   vtolHoverSpeedFraction, legacyRunway, runwaysList, wakeTrailSpreadDistance, wakeTrailDurationTicks, flareType) -> {
		// Merges the legacy singular "runway" (if present) with the new plural "runways" list - a vehicle file could in principle use either, or (unusually) both at once, and both are honored rather than one silently overriding the other.
		List<RunwayDefinition> mergedRunways = runwaysList;
		if (legacyRunway.isPresent()) {
			mergedRunways = new java.util.ArrayList<>(runwaysList);
			mergedRunways.add(0, legacyRunway.get());
		}
		return new GroundVehicleParts(wheelParts, steeringWheelParts, crawlerTracks, trackRollerParts, vtolRotorParts,
				isUav, isTargetDrone, vtolHoverSpeedFraction, mergedRunways, wakeTrailSpreadDistance, wakeTrailDurationTicks, flareType);
	}));
}
