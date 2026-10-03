package com.example.tudursvehiclemod.entity;

/**
 * Where an aircraft attacking with an aerial torpedo (WeaponType.TORPEDO) should be steering, and when it
 * should release - the plain decision logic of the torpedo run, with no Minecraft types in it so it can be
 * exercised on its own. AircraftEntity's own tudursvehiclemod$updateCarrierLockPursuit() feeds it positions
 * every tick and turns what it answers into steering, exactly as it does for its own other attack modes.
 *
 * <p>A torpedo has to be dropped in level flight, some way off the target, and runs straight once in the
 * water - so it is aimed by the line the aircraft flies, not by pointing at the target at the last moment.
 * Ships are its targets, and a ship is best hit on its beam (side on). The run therefore goes:
 * <ol>
 * <li>{@link Phase#POSITION} - fly to the entry point: startDistance out from the target, square off its
 * side (perpendicular to the target's own heading), on whichever side the aircraft already is.
 * <li>{@link Phase#RUN} - turn in and fly the run line (the line through the target, square off its beam) in
 * toward the target, holding the run altitude. The aircraft steers for a point a little further along the line
 * ahead of itself (ordinary line-following, looking {@link #LOOKAHEAD_TURN_RADII} turn radii ahead), not for the
 * target itself, so an aircraft that turned in off the line - having arrived pointing outward, say - closes onto
 * the line first and still comes in on the beam. The line follows the target's CURRENT heading every tick, so a
 * target that turns during the run is still hit on its beam.
 * <li>{@link Phase#HOLD} - from the moment the horizontal distance drops to releaseDistance, keep the
 * heading and stay level for {@link #HOLD_TICKS} ticks, releasing as soon as a shot actually goes out (a
 * cooldown, say, can delay it). The heading is no longer steered from here on. The release is only asked for
 * while the aircraft is pointed at the target (within {@link #RELEASE_AIM_TOLERANCE_DEGREES}) and coming in
 * from the side (within {@link #RELEASE_BEAM_TOLERANCE_DEGREES} of square to the target's heading); a run that
 * gets there badly lined up does not waste its torpedo and simply carries on into the next run, which -
 * starting from straight out along the beam where this one breaks off - lines up properly.
 * <li>{@link Phase#BREAK} - carry straight on, past the target, until startDistance away again; then
 * {@link Phase#POSITION} for the next run, from whichever side it has come out on.
 * </ol>
 *
 * <p>Why the heading is held from the release point onward: steering at the target's bearing all the way in
 * is what made the aircraft circle the target forever. Near the target its bearing swings faster than the
 * aircraft can turn, and a pursuit with a turn radius larger than the release distance settles into an
 * orbit around the target that never comes inside that distance. Committing to a long straight run from far
 * out (startDistance, typically far larger than a turn radius) and never turning back toward the target once
 * close removes that orbit altogether: a run either reaches the release point or overshoots, and an overshoot
 * simply becomes the next BREAK.
 *
 * <p>Directions follow Minecraft's own convention: yaw 0 faces +Z, and a heading's forward vector is
 * (-sin yaw, cos yaw).
 */
public final class TorpedoRunPlanner {

	public enum Phase {
		POSITION, RUN, HOLD, BREAK
	}

	/** Ticks of level, straight flight kept from the release point onward - the window in which a delayed release can still go out. */
	public static final int HOLD_TICKS = 5;
	/** How far (degrees) the heading may be off the target's bearing for the release to go out. */
	static final float RELEASE_AIM_TOLERANCE_DEGREES = 15f;
	/**
	 * How far (degrees) from square to the target's heading the run may come in for the release to go out. The one
	 * case simulation found coming in badly was a FIRST run by a wide-turning aircraft (turn radius 50-80 blocks
	 * against the default 200-block start distance), which enters the run from wherever it happened to be and can
	 * reach the release distance before it has settled onto the beam line - about 0.03% of releases over 3000
	 * random starts. Rather than drop those torpedoes off the beam, the run passes and the next one, which starts
	 * from straight out along the beam, takes the shot.
	 */
	static final float RELEASE_BEAM_TOLERANCE_DEGREES = 30f;

	/** Floor for the arrival radius and the line-following look-ahead (blocks), for a very tight-turning aircraft. */
	static final double MIN_GUIDANCE_RADIUS = 30.0;
	/**
	 * How far ahead along the run line (in turn radii) the aircraft steers. Chosen by simulation over 6000 runs
	 * (turn radii from about 14 to 86 blocks; stationary, straight-running and turning targets): two turn radii
	 * - the first value tried - left the look-ahead point on the target itself for most of a 200-block run once the
	 * turn radius was large, which is just steering at the target, and let up to 77% of releases come in well off
	 * the beam (by more than 30 degrees) against a moving target; half a turn radius, with the line following the
	 * target's current heading, brought that to 0% in every case, with every release still aimed at the target.
	 */
	static final double LOOKAHEAD_TURN_RADII = 0.5;
	/** The run only starts at least this fraction of startDistance out from the target, so arriving "at" the entry point can never mean being right next to the target already. */
	static final double RUN_ENTRY_MIN_DISTANCE_FRACTION = 0.5;

	/** A BREAK that has somehow not got far enough out by now (held off course by obstacle avoidance, say) gives up and sets up a fresh run anyway. */
	static final int MAX_BREAK_TICKS = 600;

	/** What to do this tick. When steerToBearing is false, hold the current heading. tryFire asks the caller to attempt the release (and to call {@link #markFired()} if it actually went out). */
	public record Command(Phase phase, boolean steerToBearing, float bearingDegrees, boolean tryFire, boolean enteredRun) {
	}

	private Phase phase = Phase.POSITION;
	/** Which side of the target's beam this run is set up on: +1/-1, or 0 to choose afresh at the next update. */
	private int sideSign;
	/** The run's own axis (unit, horizontal, from the target toward the entry point), frozen when the run starts and used only to detect an overshoot - so the target turning mid-run cannot make the aircraft appear to have overshot. Steering itself follows the target's current beam (see runLineBearing()). */
	private double runAxisX, runAxisZ;
	private int phaseTicks;
	private boolean fired;

	public Phase phase() {
		return this.phase;
	}

	/** The release went out - stop asking for further attempts during this HOLD. */
	public void markFired() {
		this.fired = true;
	}

	/** Back to the start: a new target (or a new lock) sets up its own first run from scratch. */
	public void reset() {
		this.phase = Phase.POSITION;
		this.sideSign = 0;
		this.phaseTicks = 0;
		this.fired = false;
	}

	/** The entry point (x, z) for the current side - startDistance out from the target, square off its beam. */
	public double[] entryPoint(double tx, double tz, float targetYawDegrees, double startDistance) {
		double[] side = sideVector(targetYawDegrees, this.sideSign == 0 ? 1 : this.sideSign);
		return new double[]{tx + side[0] * startDistance, tz + side[1] * startDistance};
	}

	/**
	 * One tick.
	 *
	 * @param px, pz           the aircraft's own position
	 * @param aircraftYaw      the aircraft's own heading - only used to judge whether the release is lined up
	 * @param tx, tz           the target's position
	 * @param targetYawDegrees the target's own heading
	 * @param startDistance    how far out the run starts (CasAttackStartAltitude, for a torpedo)
	 * @param releaseDistance  how far out the torpedo is released (CasAttackStopAltitude, for a torpedo)
	 * @param turnRadius       the aircraft's own turn radius (blocks) - sets how close to the entry point counts as
	 *                         having reached it (twice this, so circling the point always counts) and how far ahead
	 *                         along the run line it steers
	 *
	 * <p>The run starts once the aircraft is within that radius of the entry point and at least
	 * {@link #RUN_ENTRY_MIN_DISTANCE_FRACTION} of startDistance out from the target. Steering for a fixed point with a
	 * limited turn rate ends, at worst, in circling it at about one turn radius - always inside twice that - so the
	 * entry point is always reached; and a run can never circle the target either, since circling it means crossing its
	 * beam line, which ends the run as an overshoot.
	 */
	public Command update(double px, double pz, float aircraftYaw, double tx, double tz, float targetYawDegrees,
			double startDistance, double releaseDistance, double turnRadius) {
		double guidanceRadius = Math.max(MIN_GUIDANCE_RADIUS, 2.0 * turnRadius);
		double lookAhead = Math.max(MIN_GUIDANCE_RADIUS, LOOKAHEAD_TURN_RADII * turnRadius);
		this.phaseTicks++;
		double dx = px - tx;
		double dz = pz - tz;
		double distance = Math.sqrt(dx * dx + dz * dz);
		switch (this.phase) {
			case POSITION -> {
				if (this.sideSign == 0) {
					double[] n = sideVector(targetYawDegrees, 1);
					this.sideSign = dx * n[0] + dz * n[1] >= 0.0 ? 1 : -1;
				}
				double[] entry = this.entryPoint(tx, tz, targetYawDegrees, startDistance);
				double ex = entry[0] - px;
				double ez = entry[1] - pz;
				if (Math.sqrt(ex * ex + ez * ez) <= guidanceRadius
						&& distance >= startDistance * RUN_ENTRY_MIN_DISTANCE_FRACTION) {
					double[] side = sideVector(targetYawDegrees, this.sideSign);
					this.runAxisX = side[0];
					this.runAxisZ = side[1];
					this.enter(Phase.RUN);
					return new Command(Phase.RUN, true, this.runLineBearing(px, pz, tx, tz, targetYawDegrees, lookAhead), false, true);
				}
				return new Command(Phase.POSITION, true, bearing(px, pz, entry[0], entry[1]), false, false);
			}
			case RUN -> {
				if (distance <= releaseDistance) {
					this.enter(Phase.HOLD);
					this.fired = false;
					return new Command(Phase.HOLD, false, 0f, linedUp(px, pz, aircraftYaw, tx, tz, targetYawDegrees), false);
				}
				// Past the target's own beam line (along the frozen run axis) without having come inside the release distance: this run missed, so let it carry on through as a BREAK rather than turning back at the target - turning back is exactly the circling this class exists to avoid.
				double along = dx * this.runAxisX + dz * this.runAxisZ;
				if (along < 0.0) {
					this.enter(Phase.BREAK);
					return new Command(Phase.BREAK, false, 0f, false, false);
				}
				return new Command(Phase.RUN, true, this.runLineBearing(px, pz, tx, tz, targetYawDegrees, lookAhead), false, false);
			}
			case HOLD -> {
				// phaseTicks counts the ticks since the release point was reached (that tick being 0).
				if (this.phaseTicks >= HOLD_TICKS) {
					this.enter(Phase.BREAK);
					return new Command(Phase.BREAK, false, 0f, false, false);
				}
				return new Command(Phase.HOLD, false, 0f, !this.fired && linedUp(px, pz, aircraftYaw, tx, tz, targetYawDegrees), false);
			}
			case BREAK -> {
				if (distance >= startDistance || this.phaseTicks >= MAX_BREAK_TICKS) {
					this.enter(Phase.POSITION);
					this.sideSign = 0;
					return this.update(px, pz, aircraftYaw, tx, tz, targetYawDegrees, startDistance, releaseDistance, turnRadius);
				}
				return new Command(Phase.BREAK, false, 0f, false, false);
			}
			default -> throw new IllegalStateException();
		}
	}

	/** Bearing to the point lookAhead further in along the run line (the target's CURRENT beam, on this run's side) than the aircraft's own position on it - the target itself once that close. */
	private float runLineBearing(double px, double pz, double tx, double tz, float targetYawDegrees, double lookAhead) {
		double[] axis = sideVector(targetYawDegrees, this.sideSign);
		double along = (px - tx) * axis[0] + (pz - tz) * axis[1];
		double carrot = Math.max(0.0, along - lookAhead);
		return bearing(px, pz, tx + axis[0] * carrot, tz + axis[1] * carrot);
	}

	private void enter(Phase next) {
		this.phase = next;
		this.phaseTicks = 0;
	}

	/** Pointed at the target and coming in from its side - see RELEASE_AIM_TOLERANCE_DEGREES / RELEASE_BEAM_TOLERANCE_DEGREES. */
	static boolean linedUp(double px, double pz, float aircraftYaw, double tx, double tz, float targetYawDegrees) {
		float aimError = bearing(px, pz, tx, tz) - aircraftYaw;
		aimError = (aimError % 360f + 540f) % 360f - 180f;
		if (Math.abs(aimError) > RELEASE_AIM_TOLERANCE_DEGREES) {
			return false;
		}
		double a = Math.toRadians(aircraftYaw);
		double t = Math.toRadians(targetYawDegrees);
		// |cos| of the angle between the two headings: 0 when square on, 1 when along the target's own line.
		double alongTarget = Math.abs(Math.sin(a) * Math.sin(t) + Math.cos(a) * Math.cos(t));
		return alongTarget <= Math.sin(Math.toRadians(RELEASE_BEAM_TOLERANCE_DEGREES));
	}

	/** Unit horizontal vector square off a heading (perpendicular to its forward vector), on the given side. */
	static double[] sideVector(float yawDegrees, int sign) {
		double yaw = Math.toRadians(yawDegrees);
		// forward = (-sin, cos); perpendicular = (cos, sin)
		return new double[]{Math.cos(yaw) * sign, Math.sin(yaw) * sign};
	}

	/** Minecraft yaw of the direction from (fromX, fromZ) to (toX, toZ). */
	static float bearing(double fromX, double fromZ, double toX, double toZ) {
		return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
	}
}
