package com.example.tudursvehiclemod.asset;

import net.minecraft.util.math.MathHelper;

/** A single stop in a CAS strike's own fixed route - position is relative to the marked target point, same "relative to center" convention as block.DroneWaypoint's own doc. attack marks whether the strike aircraft should be actively firing while flying the leg leading up to and away from this waypoint (see AbstractVehicleEntity's own tudursvehiclemod$updateCasAutoFire() doc for exactly how that's determined tick-to-tick) - a route typically starts and ends with attack=false waypoints (the approach and egress legs) with attack=true waypoints marking the actual bombing run in between. Parsed from a repeated "CasWaypoint = relX,relY,relZ,speedPercent,attack" line in the owning weapon's own.txt file (see tudursvehiclemod$parse()'s own doc for speedPercent's exact meaning), same repeated-key convention as that file's own "Item =.." lines. */
public record CasWaypoint(int relX, int relY, int relZ, float speedFraction, boolean attack) {

	/** Parses one "relX,relY,relZ,speedPercent,attack" line - returns null (rather than throwing) on anything malformed, so one corrupted line doesn't take the whole weapon file down with it, same defensive convention as block.DroneWaypoint's own tudursvehiclemod$decode(). attack accepts "true"/"false" (case-insensitive) OR "1"/"0" - anything else is treated as malformed (null) rather than silently defaulting to false, since Boolean.parseBoolean() alone would otherwise treat "1" as false with no warning at all. speedPercent is a PERCENTAGE of the spawned aircraft's own max speed (e.g. 100 = full speed), same input convention as client.screen.DroneWaypointsScreen's own percent-based speed field for a real Drone Center's own route - divided by 100 here to get the actual 0.0-1.0 fraction the drone autopilot itself consumes (unclamped internally: cruiseSpeedTarget = maxSpeed * speedFraction), clamped to a safe 0.05-1.0 range with a logged warning if the given percentage was outside 0-100, since a value in the wrong units would otherwise silently produce an aircraft that instantly rockets out of loaded chunks/render distance the moment it starts flying. */
	public static CasWaypoint tudursvehiclemod$parse(String line) {
		String[] parts = line.split(",", -1);
		if (parts.length != 5) {
			return null;
		}
		String attackText = parts[4].strip();
		Boolean attack = switch (attackText.toLowerCase(java.util.Locale.ROOT)) {
			case "true", "1" -> Boolean.TRUE;
			case "false", "0" -> Boolean.FALSE;
			default -> null;
		};
		if (attack == null) {
			return null;
		}
		try {
			float speedPercent = Float.parseFloat(parts[3].strip());
			if (speedPercent < 0.0f || speedPercent > 100.0f) {
				org.slf4j.LoggerFactory.getLogger("VehicleMod/CasWaypoint").warn(
						"CasWaypoint speed value {} is outside the expected 0-100 percent range (a PERCENTAGE of the aircraft's own max speed, same convention as a real Drone Center's own route) - clamping. Line: '{}'",
						speedPercent, line);
			}
			float speedFraction = MathHelper.clamp(speedPercent / 100f, 0.05f, 1.0f);
			return new CasWaypoint(
					Integer.parseInt(parts[0].strip()),
					Integer.parseInt(parts[1].strip()),
					Integer.parseInt(parts[2].strip()),
					speedFraction,
					attack
			);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
