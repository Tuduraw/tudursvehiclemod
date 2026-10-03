package com.example.tudursvehiclemod.asset;

import java.util.List;

/**
 * Full configuration of a WeaponType.MIDGET weapon - a weapon that launches a submarine (a midget
 * submarine) from the mounting position of the weapon itself, the way WeaponType.CARRIER launches an
 * aircraft, to fly a fixed route by itself.
 *
 * <p>Parsed from a weapon.txt file's own "MidgetVehicle" / "MidgetWeaponIndex" / "MidgetAccuracy" /
 * "MidgetTimeout" / "MidgetStuckTimeout" / "MidgetYawOffset" / "MidgetTargetYawOffset" /
 * "MidgetDetectRange" / "MidgetDetectInterval" / "MidgetAvoidStep" / "MidgetRecovery" keys and
 * the repeated "MidgetWaypoint" and "MidgetLaunchWaypoint" lines.
 *
 * <p>Unlike CarrierAircraftConfig this has no formation, no runway and no landing route: a submarine
 * has no deck to land on. It comes home along its own route and is recovered when it reaches the
 * mounting position again (when recovery is on).
 *
 * <p>The waypoints use the same "relative to the marked point" convention as a Carrier's: the point
 * the shooter marks with the crosshair (see CasTargetMode) is the route's centre, rotated to face the
 * way the shooter faced. What differs is the vertical coordinate. A midget is steered by DEPTH, like a
 * torpedo: the Y of a {@link MidgetWaypoint} is a depth below the water surface at that spot, in blocks
 * (positive = deeper, 0 = at the surface), not a height.
 *
 * <p>detectRange / detectIntervalTicks / avoidStep are the collision avoidance: every
 * detectIntervalTicks the submarine looks detectRange blocks ahead for terrain and, if the way is
 * blocked, surfaces avoidStep blocks at a time, looking again at every step. See
 * entity.MidgetNavigator.
 *
 * <p>attackRange (MidgetAttackRange) is how close a midget launched in lock mode gets to its designated target before
 * attacking it - see entity.SubmarineEntity's own tudursvehiclemod$setMidgetDesignatedTarget().
 */
public record MidgetConfig(String vehicleFileName, int weaponIndex, float accuracy, int timeoutTicks, int stuckTimeoutTicks,
		float yawOffsetDegrees, float targetYawOffsetDegrees, List<MidgetWaypoint> launchWaypoints, List<MidgetWaypoint> waypoints,
		double detectRange, int detectIntervalTicks, double avoidStep, boolean recovery, double attackRange) {

	/**
	 * One stop of a midget's route: x/z in blocks (relative to the marked point for a route waypoint,
	 * relative to the mounting position for a launch waypoint), depth below the water surface in
	 * blocks, the speed as a fraction of the submarine's dive speed, and whether it fires its attack
	 * weapon while on the leg that leads to this stop.
	 */
	public record MidgetWaypoint(int relX, int depth, int relZ, float speedFraction, boolean attack) {

		/**
		 * Parses one "relX,depth,relZ,speedPercent,attack" value - null on anything malformed (the caller
		 * warns and skips the line), so one bad line does not take the whole weapon file down. The depth
		 * must not be negative: a depth is measured down from the surface, so a negative one would put the
		 * waypoint in the air.
		 */
		public static MidgetWaypoint parse(String line) {
			String[] parts = line.split(",", -1);
			if (parts.length != 5) {
				return null;
			}
			Boolean attack = switch (parts[4].strip().toLowerCase(java.util.Locale.ROOT)) {
				case "true", "1" -> Boolean.TRUE;
				case "false", "0" -> Boolean.FALSE;
				default -> null;
			};
			if (attack == null) {
				return null;
			}
			try {
				int depth = Integer.parseInt(parts[1].strip());
				if (depth < 0) {
					return null;
				}
				float speedPercent = Float.parseFloat(parts[3].strip());
				float speedFraction = Math.max(0.05f, Math.min(1.0f, speedPercent / 100f));
				return new MidgetWaypoint(Integer.parseInt(parts[0].strip()), depth, Integer.parseInt(parts[2].strip()), speedFraction, attack);
			} catch (NumberFormatException e) {
				return null;
			}
		}
	}
}
