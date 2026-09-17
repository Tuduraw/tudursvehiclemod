package com.example.tudursvehiclemod.block;

/** A single stop in a Drone Center's own patrol route. Position is relative to the owning Drone Center's own block position (not absolute world coordinates), matching the existing orbitAltitude's own "relative to center" convention - this way a saved route stays correct even if the whole structure (center + surrounding builds) is ever moved/pasted elsewhere. speedFraction is the same "% of this vehicle's own maxSpeed" convention the simple settings screen's own cruise speed already uses - each waypoint can cruise at its own speed. rollAngle (degrees) is the target bank angle this vehicle rolls toward as it actually ARRIVES at this waypoint. rollManeuverabilityMultiplier and turnManeuverabilityMultiplier (separating what a single combined multiplier previously controlled together) independently scale how quickly the roll transition happens versus how sharply this vehicle can actually turn (yaw) approaching this specific waypoint - a waypoint can have crisp turning but a slow, lazy roll, or vice versa. */
public record DroneWaypoint(int relX, int relY, int relZ, float speedFraction, float rollAngle,
							 float rollManeuverabilityMultiplier, float turnManeuverabilityMultiplier) {

	/** A reasonable default for a freshly-added waypoint before the player has set its own position - directly above the center, at the default orbit altitude, same cruise speed as the simple settings screen's own default, level (0 roll), normal (1x) roll/turn maneuverability. */
	public static DroneWaypoint createDefault() {
		return new DroneWaypoint(0, 15, 0, 0.5f, 0f, 1.0f, 1.0f);
	}

	/** Encodes as "relX,relY,relZ,speedFraction,rollAngle,rollManeuverabilityMultiplier,turnManeuverabilityMultiplier" for the simple CSV-style persistence this project already uses elsewhere (see AbstractVehicleEntity's own tudursvehiclemod$encodeIntArray()/encodeFloatArray() for the same pattern with plain arrays). */
	public String tudursvehiclemod$encode() {
		return this.relX + "," + this.relY + "," + this.relZ + "," + this.speedFraction + "," + this.rollAngle
				+ "," + this.rollManeuverabilityMultiplier + "," + this.turnManeuverabilityMultiplier;
	}

	/** Inverse of tudursvehiclemod$encode() - returns null (rather than throwing) on anything malformed, so one corrupted entry doesn't take the whole saved route down with it. Fields were added over time (rollAngle, then a single maneuverabilityMultiplier, then splitting that into separate roll/turn multipliers) after routes with OLDER encodings may already exist (saved worlds, in-progress route books) - also accepts the 4-field (pre-rollAngle), 5-field (pre-maneuverability), and 6-field (single combined maneuverability) forms, defaulting/mapping the missing/combined field(s) appropriately rather than dropping every pre-existing waypoint as corrupted. */
	public static DroneWaypoint tudursvehiclemod$decode(String encoded) {
		String[] parts = encoded.split(",", -1);
		if (parts.length != 4 && parts.length != 5 && parts.length != 6 && parts.length != 7) {
			return null;
		}
		try {
			float rollAngle = parts.length >= 5 ? Float.parseFloat(parts[4]) : 0f;
			float rollManeuverability;
			float turnManeuverability;
			if (parts.length == 7) {
				rollManeuverability = Float.parseFloat(parts[5]);
				turnManeuverability = Float.parseFloat(parts[6]);
			} else if (parts.length == 6) {
				// Older single combined multiplier - applies the same recorded value to both, preserving prior behavior exactly for existing routes.
				float combined = Float.parseFloat(parts[5]);
				rollManeuverability = combined;
				turnManeuverability = combined;
			} else {
				rollManeuverability = 1.0f;
				turnManeuverability = 1.0f;
			}
			return new DroneWaypoint(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
					Integer.parseInt(parts[2]), Float.parseFloat(parts[3]), rollAngle, rollManeuverability, turnManeuverability);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
