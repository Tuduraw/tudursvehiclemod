package com.example.tudursvehiclemod.entity.projectile;

import net.minecraft.util.math.Vec3d;

/** The one trajectory model every range estimate uses - the server's CAS/Carrier/Midget target point, the AI's bomb release, and the HUD's distance number and impact marker - so they all agree with each other and with the shell actually fired.
 *
 * <p>It is the real projectile's own motion: VehicleProjectileEntity is a ThrownItemEntity, whose tick (ThrownEntity.tick()) first takes gravity off the vertical velocity, then multiplies the whole velocity by the air drag, then moves by it. The copies this replaced each ran their own loop, drag first and gravity second, the HUD marker without drag at all, and they had drifted apart in their edge cases. */
public final class Ballistics {
	/** ThrownEntity's air drag (applyDrag()). */
	public static final double AIR_DRAG_PER_TICK = 0.99;

	private Ballistics() {
	}

	/** One tick of the real projectile's velocity: gravity, then drag. */
	public static Vec3d step(Vec3d velocity, double gravity) {
		return new Vec3d(velocity.x, velocity.y - gravity, velocity.z).multiply(AIR_DRAG_PER_TICK);
	}

	/** Horizontal distance at which a shot launched with horizontalSpeed/verticalSpeed comes back down to its launch height, or -1 if it never rises above it at all (aimed level or down, or so shallow that the first tick already drops - those used to come out as a range of 0, i.e. "the shooter's own position"), or if gravity is not positive, or nothing comes back within maxTicks. */
	public static double levelReturnDistance(double horizontalSpeed, double verticalSpeed, double gravity, int maxTicks) {
		if (gravity <= 0.0 || verticalSpeed <= 0.0) {
			return -1.0;
		}
		double vh = horizontalSpeed;
		double vv = verticalSpeed;
		double distance = 0.0;
		double height = 0.0;
		for (int tick = 0; tick < maxTicks; tick++) {
			vv = (vv - gravity) * AIR_DRAG_PER_TICK;
			vh *= AIR_DRAG_PER_TICK;
			double nextHeight = height + vv;
			if (nextHeight <= 0.0) {
				if (tick == 0) {
					return -1.0;
				}
				// Interpolated within the last tick rather than snapped to a whole tick.
				double fraction = height / (height - nextHeight);
				return distance + vh * fraction;
			}
			distance += vh;
			height = nextHeight;
		}
		return -1.0;
	}

	/** A generous tick cap for levelReturnDistance(): three times the drag-free flight time, and never under a minute. */
	public static int levelReturnTickCap(double verticalSpeed, double gravity) {
		if (gravity <= 0.0) {
			return 1200;
		}
		return (int) Math.max(1200.0, 2.0 * verticalSpeed / gravity * 3.0);
	}

	/** Where a shot from origin with initialVelocity comes down through height targetY (interpolated within the last tick). If it hasn't within maxTicks, wherever it is then, put at targetY. */
	public static Vec3d fallTo(Vec3d origin, Vec3d initialVelocity, double gravity, double targetY, int maxTicks) {
		Vec3d position = origin;
		Vec3d velocity = initialVelocity;
		for (int tick = 0; tick < maxTicks; tick++) {
			velocity = step(velocity, gravity);
			Vec3d next = position.add(velocity);
			if (next.y <= targetY) {
				double fraction = (position.y - targetY) / Math.max(position.y - next.y, 1.0e-6);
				return new Vec3d(position.x + velocity.x * fraction, targetY, position.z + velocity.z * fraction);
			}
			position = next;
		}
		return new Vec3d(position.x, targetY, position.z);
	}

	/** A launch elevation found by solveElevation(): degrees above the horizontal (negative = below), and how many ticks the shot takes to get there. */
	public record Elevation(double degreesUp, double flightTicks) {
	}

	/** Elevation grid solveElevation() first scans for crossings, before refining each by bisection: every half degree from straight-ish down to straight-ish up. */
	private static final double SOLVE_MIN_DEGREES = -80.0;
	private static final double SOLVE_MAX_DEGREES = 85.0;
	private static final double SOLVE_STEP_DEGREES = 0.5;

	/** The inverse of the forward simulations above: the elevation to launch at, with speed blocks/tick, to pass through a point horizontalDistance away and heightDifference above the muzzle - flown exactly as the real projectile flies (step()), within maxTicks. Null if no elevation reaches it (too far, or too high for this speed).
	 *
	 * <p>A shell can usually get there two ways - flat (the lower elevation) or lobbed over (the higher one, coming down steeply, which clears what is in between). highArc picks which; when only one way exists (a target near the limit of range, or gravity 0, where the path is a straight line) that one is returned either way. */
	public static Elevation solveElevation(double horizontalDistance, double heightDifference, double speed, double gravity,
			boolean highArc, int maxTicks) {
		if (!(speed > 0.0) || !Double.isFinite(horizontalDistance) || !Double.isFinite(heightDifference)) {
			return null;
		}
		if (horizontalDistance < 1.0e-3) {
			// Straight up or down - only meaningful for a weapon that can point there at all.
			return heightDifference >= 0.0 ? new Elevation(90.0, 0.0) : new Elevation(-90.0, 0.0);
		}
		java.util.List<double[]> brackets = new java.util.ArrayList<>();
		double previousDegrees = Double.NaN;
		double previousMiss = Double.NaN;
		for (double degrees = SOLVE_MIN_DEGREES; degrees <= SOLVE_MAX_DEGREES + 1.0e-9; degrees += SOLVE_STEP_DEGREES) {
			double[] arrival = heightAtDistance(degrees, horizontalDistance, speed, gravity, maxTicks);
			double miss = arrival == null ? Double.NaN : arrival[0] - heightDifference;
			if (!Double.isNaN(miss) && !Double.isNaN(previousMiss) && (miss == 0.0 || Math.signum(miss) != Math.signum(previousMiss))) {
				brackets.add(new double[]{previousDegrees, degrees});
			}
			previousDegrees = degrees;
			previousMiss = miss;
		}
		if (brackets.isEmpty()) {
			return null;
		}
		double[] bracket = highArc ? brackets.get(brackets.size() - 1) : brackets.get(0);
		double low = bracket[0];
		double high = bracket[1];
		double[] lowArrival = heightAtDistance(low, horizontalDistance, speed, gravity, maxTicks);
		double lowMiss = lowArrival[0] - heightDifference;
		for (int i = 0; i < 40; i++) {
			double mid = (low + high) * 0.5;
			double[] midArrival = heightAtDistance(mid, horizontalDistance, speed, gravity, maxTicks);
			if (midArrival == null) {
				// Inside a bracket whose ends both arrive, a gap is only possible at the edge of reach: keep the arriving side.
				high = mid;
				continue;
			}
			double midMiss = midArrival[0] - heightDifference;
			if (Math.signum(midMiss) == Math.signum(lowMiss)) {
				low = mid;
				lowMiss = midMiss;
			} else {
				high = mid;
			}
		}
		double degrees = (low + high) * 0.5;
		double[] arrival = heightAtDistance(degrees, horizontalDistance, speed, gravity, maxTicks);
		if (arrival == null) {
			arrival = heightAtDistance(low, horizontalDistance, speed, gravity, maxTicks);
			degrees = low;
		}
		return arrival == null ? null : new Elevation(degrees, arrival[1]);
	}

	/** {height relative to the muzzle, ticks} where a shot launched at elevationDegrees reaches horizontalDistance (interpolated within the tick), or null if it doesn't get that far within maxTicks. */
	private static double[] heightAtDistance(double elevationDegrees, double horizontalDistance, double speed, double gravity, int maxTicks) {
		double radians = Math.toRadians(elevationDegrees);
		double vh = speed * Math.cos(radians);
		double vv = speed * Math.sin(radians);
		double x = 0.0;
		double y = 0.0;
		for (int tick = 0; tick < maxTicks; tick++) {
			vv = (vv - gravity) * AIR_DRAG_PER_TICK;
			vh *= AIR_DRAG_PER_TICK;
			double nextX = x + vh;
			double nextY = y + vv;
			if (nextX >= horizontalDistance) {
				double fraction = (horizontalDistance - x) / Math.max(nextX - x, 1.0e-9);
				return new double[]{y + (nextY - y) * fraction, tick + fraction};
			}
			if (vh < 1.0e-4) {
				return null;
			}
			x = nextX;
			y = nextY;
		}
		return null;
	}

	/** Casts a ray along one tick of flight; the hit point, or null if nothing was hit. */
	@FunctionalInterface
	public interface StepRaycast {
		Vec3d hit(Vec3d from, Vec3d to);
	}

	/** Where the flight ended: the block hit, or (hitBlock false) the point where it fell below the world, ran past maxHorizontalDistance, or ran out of ticks. */
	public record Impact(Vec3d pos, boolean hitBlock) {
	}

	/** Flies a shot tick by tick, casting a ray along each tick of flight, until it hits a block. */
	public static Impact flyUntilBlocked(Vec3d origin, Vec3d initialVelocity, double gravity, double worldBottomY,
			double maxHorizontalDistance, int maxTicks, StepRaycast raycast) {
		Vec3d position = origin;
		Vec3d velocity = initialVelocity;
		for (int tick = 0; tick < maxTicks; tick++) {
			velocity = step(velocity, gravity);
			Vec3d next = position.add(velocity);
			Vec3d hit = raycast.hit(position, next);
			if (hit != null) {
				return new Impact(hit, true);
			}
			double dx = next.x - origin.x;
			double dz = next.z - origin.z;
			if (next.y < worldBottomY || dx * dx + dz * dz > maxHorizontalDistance * maxHorizontalDistance) {
				return new Impact(next, false);
			}
			position = next;
		}
		return new Impact(position, false);
	}
}
