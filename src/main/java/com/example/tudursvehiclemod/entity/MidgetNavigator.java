package com.example.tudursvehiclemod.entity;

/**
 * The navigation brain of a launched midget submarine (WeaponType.MIDGET): which depth to hold, and
 * whether to hold still - everything that does not need a Minecraft world. The entity feeds it the
 * submarine's depth and a way to ask "is the straight way ahead blocked?" (a {@link PathProbe}), and
 * reads back a target depth, a pitch and a direct vertical speed to steer by. Keeping the decision
 * logic here, with no Minecraft types in it, is what allows it to be exercised on its own against
 * simulated seabeds.
 *
 * <p>Depth is measured downward from the water surface in blocks (positive = deeper, 0 = at the
 * surface), the same convention as a torpedo's TargetDepth and as a MidgetWaypoint's depth.
 *
 * <h3>Two ways to change depth</h3>
 * <p>Like the base mod's own submarine (which already has a ballast-tank-style buoyancy spring for its
 * ascend key, independent of pitch - see AbstractVehicleEntity's own SURFACE_SPRING_STRENGTH /
 * SURFACE_VERTICAL_DAMPING), a midget changes depth two ways at once: pitching to glide up or down
 * while under way ({@link #targetPitch}), and a direct vertical speed from ballast control
 * ({@link #buoyancyVelocityY}) that is independent of pitch and of forward speed. The same spring
 * formula as the ascend key, generalised from "aim for the true surface" to "aim for any depth", gives
 * a far faster, more controllable response than pitch alone (an error of several blocks is roughly
 * halved every 10 ticks, reaching a modest peak speed) - this is what actually does the work of
 * avoiding something ahead, with the pitch's own shallower, slower response along for a natural
 * gliding attitude rather than carrying the correction by itself.
 *
 * <h3>Collision avoidance</h3>
 * <ul>
 * <li>Every {@code detectIntervalTicks} the way ahead is probed over {@code detectRange} blocks, along the
 * path the submarine is about to take (toward the depth it is heading for).
 * <li>If it is blocked the submarine starts to avoid: it heads continuously for the surface (so both the
 * buoyancy spring and the pitch pull it upward) and, <b>each time its depth has risen past another
 * {@code avoidStep}-block mark, probes again at once</b> mid-rise - without waiting for the interval -
 * since a single step's worth of rise may not clear the obstacle. It keeps rising, checking again at
 * every such mark, until a probe at one of them finds the way clear; only then does it level off there.
 * <li>Once clear it levels off and <b>holds that depth</b> as a ceiling on how deep it will go
 * ({@link #depthCap}). It goes back toward the route depth only a step at a time, on the ordinary
 * interval, and only when the way ahead at the deeper depth is clear - so it does not bob up and
 * down against terrain it has just cleared.
 * <li>At the surface there is nowhere left to rise. If the way is still blocked there (land ahead),
 * the submarine holds still ({@link #mustHold()}) until the way opens - it never drives into it.
 * </ul>
 */
public final class MidgetNavigator {

	/**
	 * Answers whether the way ahead is blocked if the submarine headed for targetDepth. The probe knows the
	 * submarine's own actual current depth (it is a closure over the entity's own state) and decides for
	 * itself how the transition from there to targetDepth actually unfolds along the way - the navigator
	 * itself has no model of that (ray shape, transition distance, pitch vs. buoyancy mix all belong to the
	 * actual physics, not to this plain decision logic) and only ever asks "is it safe to go THERE". See
	 * MidgetHullProbe's own doc for the concrete implementation this mod uses.
	 */
	@FunctionalInterface
	public interface PathProbe {
		/**
		 * @param rangeBlocks how far ahead to look, once at targetDepth
		 * @param targetDepth the depth (blocks) being considered
		 * @return true if the hull would hit something getting to and then cruising at targetDepth
		 */
		boolean blocked(double rangeBlocks, double targetDepth);
	}

	/** Default look-ahead (blocks) - see MidgetConfig. */
	public static final double DEFAULT_DETECT_RANGE = 50.0;
	/** Default probing interval (ticks) - see MidgetConfig. */
	public static final int DEFAULT_DETECT_INTERVAL = 10;
	/** Default surfacing step (blocks) - see MidgetConfig. */
	public static final double DEFAULT_AVOID_STEP = 1.0;

	/** How close (blocks) to a depth counts as having reached it. */
	static final double DEPTH_TOLERANCE = 0.15;
	/** Depth (blocks) at or above which the submarine counts as being at the surface: there is nothing higher to rise to. */
	static final double SURFACE_DEPTH = 0.05;
	private final double detectRange;
	private final int detectIntervalTicks;
	private final double avoidStep;

	private int ticksSinceProbe;
	private boolean avoiding;
	/** While avoiding: the depth at which the submarine probes next - one step above the last probe, or above where it started. It keeps rising toward the surface past it; reaching it is only the cue to look again. */
	private double nextProbeDepth;
	/** The deepest the submarine may currently go (blocks), set by the last avoidance; infinity until the first one. */
	private double depthCap = Double.POSITIVE_INFINITY;
	private boolean mustHold;

	public MidgetNavigator(double detectRange, int detectIntervalTicks, double avoidStep) {
		this.detectRange = Math.max(1.0, detectRange);
		this.detectIntervalTicks = Math.max(1, detectIntervalTicks);
		this.avoidStep = Math.max(0.1, avoidStep);
		// The first probe happens on the very first tick rather than only after a full interval.
		this.ticksSinceProbe = this.detectIntervalTicks;
	}

	public static MidgetNavigator withDefaults() {
		return new MidgetNavigator(DEFAULT_DETECT_RANGE, DEFAULT_DETECT_INTERVAL, DEFAULT_AVOID_STEP);
	}

	public boolean isAvoiding() {
		return this.avoiding;
	}

	/** True when the way ahead is blocked and the submarine is already as high as it can get: it should hold still rather than advance. */
	public boolean mustHold() {
		return this.mustHold;
	}

	public double depthCap() {
		return this.depthCap;
	}

	/**
	 * One tick. Returns the depth the submarine should be heading for (never deeper than routeDepth, never
	 * deeper than the cap an earlier avoidance left, never above the surface).
	 *
	 * @param routeDepth the depth the route calls for here (blocks, positive = deeper)
	 * @param depthNow   the submarine's depth now
	 * @param probe      asks whether the way ahead is blocked
	 */
	public double tick(double routeDepth, double depthNow, PathProbe probe) {
		double route = Math.max(0.0, routeDepth);
		double now = Math.max(0.0, depthNow);
		this.ticksSinceProbe++;
		if (this.avoiding) {
			if (now <= this.nextProbeDepth + DEPTH_TOLERANCE) {
				// Has risen another step: look again at once, whatever the interval says - one step may not have been enough.
				this.ticksSinceProbe = 0;
				if (probe.blocked(this.detectRange, now)) {
					this.riseFurther(now);
				} else {
					this.avoiding = false;
					this.mustHold = false;
					// Level off here and keep this depth as the ceiling on going deeper.
					this.depthCap = Math.max(0.0, now);
				}
			}
		} else if (this.ticksSinceProbe >= this.detectIntervalTicks) {
			this.ticksSinceProbe = 0;
			double heading = Math.min(route, this.depthCap);
			if (probe.blocked(this.detectRange, heading)) {
				this.avoiding = true;
				this.riseFurther(now);
			} else {
				this.mustHold = false;
				// Clear: go back toward the route depth one step at a time, only if the way is clear at the deeper depth too.
				if (this.depthCap < route) {
					double deeper = Math.min(route, this.depthCap + this.avoidStep);
					if (!probe.blocked(this.detectRange, deeper)) {
						this.depthCap = deeper;
					}
				}
			}
		}
		if (this.avoiding) {
			// Keeps rising toward the surface; the probes (above) decide when to stop.
			return 0.0;
		}
		return Math.max(0.0, Math.min(route, this.depthCap));
	}

	/** Sets the depth of the next probe one step above where the submarine is; at the surface there is no higher to go, so it must hold still. */
	private void riseFurther(double now) {
		if (now <= SURFACE_DEPTH) {
			this.nextProbeDepth = 0.0;
			this.mustHold = true;
			return;
		}
		this.nextProbeDepth = Math.max(0.0, now - this.avoidStep);
		this.mustHold = false;
	}

	/** The steepest pitch (degrees) a midget is steered to: a gentle gliding attitude - most of the actual depth change is the buoyancy spring's own job (see {@link #buoyancyVelocityY}), not the hull's own slow-to-respond pitch. The same angle whether avoiding or not: it is the buoyancy spring, not a steeper pitch, that does the extra work while avoiding. */
	public static final float MAX_STEER_PITCH_DEGREES = 15.0f;
	/** Depth error (blocks) at which the pitch reaches its maximum: the pitch is proportional to the error up to here, so the boat flattens out smoothly as it nears the depth rather than overshooting. */
	static final double PITCH_FULL_ERROR_BLOCKS = 6.0;
	/** Same convention as AbstractVehicleEntity's own SURFACE_SPRING_STRENGTH - how strongly the buoyancy spring below reacts to a depth error. */
	public static final double BUOYANCY_SPRING_STRENGTH = 0.02;
	/** Same convention as AbstractVehicleEntity's own SURFACE_VERTICAL_DAMPING. */
	public static final double BUOYANCY_VERTICAL_DAMPING = 0.8;
	/** Caps the buoyancy spring's own vertical speed (blocks/tick) - a real boat's ballast pumps move water at a finite rate regardless of how large the depth error is. High enough that the spring's own natural peak (reached well before this, for any error this navigator actually produces - see the class's own doc) is never actually clipped by it; this exists only as an outer safety bound. */
	static final double BUOYANCY_MAX_SPEED = 1.0;
	/** Within this many blocks of the surface the pitch is not allowed to point the nose upward any more than a gentle amount - a hull driven hard upward at the surface breaks it, and the submarine's own broaching correction would only have to fight it. */
	static final double SURFACE_NOSE_UP_ZONE_BLOCKS = 2.0;
	/** The nose-up pitch (degrees, magnitude) still allowed inside that zone, at the surface itself. */
	static final float SURFACE_NOSE_UP_LIMIT_DEGREES = 3.0f;

	/**
	 * The pitch (degrees, Minecraft's convention: positive = nose DOWN = heading deeper) to steer to in
	 * order to reach goalDepth from depthNow. Proportional to the depth error, saturating at
	 * {@link #MAX_STEER_PITCH_DEGREES}, so the boat levels off smoothly as it arrives. Nose-up (negative)
	 * pitch is limited near the surface, linearly from the full limit at {@link #SURFACE_NOSE_UP_ZONE_BLOCKS}
	 * down to {@link #SURFACE_NOSE_UP_LIMIT_DEGREES} at depth 0, so it never breaks the surface hard.
	 */
	public static float targetPitch(double goalDepth, double depthNow) {
		double error = goalDepth - depthNow;
		double fraction = Math.max(-1.0, Math.min(1.0, error / PITCH_FULL_ERROR_BLOCKS));
		float pitch = (float) (fraction * MAX_STEER_PITCH_DEGREES);
		if (pitch < 0f) {
			double zone = Math.max(0.0, Math.min(1.0, depthNow / SURFACE_NOSE_UP_ZONE_BLOCKS));
			float limit = SURFACE_NOSE_UP_LIMIT_DEGREES + (float) (zone * (MAX_STEER_PITCH_DEGREES - SURFACE_NOSE_UP_LIMIT_DEGREES));
			pitch = Math.max(-limit, pitch);
		}
		return pitch;
	}

	/**
	 * Direct, pitch-independent vertical speed (blocks/tick, positive = rising) from ballast control, toward
	 * goalDepth from depthNow - the same spring-damper the base mod's own ascend key already uses
	 * (AbstractVehicleEntity's own tudursvehiclemod$applySurfaceFloatSpring(), there aimed only at the true
	 * water surface), generalised to any target depth and exposed here as a plain function of the error and
	 * the current vertical speed, so the caller supplies its own currentVelocityY (the hull's actual vertical
	 * speed this tick) and gets back what it should be next. See the class's own doc for why this, not pitch,
	 * does most of the actual work of changing depth.
	 */
	public static double buoyancyVelocityY(double goalDepth, double depthNow, double currentVelocityY) {
		double error = depthNow - goalDepth; // depthNow > goalDepth (too deep) must rise: positive Y velocity.
		double next = (currentVelocityY + error * BUOYANCY_SPRING_STRENGTH) * BUOYANCY_VERTICAL_DAMPING;
		return Math.max(-BUOYANCY_MAX_SPEED, Math.min(BUOYANCY_MAX_SPEED, next));
	}

	/** Moves currentPitch toward target by at most maxStep degrees. */
	public static float approachPitch(float currentPitch, float target, float maxStep) {
		float diff = target - currentPitch;
		if (Math.abs(diff) <= maxStep) {
			return target;
		}
		return currentPitch + Math.copySign(maxStep, diff);
	}

	/** Within this horizontal distance (blocks) of a waypoint the submarine counts as having reached it and moves on to the next. */
	public static final double WAYPOINT_REACHED_BLOCKS = 4.0;
	/** A yaw error (degrees) at which the rudder is fully over; smaller errors turn it proportionally, so the boat settles on the bearing instead of weaving across it. */
	static final float FULL_RUDDER_ERROR_DEGREES = 30.0f;
	/** Beyond this yaw error (degrees) the boat slows down to turn rather than drive on at full speed in the wrong direction. */
	static final float SLOW_TURN_ERROR_DEGREES = 60.0f;

	/**
	 * The bearing (degrees, Minecraft's yaw convention: 0 faces +Z, increasing clockwise seen from above) from
	 * (fromX, fromZ) to (toX, toZ).
	 */
	public static float bearingDegrees(double fromX, double fromZ, double toX, double toZ) {
		return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
	}

	/** Wraps an angle in degrees to [-180, 180). */
	public static float wrapDegrees(float degrees) {
		float d = degrees % 360.0f;
		if (d >= 180.0f) {
			d -= 360.0f;
		} else if (d < -180.0f) {
			d += 360.0f;
		}
		return d;
	}

	/**
	 * The rudder input in [-1, 1] that turns a boat whose yaw is currentYaw toward desiredYaw, in the
	 * convention of SubmarineEntity's own steering: the boat's yaw changes by {@code -input * turnRate} each
	 * tick, so a desired yaw that is greater than the current one (positive error) needs a NEGATIVE input.
	 */
	public static float rudderInput(float currentYaw, float desiredYaw) {
		float error = wrapDegrees(desiredYaw - currentYaw);
		float fraction = Math.max(-1.0f, Math.min(1.0f, error / FULL_RUDDER_ERROR_DEGREES));
		return -fraction;
	}

	/**
	 * The throttle input in [-1, 1] to ask for while heading for a waypoint: the waypoint's own speed fraction when
	 * the boat is roughly pointed at it, reduced while it is still turning round (it slows to a fraction of the
	 * speed once the error passes {@link #SLOW_TURN_ERROR_DEGREES}), and zero when the way ahead is blocked and it
	 * cannot rise any further ({@link #mustHold()}).
	 */
	public static float throttleTarget(float speedFraction, float yawErrorDegrees, boolean hold) {
		if (hold) {
			return 0f;
		}
		float error = Math.abs(wrapDegrees(yawErrorDegrees));
		float scale = error >= SLOW_TURN_ERROR_DEGREES ? 0.3f : 1.0f - 0.7f * (error / SLOW_TURN_ERROR_DEGREES);
		return Math.max(0f, Math.min(1.0f, speedFraction)) * scale;
	}

	/** How close (as a fraction of full throttle) the throttle must already be to its target before the input is released to neutral, so it settles instead of flickering between up and down. */
	static final float THROTTLE_DEADBAND = 0.03f;

	/**
	 * The throttle INPUT (-1, 0 or +1) that takes the current throttle toward a target. SubmarineEntity's own
	 * throttle ramp only looks at the SIGN of its input (it steps the throttle up or down by a fixed amount per
	 * tick, like a player holding W or S), so a target speed has to be expressed as "keep pushing up", "keep
	 * pushing down" or "let go" - and let go once within a small deadband of the target, or it would flicker
	 * around it.
	 */
	public static float throttleInput(float currentThrottle, float targetThrottle) {
		float error = targetThrottle - currentThrottle;
		if (Math.abs(error) <= THROTTLE_DEADBAND) {
			return 0f;
		}
		return error > 0f ? 1.0f : -1.0f;
	}

	/** What a midget chasing a designated target should do this tick - see {@link #pursuitDecision}. */
	public enum Pursuit {
		/** Keep heading for the target. */
		CHASE,
		/** In range and pointed at it: attack (and, once a shot actually goes out, stop chasing). */
		ATTACK,
		/** Ran past it at close range: stop chasing and carry on with the route rather than turn back. */
		GIVE_UP
	}

	/** How far (degrees) the heading may be off the target's bearing for an attack. */
	public static final float PURSUIT_ATTACK_AIM_TOLERANCE_DEGREES = 15f;

	/**
	 * A midget chasing a designated target (the mothership fired its Midget weapon in lock mode): attack once within
	 * attackRange and pointed at the target; give up once the target is BEHIND it at close range (within about two turn
	 * radii) - a target it has run past and could only reach by turning back, which with a turning circle about as big as
	 * the distance left is exactly how an endless orbit around the target starts. A target behind it but further away is
	 * still chased (far away, turning round cannot become an orbit).
	 */
	public static Pursuit pursuitDecision(double x, double z, float yaw, double targetX, double targetZ, double attackRange, double turnRadius) {
		double dx = targetX - x;
		double dz = targetZ - z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		float error = Math.abs(wrapDegrees(bearingDegrees(x, z, targetX, targetZ) - yaw));
		if (distance <= attackRange && error <= PURSUIT_ATTACK_AIM_TOLERANCE_DEGREES) {
			return Pursuit.ATTACK;
		}
		if (error > 90f && distance <= 2.0 * turnRadius + WAYPOINT_REACHED_BLOCKS) {
			return Pursuit.GIVE_UP;
		}
		return Pursuit.CHASE;
	}

	/** After running past the target (Pursuit.GIVE_UP), how far out is far enough to turn back in for another attack: beyond the attack range by two turn radii, so the turn back cannot bring it in close enough to start circling the target. */
	public static boolean extendedFarEnough(double distanceToTarget, double attackRange, double turnRadius) {
		return distanceToTarget >= attackRange + 2.0 * turnRadius + WAYPOINT_REACHED_BLOCKS;
	}

	/** Back to a fresh start (used when a new route leg begins that should not inherit an old ceiling). */
	public void reset() {
		this.avoiding = false;
		this.mustHold = false;
		this.depthCap = Double.POSITIVE_INFINITY;
		this.ticksSinceProbe = this.detectIntervalTicks;
	}
}
