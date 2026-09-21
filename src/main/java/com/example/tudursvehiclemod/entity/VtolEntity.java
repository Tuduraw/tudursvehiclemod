package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;

/** Fixed-wing aircraft, throttle-based:. */
public class VtolEntity extends AircraftEntity {

	/** This vehicle's own flight mode - true (default) = helicopter-style VTOL; false = behaves exactly like AircraftEntity (unmodified copy of its movement). */
	private static final TrackedData<Boolean> HELICOPTER_MODE =
			DataTracker.registerData(VtolEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Integer> TRANSITION_TICKS_REMAINING =
			DataTracker.registerData(VtolEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> TRANSITIONING_TO_AIRCRAFT =
			DataTracker.registerData(VtolEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Mode transition duration (3 seconds). */
	private static final int VTOL_TRANSITION_DURATION_TICKS = 60;
	/** Fixed per-tick braking rate for the toHelicopter transition. */
	private static final float VTOL_TO_HELICOPTER_BRAKE_RATE = 0.95f;
	/** Extra post-transition braking window length, ticks. */
	private static final int VTOL_POST_TRANSITION_BRAKE_TICKS = 20;
	/** Counts down through VTOL_POST_TRANSITION_BRAKE_TICKS after a toHelicopter transition finishes. */
	private int vtolPostTransitionBrakeTicksRemaining;
	/** Blend rate used only during vtolPostTransitionBrakeTicksRemaining's own window (stronger than def.acceleration()). */
	private static final float VTOL_POST_TRANSITION_BRAKE_BLEND = 0.2f;
	/** Mode switching requires roll/pitch within this many degrees of level. */
	private static final float VTOL_LEVEL_TOLERANCE_DEGREES = 5.0f;
	private static final float VTOL_VERTICAL_THROTTLE_STEP = 0.03f;

	/** How fast unpiloted helicopter-mode throttle drifts back to 0.5 (neutral hover). */
	private static final float VTOL_UNMANNED_THROTTLE_DECAY = 0.01f;
	/** Same fixes as HelicopterEntity, which had the same issues: 10x faster once effectively grounded - the gentle airborne descent (VTOL_UNMANNED_THROTTLE_DECAY, toward 0.375) is unaffected. */
	private static final float VTOL_GROUNDED_THROTTLE_DECAY = VTOL_UNMANNED_THROTTLE_DECAY * 10f;
	/** Once throttle has eased to within this distance of its own current target, snaps the rest of the way there exactly - the asymptotic ease alone can approach but never actually reach that target on its own. Same reasoning/value as HelicopterEntity's own VERTICAL_THROTTLE_SNAP_THRESHOLD. */
	private static final float VTOL_THROTTLE_SNAP_THRESHOLD = 0.02f;
	private static final double VTOL_MAX_VERTICAL_SPEED = 0.6;
	private static final double VTOL_VERTICAL_SPEED_BLEND = 0.08;
	/** Stall engaged immediately once a transition
	 * to aircraft mode completes, before real speed had actually built up
	 * - this many further ticks of stall suppression run right after such
	 * a transition finishes (same idea as SPAWN_GRACE_TICKS). */
	private static final int VTOL_POST_TRANSITION_STALL_GRACE_TICKS = 60;
	private int vtolPostTransitionStallGraceTicksRemaining;

	/** Yaw locked at the instant a mode transition begins; held fixed for the whole transition regardless of mouse input. */
	private float vtolTransitionLockedYaw;
	/** Rising-edge detector for vtolTransitionLockedYaw's own capture; reset once confirmed not transitioning. */
	private boolean wasTransitioningLastTick;

	public VtolEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		// Per this class's own doc: helicopter mode is the default.
		builder.add(HELICOPTER_MODE, true);
		builder.add(TRANSITION_TICKS_REMAINING, 0);
		builder.add(TRANSITIONING_TO_AIRCRAFT, false);
	}

	public boolean isHelicopterMode() {
		return this.dataTracker.get(HELICOPTER_MODE);
	}

	/** This class previously never overrode this at all, so it always inherited AircraftEntity's own aircraft-tuned rate (turnSpeed() * a multiplier) even in helicopter mode - where PlayerLookRateMixin's own client-side view clamping (see that class's own doc) actually reads THIS pair of methods (not the aircraft-style path, since usesAircraftStyleOrientation() already returns false in helicopter mode) to limit how fast the pilot's own camera can move. Matches HelicopterEntity's own approach while actually in helicopter mode; aircraft mode keeps the inherited (super) rate unchanged. */
	@Override
	public float getYawFollowRateDegrees() {
		return isHelicopterMode() ? this.tudursvehiclemod$getEffectiveTurnSpeed() : super.getYawFollowRateDegrees();
	}

	/** Same reasoning as getYawFollowRateDegrees() just above. */
	@Override
	public float getPitchFollowRateDegrees() {
		return isHelicopterMode() ? this.tudursvehiclemod$getEffectiveTurnSpeed() : super.getPitchFollowRateDegrees();
	}

	public boolean isTransitioning() {
		return this.dataTracker.get(TRANSITION_TICKS_REMAINING) > 0;
	}

	public boolean isTransitioningToAircraft() {
		return this.dataTracker.get(TRANSITIONING_TO_AIRCRAFT);
	}

	/** Rotor blades spin at a constant rate while piloted in/transitioning to helicopter mode, keep spinning while not landed even when unpiloted, stop only once actually landed; aircraft mode unchanged. */
	@Override
	protected float getSpinningPartSpeedMultiplier() {
		if (!(getControllingPassenger() instanceof PlayerEntity) && !this.tudursvehiclemod$isDroneActive()) {
			// Uses this vehicle's own already-correct landed-state detection directly instead - spins whenever not landed, regardless of vertical motion, stops only once actually landed.
			return this.ticksSinceGrounded <= GROUNDED_GRACE_TICKS ? 0f : 1f;
		}
		if (isHelicopterMode() || isTransitioning()) {
			return 1f;
		}
		return super.getSpinningPartSpeedMultiplier();
	}

	/** For a future AddPartRotor nacelle-tilt animation - 0 = helicopter orientation, 1 = aircraft orientation. */
	public float getVtolTiltProgress() {
		if (isTransitioning()) {
			int ticksRemaining = this.dataTracker.get(TRANSITION_TICKS_REMAINING);
			float progress = 1f - ticksRemaining / (float) VTOL_TRANSITION_DURATION_TICKS;
			return isTransitioningToAircraft() ? progress : (1f - progress);
		}
		return isHelicopterMode() ? 0f : 1f;
	}

	/** Only starts a transition while level (within VTOL_LEVEL_TOLERANCE_DEGREES); no-op mid-transition. Called from network.VtolModeTogglePayload's server handler. */
	public void tudursvehiclemod$tryToggleVtolMode() {
		if (isTransitioning()) {
			return;
		}
		if (Math.abs(this.getRoll()) > VTOL_LEVEL_TOLERANCE_DEGREES
				|| Math.abs(this.getPitch()) > VTOL_LEVEL_TOLERANCE_DEGREES) {
			return;
		}
		this.dataTracker.set(TRANSITIONING_TO_AIRCRAFT, isHelicopterMode());
		this.dataTracker.set(TRANSITION_TICKS_REMAINING, VTOL_TRANSITION_DURATION_TICKS);
		// Mouse input during the transition made
		// yaw badly unstable (see tudursvehiclemod$updateVtolTransitionMovement()'s
		// own doc for why) - captured once here, right as the transition
		// begins, so that method has a genuinely fixed target to hold
		// regardless of anything the pilot's mouse does for the whole
		// transition's duration.
		this.vtolTransitionLockedYaw = this.getYaw();
	}

	/** Whether this aircraft is currently stalled. */
	public boolean isStalling() {
		return this.stalling;
	}

	@Override
	protected Identifier defaultDefinitionId() {
		return Identifier.of(VehicleMod.MOD_ID, "vtol");
	}



	@Override
	public float getYaw(float tickDelta) {
		Quaternionf interpolated = new Quaternionf(this.prevOrientation).slerp(this.orientation, tickDelta);
		return extractYawPitchRoll(interpolated)[0];
	}

	@Override
	public float getYaw() {
		return extractYawPitchRoll(this.orientation)[0];
	}

	@Override
	public void setYaw(float yaw) {
		if (this.orientation == null) {
			// Called from Entity's own constructor before our fields exist yet.
			return;
		}
		float[] current = extractYawPitchRoll(this.orientation);
		setOrientationFromEuler(yaw, current[1], current[2]);
		super.setYaw(yaw);
	}

	@Override
	public float getPitch(float tickDelta) {
		Quaternionf interpolated = new Quaternionf(this.prevOrientation).slerp(this.orientation, tickDelta);
		return extractYawPitchRoll(interpolated)[1];
	}

	@Override
	public float getPitch() {
		return extractYawPitchRoll(this.orientation)[1];
	}

	@Override
	public void setPitch(float pitch) {
		if (this.orientation == null) {
			return;
		}
		float[] current = extractYawPitchRoll(this.orientation);
		setOrientationFromEuler(current[0], pitch, current[2]);
		super.setPitch(pitch);
	}

	@Override
	public float getRoll(float tickDelta) {
		Quaternionf interpolated = new Quaternionf(this.prevOrientation).slerp(this.orientation, tickDelta);
		return extractYawPitchRoll(interpolated)[2];
	}

	@Override
	public float getRoll() {
		return extractYawPitchRoll(this.orientation)[2];
	}

	@Override
	public org.joml.Quaternionf tudursvehiclemod$getBodyOrientation() {
		return new Quaternionf(this.orientation);
	}

	/** Render-interpolated counterpart - see AbstractVehicleEntity's own tudursvehiclemod$getBodyOrientation(float)
	 * doc for why this overload exists, and AircraftEntity's own identical override (this class's
	 * own no-arg override above already matches that class's exactly, for the same reason: whatever
	 * keeps {@code orientation} current regardless of helicopter/aircraft mode applies here too). */
	@Override
	public Quaternionf tudursvehiclemod$getBodyOrientation(float tickDelta) {
		return new Quaternionf(this.prevOrientation).slerp(this.orientation, tickDelta);
	}

	@Override
	public float getExcessPitchForCamera(float tickDelta) {
		return 0f;
	}

	@Override
	protected void updateVehicleMovement(VehicleDefinition def) {
		// Checked FIRST, before even the takeoff dispatch just below, so it takes priority regardless of which mode this vehicle happens to currently be in. See tudursvehiclemod$updateVtolDroneLanding()'s own doc for the full routine.
		if (this.tudursvehiclemod$getDroneLandingCenter() != null) {
			tudursvehiclemod$updateVtolDroneLanding(def);
			return;
		}
		// A fresh VtolEntity spawns in helicopter mode (see HELICOPTER_MODE's own initDataTracker default), and the existing autopilot only exists for aircraft mode - stuck in helicopter mode, a drone-active VTOL would never climb (the existing unmanned-decay logic actually targets a gentle DESCENT) or ever trigger the mode transition at all, so NONE of the aircraft-mode autopilot's own behavior (including blade spin via getThrottle() and gear retraction) ever ran. Handles takeoff (climb + level out, then transition to aircraft mode) as its own small, self-contained routine here, checked first - once the transition completes, the existing (already-working) aircraft-mode autopilot takes over via the normal dispatch below.
		// Also requires getDroneCenterPos() != null explicitly, not just isDroneActive() alone - see AircraftEntity's own updateVehicleMovement() doc for why (isDroneActive() now reads a synced flag for blade-spin rendering purposes, but droneCenterPos itself is only ever meaningfully populated server-side).
		if (!(getControllingPassenger() instanceof PlayerEntity) && this.tudursvehiclemod$isDroneActive()
				&& this.tudursvehiclemod$getDroneCenterPos() != null && isHelicopterMode() && !isTransitioning()) {
			tudursvehiclemod$updateVtolDroneTakeoff(def);
			return;
		}
		// A gradual transition takes priority over either steady-state mode -
		// isHelicopterMode() itself doesn't flip until the transition finishes.
		if (isTransitioning()) {
			tudursvehiclemod$updateVtolTransitionMovement(def);
			return;
		}
		// Not transitioning right now - the NEXT one (either direction)
		// needs to capture a fresh vtolTransitionLockedYaw of its own,
		// not skip the capture thinking one's already in progress.
		this.wasTransitioningLastTick = false;
		if (isHelicopterMode()) {
			tudursvehiclemod$updateHelicopterModeMovement(def);
			return;
		}
		// Aircraft mode: reuses AircraftEntity's own movement entirely unchanged.
		super.updateVehicleMovement(def);
	}

	/** The transition to aircraft mode needs more altitude to work with (terrain clearance) than a modest clearance above the Drone Center alone previously provided, while staying RELATIVE to the Drone Center's own position (an absolute world altitude would be too much or too little depending on terrain): clearance (blocks) above the linked Drone Center this takeoff routine climbs to before attempting the transition to aircraft mode. */
	private static final double VTOL_DRONE_TAKEOFF_CLEARANCE = 100.0;

	/** This takeoff routine's own throttle blend, tracked here so it's NEVER read back through the fuel/wing-lock-gated getThrottle() (see setThrottleDirect()'s own doc - it forces the STORED value to 0 whenever out of fuel or wing-locked-folded). Reading the gated value back every tick restarted this blend from 0 every single tick, keeping the computed vertical speed permanently negative (descending) and preventing any climb at all. */
	private float droneTakeoffThrottle = 0.5f;

	/** This VTOL's own drone-controlled takeoff routine (see updateVehicleMovement()'s own doc for why this exists at all) - climbs straight up while leveling out and killing any horizontal drift, then triggers the transition to aircraft mode once high enough and level enough for tudursvehiclemod$tryToggleVtolMode() to actually accept it. */
	private void tudursvehiclemod$updateVtolDroneTakeoff(VehicleDefinition def) {
		net.minecraft.util.math.BlockPos center = this.tudursvehiclemod$getDroneCenterPos();
		if (center == null) {
			// Defensive: the dispatch above already checks this before calling here, but guards again in case this method is ever reached some other way.
			return;
		}
		double targetY = center.getY() + VTOL_DRONE_TAKEOFF_CLEARANCE;
		double altitudeError = targetY - this.getY();

		float verticalTarget = altitudeError > 0.5 ? 1.0f : 0.5f;
		this.droneTakeoffThrottle += (verticalTarget - this.droneTakeoffThrottle) * VTOL_UNMANNED_THROTTLE_DECAY;
		setThrottleDirect(this.droneTakeoffThrottle);
		double targetVerticalSpeed = (this.droneTakeoffThrottle - 0.5) * 2.0 * VTOL_MAX_VERTICAL_SPEED;
		double newY = this.getVelocity().y + (targetVerticalSpeed - this.getVelocity().y) * VTOL_VERTICAL_SPEED_BLEND;

		float blend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
		this.horizontalCruise = this.horizontalCruise.add(Vec3d.ZERO.subtract(this.horizontalCruise).multiply(blend));
		// Same fix/reasoning as AbstractVehicleEntity's own tudursvehiclemod$blendCruiseSpeedToward() doc.
		if (this.horizontalCruise.lengthSquared() < 1.0e-8) {
			this.horizontalCruise = Vec3d.ZERO;
		}

		float newRoll = this.getRoll() + (0f - this.getRoll()) * ROTATION_SMOOTHING;
		float newPitch = this.getPitch() + (0f - this.getPitch()) * ROTATION_SMOOTHING;
		float yaw = this.getYaw();
		this.setOrientationFromEuler(yaw, newPitch, newRoll);
		this.orientation.normalize();
		super.setPitch(newPitch);
		this.dataTracker.set(SYNCED_PITCH, newPitch);
		this.dataTracker.set(SYNCED_ROLL, newRoll);

		this.setVelocity(this.horizontalCruise.x, newY, this.horizontalCruise.z);
		this.move(MovementType.SELF, this.getVelocity());

		if (altitudeError <= 0.5) {
			this.tudursvehiclemod$tryToggleVtolMode();
		}
	}

	/** Altitude (world Y) held fixed during the "fly to above the Home Point, without descending yet" phase of tudursvehiclemod$updateVtolDroneLanding() - captured once when that phase begins (NaN means "not yet captured"), cleared once landing actually completes or is otherwise no longer in progress, so a later landing attempt captures a fresh value rather than reusing a stale one from a previous flight. */
	private double vtolLandingHeldAltitude = Double.NaN;
	/** Horizontal distance (blocks) to directly above the Home Point within which this VTOL considers itself "arrived" and switches to helicopter mode to begin its vertical descent - see tudursvehiclemod$updateVtolDroneLanding()'s own doc. */
	private static final double VTOL_LANDING_APPROACH_RADIUS = 3.0;

	/** Three-phase vertical landing for a manually-deactivated drone VTOL: (1) aircraft mode, fly horizontally toward the Home Point at held altitude; (2) once close, trigger tudursvehiclemod$tryToggleVtolMode(); (3) once the transition to helicopter mode actually completes, release the drone link immediately - the vehicle then descends via its own ordinary unpiloted-helicopter-mode decay. 着陸動作の再設計(垂直着陸)". */
	private void tudursvehiclemod$updateVtolDroneLanding(VehicleDefinition def) {
		net.minecraft.util.math.BlockPos center = this.tudursvehiclemod$getDroneLandingCenter();
		if (center == null) {
			// Defensive: the dispatch above already checks this before calling here, but guards again in case this method is ever reached some other way.
			return;
		}

		// Phase 3: the mode flip has actually completed - release control immediately (see this method's own doc for why no further "landed" detection is needed at all), then let this same tick's own now-unpiloted helicopter-mode movement run right away rather than doing nothing this tick.
		if (isHelicopterMode() && !isTransitioning()) {
			this.vtolLandingHeldAltitude = Double.NaN;
			this.tudursvehiclemod$completeDroneLanding();
			tudursvehiclemod$updateHelicopterModeMovement(def);
			return;
		}

		// Phase 2: already transitioning (tryToggleVtolMode() was called on an earlier tick, below) - the existing transition movement handles this entirely on its own; phase 3 above takes over automatically once it finishes.
		if (isTransitioning()) {
			tudursvehiclemod$updateVtolTransitionMovement(def);
			return;
		}

		// Phase 1: still in aircraft mode - flies horizontally toward directly above the Home Point, holding the altitude captured at the very start of this phase (not descending yet at all - the actual vertical descent only ever happens after switching to helicopter mode, per direct request).
		if (Double.isNaN(this.vtolLandingHeldAltitude)) {
			this.vtolLandingHeldAltitude = this.getY();
		}
		com.example.tudursvehiclemod.block.DroneWaypoint homePoint = this.getEntityWorld().getBlockEntity(center) instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity droneCenterEntity
				? droneCenterEntity.tudursvehiclemod$getHomePoint() : com.example.tudursvehiclemod.block.DroneWaypoint.createDefault();
		double targetX = center.getX() + 0.5 + homePoint.relX();
		double targetZ = center.getZ() + 0.5 + homePoint.relZ();
		double dx = targetX - this.getX();
		double dz = targetZ - this.getZ();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		if (horizontalDistance < VTOL_LANDING_APPROACH_RADIUS) {
			this.tudursvehiclemod$tryToggleVtolMode();
			return;
		}

		float effectiveTurnRate = this.getYawFollowRateDegrees();
		float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float newYaw = stepTowardAngle(this.getYaw(), desiredYaw, effectiveTurnRate);

		float baseApproachSpeed = def.maxSpeed() * Math.max(homePoint.speedFraction(), 0.3f);
		float decelFactor = (float) MathHelper.clamp(horizontalDistance / 20.0, 0.2, 1.0);
		float cruiseSpeedTarget = baseApproachSpeed * decelFactor;

		Vec3d currentVelocity = this.getVelocity();
		double currentSpeedMagnitude = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
		double blendedSpeedMagnitude = currentSpeedMagnitude + (cruiseSpeedTarget - currentSpeedMagnitude) * 0.05;

		double altitudeError = this.vtolLandingHeldAltitude - this.getY();
		double targetVerticalSpeed = MathHelper.clamp(altitudeError * 0.2, -cruiseSpeedTarget * 0.5, cruiseSpeedTarget * 0.5);
		double newVerticalVelocity = currentVelocity.y + (targetVerticalSpeed - currentVelocity.y) * 0.1;

		double newYawRad = Math.toRadians(newYaw);
		Vec3d finalVelocity = new Vec3d(-Math.sin(newYawRad) * blendedSpeedMagnitude, newVerticalVelocity, Math.cos(newYawRad) * blendedSpeedMagnitude);

		float newRoll = this.getRoll() + (0f - this.getRoll()) * ROTATION_SMOOTHING;
		float newPitch = this.getPitch() + (0f - this.getPitch()) * ROTATION_SMOOTHING;
		this.setOrientationFromEuler(newYaw, newPitch, newRoll);
		this.orientation.normalize();
		super.setYaw(newYaw);
		super.setPitch(newPitch);
		this.dataTracker.set(SYNCED_YAW, newYaw);
		this.dataTracker.set(SYNCED_PITCH, newPitch);
		this.dataTracker.set(SYNCED_ROLL, newRoll);
		this.cruiseSpeed = (float) blendedSpeedMagnitude;
		setThrottleDirect(decelFactor);

		this.setVelocity(finalVelocity);
		this.move(MovementType.SELF, this.getVelocity());
	}

	private static final float VTOL_THROTTLE_CENTER_SMOOTHING = 0.05f;
	/** Safety net against the speed-based transition-extension running forever. */
	private int transitionExtensionTicksWaited;
	/** Ticks transitionExtensionTicksWaited allows before forcing a stuck transition to finish (200 = 10 real seconds). */
	private static final int MAX_TRANSITION_EXTENSION_TICKS = 200;
	/** Current horizontal cruise velocity in helicopter mode - W/S/A/D move this vehicle like an ordinary helicopter. */
	private Vec3d horizontalCruise = Vec3d.ZERO;

	/** Grounded-state tracking for helicopter-mode movement, ported from HelicopterEntity's own ticksSinceGrounded/GROUNDED_GRACE_TICKS. */
	private int ticksSinceGrounded;
	private static final int GROUNDED_GRACE_TICKS = 3;

	@Override
	public void tudursvehiclemod$suppressStallWhileCarried() {
		super.tudursvehiclemod$suppressStallWhileCarried();
		this.ticksSinceGrounded = 0;
	}

	/** Overrides the parent's own version: skips the direct SYNCED_YAW/-PITCH/-ROLL write (this class's own tudursvehiclemod$easeTowardsGroundAttitude() already keeps those correct) and no longer rotates `orientation` directly (setYaw(), called by the carry system right before this, already does that as a side effect - rotating it again here was a genuine double-application). Only prevOrientation and groundAttitudeTargetYaw/groundLevelTargetYaw still need updating here. */
	@Override
	public void tudursvehiclemod$rotateOrientationForCarry(float deltaYawDegrees) {
		org.joml.Quaternionf worldYawRotation = new org.joml.Quaternionf().rotateY((float) Math.toRadians(-deltaYawDegrees));
		worldYawRotation.mul(this.prevOrientation, this.prevOrientation);
		this.groundAttitudeTargetYaw += deltaYawDegrees;
		this.groundLevelTargetYaw += deltaYawDegrees;
	}

	private void tudursvehiclemod$updateHelicopterModeMovement(VehicleDefinition def) {
		this.prevPropellerRotation = this.propellerRotation;
		this.prevOrientation.set(this.orientation);
		if (this.getControllingPassenger() == null && this.getEntityWorld().isClient()) {
			// Skips this method's own local physics recomputation for an unpiloted VTOL (helicopter mode) on the client, but still applies move() with the current (network-synced) velocity - matching AircraftEntity's own established pattern. Placed AFTER prevOrientation.set(orientation) above so the orientation interpolation baseline still updates every tick (see that field's own doc for the bug this ordering avoids).
			this.move(MovementType.SELF, this.getVelocity());
			// TicksSinceGrounded is otherwise never touched at all on the client while this guard is active, leaving getSpinningPartSpeedMultiplier() (read by both the rotor animation and engine sound) stuck reporting "still airborne" forever - kept up to date here too, using isOnGround() as it stands immediately after the move() call just above, without resurrecting this method's own full movement recomputation on the client. Exact same fix as HelicopterEntity's own identical bug.
			if (this.isOnGround()) {
				this.ticksSinceGrounded = 0;
			} else {
				this.ticksSinceGrounded++;
			}
			return;
		}

		// VTOL mode's own water-ditching behavior
		// must match aircraft mode's exactly - a non-float-capable
		// vehicle touching water is disabled here too (ignores all
		// input/hover control), floats/sinks, and takes damage over
		// time, using the exact same fields/logic aircraft mode's own
		// sinking branch already uses. Checked before any normal hover
		// "水上機以外の水面不時着"). Checked before any normal hover
		// logic runs at all, so it fully overrides it while active.
		boolean sinking = !def.isFloatCapable() && this.isTouchingWater();
		if (sinking) {
			Vec3d currentVelocity = this.getVelocity();
			double verticalVelocity;
			if (this.tudursvehiclemod$isDestroyed()) {
				verticalVelocity = SINKING_VERTICAL_SPEED;
			} else {
				java.util.OptionalDouble surfaceY = tudursvehiclemod$findWaterSurfaceY();
				if (surfaceY.isPresent()) {
					double targetY = surfaceY.getAsDouble() + SURFACE_FLOAT_DEPTH - SINKING_HULL_EXTRA_DEPTH;
					// Per tudursvehiclemod$applySurfaceFloatSpring()'s own doc: shared across every vehicle type with water buoyancy - freezes this vehicle's own Y outright once settled at a stable surface, only computing/applying spring velocity while genuinely transitioning.
					verticalVelocity = this.tudursvehiclemod$applySurfaceFloatSpring(targetY, currentVelocity.y,
							SURFACE_SPRING_STRENGTH, SURFACE_VERTICAL_DAMPING, SURFACE_SETTLE_POSITION_THRESHOLD, SURFACE_SETTLE_VELOCITY_THRESHOLD);
				} else {
					this.tudursvehiclemod$resetSurfaceFloatLock();
					verticalVelocity = SINKING_VERTICAL_SPEED;
				}
			}
			this.setVelocity(currentVelocity.x * 0.9, verticalVelocity, currentVelocity.z * 0.9);
			this.cruiseSpeed = 0f;
			this.horizontalCruise = Vec3d.ZERO;
			setThrottleDirect(0f);
			if (!this.tudursvehiclemod$isDestroyed() && this.getEntityWorld() instanceof ServerWorld serverWorld) {
				this.ticksSinceWaterDamage++;
				if (this.ticksSinceWaterDamage >= WATER_DAMAGE_INTERVAL_TICKS) {
					this.ticksSinceWaterDamage = 0;
					float damageAmount = this.getMaxHealth() * WATER_DAMAGE_FRACTION;
					this.damage(serverWorld, this.getDamageSources().drown(), damageAmount);
				}
			} else {
				this.ticksSinceWaterDamage = 0;
			}
			this.move(MovementType.SELF, this.getVelocity());
			return;
		}

		if (this.isOnGround()) {
			this.ticksSinceGrounded = 0;
		} else {
			this.ticksSinceGrounded++;
		}
		boolean effectivelyGrounded = this.ticksSinceGrounded <= GROUNDED_GRACE_TICKS;

		LivingEntity pilotEntity = getControllingPassenger();
		PlayerEntity player = pilotEntity instanceof PlayerEntity p ? p : null;

		if (player != null) {
			// VTOL mode's own vertical throttle is
			// now driven by the SAME keys as entity.HelicopterEntity's
			// own identical mechanism (jump to ascend, sneak/descend to
			// go down) instead of W/S - see this vehicle's own
			// horizontalCruise field doc for what W/S/A/D drive instead
			// now (actual horizontal movement, matching a real
			// helicopter, rather than this vehicle's own vertical
			// throttle at all anymore). Keeps this vehicle's own
			// existing 0.1 (0.5 = neutral hover) throttle convention
			// rather than switching to HelicopterEntity's own distinct
			// -1.1 one, so nothing else reading getThrottle() (HUD
			// display included) needs to change at all.
			boolean hasTakenOff = !effectivelyGrounded;
			float verticalReturnTarget = hasTakenOff ? 0.5f : 0f;
			float verticalReturnStep = hasTakenOff ? VTOL_THROTTLE_CENTER_SMOOTHING : VTOL_VERTICAL_THROTTLE_STEP * 0.5f;
			boolean pilotJumping = tudursvehiclemod$isPilotJumping(player);
			float throttle = getThrottle();
			// This vehicle's own vertical throttle logic (helicopter mode) never checked fuel at all, matching HelicopterEntity's own identical bug - letting it keep flying (or even climbing) with an empty tank. Out of fuel, throttle can only ever decrease (autorotation-style controlled descent), regardless of whatever the player is actually pressing.
			if (this.tudursvehiclemod$isOutOfFuel()) {
				throttle = Math.max(0f, throttle - VTOL_VERTICAL_THROTTLE_STEP);
			} else if (pilotJumping && !this.isDescending()) {
				throttle = Math.min(1f, throttle + VTOL_VERTICAL_THROTTLE_STEP);
			} else if (this.isDescending() && !pilotJumping) {
				throttle = Math.max(0f, throttle - VTOL_VERTICAL_THROTTLE_STEP);
			} else if (throttle > verticalReturnTarget) {
				throttle = Math.max(verticalReturnTarget, throttle - verticalReturnStep);
			} else if (throttle < verticalReturnTarget) {
				throttle = Math.min(verticalReturnTarget, throttle + verticalReturnStep);
			}
			setThrottleDirect(throttle);

			// Matching HelicopterEntity's own identical behavior:
			// yaw/pitch are fixed (left entirely
			// untouched) once landed, rather than continuing to follow
			// the pilot's own current view direction while grounded - it
			// stays facing exactly whatever way it was when it touched
			// down. Roll still eases level though, same reasoning as
			// HelicopterEntity's own copy.
			if (!effectivelyGrounded) {
				float newYaw = this.isFreeLook() ? this.getYaw() : player.getYaw();
				float newPitch = stepTowardAngle(this.getPitch(), 0f, this.tudursvehiclemod$getEffectiveTurnSpeed());
				float newRoll = this.getRoll() + (0f - this.getRoll()) * 0.2f;
				this.setOrientationFromEuler(newYaw, newPitch, newRoll);
				this.orientation.normalize();
				super.setYaw(newYaw);
				super.setPitch(newPitch);
				this.dataTracker.set(SYNCED_YAW, newYaw);
				this.dataTracker.set(SYNCED_PITCH, newPitch);
				this.dataTracker.set(SYNCED_ROLL, newRoll);
			} else {
				// Continuously syncs the pilot's own real view yaw to match this vehicle's own fixed facing while grounded (unless they're in free-look, which is explicitly exempt from following the vehicle at all), so by the time this vehicle actually takes off, player.getYaw() is already identical to this.getYaw() - nothing left to snap to, gradually or otherwise.
				if (!this.isFreeLook() && player.getYaw() != this.getYaw()) {
					player.setYaw(this.getYaw());
					if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
						serverPlayer.networkHandler.requestTeleport(
								serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), serverPlayer.getYaw(), serverPlayer.getPitch());
					}
				}
				// Matching HelicopterEntity's own identical behavior:
				// yaw/pitch stay completely
				// untouched while grounded - only roll eases back
				// towards level, same reasoning as HelicopterEntity's
				// own copy. VtolEntity has no raw roll/prevRoll fields at
				// all (unlike HelicopterEntity) - roll is derived from
				// this.orientation instead (see getRoll()'s own doc), so
				// this goes through the same extractYawPitchRoll/
				// setOrientationFromEuler round-trip the "not grounded"
				// branch above already uses, just holding yaw/pitch at
				// their own current values instead of recomputing them.
				float[] current = extractYawPitchRoll(this.orientation);
				float newRoll = current[2] + (0f - current[2]) * 0.3f;
				this.setOrientationFromEuler(current[0], current[1], newRoll);
				this.orientation.normalize();
				this.dataTracker.set(SYNCED_ROLL, newRoll);
			}

			// Forward/strafe combined into one
			// target vector, relative to current facing - exactly
			// matching entity.HelicopterEntity's own identical
			// mechanism. Same "no independent ground travel while
			// grounded" reasoning as that copy too.
			float forwardInput = MathHelper.clamp(this.getSyncedThrottleInput(), def.reverseThrottle(), 1.0f);
			float lateralInput = this.getSyncedSidewaysInput();
			double yawRad = Math.toRadians(this.getYaw());
			Vec3d forward = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
			Vec3d right = new Vec3d(Math.cos(yawRad), 0, Math.sin(yawRad));
			// VTOL mode's own WASD movement felt
			// far too fast compared to aircraft mode: this used to use
			// def.maxSpeed() directly - the SAME top speed aircraft mode
			// itself flies at - for VTOL mode's own hover movement too.
			// Scaled by def.vtolHoverSpeedFraction() instead now (10% of
			// maxSpeed by default - see that field's own doc), a
			// project-specific, per-vehicle-configurable directive.
			float vtolHoverSpeed = def.maxSpeed() * def.vtolHoverSpeedFraction();
			Vec3d targetHorizontal = effectivelyGrounded ? Vec3d.ZERO
					: forward.multiply(forwardInput * vtolHoverSpeed).add(right.multiply(lateralInput * vtolHoverSpeed));
			// Right after a toHelicopter
			// transition finishes (see VTOL_POST_TRANSITION_BRAKE_TICKS's
			// own doc), blends toward targetHorizontal at a stronger,
			// FIXED rate instead of def.acceleration()'s own (possibly
			// much slower) one - this still fully respects whatever the
			// pilot's own WASD is ALREADY commanding (targetHorizontal
			// itself is computed identically either way) - it only ever
			// changes how QUICKLY horizontalCruise catches up to it, so
			// genuinely active WASD input is never overridden or
			// ignored, just responded to faster than usual for this
			// brief window while any truly leftover aircraft-mode speed
			// also gets killed off quickly.
			float blend;
			if (this.vtolPostTransitionBrakeTicksRemaining > 0) {
				this.vtolPostTransitionBrakeTicksRemaining--;
				blend = VTOL_POST_TRANSITION_BRAKE_BLEND;
			} else {
				blend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
			}
			this.horizontalCruise = this.horizontalCruise.add(targetHorizontal.subtract(this.horizontalCruise).multiply(blend));
			// Same fix/reasoning as AbstractVehicleEntity's own tudursvehiclemod$blendCruiseSpeedToward() doc.
			if (this.horizontalCruise.subtract(targetHorizontal).lengthSquared() < 1.0e-8) {
				this.horizontalCruise = targetHorizontal;
			}

			double targetVerticalSpeed = (throttle - 0.5) * 2.0 * VTOL_MAX_VERTICAL_SPEED;
			double newY = this.getVelocity().y + (targetVerticalSpeed - this.getVelocity().y) * VTOL_VERTICAL_SPEED_BLEND;
			this.setVelocity(this.horizontalCruise.x, newY, this.horizontalCruise.z);
		} else {
			// This used to just read getThrottle()
			// unchanged here, meaning the stored throttle stayed frozen
			// at whatever it was left at while unpiloted (whether from a
			// full dismount or simply switching to a different seat),
			// then applied instantly (skipping the usual gradual ramp
			// entirely) the moment a pilot took the controls again.
			// Decayed here too now, same gradual idea as CarEntity's own
			// UNMANNED_THROTTLE_DECAY, just centered on 0.5 (this
			// vehicle's own neutral-hover value) instead of 0.
			float throttle = getThrottle();
			// This never distinguished grounded from airborne at all, always decaying toward the same constant 0.375 regardless - throttle (and dependent engine sound) could never actually reach true "off" (0.0) once genuinely landed. Now targets 0.0 once effectively grounded (matching HelicopterEntity's own -1 equivalent for its own -1.1 convention), keeping 0.375 (a gentle, controlled descent) only while still genuinely airborne - exact same fix as that class's own identical bug.
			float verticalTarget = effectivelyGrounded ? 0f : 0.375f;
			float decayRate = effectivelyGrounded ? VTOL_GROUNDED_THROTTLE_DECAY : VTOL_UNMANNED_THROTTLE_DECAY;
			throttle += (verticalTarget - throttle) * decayRate;
			// Per the same diagnosis as HelicopterEntity's own VERTICAL_THROTTLE_SNAP_THRESHOLD: the asymptotic ease alone can approach but never actually reach its own target exactly.
			if (Math.abs(throttle - verticalTarget) < VTOL_THROTTLE_SNAP_THRESHOLD) {
				throttle = verticalTarget;
			}
			setThrottleDirect(throttle);

			float blend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
			this.horizontalCruise = this.horizontalCruise.add(Vec3d.ZERO.subtract(this.horizontalCruise).multiply(blend));
			// Same fix/reasoning as AbstractVehicleEntity's own tudursvehiclemod$blendCruiseSpeedToward() doc.
			if (this.horizontalCruise.lengthSquared() < 1.0e-8) {
				this.horizontalCruise = Vec3d.ZERO;
			}
			double targetVerticalSpeed = (throttle - 0.5) * 2.0 * VTOL_MAX_VERTICAL_SPEED;
			double newY = this.getVelocity().y + (targetVerticalSpeed - this.getVelocity().y) * VTOL_VERTICAL_SPEED_BLEND;
			this.setVelocity(this.horizontalCruise.x, newY, this.horizontalCruise.z);
		}
		double preMoveSpeed = this.getVelocity().length();
		this.move(MovementType.SELF, this.getVelocity());
		// Matches aircraft mode's own one-time ditching-impact damage.
		this.tudursvehiclemod$checkLandingImpactDamage(preMoveSpeed, def);
	}

	/** Blends between the two modes over VTOL_TRANSITION_DURATION_TICKS; stall never engages during this window. */
	private void tudursvehiclemod$updateVtolTransitionMovement(VehicleDefinition def) {
		// The yaw instability during a
		// transition was STILL happening even after
		// tudursvehiclemod$tryToggleVtolMode() started capturing
		// vtolTransitionLockedYaw: that capture only ever ran
		// SERVER-side (this method itself runs on BOTH sides, for the
		// client's own local rendering too - see this class's own
		// TRANSITION_TICKS_REMAINING doc) - the CLIENT's own copy of
		// vtolTransitionLockedYaw was never actually set at all, staying
		// at its default (0), so the CLIENT's own rendering snapped yaw
		// to 0 the instant a transition began even though the SERVER's
		// own authoritative state was correctly holding steady. Captures
		// independently on WHICHEVER side first notices the transition
		// has begun instead (a rising-edge check, robust to network
		// lag - a laggy client might first observe this already several
		// ticks into the transition, past any single exact tick-count
		// this could otherwise try to match), so both sides always have
		// their own correct value regardless of tryToggleVtolMode()'s
		// own server-only capture.
		if (!this.wasTransitioningLastTick) {
			this.vtolTransitionLockedYaw = this.getYaw();
		}
		this.wasTransitioningLastTick = true;

		int ticksRemaining = Math.max(0, this.dataTracker.get(TRANSITION_TICKS_REMAINING) - 1);
		boolean toAircraft = isTransitioningToAircraft();
		// Fixing the earlier altitude-loss bug
		// (compensating verticalFraction for actual built-up speed, not
		// just elapsed time) only moved the problem rather than solving
		// it - now manifesting as violent VERTICAL shaking instead: this
		// speedFraction calculation used to happen AFTER the transition
		// had already been allowed to fully END purely on its own fixed
		// clock (ticksRemaining reaching 0 immediately flips
		// HELICOPTER_MODE and stops this whole method from ever running
		// again at all) - meaning that same "still not enough speed"
		// case this whole fix was meant to handle could ALSO make the
		// transition's own clock run out first, at which point hover
		// lift assist doesn't just decay, it vanishes OUTRIGHT the very
		// next tick (ordinary aircraft-mode movement, with no hover
		// assist mechanism in it at all, takes over) - an abrupt full
		// stall right as the transition ends, precisely the kind of
		// sudden drop/recovery-pitch cycle that reads as violent
		// vertical shaking. Computed HERE, before that decision, so it
		// can actually gate it now instead.
		float speedFraction = def.maxSpeed() > 0.001f
				? MathHelper.clamp(this.cruiseSpeed / (def.maxSpeed() * 0.6f), 0f, 1f) : 1f;
		// Per this same reasoning: for a toAircraft transition
		// specifically, the transition itself is no longer allowed to
		// actually FINISH (flip HELICOPTER_MODE, let TRANSITION_TICKS_REMAINING
		// reach 0 for real) until speedFraction has ALSO caught up -
		// held open at 1 tick remaining for as long as it takes,
		// however much longer than VTOL_TRANSITION_DURATION_TICKS that
		// actually needs, rather than ever handing off to ordinary
		// aircraft movement (with no hover assist at all) before this
		// vehicle can genuinely sustain itself on wing lift alone.
		// Unlike toAircraft (which genuinely does
		// need to wait for real airspeed before ordinary wing-lift-only
		// aircraft movement can take over safely, and reportedly already
		// works fine), waiting for toHelicopter's own speed to decay
		// naturally isn't realistic at all for a vehicle whose whole
		// point in aircraft mode is flying fast - this direction no
		// longer waits on speed at all, reverting to a fixed duration
		// (VTOL_TRANSITION_DURATION_TICKS) exactly like every other
		// vehicle's own transition-style mechanic. Strong, FIXED-RATE
		// braking (independent of throttle/def.acceleration() entirely -
		// see the cruiseSpeed decay just below) is applied throughout
		// this same fixed window instead, so real speed has already
		// dropped substantially by the time helicopter-mode movement
		// takes over, without needing the transition's own duration to
		// stretch out to guarantee it.
		if (toAircraft && ticksRemaining == 0 && speedFraction < 1f
				&& this.transitionExtensionTicksWaited < MAX_TRANSITION_EXTENSION_TICKS) {
			ticksRemaining = 1;
			this.transitionExtensionTicksWaited++;
		} else {
			this.transitionExtensionTicksWaited = 0;
		}
		this.dataTracker.set(TRANSITION_TICKS_REMAINING, ticksRemaining);
		if (ticksRemaining == 0) {
			this.dataTracker.set(HELICOPTER_MODE, !toAircraft);
			if (toAircraft) {
				// Stall engaged incorrectly right
				// after a transition: hasLifted/the various "ease towards
				// X" lock flags are only ever normally maintained by the
				// regular, player-piloted aircraft movement branch below -
				// explicitly set/cleared here instead, matching what the
				// very first tick of ordinary piloted aircraft movement
				// would already do on its own.
				this.hasLifted = true;
				this.groundAttitudeYawLocked = false;
				this.levelFlightYawLocked = false;
				this.groundLevelYawLocked = false;
				this.stallYawLocked = false;
				this.vtolPostTransitionStallGraceTicksRemaining = VTOL_POST_TRANSITION_STALL_GRACE_TICKS;
			} else {
				// Switching to VTOL/helicopter
				// mode specifically left this vehicle uncontrollable
				// (the OPPOSITE direction was fine): getThrottle()/
				// setThrottleDirect() mean two COMPLETELY different
				// things depending on mode - forward airspeed in
				// aircraft mode (and throughout this whole transition,
				// which still reads/writes it that way below), but
				// vertical hover control (0.5 = neutral) once
				// tudursvehiclemod$updateHelicopterModeMovement() takes
				// over. Reset to neutral hover here, the exact instant
				// this transition actually finishes.
				setThrottleDirect(0.5f);
				// Rather than an abrupt
				// discontinuity right at the handoff (this vehicle's own
				// actual velocity, however much the fixed braking below
				// has already reduced it to, simply being discarded and
				// replaced by helicopter mode's own horizontalCruise,
				// which would otherwise start from wherever it last was
				// - typically zero) - seeds horizontalCruise from THIS
				// vehicle's own actual current horizontal velocity right
				// here, so tudursvehiclemod$updateHelicopterModeMovement()'s
				// own EXTRA post-transition braking (see
				// vtolPostTransitionBrakeTicksRemaining's own doc) has a
				// real, continuous starting point to decelerate smoothly
				// from, rather than a sudden snap to zero (or to
				// whatever WASD the pilot happens to already be
				// pressing) on the very first helicopter-mode tick.
				Vec3d currentVel = this.getVelocity();
				this.horizontalCruise = new Vec3d(currentVel.x, 0, currentVel.z);
				this.vtolPostTransitionBrakeTicksRemaining = VTOL_POST_TRANSITION_BRAKE_TICKS;
			}
		}
		float progress = 1f - ticksRemaining / (float) VTOL_TRANSITION_DURATION_TICKS;
		// A significant altitude drop occurred right at
		// the START of an aircraft-to-VTOL transition: toHelicopter's
		// own verticalFraction (how much hover-style lift-assist is
		// currently being applied) used to RAMP UP gradually from 0
		// over the whole transition (matching progress itself) - at the
		// very start, this meant essentially NO lift-assist was applied
		// at all yet, so gravity acted almost entirely unopposed for
		// that initial stretch (a real, if unintentional, "freefall"
		// window). Softened WITHOUT actually
		// changing gravity itself: kept at FULL (1.0) hover-lift-assist
		// for this WHOLE transition instead, immediately from the very
		// first tick, rather than ramping it up over time at all - there's
		// no real reason to delay it in the first place (unlike toAircraft,
		// which genuinely needs to wait for real airspeed before easing
		// off of it), so simply never easing it down during this
		// transition at all removes the freefall window entirely.
		float timeBasedVerticalFraction = toAircraft ? MathHelper.clamp(1f - progress, 0f, 1f) : 1f;
		float verticalFraction = toAircraft
				? Math.max(timeBasedVerticalFraction, 1f - speedFraction)
				: timeBasedVerticalFraction;
		float horizontalFraction = toAircraft ? progress : (1f - progress);

		LivingEntity pilotEntity = getControllingPassenger();
		PlayerEntity player = pilotEntity instanceof PlayerEntity p ? p : null;

		float throttle;
		if (player != null) {
			throttle = updateThrottle(player, 0.02f * def.throttleUpDown().orElse(1.0f), def.reverseThrottle(), 1.0f);

			// Yaw used to read player.getYaw()
			// directly here, completely unclamped and unsmoothed (unlike
			// AircraftEntity's own updateOrientation(), which rate-limits
			// pendingYawInput) - any mouse movement mid-transition snapped
			// this VTOL's own yaw to match instantly, making it badly
			// unstable. Holds vtolTransitionLockedYaw (captured once,
			// right as the transition began - see that field's own doc)
			// fixed for the whole transition instead, regardless of
			// anything the pilot's mouse does in the meantime. Also
			// drains any pendingYawInput/pendingPitchInput that
			// accumulated during the transition, so it can't suddenly
			// all apply at once (a jerky yaw snap of its own) the very
			// first tick after the transition actually finishes.
			float newYaw = this.vtolTransitionLockedYaw;
			this.pendingYawInput = 0f;
			this.pendingPitchInput = 0f;
			// Matches roll's own smooth percentage-based blend below instead of a linear, constant-rate step (which moved at the same fixed def.turnSpeed()-per-tick rate the whole way, then stopped abruptly the instant it reached 0 - jarring for a large starting pitch, since aircraft mode allows any pitch angle but VTOL mode strictly enforces 0).
			float newPitch = this.getPitch() + (0f - this.getPitch()) * 0.2f;
			float newRoll = this.getRoll() + (0f - this.getRoll()) * 0.2f;
			this.setOrientationFromEuler(newYaw, newPitch, newRoll);
			this.orientation.normalize();
			super.setYaw(newYaw);
			super.setPitch(newPitch);
			this.dataTracker.set(SYNCED_YAW, newYaw);
			this.dataTracker.set(SYNCED_PITCH, newPitch);
			this.dataTracker.set(SYNCED_ROLL, newRoll);
		} else {
			// Reading the fuel/wing-lock-gated getThrottle() here meant a possibly-zeroed throttle, so cruiseSpeed never built up and speedFraction never reached 1 - extending this transition indefinitely while near-full hover-lift-assist stayed active, exactly countering gravity without ever braking off any leftover climb rate from the takeoff phase. An unpiloted vehicle has no player throttle input to read anyway - always tries to build up to full speed as quickly as possible instead, so the transition can actually complete promptly.
			throttle = 1.0f;
		}

		Vec3d currentVelocity = this.getVelocity();

		double gravityEffect = def.gravity();
		double liftAssist = -gravityEffect * verticalFraction;
		double newY = currentVelocity.y + gravityEffect + liftAssist;

		Vec3d heading = new Vec3d(-Math.sin(Math.toRadians(this.getYaw())), 0, Math.cos(Math.toRadians(this.getYaw())));
		float targetSpeed = throttle * this.tudursvehiclemod$getEffectiveMaxSpeed() * horizontalFraction;
		// ToHelicopter specifically brakes at a
		// strong, FIXED rate (independent of throttle and
		// def.acceleration() entirely - a real aircraft's own
		// deceleration into hover doesn't wait on the pilot easing off
		// the throttle at all) rather than easing toward targetSpeed the
		// same gentle way toAircraft's own acceleration ramp-up does -
		// see this whole method's own doc for why waiting for speed to
		// merely decay naturally wasn't realistic at all here. targetSpeed
		// itself is left unused for this direction (throttle no longer
		// meaningfully drives forward speed once braking has started).
		if (toAircraft) {
			this.cruiseSpeed += (targetSpeed - this.cruiseSpeed) * MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
			// Same fix/reasoning as AbstractVehicleEntity's own tudursvehiclemod$blendCruiseSpeedToward() doc.
			if (Math.abs(targetSpeed - this.cruiseSpeed) < 1.0e-4f) {
				this.cruiseSpeed = targetSpeed;
			}
		} else {
			this.cruiseSpeed *= VTOL_TO_HELICOPTER_BRAKE_RATE;
		}
		Vec3d horizontalTarget = heading.multiply(this.cruiseSpeed);

		Vec3d target = new Vec3d(horizontalTarget.x, newY, horizontalTarget.z);
		this.setVelocity(target);
		this.move(MovementType.SELF, this.getVelocity());
	}


	/** Damage to BOTH this aircraft and whatever it hit, on a high-speed collision with another entity. uses the SAME real-mesh-surface test as projectile hit detection (see AbstractVehicleEntity's own tudursvehiclemod$isPointNearMeshSurface() doc) for vehicle-vs-vehicle collisions specifically, rather than trusting the crude bounding-box overlap that found the candidate in the first place (that overlap is still used as a cheap first-pass search filter below, exactly like projectile hit detection's own search box). */
	protected void tudursvehiclemod$checkEntityCrashDamage(double preMoveSpeed) {
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		if (preMoveSpeed < ENTITY_CRASH_MIN_SPEED) {
			return;
		}
		float damage = (float) (preMoveSpeed * ENTITY_CRASH_DAMAGE_PER_SPEED);
		java.util.List<Entity> candidates = this.getEntityWorld().getOtherEntities(this, this.getBoundingBox().expand(0.1),
				e -> (e instanceof LivingEntity || e instanceof AbstractVehicleEntity)
						&& !(e instanceof com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity)
						&& e.isAlive() && !this.hasPassenger(e) && !this.tudursvehiclemod$isRecentlyDismounted(e));
		if (candidates.isEmpty()) {
			return;
		}
		// The actual per-
		// candidate touching check (tudursvehiclemod$anyCornerNearMesh()/
		// tudursvehiclemod$isPointNearMeshSurface() - pure, read-only
		// geometry math, no side effects) runs in PARALLEL across every
		// candidate here - same reasoning as AbstractVehicleEntity's own
		// tudursvehiclemod$updateCustomHitDetection() (see that method's
		// own doc): the actual damage() calls below are deliberately kept
		// OUTSIDE this parallel step, strictly sequential, only once
		// parallelStream() itself has fully finished.
		java.util.List<Entity> actuallyTouching = candidates.parallelStream()
				.filter(other -> {
					if (other instanceof AbstractVehicleEntity otherVehicle) {
						// Checks BOTH directions (the other vehicle's own corners against this aircraft's own mesh, and this aircraft's own corners against the other vehicle's own mesh) since either one's own surface could be the one actually making contact.
						return tudursvehiclemod$anyCornerNearMesh(otherVehicle, this)
								|| tudursvehiclemod$anyCornerNearMesh(this, otherVehicle);
					}
					// A living entity has no mesh of its own to sample corners from - just tests its own position directly against this aircraft's own mesh.
					return this.tudursvehiclemod$isPointNearMeshSurface(other.getEntityPos());
				})
				.collect(java.util.stream.Collectors.toList());
		for (Entity other : actuallyTouching) {
			other.damage(serverWorld, this.getDamageSources().flyIntoWall(), damage);
			this.damage(serverWorld, this.getDamageSources().flyIntoWall(), damage);
		}
	}

	/** Eases this aircraft's current yaw-preserving pitch towards def.onGroundPitch() and rolls back towards level (at GROUND_ROLL_SMOOTHING_MULTIPLIER times the yaw/pitch rate - see that field's own doc). player's own A/D input steers this target yaw (taxi turning) while surfaced, same turnSpeed-scaled convention as ShipEntity/CarEntity's own steering. */
	protected void tudursvehiclemod$easeTowardsGroundAttitude(VehicleDefinition def, PlayerEntity player) {
		if (this.getEntityWorld().isClient()) {
			// See tudursvehiclemod$easeTowardsLevelFlight()'s own doc for why this eases toward the synced target (via SYNCED_YAW/-PITCH) rather than hard-setting to it, and why..
			Quaternionf syncedTarget = new Quaternionf()
					.rotationY((float) Math.toRadians(-this.dataTracker.get(SYNCED_YAW)))
					.rotateX((float) Math.toRadians(this.dataTracker.get(SYNCED_PITCH)));
			this.orientation.slerp(syncedTarget, CLIENT_SYNC_SMOOTHING);
			this.orientation.normalize();
			return;
		}
		if (!this.groundAttitudeYawLocked) {
			this.groundAttitudeTargetYaw = getYaw();
			this.groundAttitudeYawLocked = true;
		}
		// Unlike AircraftEntity's own identical
		// method (which lets the pilot's own A/D input steer this
		// target yaw for runway taxi turning), VtolEntity stays
		// completely yaw-locked while grounded instead, the same as
		// HelicopterEntity's own "stays facing exactly whatever way it
		// was when it touched down" - a VTOL landing vertically isn't
		// expected to taxi/steer on the ground the way a runway aircraft
		// would.
		float[] current = extractYawPitchRoll(this.orientation);
		float baseSmoothing = 0.3f;
		float newYaw = current[0] + MathHelper.wrapDegrees(this.groundAttitudeTargetYaw - current[0]) * baseSmoothing;
		float newPitch = current[1] + (-def.onGroundPitch() - current[1]) * baseSmoothing;
		float rollSmoothing = Math.min(1f, baseSmoothing * GROUND_ROLL_SMOOTHING_MULTIPLIER);
		float newRoll = current[2] + (0f - current[2]) * rollSmoothing;
		this.setOrientationFromEuler(newYaw, newPitch, newRoll);
		this.orientation.normalize();
		super.setYaw(newYaw);
		super.setPitch(newPitch);
		this.dataTracker.set(SYNCED_YAW, newYaw);
		this.dataTracker.set(SYNCED_PITCH, newPitch);
		this.dataTracker.set(SYNCED_ROLL, newRoll);
	}

	/** Eases toward level (current heading, pitch=0, roll=0, roll at GROUND_ROLL_SMOOTHING_MULTIPLIER times the yaw rate - see that field's own doc) during the ground-roll level-out (see GROUND_LEVEL_SPEED_FRACTION's own doc). player's own A/D input steers this target yaw (taxi turning) while surfaced, same turnSpeed-scaled convention as ShipEntity/CarEntity's own steering. */
	protected void tudursvehiclemod$easeTowardsLevelOnGround(PlayerEntity player) {
		if (this.getEntityWorld().isClient()) {
			Quaternionf syncedTarget = new Quaternionf()
					.rotationY((float) Math.toRadians(-this.dataTracker.get(SYNCED_YAW)))
					.rotateX((float) Math.toRadians(this.dataTracker.get(SYNCED_PITCH)));
			this.orientation.slerp(syncedTarget, CLIENT_SYNC_SMOOTHING);
			this.orientation.normalize();
			return;
		}
		if (!this.groundLevelYawLocked) {
			this.groundLevelTargetYaw = getYaw();
			this.groundLevelYawLocked = true;
		}
		// See tudursvehiclemod$easeTowardsGroundAttitude()'s own doc - VTOL stays yaw-locked while grounded, unlike AircraftEntity's own taxi-steering equivalent.
		float[] current = extractYawPitchRoll(this.orientation);
		float newYaw = current[0] + MathHelper.wrapDegrees(this.groundLevelTargetYaw - current[0]) * GROUND_LEVEL_SMOOTHING;
		float newPitch = current[1] + (0f - current[1]) * GROUND_LEVEL_SMOOTHING;
		float rollSmoothing = Math.min(1f, GROUND_LEVEL_SMOOTHING * GROUND_ROLL_SMOOTHING_MULTIPLIER);
		float newRoll = current[2] + (0f - current[2]) * rollSmoothing;
		this.setOrientationFromEuler(newYaw, newPitch, newRoll);
		this.orientation.normalize();
		super.setYaw(newYaw);
		super.setPitch(newPitch);
		this.dataTracker.set(SYNCED_YAW, newYaw);
		this.dataTracker.set(SYNCED_PITCH, newPitch);
		this.dataTracker.set(SYNCED_ROLL, newRoll);
	}

	@Override
	public float getAnimationPhase(float tickDelta) {
		return this.prevPropellerRotation + (this.propellerRotation - this.prevPropellerRotation) * tickDelta;
	}

	@Override
	public boolean tudursvehiclemod$supportsLandingGearDisplay() {
		return true;
	}
}
