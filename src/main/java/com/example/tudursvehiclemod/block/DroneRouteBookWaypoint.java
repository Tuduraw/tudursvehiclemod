package com.example.tudursvehiclemod.block;

/** A single recorded stop in item.DroneRouteBookItem's own patrol route. Unlike DroneWaypoint (relative to a specific Drone Center's own block position), this stores ABSOLUTE world coordinates - recording a waypoint by walking to it was awkward while the Drone Center's own config screen was open (barely any room to move while a GUI is open), this item is meant to be carried and used freely anywhere in the world, with no Drone Center nearby at all - only once inserted into a specific center does a route ever become relative to that center (see DroneCenterBlockEntity's own tudursvehiclemod$getWaypoints()/setWaypoints() doc for where that conversion actually happens). rollAngle, rollManeuverabilityMultiplier, and turnManeuverabilityMultiplier have the same meaning as DroneWaypoint's own fields of the same name. */
public record DroneRouteBookWaypoint(int x, int y, int z, float speedFraction, float rollAngle,
									  float rollManeuverabilityMultiplier, float turnManeuverabilityMultiplier) {

	public static DroneRouteBookWaypoint createDefault(int x, int y, int z) {
		return new DroneRouteBookWaypoint(x, y, z, 0.5f, 0f, 1.0f, 1.0f);
	}

	/** Same CSV encoding shape as DroneWaypoint's own tudursvehiclemod$encode() - just absolute x/y/z here instead of relative. */
	public String tudursvehiclemod$encode() {
		return this.x + "," + this.y + "," + this.z + "," + this.speedFraction + "," + this.rollAngle
				+ "," + this.rollManeuverabilityMultiplier + "," + this.turnManeuverabilityMultiplier;
	}

	/** Inverse of tudursvehiclemod$encode() - returns null (rather than throwing) on anything malformed, so one corrupted entry doesn't take the whole recorded route down with it. Accepts the older 4/5/6-field forms too, same backward-compatibility reasoning as DroneWaypoint's own decode(). */
	public static DroneRouteBookWaypoint tudursvehiclemod$decode(String encoded) {
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
				float combined = Float.parseFloat(parts[5]);
				rollManeuverability = combined;
				turnManeuverability = combined;
			} else {
				rollManeuverability = 1.0f;
				turnManeuverability = 1.0f;
			}
			return new DroneRouteBookWaypoint(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
					Integer.parseInt(parts[2]), Float.parseFloat(parts[3]), rollAngle, rollManeuverability, turnManeuverability);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
