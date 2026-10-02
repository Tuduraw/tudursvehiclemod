package com.example.tudursvehiclemod.entity;

/**
 * The geometry of a midget submarine's "is the way ahead blocked?" check (see {@link MidgetNavigator.PathProbe}),
 * separated from the block lookups themselves so it can be tested without a world.
 *
 * <p>A submarine is not a point: a ray down its centre line would slip past a rock that the hull's own
 * width or height would strike. So the way ahead is tested with {@link #RAY_COUNT} parallel rays, one down
 * the centre of the hull and one from each of its four sides (above, below, left and right), all running
 * the same direction for the whole look-ahead. The hull is blocked if ANY of them meets terrain.
 *
 * <p>Directions follow Minecraft's convention (yaw 0 faces +Z) and the depth convention of the navigator
 * (positive depth = deeper, so Y decreases as depth increases).
 *
 * <h3>Why the check is flat at the candidate depth, not a ramp toward it</h3>
 * <p>{@link MidgetNavigator.PathProbe} asks "is it safe to head for targetDepth", not for a slope - checking a
 * single ray that ramps GRADUALLY from the current depth to targetDepth over the whole look-ahead does not
 * match how a midget's depth actually changes. Its buoyancy spring (see MidgetNavigator's own doc) reaches
 * most of a depth change within a fairly short distance, then levels off - not spread evenly over the full
 * detect range. A probe that assumed a gentle ramp across the whole range read as clear in cases where the
 * actual, much faster descent dipped into the last stretch of an obstacle before it had fully cleared it -
 * confirmed by simulation against a seven-block ridge, which a gradual-ramp check let the submarine collide
 * with. The fix is to assume the WORST case for safety: the submarine is already at targetDepth, right now,
 * for the whole look-ahead - {@link #blocked} checks exactly that flat path. A gradual real transition only
 * ever behaves better than this (it spends part of the look-ahead shallower than targetDepth, where this
 * class's own ridge was, by construction, never a problem for a path already proven clear at that depth
 * from here on), so the straightforward check is also the correct one, not merely a convenient simplification.
 */
public final class MidgetHullProbe {

	private MidgetHullProbe() {
	}

	/** Centre plus four sides. */
	public static final int RAY_COUNT = 5;

	/** A way of asking whether a straight segment of the world meets terrain. */
	@FunctionalInterface
	public interface SegmentQuery {
		/** True if the segment from (x0, y0, z0) to (x1, y1, z1) meets a solid block (water does not count). */
		boolean hitsTerrain(double x0, double y0, double z0, double x1, double y1, double z1);
	}

	/**
	 * Whether a hull would run into terrain heading for targetDepth from (x, y, z): flat at targetDepth (not a
	 * ramp from the current depth - see the class's own doc for why), for rangeBlocks ahead on heading
	 * yawDegrees.
	 *
	 * @param depthNow    the submarine's own actual depth at (x, y, z) - needed only to work out how far (in
	 *                    world Y) targetDepth is from here
	 * @param halfWidth   half the hull's width (blocks), used for the left/right rays
	 * @param halfHeight  half the hull's height (blocks), used for the top/bottom rays
	 */
	public static boolean blocked(SegmentQuery world, double x, double y, double z, float yawDegrees,
			double halfWidth, double halfHeight, double rangeBlocks, double depthNow, double targetDepth) {
		double targetY = y - (targetDepth - depthNow);
		double yaw = Math.toRadians(yawDegrees);
		// Forward and right (horizontal) unit vectors for this heading. Minecraft: yaw 0 faces +Z, and the hull's right is -X at yaw 0.
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double dx = fx * rangeBlocks;
		double dz = fz * rangeBlocks;
		double[][] offsets = {
				{0.0, 0.0, 0.0},
				{0.0, halfHeight, 0.0},
				{0.0, -halfHeight, 0.0},
				{rx * halfWidth, 0.0, rz * halfWidth},
				{-rx * halfWidth, 0.0, -rz * halfWidth}};
		for (double[] o : offsets) {
			double sx = x + o[0];
			double sy = targetY + o[1];
			double sz = z + o[2];
			if (world.hitsTerrain(sx, sy, sz, sx + dx, sy, sz + dz)) {
				return true;
			}
		}
		return false;
	}
}
