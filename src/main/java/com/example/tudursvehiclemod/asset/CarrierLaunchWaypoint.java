package com.example.tudursvehiclemod.asset;

import java.util.Optional;

/** A single waypoint within a WeaponType.CARRIER weapon's own dedicated launch route (CarrierAircraftConfig's own launchWaypoints) - same relX/relY/relZ/speedFraction meaning as CasWaypoint's own same-named fields, but WITHOUT an attack flag (launch waypoints never attack) and WITH three additional, optional one-time-trigger fields applied the instant this waypoint is reached: gearState/bayState (force landing_gear/weapon_bay toggle_parts open/closed, or empty for no change -for the known "only waypoint 0 and a landing route's final waypoint actually consume these" scope limitation) and speedBoostKmh (CarrierCatapultSpeed - snaps velocity magnitude to this km/h value, current direction preserved; consumed only for launch-route waypoints and waypoint 0's spawn-time application, never for landing-route waypoints).
 *
 * Parsed from a weapon.txt file's own "CarrierLaunchWaypoint = relX,relY,relZ,speedPercent,gear,bay,speedBoostKmh" line - gear/bay accept "0"/"1"/"-" (no change, default if omitted); speedBoostKmh accepts a number or "-". All three trailing columns are independently optional, but MUST stay in this exact gear,bay,speedBoostKmh order when present. */
public record CarrierLaunchWaypoint(int relX, int relY, int relZ, float speedFraction, Optional<Boolean> gearState, Optional<Boolean> bayState, Optional<Float> speedBoostKmh) {

	/** Parses one "CarrierLaunchWaypoint" line's own value, per this record's own format doc. Returns null (caller should warn and skip) if malformed. */
	public static CarrierLaunchWaypoint tudursvehiclemod$parse(String line) {
		String[] parts = line.split(",", -1);
		if (parts.length < 4) {
			return null;
		}
		try {
			int relX = Integer.parseInt(parts[0].trim());
			int relY = Integer.parseInt(parts[1].trim());
			int relZ = Integer.parseInt(parts[2].trim());
			float speedFraction = Float.parseFloat(parts[3].trim()) / 100f;
			Optional<Boolean> gearState = parts.length > 4 ? tudursvehiclemod$parseTriggerColumn(parts[4]) : Optional.empty();
			Optional<Boolean> bayState = parts.length > 5 ? tudursvehiclemod$parseTriggerColumn(parts[5]) : Optional.empty();
			Optional<Float> speedBoostKmh = parts.length > 6 ? tudursvehiclemod$parseSpeedBoostColumn(parts[6]) : Optional.empty();
			if (gearState == null || bayState == null || speedBoostKmh == null) {
				return null;
			}
			return new CarrierLaunchWaypoint(relX, relY, relZ, speedFraction, gearState, bayState, speedBoostKmh);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Returns null (distinct from a valid Optional.empty()) if trimmed isn't "-"/a valid float, so the caller can tell "malformed" apart from "explicitly no boost" - same defensive convention as tudursvehiclemod$parseTriggerColumn's own doc. Deliberately unclamped/unvalidated beyond basic parseability (no 0-X range check the way CasWaypoint's own speedPercent has) - a catapult launch speed has no natural upper bound the way a "% of max speed" fraction does. */
	private static Optional<Float> tudursvehiclemod$parseSpeedBoostColumn(String raw) {
		String trimmed = raw.trim();
		if (trimmed.isEmpty() || trimmed.equals("-")) {
			return Optional.empty();
		}
		try {
			return Optional.of(Float.parseFloat(trimmed));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Returns null (distinct from a valid Optional.empty()) if trimmed isn't "-"/"0"/"1", so the caller can tell "malformed" apart from "explicitly no change". */
	private static Optional<Boolean> tudursvehiclemod$parseTriggerColumn(String raw) {
		String trimmed = raw.trim();
		if (trimmed.isEmpty() || trimmed.equals("-")) {
			return Optional.empty();
		} else if (trimmed.equals("0")) {
			return Optional.of(false);
		} else if (trimmed.equals("1")) {
			return Optional.of(true);
		}
		return null;
	}
}
