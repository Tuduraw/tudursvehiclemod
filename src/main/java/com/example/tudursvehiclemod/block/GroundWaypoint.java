package com.example.tudursvehiclemod.block;

/** The ground/surface counterpart to DroneWaypoint (see that record's own doc for the aircraft version this parallels).
 *
 * TWO deliberate differences from DroneWaypoint, both per that same direct request:
 * - NO relY at all. For a Car or a Ship, vertical position is a consequence of the terrain/water surface underneath it, not something a route can meaningfully command - the vehicle simply ends up at whatever height the ground or waterline puts it at. Arrival is therefore judged purely on HORIZONTAL distance (see AbstractVehicleEntity's own tudursvehiclemod$updateGroundWaypointAutopilot() doc), so a route stays valid across sloped/uneven terrain without the author having to match every Y themselves.
 * - waitTicks: how long to sit stationary at this waypoint before moving on. An aircraft physically cannot stop, so DroneWaypoint has no equivalent; a surface vehicle can, and a patrol route that pauses at each stop is a natural thing to want. 0 (the default) means "don't stop at all" - the vehicle rolls straight through this waypoint on toward the next, exactly like an aircraft route does.
 *
 * relX/relZ/speedFraction keep DroneWaypoint's own meaning and conventions exactly (position relative to the owning Drone Center's own block position, speed as a fraction of this vehicle's own maxSpeed), so both route types read the same way to an author. Deliberately has no rollAngle/maneuverability multipliers: a surface vehicle's own banking/turn behavior comes from its own existing steering-input-driven physics (see the autopilot's own doc for why this drives INPUTS rather than velocity directly), not from anything a waypoint would specify. */
public record GroundWaypoint(int relX, int relZ, float speedFraction, int waitTicks) {

	/** A reasonable default for a freshly-added waypoint before the player has set its own position - at the center itself, half speed, no dwell. Mirrors DroneWaypoint's own createDefault() convention. */
	public static GroundWaypoint createDefault() {
		return new GroundWaypoint(0, 0, 0.5f, 0);
	}

	/** Encodes as "relX,relZ,speedFraction,waitTicks" - the same simple CSV-style persistence DroneWaypoint's own tudursvehiclemod$encode() uses. */
	public String tudursvehiclemod$encode() {
		return this.relX + "," + this.relZ + "," + this.speedFraction + "," + this.waitTicks;
	}

	/** Inverse of tudursvehiclemod$encode() - returns null (rather than throwing) on anything malformed, so one corrupted entry doesn't take the whole saved route down with it, same defensive convention as DroneWaypoint's own tudursvehiclemod$decode(). */
	public static GroundWaypoint tudursvehiclemod$decode(String encoded) {
		String[] parts = encoded.split(",", -1);
		if (parts.length != 4) {
			return null;
		}
		try {
			return new GroundWaypoint(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
					Float.parseFloat(parts[2].trim()), Math.max(0, Integer.parseInt(parts[3].trim())));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
