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
public class AircraftEntity extends AbstractVehicleEntity implements FreeCameraVehicle {

	/** Blade/propeller rotation while genuinely autonomous (isUsingRampedAutopilotThrottle() - drone-active OR gliding, see that method's own doc) uses casWaypointSpeedFraction (the actual, gradually-ramping throttle this vehicle's own autopilot is currently holding) directly, rather than the stored getThrottle() value - the latter is subject to setThrottleDirect()'s own isOutOfFuel()/isWingLockedFolded() gate (see that method's own doc), which would otherwise stop blade rotation entirely for a Drone-linked vehicle (which normally carries zero fuel by design, precisely so Drone-linking alone can't be used to bypass normal fuel consumption/refueling - see AbstractVehicleEntity's own fuel-related doc) even while its own movement (driven directly by velocity, not by the throttle value) keeps flying/orbiting normally regardless. The autopilot's own actual flight was already fuel-independent to begin with (driven directly by velocity), so this only brings the VISUAL blade-spin display in line with what the vehicle is already actually doing. */
	@Override
	protected float getSpinningPartSpeedMultiplier() {
		if (this.tudursvehiclemod$isUsingRampedAutopilotThrottle()) {
			return this.tudursvehiclemod$getCasWaypointSpeedFraction();
		}
		return super.getSpinningPartSpeedMultiplier();
	}

	/** Reliable, explicit sync for orientation. */
	protected static final TrackedData<Float> SYNCED_YAW =
			DataTracker.registerData(AircraftEntity.class, TrackedDataHandlerRegistry.FLOAT);
	protected static final TrackedData<Float> SYNCED_PITCH =
			DataTracker.registerData(AircraftEntity.class, TrackedDataHandlerRegistry.FLOAT);
	protected static final TrackedData<Float> SYNCED_ROLL =
			DataTracker.registerData(AircraftEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Whether a player is currently piloting a Carrier-launched aircraft directly - see tudursvehiclemod$isCarrierPlayerControlled()/tudursvehiclemod$setCarrierPlayerControlled()'s own doc. DataTracker-synced (not a plain field) so it's reliably visible on both client and server the instant it's set - both this aircraft's own onPilotMounted() (see AbstractVehicleEntity's own doc) and its own onTrackedDataSet() (below) depend on that. */
	private static final TrackedData<Boolean> CARRIER_PLAYER_CONTROLLED =
			DataTracker.registerData(AircraftEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** This backs casWaypointSpeedFraction (see that field's own doc), which used to be a plain (server-only) field - blade-spin rendering and engine sound are BOTH computed client-side, so a server-only value always reads as its own untouched default (0) on any observing client, including the piloting player's own, regardless of what the server-side value actually is. Same root cause and fix pattern as GEAR_DEPLOYED's and CARRIER_WEAPON_BAY_OVERRIDE_ACTIVE/_VALUE's own earlier fixes for this exact class of bug. */
	private static final TrackedData<Float> CAS_WAYPOINT_SPEED_FRACTION =
			DataTracker.registerData(AircraftEntity.class, TrackedDataHandlerRegistry.FLOAT);

	/** Recomputes cruiseSpeed from this entity's own actual velocity whenever CARRIER_PLAYER_CONTROLLED is seen as true - onTrackedDataSet() fires for the INITIAL sync of a tracked value too (not just later changes), so this covers both a normal switch AND switching into an aircraft the client had never tracked before (was out of render distance until the moment of mounting) in one path, correctly on whichever side is actually running this. */
	@Override
	public void onTrackedDataSet(net.minecraft.entity.data.TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (data == CARRIER_PLAYER_CONTROLLED && this.dataTracker.get(CARRIER_PLAYER_CONTROLLED)) {
			// Math.sqrt of the sum of squares is an UNSIGNED magnitude - losing which direction (forward/backward relative to this aircraft's own current facing) it was actually moving in entirely. cruiseSpeed is itself a SIGNED value everywhere else it's used (negative = moving backward relative to current facing) - forcing it positive here, even while genuinely still moving backward at this exact instant, made the very next tick's own heading-based velocity reconstruction reverse direction on the spot. Unlike tudursvehiclemod$setCarrierPlayerControlled()'s own similar recompute (which pairs this same kind of speed with a yaw REALIGNMENT to the velocity's own actual direction, making an unsigned magnitude self-consistent there), this one leaves yaw untouched - so the speed itself must be signed instead, dotted with this aircraft's own EXISTING forward-facing unit vector rather than realigning yaw to match.
			Vec3d currentVelocity = this.getVelocity();
			double yawRad = Math.toRadians(this.getYaw());
			double forwardX = -Math.sin(yawRad);
			double forwardZ = Math.cos(yawRad);
			this.cruiseSpeed = (float) (currentVelocity.x * forwardX + currentVelocity.z * forwardZ);
		}
	}

	/** Mouse-driven yaw's own rate cap is def.turnSpeed() * YAW_RATE_MULTIPLIER (3x). PITCH_RATE_MULTIPLIER was already 3x (matching yaw) before this request, so it's now 3x that (9x def.turnSpeed()) to satisfy "3x the current pitch performance" specifically. */
	protected static final float YAW_RATE_MULTIPLIER = 3.0f;
	protected static final float PITCH_RATE_MULTIPLIER = 9.0f;
	protected static final float ROLL_RATE_DEGREES_PER_TICK = 6f;
	protected static final float MIN_FLIGHT_SPEED_FRACTION = 0.5f;
	/** sustainedFlight only turns back OFF once actual speed drops below THIS (lower) fraction. */
	protected static final float SUSTAINED_FLIGHT_DISENGAGE_FRACTION = 0.4f;
	/** How many consecutive ticks of a CHANGED isOnGround()/isTouchingWater() reading are required before committing to a surfaced state change. */
	protected static final int SURFACED_DEBOUNCE_TICKS = 3;
	protected static final double LIFTOFF_VELOCITY = 0.35;
	/** How fast cruiseSpeed blends towards its target while grounded/floating. */
	protected static final float GROUND_SPEED_BLEND = 0.04f;
	/** How fast cruiseSpeed blends towards its target while grounded/floating AND decelerating specifically (throttle reduced/braking). */
	protected static final float GROUND_DECEL_BLEND = 0.03f;
	/** How fast cruiseSpeed blends towards its target while airborne with the throttle below MIN_FLIGHT_SPEED_FRACTION. */
	protected static final float LOW_THROTTLE_GLIDE_BLEND = 0.003f;
	/** How quickly the actually-applied yaw/pitch/roll delta ramps towards its target rate each tick (and back towards 0 once input stops). */
	protected static final float ROTATION_SMOOTHING = 0.2f;

	/** How many consecutive ticks with NO yaw/pitch/roll input at all before gently easing towards level flight (pitch=0, roll=0). */
	protected static final int LEVEL_ASSIST_GRACE_TICKS = 100;
	/** Proportional easing factor towards level once the grace period elapses. */
	protected static final float LEVEL_ASSIST_SMOOTHING = 0.02f;
	/** How quickly the client eases its own orientation towards the latest network-synced yaw/pitch while unpiloted, rather than snapping straight to it. */
	protected static final float CLIENT_SYNC_SMOOTHING = 0.4f;

	@Override
	public float getYawFollowRateDegrees() {
		return this.tudursvehiclemod$getEffectiveTurnSpeed() * YAW_RATE_MULTIPLIER;
	}

	@Override
	public float getPitchFollowRateDegrees() {
		return this.tudursvehiclemod$getEffectiveTurnSpeed() * PITCH_RATE_MULTIPLIER;
	}

	/** Autopilot-driven turning (formation-follow, CAS waypoint navigation, Carrier orbit/lock-pursuit) used only a flat, yaw-only turn rate while this aircraft's own pitch rate is 3x FASTER STILL. Maximum real turning performance for this mod's own aircraft comes from banking (rolling) so that a pitch-up input effectively becomes the turn, exactly like a real aircraft's own coordinated turn, rather than a flat, unbanked yaw-only rotation (mirroring how player-controlled flight already achieves its own fastest turns - see updateOrientation()'s own doc). Maximum bank angle a coordinated autopilot turn ever holds - a hard, aggressive bank (comfortably short of knife-edge) rather than a fully vertical 90 degrees, matching a real high-performance aircraft's own practical sustained-turn limit. */
	protected static final float COORDINATED_TURN_MAX_BANK_DEGREES = 75.0f;
	/** How many degrees of target bank per degree of remaining yaw error - a small correction stays nearly level; hitting COORDINATED_TURN_MAX_BANK_DEGREES already at a comfortably moderate yaw error (37.5 degrees at this value) means a genuinely sharp turn banks in hard almost immediately, rather than only gradually working up to full bank as the error itself changes. */
	protected static final float COORDINATED_TURN_BANK_GAIN = 2.0f;

	/** See COORDINATED_TURN_MAX_BANK_DEGREES's own doc for the full reasoning. Computes ONE tick's worth of a proper banked/coordinated turn toward desiredYaw: banks (rolls) proportionally to the remaining yaw error (clamped to COORDINATED_TURN_MAX_BANK_DEGREES, ramped at ROLL_RATE_DEGREES_PER_TICK same as a player's own roll input rate), then derives this tick's own ACTUAL yaw rate by blending between yawRate (this aircraft's own flat, unbanked rate) and pitchRate (its own much faster pitch rate) according to how banked it currently is - fully banked achieves the full pitch-driven rate, level achieves only the plain yaw rate, matching the real mechanism (a banked pitch-up input IS the fast turn) rather than an artificial standalone multiplier. Returns {newYaw, newRoll} - callers apply everything else (speed, pitch-from-vertical-velocity, final orientation) exactly as before, just sourcing yaw/roll from here instead of a plain stepTowardAngle() call and a separately-computed, purely cosmetic roll. */
	protected float[] tudursvehiclemod$computeCoordinatedTurn(float currentYaw, float currentRoll, float desiredYaw, float yawRate, float pitchRate) {
		float yawError = MathHelper.wrapDegrees(desiredYaw - currentYaw);
		float targetBank = MathHelper.clamp(yawError * COORDINATED_TURN_BANK_GAIN, -COORDINATED_TURN_MAX_BANK_DEGREES, COORDINATED_TURN_MAX_BANK_DEGREES);
		float newRoll = currentRoll + MathHelper.clamp(targetBank - currentRoll, -ROLL_RATE_DEGREES_PER_TICK, ROLL_RATE_DEGREES_PER_TICK);
		float bankFraction = Math.abs(newRoll) / COORDINATED_TURN_MAX_BANK_DEGREES;
		float effectiveYawRate = yawRate + (pitchRate - yawRate) * bankFraction;
		float newYaw = stepTowardAngle(currentYaw, desiredYaw, effectiveYawRate);
		return new float[]{newYaw, newRoll};
	}

	/** A standalone variant of tudursvehiclemod$computeCoordinatedTurn() for a caller that already computes its own roll independently (e.g. tudursvehiclemod$updateDroneWaypointAutopilot()'s own intentional, map-maker-configurable DroneWaypoint.rollAngle() feature) - takes whatever the current roll already IS (from wherever it actually came from) and returns only the resulting bank-boosted yaw rate, without computing or returning a roll value of its own at all. */
	protected float tudursvehiclemod$computeBankBoostedYawRate(float currentRoll, float yawRate, float pitchRate) {
		float bankFraction = MathHelper.clamp(Math.abs(currentRoll) / COORDINATED_TURN_MAX_BANK_DEGREES, 0.0f, 1.0f);
		return yawRate + (pitchRate - yawRate) * bankFraction;
	}


	/** False while this non-float-capable aircraft sits on/under water (matching AbstractVehicleEntity's own base doc for why that's restricted), and once this vehicle is destroyed (see that base class's own tudursvehiclemod$canFireWeapons() doc) - this override replaces the base check entirely rather than calling super, so that condition has to be repeated here too. */
	@Override
	public boolean tudursvehiclemod$canFireWeapons() {
		return !this.tudursvehiclemod$isDestroyed() && (getDefinition().isFloatCapable() || tudursvehiclemod$findWaterSurfaceY().isEmpty());
	}

	public float propellerRotation;
	public float prevPropellerRotation;

	/** True once this aircraft has been detected flying (reachable only via one of this class's own NOT-surfaced attitude branches - player-piloted flight, stalling, or autopilot) since it last left that state; forced false the moment it's detected surfaced/grounded again, or while genuinely being carried on a moving runway (see tudursvehiclemod$suppressStallWhileCarried()'s own doc). Gates stall eligibility (a landed/just-carried aircraft can't spuriously stall) - deliberately NOT consulted by this class's own surfaced/rawSurfaced determination itself (see that field's own doc for why reusing it there once caused a permanent deadlock). */
	protected boolean hasLifted;
	/** World time (this.getEntityWorld().getTime()) tudursvehiclemod$suppressStallWhileCarried() was last called - see that method's own doc, and updateVehicleMovement()'s own rawSurfaced doc for why this exists as ITS OWN field rather than reusing hasLifted for this too. Defaults far enough in the past that "was this called recently" reads false for any aircraft that's never been near a runway at all. */
	private long lastCarriedByRunwayTick = Long.MIN_VALUE / 2;

	/** Full 3D orientation as a single quaternion. */
	protected final Quaternionf orientation = new Quaternionf();
	protected final Quaternionf prevOrientation = new Quaternionf();

	/** Raw mouse deltas relayed from the client since this was last consumed (every tick, in updateOrientation()). */
	public float pendingYawInput;
	public float pendingPitchInput;

	/** Consecutive ticks with no yaw/pitch/roll input at all. */
	protected int noInputTicks;
	/** Locked target yaw for tudursvehiclemod$easeTowardsLevelFlight(). */
	protected float levelFlightTargetYaw;
	protected boolean levelFlightYawLocked;
	/** Locked target yaw for tudursvehiclemod$easeTowardsLevelOnGround(). */
	protected float groundLevelTargetYaw;
	protected boolean groundLevelYawLocked;
	/** Persistent stall state with hysteresis. */
	protected boolean stalling;
	/** Consecutive ticks spent with a high climb factor. */
	protected int steepClimbTicks;
	/** See tudursvehiclemod$smoothedSpeedTargetPitch()'s own doc. */
	protected float smoothedSpeedTargetPitch;
	/** Persistent state with hysteresis. */
	protected boolean sustainedFlightState;
	/** Debounced surfaced state. */
	protected boolean surfacedState;
	protected int surfacedDebounceTicks;
	/** Locked target yaw for tudursvehiclemod$forceNoseDown(). */
	protected float stallTargetYaw;
	protected boolean stallYawLocked;

	/** A Carrier-carried aircraft could enter a stall (nose pitched down, never recovering) - this vehicle's own carry logic (see AbstractVehicleEntity's own tudursvehiclemod$carryRunwayDeckEntities()) directly calls move() to displace this vehicle, entirely bypassing this class's own cruiseSpeed tracking - meaning cruiseSpeed itself simply stays wherever it already was (typically near 0, for an otherwise-parked aircraft) throughout, so this class's own stall-engage logic (which only checks actualSpeed, unaware that this vehicle's own ACTUAL position is genuinely being displaced right now, just not via cruiseSpeed) sees "low speed" and stays permanently locked into stalling - stall-disengage requires speed to climb back up past a threshold, which cruiseSpeed staying near 0 forever means never actually happens. Called every tick this vehicle is being carried to directly force stalling back off, letting this class's own normal (non-stalling) orientation logic level it back out on its own. */
	public void tudursvehiclemod$suppressStallWhileCarried() {
		this.stalling = false;
		this.stallYawLocked = false;
		// The stall-engage condition itself requires hasLifted==true - forcing it false here structurally prevents stall from ever engaging at all (matching the SAME state a genuinely landed/grounded aircraft is already in, rather than depending on isOnGround()-based landed detection working correctly while the runway tile itself is moving each tick).
		this.hasLifted = false;
		// Critical bug report - see updateVehicleMovement()'s own rawSurfaced doc for the full mechanism this fixes.
		this.lastCarriedByRunwayTick = this.getEntityWorld().getTime();
	}

	/** Motionless, in mid-air after its own carrier (and runway) disappeared while it was still being carried: called once, the instant that happens (see AbstractVehicleEntity's own onRemoved()) - forces hasLifted back to true immediately, so this class's own normal physics recognizes it's genuinely airborne (with no ground support at all anymore) right away, rather than depending on some later tick's own gradual/debounced re-evaluation of that state to ever actually happen correctly once nothing is carrying it into a "landed" state anymore. */
	public void tudursvehiclemod$releaseFromCarrier() {
		this.hasLifted = true;
	}

	/** This class's own rendered orientation is a Quaternionf (see that field's own doc), not just the simple Entity.yaw field. This method used to ALSO directly rotate `orientation` itself - a genuine double-application, since this class's own setYaw() override (called by the carry system immediately before this method, with the new absolute yaw) already updates `orientation` as a side effect on its own. Only prevOrientation still needs rotating here (setYaw() never touches it) - matching it to the same new orientation setYaw() just produced, so the slerp-based interpolation between them (used for smooth rendering) doesn't visually snap. */
	public void tudursvehiclemod$rotateOrientationForCarry(float deltaYawDegrees) {
		org.joml.Quaternionf worldYawRotation = new org.joml.Quaternionf().rotateY((float) Math.toRadians(-deltaYawDegrees));
		worldYawRotation.mul(this.prevOrientation, this.prevOrientation);
		float[] updatedAngles = extractYawPitchRoll(this.orientation);
		this.dataTracker.set(SYNCED_YAW, updatedAngles[0]);
		this.dataTracker.set(SYNCED_PITCH, updatedAngles[1]);
		this.dataTracker.set(SYNCED_ROLL, updatedAngles[2]);
		this.groundAttitudeTargetYaw += deltaYawDegrees;
		this.groundLevelTargetYaw += deltaYawDegrees;
	}
	/** Locked target yaw for tudursvehiclemod$easeTowardsGroundAttitude(). */
	protected float groundAttitudeTargetYaw;
	protected boolean groundAttitudeYawLocked;
	/** Whether a player was controlling this aircraft as of the last tick. */
	protected boolean wasPiloted = true;
	/** Roll's attitude-assist target (nearest 90-degree increment), locked in once when the assist engages and held fixed until input resumes. */
	protected float rollSnapTarget;
	protected boolean rollSnapTargetLocked;

	/** The actually-applied yaw/pitch/roll delta, eased towards its target each tick rather than snapping to it. */
	protected float smoothedYawDelta;
	protected float smoothedPitchDelta;
	protected float smoothedRollDelta;

	public AircraftEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(SYNCED_YAW, 0.0f);
		builder.add(SYNCED_PITCH, 0.0f);
		builder.add(SYNCED_ROLL, 0.0f);
		builder.add(CARRIER_PLAYER_CONTROLLED, false);
		builder.add(CAS_WAYPOINT_SPEED_FRACTION, 0.0f);
	}

	/** Whether this aircraft is currently stalled. */
	public boolean isStalling() {
		return this.stalling;
	}

	@Override
	protected Identifier defaultDefinitionId() {
		return Identifier.of(VehicleMod.MOD_ID, "aircraft");
	}

	/** Decomposes a quaternion assumed to equal Ry(-yaw)*Rx(pitch)*Rz(roll) back into (yaw, pitch, roll) degrees. */
	protected static float[] extractYawPitchRoll(Quaternionf q) {
		float x = q.x;
		float y = q.y;
		float z = q.z;
		float w = q.w;

		float pitchRad = (float) Math.asin(MathHelper.clamp(2f * (x * w - y * z), -1f, 1f));
		float negYawRad = (float) Math.atan2(2f * (x * z + y * w), 1f - 2f * (x * x + y * y));
		float rollRad = (float) Math.atan2(2f * (x * y + z * w), 1f - 2f * (x * x + z * z));

		float pitch = (float) Math.toDegrees(pitchRad);
		float yaw = -(float) Math.toDegrees(negYawRad);
		float roll = (float) Math.toDegrees(rollRad);
		return new float[]{yaw, pitch, roll};
	}

	protected void setOrientationFromEuler(float yaw, float pitch, float roll) {
		this.orientation.rotationY((float) Math.toRadians(-yaw))
				.rotateX((float) Math.toRadians(pitch))
				.rotateZ((float) Math.toRadians(roll));
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
	 * doc for why this overload exists at all. Same slerp getYaw(float)/getPitch(float) already use,
	 * so the renderer's own mesh transform gets exactly the same smooth, gimbal-free interpolation
	 * those two already provide per-axis - now delivered as one combined rotation instead of three
	 * independently-extracted (and therefore independently gimbal-sensitive) angles. */
	@Override
	public Quaternionf tudursvehiclemod$getBodyOrientation(float tickDelta) {
		return new Quaternionf(this.prevOrientation).slerp(this.orientation, tickDelta);
	}

	@Override
	protected void onPilotMounted() {
		super.onPilotMounted();
		// Reversed the vehicle keeps its OWN existing orientation on mount (no longer snapped to match the new pilot's current look direction), and..
		if (getControllingPassenger() instanceof PlayerEntity player) {
			float[] current = extractYawPitchRoll(this.orientation);
			player.setYaw(current[0]);
			player.setPitch(current[1]);
		}
	}

	/** No longer used - the camera now reconstructs its own rotation directly from this vehicle's getYaw()/getPitch()/getRoll() (see CameraMixin), rather than.. */
	@Override
	public float getExcessPitchForCamera(float tickDelta) {
		return 0f;
	}

	/** Applies this tick's orientation change directly onto `orientation`, as a rotation in the aircraft's OWN current local frame (a post-multiply). */
	protected void updateOrientation(PlayerEntity pilot) {
		boolean freeLook = this.isFreeLook();
		if (freeLook) {
			// Free-look: pilot can look around freely without the MOUSE affecting the aircraft's own orientation.
			this.pendingYawInput = 0f;
			this.pendingPitchInput = 0f;
		}

		// Every axis of player-controlled maneuvering (yaw, pitch, roll) drops to 1/10 of normal while destroyed.
		float destroyedManeuverabilityMultiplier = this.tudursvehiclemod$isDestroyed() ? 0.1f : 1f;
		float yawRate = getYawFollowRateDegrees() * destroyedManeuverabilityMultiplier;
		float pitchRate = getPitchFollowRateDegrees() * destroyedManeuverabilityMultiplier;
		float targetYawDelta = freeLook ? 0f : MathHelper.clamp(this.pendingYawInput, -yawRate, yawRate);
		float targetPitchDelta = freeLook ? 0f : MathHelper.clamp(this.pendingPitchInput, -pitchRate, pitchRate);
		// Discard anything beyond the rate limit rather than saving it for a future tick to keep draining.
		this.pendingYawInput = 0f;
		this.pendingPitchInput = 0f;

		// Sign flipped per request.
		float targetRollDelta = this.getSyncedSidewaysInput() * ROLL_RATE_DEGREES_PER_TICK * destroyedManeuverabilityMultiplier;

		// Ease the ACTUALLY-APPLIED delta towards the target rate each tick, rather than jumping straight to it.
		this.smoothedYawDelta += (targetYawDelta - this.smoothedYawDelta) * ROTATION_SMOOTHING;
		this.smoothedPitchDelta += (targetPitchDelta - this.smoothedPitchDelta) * ROTATION_SMOOTHING;
		this.smoothedRollDelta += (targetRollDelta - this.smoothedRollDelta) * ROTATION_SMOOTHING;
		float dYaw = this.smoothedYawDelta;
		float dPitch = this.smoothedPitchDelta;
		float dRoll = this.smoothedRollDelta;

		// Apply the pilot's own input (if any) as a rotation in the aircraft's CURRENT local frame.
		Quaternionf inputDelta = new Quaternionf()
				.rotateY((float) Math.toRadians(-dYaw))
				.rotateX((float) Math.toRadians(dPitch))
				.rotateZ((float) Math.toRadians(-dRoll));
		this.orientation.mul(inputDelta).normalize();

		// Based on the RAW target (before smoothing), not the smoothed value that was actually applied above.
		boolean hasInput = targetYawDelta != 0f || targetPitchDelta != 0f || targetRollDelta != 0f;
		if (hasInput || this.isManualMode()) {
			this.noInputTicks = 0;
			this.rollSnapTargetLocked = false;
		} else {
			this.noInputTicks++;
			if (this.noInputTicks >= LEVEL_ASSIST_GRACE_TICKS) {
				// Gently ease towards level (same heading, pitch=0, roll=nearest 90-degree increment), by building that target orientation directly and SLERPing the whole..
				float currentYaw = getYaw();
				float currentRoll = getRoll();
				if (!this.rollSnapTargetLocked) {
					this.rollSnapTarget = Math.round(currentRoll / 90f) * 90f;
					this.rollSnapTargetLocked = true;
				}
				Quaternionf levelTarget = new Quaternionf()
						.rotationY((float) Math.toRadians(-currentYaw))
						.rotateZ((float) Math.toRadians(this.rollSnapTarget));
				this.orientation.slerp(levelTarget, LEVEL_ASSIST_SMOOTHING);
				this.orientation.normalize();
			}
		}

		// Best-effort: also keep vanilla's own yaw/pitch fields updated..
		float[] extracted = extractYawPitchRoll(this.orientation);
		super.setYaw(extracted[0]);
		super.setPitch(extracted[1]);
		// Reliable sync (see SYNCED_YAW/-PITCH/-ROLL's own doc).
		this.dataTracker.set(SYNCED_YAW, extracted[0]);
		this.dataTracker.set(SYNCED_PITCH, extracted[1]);
		this.dataTracker.set(SYNCED_ROLL, extracted[2]);
	}

	/** Landing gear deployed (progress=0) reduces max speed to this fraction, blending smoothly back to 1.0 (no reduction) as it retracts (progress=1). */
	protected static final float GEAR_DEPLOYED_MAX_SPEED_MULTIPLIER = 0.7f;
	/** How much faster cruiseSpeed blends towards its target specifically when DEcelerating (target below current) versus accelerating. */
	protected static final float DECEL_SPEEDUP_MULTIPLIER = 1.5f;
	/** How much the throttle drops per tick once unpiloted, instead of holding at whatever it was left at. */
	protected static final float UNMANNED_THROTTLE_DECAY = 0.01f;
	/** Small constant downward velocity applied while surfaced, instead of exactly 0. */
	protected static final double GROUNDED_STICK_VELOCITY = -0.05;
	/** Float-capable aircraft actually on water uses spring buoyancy instead of GROUNDED_STICK_VELOCITY (same formula as Ship/SubmarineEntity's surfaced mode). */
	protected static final double SURFACE_FLOAT_DEPTH = 0.125;

	/** Topmost water block near this vehicle, or empty if none nearby - callers should fall back to plain gravity (not current Y) when empty. */
	protected java.util.OptionalDouble tudursvehiclemod$findWaterSurfaceY() {
		net.minecraft.util.math.BlockPos basePos = net.minecraft.util.math.BlockPos.ofFloored(this.getX(), this.getY(), this.getZ());
		double surfaceY = this.getY();
		boolean foundWater = false;
		for (int dy = -2; dy <= 3; dy++) {
			net.minecraft.util.math.BlockPos checkPos = basePos.add(0, dy, 0);
			if (this.getEntityWorld().getFluidState(checkPos).isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
				surfaceY = checkPos.getY() + 1.0;
				foundWater = true;
			}
		}
		return foundWater ? java.util.OptionalDouble.of(surfaceY) : java.util.OptionalDouble.empty();
	}

	/** Slow, gradual downward velocity while sinking (not an immediate plunge - gives passengers a moment to bail out). */
	protected static final double SINKING_VERTICAL_SPEED = -0.08;
	/** How much deeper a damaged, non-float-capable hull sits while still floating, vs SURFACE_FLOAT_DEPTH's normal depth. */
	protected static final double SINKING_HULL_EXTRA_DEPTH = 2.0;
	/** How often this hull takes on more water while floating-but-damaged. */
	protected static final int WATER_DAMAGE_INTERVAL_TICKS = 100;
	/** Fraction of max health lost every WATER_DAMAGE_INTERVAL_TICKS while floating-but-damaged (10% = fully destroyed within 50 real seconds). */
	protected static final float WATER_DAMAGE_FRACTION = 0.1f;
	protected int ticksSinceWaterDamage;
	/** Ground attitude correction (OnGroundPitch) only engages once cruiseSpeed has dropped to this fraction of def.maxSpeed() or below. */
	protected static final float GROUND_ATTITUDE_SPEED_FRACTION = 0.15f;
	/** At/above this fraction of def.maxSpeed() while surfaced, the aircraft levels out (pitch/roll towards 0) in preparation for takeoff, rather than staying in its.. */
	protected static final float GROUND_LEVEL_SPEED_FRACTION = GROUND_ATTITUDE_SPEED_FRACTION;
	/** Stalling engages below this fraction of effectiveMaxSpeed while airborne - now def.stallSpeedFraction(), see that field's own doc; 0.3 remains the converter's own default, unchanged from when this was a hardcoded constant. */
	/** Ticks (this.age) after spawning during which stalling can't engage and orientation is unconditionally forced level. */
	protected static final int SPAWN_GRACE_TICKS = 60;

	/** Full damage immunity while this aircraft is still within its own spawn-grace/attitude-stabilizing window (see SPAWN_GRACE_TICKS's own doc and usages) - a freshly-spawned aircraft settling onto the ground shouldn't be able to take damage from that settling process itself, regardless of the source. Applies on ordinary ground too, not just a Carrier's own runway. */
	@Override
	public boolean damage(ServerWorld world, net.minecraft.entity.damage.DamageSource source, float amount) {
		if (this.age < SPAWN_GRACE_TICKS) {
			return false;
		}
		return super.damage(world, source, amount);
	}
	/** Gentle, from behind" Carrier landing approach that stays in motion the whole way (no speed-dependent settling) - see tudursvehiclemod$updateCarrierReturnToBase()'s own doc for how these are all used together. */
	private static final float CARRIER_LANDING_CRUISE_SPEED_FRACTION = 0.3f;
	private static final float CARRIER_LANDING_SPEED_BLEND = 0.05f;
	/** The final landing leg is built around continuous deceleration (see tudursvehiclemod$updateCarrierReturnToBase()'s own doc for the full reasoning) rather than the old "force onto an exact line" approach: how much cruiseSpeed decreases each tick during that final glide, and the floor it never drops below. The floor is deliberately kept below the "essentially landed" speed AbstractVehicleEntity's own carrier-landing recovery mechanism checks for, so simply coasting down to it (while also within recovery range) is sufficient for that separate, generic mechanism to actually pick this aircraft up - a non-zero floor guarantees this aircraft always keeps enough way on to actually reach that range in finite time, rather than potentially stalling short of it. */
	private static final float CARRIER_LANDING_FINAL_DECEL_PER_TICK = 0.01f;
	private static final float CARRIER_LANDING_FINAL_MIN_SPEED = 0.1f;
	/** Ticks of extra orbit-and-wait each successive carrierFormationIndex queues before beginning its own landing sequence - 100 ticks (5 seconds) per position, enough for the aircraft ahead in the queue to clear the immediate AddWeapon vicinity before the next one starts its own approach. */
	private static final int CARRIER_LANDING_QUEUE_DELAY_TICKS = 100;
	/** How much of the remaining horizontal-direction gap toward it is closed each tick (a small fraction - gentle enough to feel like a natural drift back on course, not an abrupt snap). See the glide-mode hook in updateVehicleMovement() for exactly how this is applied (horizontal-only, never touching vertical velocity). */
	private static final double CARRIER_GLIDE_COURSE_CORRECTION_BLEND = 0.15;
	/** Cap on accumulated fall speed during the spawn grace period. */
	protected static final double SPAWN_GRACE_MAX_FALL_SPEED = -1.5;
	/** Upper edge of the near-stall gentle-descent band, as an absolute fraction of effectiveMaxSpeed - now def.stallSpeedFraction() + 0.1, see that field's own doc. */
	/** Overall multiplier on the near-stall gravity formula. */
	protected static final float NEAR_STALL_GRAVITY_MULTIPLIER = 6.0f;
	/** Cap on accumulated fall speed from the near-stall gravity effect. */
	protected static final double NEAR_STALL_MAX_FALL_SPEED = -1.5;
	/** How quickly orientation SLERPs towards straight-down while stalling. */
	protected static final float STALL_PITCH_SMOOTHING = 0.15f;
	/** Constant baseline deceleration blend representing ongoing air resistance. */
	protected static final float AIR_DRAG_DECEL_BLEND = 0.002f;
	/** Diving accelerates cruiseSpeed towards this multiple of effectiveMaxSpeed (rather than the usual throttle-limited target). */
	protected static final float DIVE_MAX_SPEED_MULTIPLIER = 1.5f;
	/** How quickly cruiseSpeed blends towards the dive target speed. */
	protected static final float DIVE_ACCEL_BLEND = 0.05f;
	/** How quickly smoothedSpeedTargetPitch chases the raw flight-path angle back down while LEVELING OUT specifically (see that field's own doc). */
	protected static final float LEVEL_OUT_PITCH_BLEND = 0.03f;
	/** How steep (degrees, flight-path angle) an impact needs to be for tudursvehiclemod$checkBlockCrashDamage's own block-collision damage to apply at all. */
	protected static final float CRASH_ANGLE_THRESHOLD_DEGREES = 20.0f;
	/** Minimum speed (blocks/tick) for a steep-angle block impact to deal any damage at all. */
	protected static final double CRASH_MIN_SPEED = 0.5;
	/** Damage per unit of speed (blocks/tick) for a qualifying steep-angle block crash. */
	protected static final float CRASH_DAMAGE_PER_SPEED = 300.0f;
	/** Minimum speed (blocks/tick) for tudursvehiclemod$checkEntityCrashDamage's own entity-collision damage to apply at all. */
	protected static final double ENTITY_CRASH_MIN_SPEED = 0.6;
	/** Damage per unit of speed (blocks/tick) for a qualifying high-speed entity collision - tripled (was 40.0f). */
	protected static final float ENTITY_CRASH_DAMAGE_PER_SPEED = 120.0f;
	/** How quickly orientation SLERPs towards level during the ground-roll level-out (see tudursvehiclemod$easeTowardsLevelOnGround()). */
	protected static final float GROUND_LEVEL_SMOOTHING = 0.3f;
	/** Forced minimum blend rate (scaled by climbFactor, 0 at level up to this value at a full 90-degree climb) towards the climb-reduced target speed in the.. */
	protected static final float CLIMB_DECEL_MAX_BLEND = 0.04f;
	/** Floor on how far climbing can reduce the target speed while still within STEEP_CLIMB_GRACE_TICKS of sustained steep climbing. */
	protected static final float CLIMB_TARGET_MIN_FRACTION = 0.4f;
	/** How many consecutive ticks of a high climb factor (see its own check in tudursvehiclemod$climbTargetMinFraction()) count as "still within a brief maneuver" before.. */
	protected static final int STEEP_CLIMB_GRACE_TICKS = 60;
	/** Once past STEEP_CLIMB_GRACE_TICKS, the floor decays from CLIMB_TARGET_MIN_FRACTION down to 0 over this many further ticks (100 = 5 seconds). */
	protected static final int STEEP_CLIMB_DECAY_TICKS = 100;
	/** Climb factor (see its own computation at each call site) above which a climb counts as "steep" for STEEP_CLIMB_GRACE_TICKS/-DECAY_TICKS purposes. */
	protected static final float STEEP_CLIMB_THRESHOLD = 0.8f;

	@Override
	protected void updateVehicleMovement(VehicleDefinition def) {
		// A sinking wreck's attitude and descent are driven entirely by AbstractVehicleEntity's own tudursvehiclemod$applySinkingMotion(), which already ran this tick. Returning here leaves that untouched - this vehicle's own buoyancy would otherwise spring it straight back to the surface and the wreck would never go under. move() is still applied so the descent actually happens.
		if (this.tudursvehiclemod$isSinking()) {
			this.move(net.minecraft.entity.MovementType.SELF, this.getVelocity());
			return;
		}
		this.prevPropellerRotation = this.propellerRotation;
		this.prevOrientation.set(this.orientation);

		LivingEntity pilotEntity = getControllingPassenger();
		PlayerEntity player = pilotEntity instanceof PlayerEntity p ? p : null;

		// See this method's own doc for why setGlowing() is safe/proven to use for this mod's own entities too. called unconditionally now (shooter nullable) rather than only while a player is actively controlling, so "no controlling player" is always treated the same as "lock mode inactive" and correctly clears any leftover highlight.
		this.tudursvehiclemod$updateCarrierLockCandidatePreview(
				player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayerForLockPreview ? serverPlayerForLockPreview : null);

		// Runs unconditionally whenever this aircraft has its own route at all (casWaypointOverride != null, set once at spawn and never cleared for the rest of this aircraft's own lifetime), independent of isDroneActive()/carrierGlideMinSpeed/any other sub-state below - see tudursvehiclemod$updateCasForcedChunks()'s own doc for the full reasoning (this exact call was lost from an earlier edit and is being restored here).
		if (this.casWaypointOverride != null) {
			this.tudursvehiclemod$updateCasForcedChunks();
		}

		// Checked FIRST, independent of isDroneActive()/getDroneCenterPos() (the center itself may already show inactive by the time this runs) - see droneLandingCenter's own doc for why this exists at all, and why it's never set for a destroyed vehicle.
		// Per definitive log evidence: !isDestroyed() re-added here (and to the dispatch below) after logs showed updateDroneAutopilot() firing continuously, every tick, for multiple already-destroyed formation aircraft - a previous attempt at this exact same fix was reverted when the symptom seemed unreproducible, but is now confirmed necessary.
		if (player == null && !this.tudursvehiclemod$isDestroyed() && this.droneLandingCenter != null) {
			this.wasPiloted = false;
			this.tudursvehiclemod$updateDroneLandingRouteAutopilot(def, this.droneLandingCenter);
			return;
		}

		// A Drone Center-linked vehicle (see AbstractVehicleEntity's own tudursvehiclemod$isDroneActive() doc) flies its own autonomous routine entirely instead of the normal piloted/unpiloted logic below - checked first and self-contained (returns immediately) rather than interleaving with that existing, deeply-intertwined physics code, which would risk a subtle, hard-to-verify partial interaction between the two.
		// Also requires getDroneCenterPos() != null explicitly, not just isDroneActive() alone - isDroneActive() now reads a synced flag (so blade-spin rendering works correctly for observers - see getSpinningPartSpeedMultiplier()'s own doc), but droneCenterPos itself is only ever meaningfully populated SERVER-side (that field is never networked, since only the server needs it for the actual orbit math) - without this, an observing CLIENT would see isDroneActive()==true (correctly synced) while its own droneCenterPos stayed null, dispatching into the full autopilot anyway and immediately NullPointerException-ing trying to use it.
		// DroneFormationLeaderUuid != null is ALSO routed into this SAME dispatch now (rather than a separate, earlier branch that used to bypass updateDroneAutopilot() entirely - see this whole condition's own history in docs/IMPLEMENTATION_NOTES.md "編隊追随のディスパッチ不具合を修正") - that method itself now has its own dedicated handling for every formation-follow case (a Drone Center wingman with no route of its own at all, and a CAS/Carrier wingman past its own launch waypoints), so every wingman needs to actually reach it rather than being diverted away before ever getting there.
		if (player == null && !this.tudursvehiclemod$isDestroyed()
				&& ((this.tudursvehiclemod$isDroneActive() && this.tudursvehiclemod$getDroneCenterPos() != null) || this.droneFormationLeaderUuid != null)) {
			this.wasPiloted = false;
			this.tudursvehiclemod$updateDroneAutopilot(def);
			return;
		}

		if (player == null && this.wasPiloted) {
			// Just this tick transitioned from piloted to unpiloted. Preserves actual momentum either way (airborne or grounded) by deriving an implied throttle from current speed, rather than abruptly zeroing cruiseSpeed/throttle - the normal ground-deceleration blend (GROUND_DECEL_BLEND) then takes over gradually, same as it would for a piloted vehicle releasing the throttle.
			// Uses only the HORIZONTAL velocity component here, not the full 3D magnitude - the latter always included GROUNDED_STICK_VELOCITY's own constant downward "stick to the ground" nudge (see that field's own doc) whenever grounded, meaning actualSpeed (and so cruiseSpeed, which the surfaced branch below multiplies by a yaw-only, horizontal-only heading vector) was never actually 0 even sitting perfectly still.
			// Math.sqrt of the sum of squares (the previous version of this line) is an UNSIGNED magnitude, losing which direction (forward/backward relative to this aircraft's own current facing) it was actually moving in entirely - forcing cruiseSpeed positive here, even while genuinely still moving backward at the exact instant of dismounting, made the very next tick's own heading-based velocity reconstruction reverse direction on the spot (same root cause, and same fix, as onTrackedDataSet()'s own identical bug - see that method's own doc). Dotted with this aircraft's own current forward-facing unit vector instead, giving a signed speed - negative while genuinely still moving backward.
			Vec3d currentVelocity = this.getVelocity();
			double yawRad = Math.toRadians(this.getYaw());
			double forwardX = -Math.sin(yawRad);
			double forwardZ = Math.cos(yawRad);
			double actualSpeed = currentVelocity.x * forwardX + currentVelocity.z * forwardZ;
			this.cruiseSpeed = (float) actualSpeed;
			float impliedThrottle;
			if (this.tudursvehiclemod$isOutOfFuel()) {
				// Out of fuel means no engine power at all, regardless of whatever speed this aircraft happens to be gliding/falling at right now - the speed itself is preserved above (cruiseSpeed) so it still decelerates naturally via gravity/drag, but treating that speed as genuine throttle-driven propulsion (the un-gated case below) would keep the engine "pushing" at that same speed while it slowly decays, instead of having no thrust at all from the moment fuel actually ran out.
				impliedThrottle = 0f;
			} else {
				float effectiveMaxSpeedNow = this.tudursvehiclemod$getEffectiveMaxSpeed()
						* MathHelper.lerp(this.getLandingGearProgress(), GEAR_DEPLOYED_MAX_SPEED_MULTIPLIER, 1f);
				// Per the same direct report as actualSpeed's own doc above: the lower clamp bound here used to be a hardcoded 0f, silently discarding a genuinely negative (reversing) actualSpeed down to 0 even after the sign fix above - this aircraft's own normal piloted throttle range already allows down to def.reverseThrottle() (see updateThrottle()'s own call site further up this same method), so the implied throttle derived here does too, for the same continuity reason cruiseSpeed itself is preserved rather than zeroed.
				impliedThrottle = effectiveMaxSpeedNow > 0f
						? MathHelper.clamp((float) (actualSpeed / effectiveMaxSpeedNow), def.reverseThrottle(), 1f)
						: 0f;
			}
			setThrottleDirect(impliedThrottle);
			// GroundAttitudeTargetYaw/groundLevelTargetYaw only ever get locked once and then incrementally adjusted by the player's own sideways input each tick while manned - if they were actively turning right up until dismounting, this target could have diverged significantly from the vehicle's actual current yaw. Neither easing method resets its own Locked flag for the grounded+unmanned case specifically (only other branches like stalling/unpiloted-airborne do), so it kept chasing that stale, diverged target for several ticks after dismount. Resetting both here forces a fresh lock onto the actual current yaw the instant control is lost.
			this.groundAttitudeYawLocked = false;
			this.groundLevelYawLocked = false;
		}
		this.wasPiloted = player != null;

		float throttle;
		if (player != null) {
			throttle = updateThrottle(player, 0.015f * def.throttleUpDown().orElse(1.0f), def.reverseThrottle(), 1.0f);
			// Applies specifically to this
			// aircraft's own ground-taxi turning (see
			// tudursvehiclemod$easeTowardsGroundAttitude()'s own doc,
			// which uses this same getSyncedSidewaysInput() to steer
			// while grounded, same turnSpeed-scaled convention as
			// Ship/Car's own steering) - a real plane similarly can't
			// turn effectively while taxiing at very low speed. See
			// MovementStats's own pivot_turn_throttle doc /
			// AbstractVehicleEntity's own applyPivotTurnThrottleRestriction()
			// doc for exactly how this works (a no-op whenever
			// pivot_turn_throttle is its own default 0). Deliberately NOT
			// applied to this aircraft's own actual in-flight turning
			// (banking), which already has its own separate, existing
			// stall-speed mechanic covering broadly the same "needs
			// enough speed to turn effectively" concept once airborne.
			throttle = applyPivotTurnThrottleRestriction(def, throttle, this.getSyncedSidewaysInput(),
					0.015f * def.throttleUpDown().orElse(1.0f));
		} else {
			// Unpiloted: gradually spool the throttle down instead of holding whatever it was left at.
			throttle = Math.max(0f, getThrottle() - UNMANNED_THROTTLE_DECAY);
			setThrottleDirect(throttle);
		}
		this.propellerRotation += 10f + throttle * 50f;

		// A seaplane floating on water is just as "grounded" as one sitting
		// On a runway for taxi/takeoff purposes - but 
		// only for an aircraft actually configured as float-capable
		// (def.isFloatCapable(), MC Heli's own Float=true - a seaplane/
		// flying boat). An ordinary aircraft (Float=false or omitted, the
		// default) touching water instead falls into the separate
		// "sinking" state below - see that field's own doc - rather than
		// getting this same taxi-capable treatment.
		//
		// Spurious stalling while
		// floating: this uses tudursvehiclemod$findWaterSurfaceY()'s own
		// wider (dy -2.+3) scan rather than the exact isTouchingWater()
		// bounding-box check - the buoyancy spring below naturally
		// oscillates this aircraft's own Y slightly around its own target
		// float height (springs don't sit perfectly still), which could
		// momentarily lift its own hitbox clear of isTouchingWater()'s own
		// exact overlap check; that flicker was enough to occasionally tip
		// the debounced surfaced state below back to false for a moment,
		// which let stalling (correctly disabled while surfaced) briefly
		// engage instead. The wider scan tolerates that same oscillation
		// without flickering at all, since the aircraft never actually
		// leaves its own several-block-tall detection window.
		boolean nearWaterForFloating = def.isFloatCapable() && tudursvehiclemod$findWaterSurfaceY().isPresent();
		// isOnGround() is already documented elsewhere in this class (see tudursvehiclemod$suppressStallWhileCarried()'s own doc) as unreliable while the supporting runway tile itself is moving each tick.
		// Uses lastCarriedByRunwayTick (a world-time timestamp) rather than !hasLifted, to avoid a permanent deadlock the original fix had.
		boolean recentlyCarriedByRunway = this.getEntityWorld().getTime() - this.lastCarriedByRunwayTick <= 1;
		boolean rawSurfaced = this.isOnGround() || nearWaterForFloating || recentlyCarriedByRunway;
		if (rawSurfaced == this.surfacedState) {
			this.surfacedDebounceTicks = 0;
		} else {
			this.surfacedDebounceTicks++;
			if (this.surfacedDebounceTicks >= SURFACED_DEBOUNCE_TICKS) {
				this.surfacedState = rawSurfaced;
				this.surfacedDebounceTicks = 0;
			}
		}
		boolean surfaced = this.surfacedState;
		// An aircraft NOT configured as float-capable
		// (def.isFloatCapable() false - MC Heli's own Float=false/omitted,
		// an ordinary aircraft never designed to survive a water landing)
		// that's actually touching water at all - this takes priority over
		// every other orientation/velocity branch below (SPAWN_GRACE_TICKS
		// aside), forcing player input to be ignored entirely and a slow,
		// steady sink to begin, regardless of whatever the pilot's own
		// throttle/stick inputs are doing. Deliberately NOT gated by
		// isOnGround() too - a float-incapable aircraft skimming the
		// water's own surface without yet fully "landing" on it should
		// still immediately start sinking rather than briefly continuing
		// to fly normally.
		boolean sinking = !def.isFloatCapable() && this.isTouchingWater();

		// Landing gear hanging in the airflow adds drag.
		float effectiveMaxSpeed = this.tudursvehiclemod$getEffectiveMaxSpeed()
				* MathHelper.lerp(this.getLandingGearProgress(), GEAR_DEPLOYED_MAX_SPEED_MULTIPLIER, 1f);
		// Whether the aircraft has enough ACTUAL AIRSPEED to sustain controlled flight.
		float actualSpeed = (float) this.getVelocity().length();
		// Hysteresis: engages at MIN_FLIGHT_SPEED_FRACTION, only disengages once back down at/below SUSTAINED_FLIGHT_DISENGAGE_FRACTION.
		if (this.sustainedFlightState) {
			if (this.cruiseSpeed < effectiveMaxSpeed * SUSTAINED_FLIGHT_DISENGAGE_FRACTION) {
				this.sustainedFlightState = false;
			}
		} else if (this.cruiseSpeed >= effectiveMaxSpeed * MIN_FLIGHT_SPEED_FRACTION) {
			this.sustainedFlightState = true;
		}
		boolean sustainedFlight = this.sustainedFlightState;
		// Landing gear deployed lowers the stall speed threshold (gear adds drag/lift-affecting airflow disruption that changes stall behavior).
		float stallDisengageFraction = def.stallSpeedFraction() + 0.1f;
		float stallEngageFraction = MathHelper.lerp(this.getLandingGearProgress(),
				def.stallSpeedFraction() - 0.1f, def.stallSpeedFraction());
		// Stall state with hysteresis: engages below stallEngageFraction of max speed, but only disengages once back up to stallDisengageFraction..
		if (surfaced || this.age < SPAWN_GRACE_TICKS) {
			this.stalling = false;
		} else if (!this.stalling && this.hasLifted && actualSpeed < effectiveMaxSpeed * stallEngageFraction) {
			this.stalling = true;
		} else if (this.stalling && actualSpeed >= effectiveMaxSpeed * stallDisengageFraction) {
			this.stalling = false;
		}

		if (this.age < SPAWN_GRACE_TICKS && !rawSurfaced && !this.tudursvehiclemod$isCarrierPlayerControlled()) {
			// Forces a dead-level attitude (current yaw, pitch=0, roll=0) for the first few seconds after spawning, unconditionally.
			tudursvehiclemod$easeTowardsLevelFlight();
		} else if (sinking) {
			// Disabled/sinking (see that field's own doc) - eases toward
			// dead level (a lifeless aircraft settles flat rather than
			// staying frozen in whatever attitude it hit the water at)
			// and ignores every bit of player input, same as the
			// unpiloted-airborne fallback further below uses for the
			// exact same "nobody's actually flying this thing right now"
			// reason.
			tudursvehiclemod$easeTowardsLevelFlight();
			this.pendingYawInput = 0f;
			this.pendingPitchInput = 0f;
			this.hasLifted = false;
			this.groundAttitudeYawLocked = false;
			this.levelFlightYawLocked = false;
			this.groundLevelYawLocked = false;
			this.stallYawLocked = false;
		} else if (surfaced) {
			// Eases yaw-preserving pitch towards this vehicle's own configured "on ground" attitude (def.onGroundPitch().
			if (this.cruiseSpeed <= def.maxSpeed() * GROUND_ATTITUDE_SPEED_FRACTION) {
				tudursvehiclemod$easeTowardsGroundAttitude(def, player);
				this.levelFlightYawLocked = false;
				this.groundLevelYawLocked = false;
			} else if (this.cruiseSpeed >= def.maxSpeed() * GROUND_LEVEL_SPEED_FRACTION) {
				tudursvehiclemod$easeTowardsLevelOnGround(player);
			} else {
				this.levelFlightYawLocked = false;
				this.groundLevelYawLocked = false;
			}

			this.pendingYawInput = 0f;
			this.pendingPitchInput = 0f;
			this.noInputTicks = 0;
			this.rollSnapTargetLocked = false;
			this.stallYawLocked = false;

			// Always reset the moment this aircraft is surfaced, regardless of speed.
			if (player != null) {
				this.hasLifted = false;
			}
			this.steepClimbTicks = 0;
		} else if (this.stalling) {
			// Stalled (airborne, too slow to fly).
			tudursvehiclemod$forceNoseDown();
			this.hasLifted = true;
			this.groundAttitudeYawLocked = false;
			this.levelFlightYawLocked = false;
			this.groundLevelYawLocked = false;
		} else if (player != null) {
			updateOrientation(player);
			this.hasLifted = true;
			// Not unpiloted-and-airborne right now, so the next time it becomes so (this player dismounts mid-air) it locks a fresh target yaw.
			this.levelFlightYawLocked = false;
			// Not surfaced right now, so the next landing locks a fresh target too.
			this.groundAttitudeYawLocked = false;
			this.groundLevelYawLocked = false;
			this.stallYawLocked = false;
		} else {
			// Unpiloted and airborne: always ease directly towards dead-level flight (current heading, pitch=0, roll=0).
			tudursvehiclemod$easeTowardsLevelFlight();
			this.hasLifted = true;
			this.groundAttitudeYawLocked = false;
			this.groundLevelYawLocked = false;
			this.stallYawLocked = false;
		}

		Vec3d target;
		if (this.age < SPAWN_GRACE_TICKS && !surfaced && !this.tudursvehiclemod$isCarrierPlayerControlled()) {
			// Attitude is locked level during the spawn grace period (see the orientation branch above), but that alone doesn't stop this (setNoGravity(true)) entity from..
			double fallY = Math.max(SPAWN_GRACE_MAX_FALL_SPEED, this.getVelocity().y + def.gravity());
			target = new Vec3d(0, fallY, 0);
		} else if (sinking) {
			// While hull durability remains, floats via spring buoyancy
			// (SINKING_HULL_EXTRA_DEPTH deeper than normal); once
			// destroyed, actually sinks.
			// "水上機以外の水面不時着").
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
			target = new Vec3d(currentVelocity.x * 0.9, verticalVelocity, currentVelocity.z * 0.9);
			this.cruiseSpeed = 0f;
			setThrottleDirect(0f);
			// Gradual water damage while still floating.
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
		} else if (player == null && this.getEntityWorld().isClient()) {
			// This branch previously only kept the current velocity, never actually touching orientation - meaning an observing client's own copy had its roll (and yaw/pitch) frozen at whatever they happened to be the instant this branch first started being hit, since the drone autopilot itself only ever runs server-side (see that method's own crash-fix doc) and nothing else here ever updated orientation again. Eases toward the synced target (yaw/pitch/roll all three) instead, same general approach as tudursvehiclemod$easeTowardsLevelFlight()'s own doc.
			// Re-extracting (yaw, pitch, roll) from the composed orientation quaternion every tick suffers a gimbal-lock-like coupling artifact in that specific combination - the extraction itself doesn't cleanly invert the composition once multiple axes are simultaneously significant. Tracks each axis in its own dedicated field instead (seeded once, then NEVER read back from the orientation afterward), exactly matching the same philosophy that already fixed this same class of bug on the server side's own drone autopilot (see droneTrackedYaw/droneTrackedRoll's own doc).
			if (!this.clientTrackedOrientationInitialized) {
				float[] initialAngles = extractYawPitchRoll(this.orientation);
				this.clientTrackedYaw = initialAngles[0];
				this.clientTrackedPitch = initialAngles[1];
				this.clientTrackedRoll = initialAngles[2];
				this.clientTrackedOrientationInitialized = true;
			}
			float syncedYawTarget = this.dataTracker.get(SYNCED_YAW);
			float syncedPitchTarget = this.dataTracker.get(SYNCED_PITCH);
			float syncedRollTarget = this.dataTracker.get(SYNCED_ROLL);
			this.clientTrackedYaw += MathHelper.wrapDegrees(syncedYawTarget - this.clientTrackedYaw) * CLIENT_SYNC_SMOOTHING;
			this.clientTrackedPitch += MathHelper.wrapDegrees(syncedPitchTarget - this.clientTrackedPitch) * CLIENT_SYNC_SMOOTHING;
			this.clientTrackedRoll += MathHelper.wrapDegrees(syncedRollTarget - this.clientTrackedRoll) * CLIENT_SYNC_SMOOTHING;
			this.setOrientationFromEuler(this.clientTrackedYaw, this.clientTrackedPitch, this.clientTrackedRoll);
			this.orientation.normalize();
			target = this.getVelocity();
		} else if (surfaced) {
			// Grounded/floating: accelerates at the normal ground rate, but decelerates at its own, separately-tunable (and much gentler) rate.
			float targetSpeed = throttle * effectiveMaxSpeed;
			float groundBlend = targetSpeed < this.cruiseSpeed ? GROUND_DECEL_BLEND : GROUND_SPEED_BLEND;
			this.cruiseSpeed += (targetSpeed - this.cruiseSpeed) * groundBlend;
			// The exponential decay above never mathematically reaches exactly 0, leaving a persistent, ever-shrinking-but-never-zero residual that a client/server split (cruiseSpeed is a plain, non-networked field computed independently on both) can very gradually diverge on over many ticks - snaps cleanly to the target once close enough, rather than approaching it asymptotically forever.
			if (Math.abs(targetSpeed - this.cruiseSpeed) < 1.0e-4f) {
				this.cruiseSpeed = targetSpeed;
			}
			Vec3d heading = new Vec3d(-Math.sin(Math.toRadians(this.getYaw())), 0,
					Math.cos(Math.toRadians(this.getYaw())));
			double verticalVelocity;
			if (!this.isOnGround() && def.isFloatCapable()) {
				// Actually floating on water (not
				// resting on solid ground) needs genuine spring buoyancy
				// (see tudursvehiclemod$findWaterSurfaceY()'s own doc),
				// same approach ShipEntity/SubmarineEntity already use for
				// their own surfaced mode - solid ground can physically
				// support GROUNDED_STICK_VELOCITY's own small constant
				// downward nudge below (the ground itself stops it from
				// actually sinking through), but water has no equivalent
				// structural support at all, so that same constant nudge
				// just sinks a float-capable aircraft straight down
				// instead of letting it actually float.
				java.util.OptionalDouble surfaceY = tudursvehiclemod$findWaterSurfaceY();
				if (surfaceY.isPresent()) {
					double targetY = surfaceY.getAsDouble() + SURFACE_FLOAT_DEPTH;
					// Per tudursvehiclemod$applySurfaceFloatSpring()'s own doc: shared across every vehicle type with water buoyancy - freezes this vehicle's own Y outright once settled at a stable surface, only computing/applying spring velocity while genuinely transitioning.
					verticalVelocity = this.tudursvehiclemod$applySurfaceFloatSpring(targetY, this.getVelocity().y,
							SURFACE_SPRING_STRENGTH, SURFACE_VERTICAL_DAMPING, SURFACE_SETTLE_POSITION_THRESHOLD, SURFACE_SETTLE_VELOCITY_THRESHOLD);
				} else {
					// No water found nearby at all despite surfaced/float-capable being true (e.g. a debounce-lag edge case) - falls back to plain gravity rather than fighting for a target that doesn't exist.
					this.tudursvehiclemod$resetSurfaceFloatLock();
					verticalVelocity = this.getVelocity().y - 0.08;
				}
			} else {
				// Small constant downward nudge rather than exactly 0 - solid ground physically supports this.
				verticalVelocity = GROUNDED_STICK_VELOCITY;
			}
			target = new Vec3d(heading.x * this.cruiseSpeed, verticalVelocity, heading.z * this.cruiseSpeed);
		} else if (sustainedFlight) {
			// Same core computation as AbstractVehicleEntity#approachThrottledVelocity(), but done directly here rather than through that shared method, since it doesn't..
			Vec3d heading = this.getRotationVec(1.0f);
			float pitch = tudursvehiclemod$smoothedSpeedTargetPitch();
			float targetSpeed;
			float blend;
			if (pitch >= 0f) {
				float climbFactor = MathHelper.clamp(pitch / 90f, 0f, 1f);
				// Floor starts at CLIMB_TARGET_MIN_FRACTION (enough momentum to carry a loop through its brief, steep-climb portion.
				targetSpeed = throttle * effectiveMaxSpeed
						* MathHelper.lerp(climbFactor, 1f, tudursvehiclemod$climbTargetMinFraction(climbFactor));
				float normalBlend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
				blend = Math.max(normalBlend, climbFactor * CLIMB_DECEL_MAX_BLEND);
			} else {
				float diveFactor = MathHelper.clamp(-pitch / 90f, 0f, 1f);
				// Starts from throttle*effectiveMaxSpeed at diveFactor=0 (NOT a fixed effectiveMaxSpeed regardless of throttle).
				targetSpeed = MathHelper.lerp(diveFactor, throttle * effectiveMaxSpeed,
						effectiveMaxSpeed * DIVE_MAX_SPEED_MULTIPLIER);
				float normalBlend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
				blend = Math.max(normalBlend, diveFactor * DIVE_ACCEL_BLEND);
				this.steepClimbTicks = 0;
			}
			// Air drag: a constant baseline deceleration responsiveness that always applies once decelerating (targetSpeed < cruiseSpeed), regardless of..
			float effBlend = tudursvehiclemod$effectiveBlend(targetSpeed, blend);
			if (targetSpeed < this.cruiseSpeed) {
				effBlend = Math.max(effBlend, AIR_DRAG_DECEL_BLEND);
			}
			this.cruiseSpeed += (targetSpeed - this.cruiseSpeed) * effBlend;
			// Same fix/reasoning as this class's own earlier identical addition for the grounded branch above.
			if (Math.abs(targetSpeed - this.cruiseSpeed) < 1.0e-4f) {
				this.cruiseSpeed = targetSpeed;
			}
			target = heading.multiply(this.cruiseSpeed);
		} else {
			// Airborne, below 50% throttle: glide instead of dropping speed instantly.
			float pitch = tudursvehiclemod$smoothedSpeedTargetPitch();
			float targetSpeed;
			float glideBlend;
			if (pitch >= 0f) {
				float climbFactor = MathHelper.clamp(pitch / 90f, 0f, 1f);
				// Same time-decaying floor as the sustained-flight branch above.
				targetSpeed = throttle * effectiveMaxSpeed
						* MathHelper.lerp(climbFactor, 1f, tudursvehiclemod$climbTargetMinFraction(climbFactor));
				glideBlend = Math.max(LOW_THROTTLE_GLIDE_BLEND, climbFactor * CLIMB_DECEL_MAX_BLEND);
			} else {
				float diveFactor = MathHelper.clamp(-pitch / 90f, 0f, 1f);
				// Same continuity fix as the sustained-flight branch above.
				targetSpeed = MathHelper.lerp(diveFactor, throttle * effectiveMaxSpeed,
						effectiveMaxSpeed * DIVE_MAX_SPEED_MULTIPLIER);
				glideBlend = Math.max(LOW_THROTTLE_GLIDE_BLEND, diveFactor * DIVE_ACCEL_BLEND);
				this.steepClimbTicks = 0;
			}
			float effGlideBlend = tudursvehiclemod$effectiveBlend(targetSpeed, glideBlend);
			if (targetSpeed < this.cruiseSpeed) {
				effGlideBlend = Math.max(effGlideBlend, AIR_DRAG_DECEL_BLEND);
			}
			this.cruiseSpeed += (targetSpeed - this.cruiseSpeed) * effGlideBlend;
			// Same fix/reasoning as this class's own earlier identical addition for the grounded branch above.
			if (Math.abs(targetSpeed - this.cruiseSpeed) < 1.0e-4f) {
				this.cruiseSpeed = targetSpeed;
			}
			// Full 3D heading via this.getRotationVec() (matching the sustained-flight branch, and reverted for the same reason as there.
			Vec3d heading = this.getRotationVec(1.0f);
			target = heading.multiply(this.cruiseSpeed);
			// This is an absolute upper fraction (def.stallSpeedFraction() + a fixed 10 percentage points, not gear-adjusted) - same value as stallDisengageFraction above, reused here.
			float nearStallUpperFraction = stallDisengageFraction;
			if (!this.stalling && this.cruiseSpeed < effectiveMaxSpeed * nearStallUpperFraction) {
				float lowerBound = effectiveMaxSpeed * stallEngageFraction;
				float upperBound = effectiveMaxSpeed * nearStallUpperFraction;
				float nearStallFactor = upperBound > lowerBound
						? MathHelper.clamp(1f - (this.cruiseSpeed - lowerBound) / (upperBound - lowerBound), 0f, 1f)
						: 1f;
				double gravityStrength = def.gravity() * NEAR_STALL_GRAVITY_MULTIPLIER * nearStallFactor;
				double gravityDrivenY = Math.max(NEAR_STALL_MAX_FALL_SPEED, this.getVelocity().y + gravityStrength);
				double blendedY = MathHelper.lerp(nearStallFactor, target.y, gravityDrivenY);
				target = new Vec3d(target.x, blendedY, target.z);
			}
		}

		if (surfaced && sustainedFlight && !this.hasLifted) {
			// Deliberately does NOT set hasLifted=true here.
			this.tudursvehiclemod$resetSurfaceFloatLock();
			target = new Vec3d(target.x, LIFTOFF_VELOCITY, target.z);
		}

		double preMoveSpeed = target.length();
		double preMoveHorizontalSpeed = Math.sqrt(target.x * target.x + target.z * target.z);
		float preMoveFlightPathAngle = (preMoveHorizontalSpeed < 1.0e-4 && Math.abs(target.y) < 1.0e-4)
				? 0f : (float) Math.toDegrees(Math.atan2(target.y, preMoveHorizontalSpeed));

		this.setVelocity(target);
		this.move(MovementType.SELF, this.getVelocity());

		this.tudursvehiclemod$checkBlockCrashDamage(preMoveSpeed, preMoveFlightPathAngle);
		this.tudursvehiclemod$checkEntityCrashDamage(preMoveSpeed);
		this.tudursvehiclemod$checkLandingImpactDamage(preMoveSpeed, def);
		// Both of the carrierGlideMinSpeed blocks below (course correction and minimum-speed floor) are meant ONLY for a genuinely unpiloted, autonomously-gliding aircraft (see carrierGlideMinSpeed's own field doc: "this aircraft's own flight control be turned off entirely.. behaving like a genuinely unpiloted aircraft"), never for one a player has since boarded/switched into - carrierGlideMinSpeed itself is deliberately never reset once set (see that field's own doc for why), so without this guard, a player taking over an aircraft that had already begun its own glide phase would keep having their own heading/speed silently overridden, feeling exactly like control had been handed back to the autopilot. player==null (same determination already established at this method's own top, not a fresh getControllingPassenger() call) matches the exact same convention every other autopilot-dispatch branch in this method already uses.
		// And applies a gentle, continuous HORIZONTAL-ONLY course correction toward the mothership's own current AddWeapon position, blended in gradually rather than snapped to instantly. Deliberately never touches the Y/vertical velocity component at all - unlike the old, removed "actively steer toward the target" design (which also actively controlled pitch/altitude-seeking, the exact reason it kept maintaining level flight instead of ever descending), this only nudges horizontal heading, leaving gravity/normal physics to keep governing the actual descent.
		if (player == null && this.carrierGlideMinSpeed > 0f && this.carrierMothershipUuid != null && this.getEntityWorld() instanceof ServerWorld glideServerWorld) {
			Entity glideMothershipEntity = glideServerWorld.getEntity(this.carrierMothershipUuid);
			if (glideMothershipEntity instanceof AbstractVehicleEntity glideMothership && !glideMothershipEntity.isRemoved()
					&& this.carrierMothershipWeaponIndex >= 0 && this.carrierMothershipWeaponIndex < glideMothership.getDefinition().weapons().size()) {
				com.example.tudursvehiclemod.asset.WeaponDefinition glideMothershipWeapon = glideMothership.getDefinition().weapons().get(this.carrierMothershipWeaponIndex);
				Vec3d glideAddWeaponPos = glideMothership.tudursvehiclemod$computeWeaponSpawnPos(glideMothership.getDefinition(), glideMothershipWeapon, this.carrierMothershipWeaponIndex).pos();
				double gdx = glideAddWeaponPos.x - this.getX();
				double gdz = glideAddWeaponPos.z - this.getZ();
				double gHorizontalDistance = Math.sqrt(gdx * gdx + gdz * gdz);
				if (gHorizontalDistance > 1.0e-4) {
					Vec3d preCorrectionVelocity = this.getVelocity();
					Vec3d towardAddWeapon = new Vec3d(gdx / gHorizontalDistance, 0, gdz / gHorizontalDistance);
					double preHorizontalSpeed = Math.sqrt(preCorrectionVelocity.x * preCorrectionVelocity.x + preCorrectionVelocity.z * preCorrectionVelocity.z);
					Vec3d correctedHorizontal;
					if (preHorizontalSpeed > 1.0e-4) {
						Vec3d currentHorizontalDir = new Vec3d(preCorrectionVelocity.x / preHorizontalSpeed, 0, preCorrectionVelocity.z / preHorizontalSpeed);
						Vec3d blendedDir = currentHorizontalDir.add(towardAddWeapon.subtract(currentHorizontalDir).multiply(CARRIER_GLIDE_COURSE_CORRECTION_BLEND));
						double blendedLength = Math.sqrt(blendedDir.x * blendedDir.x + blendedDir.z * blendedDir.z);
						correctedHorizontal = blendedLength > 1.0e-4
								? new Vec3d(blendedDir.x / blendedLength * preHorizontalSpeed, 0, blendedDir.z / blendedLength * preHorizontalSpeed)
								: new Vec3d(preCorrectionVelocity.x, 0, preCorrectionVelocity.z);
					} else {
						correctedHorizontal = new Vec3d(towardAddWeapon.x * this.carrierGlideMinSpeed, 0, towardAddWeapon.z * this.carrierGlideMinSpeed);
					}
					this.setVelocity(correctedHorizontal.x, preCorrectionVelocity.y, correctedHorizontal.z);
				}
			}
		}
		// Gated on player==null too, for the exact same reason.
		if (player == null && this.carrierGlideMinSpeed > 0f) {
			Vec3d postMoveVelocity = this.getVelocity();
			double horizontalSpeed = Math.sqrt(postMoveVelocity.x * postMoveVelocity.x + postMoveVelocity.z * postMoveVelocity.z);
			if (horizontalSpeed < this.carrierGlideMinSpeed) {
				Vec3d horizontalDir = horizontalSpeed > 1.0e-4
						? new Vec3d(postMoveVelocity.x / horizontalSpeed, 0, postMoveVelocity.z / horizontalSpeed)
						: new Vec3d(-Math.sin(Math.toRadians(this.getYaw())), 0, Math.cos(Math.toRadians(this.getYaw())));
				Vec3d boostedVelocity = new Vec3d(horizontalDir.x * this.carrierGlideMinSpeed, postMoveVelocity.y, horizontalDir.z * this.carrierGlideMinSpeed);
				this.setVelocity(boostedVelocity);
				this.cruiseSpeed = this.carrierGlideMinSpeed;
			}
		}
		// Wake trail for a float-capable (seaplane) aircraft while actually on/near the water surface - see AbstractVehicleEntity's own tudursvehiclemod$updateWakeTrail() doc. findWaterSurfaceY()'s own narrow (-2.3 block) vertical scan already naturally excludes a plane flying high above water (returns empty there), so no separate "is this genuinely floating, not just flying over water" check is needed on top of it. Recomputes findWaterSurfaceY() fresh here rather than reusing nearWaterForFloating computed earlier in this same method - that local is out of scope by this point, and this is a cheap enough scan not to be worth threading a field through just to avoid a second call.
		if (def.isFloatCapable()) {
			// Called UNCONDITIONALLY here, before the water-detection check below - this aircraft's own wake history is now still pruned/smoothed/ratcheted every tick even while flying somewhere far from water (a seaplane can end up anywhere, unlike a boat/car), rather than freezing forever the moment that first happens.
			this.tudursvehiclemod$pruneWakeTrailOnly(this.tudursvehiclemod$getActualForwardSpeed());
			java.util.OptionalDouble wakeSurfaceY = tudursvehiclemod$findWaterSurfaceY();
			if (wakeSurfaceY.isPresent()) {
				this.tudursvehiclemod$updateWakeTrail(wakeSurfaceY.getAsDouble(), this.tudursvehiclemod$getActualForwardSpeed());
			}
		}
	}

	/** Visual roll angle (degrees) while actively turning toward the orbit target, purely cosmetic. */
	protected static final float DRONE_ORBIT_BANK_DEGREES = 20.0f;
	/** How strongly the autopilot corrects back toward its own natural orbit radius (see tudursvehiclemod$updateDroneAutopilot()'s own doc) each tick, as a fraction of the current radius error - only matters while off the circle (e.g. still climbing after takeoff, or pushed off course); has no effect once already orbiting at the natural radius. */
	protected static final double DRONE_ORBIT_RADIAL_CORRECTION_GAIN = 0.05;
	/** How strongly the autopilot corrects vertically toward its own target orbit altitude each tick, as a fraction of the current altitude error (clamped to at most half the cruise speed so it doesn't try to climb/dive unrealistically fast). */
	protected static final double DRONE_ORBIT_ALTITUDE_CORRECTION_GAIN = 0.05;

	/** This vehicle's own autonomous target-drone flight routine - by default (no recorded script yet, see that same doc for the planned scripting system this is the foundation for), climbs to its own configured orbit altitude above the linked Drone Center and then orbits it indefinitely, at a radius that naturally follows from this vehicle's own turnSpeed() and cruise speed (angularVelocity = speed / radius) rather than a uniform fixed value - a more maneuverable (higher turnSpeed) or slower vehicle naturally orbits tighter, and vice versa. Deliberately drives velocity directly toward a target point (the same general approach VtolEntity's own helicopter mode already uses for its own movement) rather than trying to reproduce this class's own complex, stall/lift-aware piloted flight model via synthetic control inputs - far simpler to get correct and verify, and a target drone's own job (being reliably somewhere specific to shoot at) benefits more from predictable positioning than from realistic fixed-wing flight dynamics anyway. Nose (yaw) is guaranteed to always exactly match the actual resulting travel direction - reconstructed from the rate-limited yaw itself rather than computed as a separate value that could diverge from where the vehicle is actually, physically heading. */
	protected void tudursvehiclemod$updateDroneAutopilot(VehicleDefinition def) {
		// A Drone Center wingman has no route of its own at all (unlike a CAS/Carrier wingman, which still carries casWaypointOverride purely for attack-flag/timeout bookkeeping even while formation-following) - diverts straight to formation-follow here, before anything below that assumes getDroneCenterPos() is actually populated (never true for a formation-follow-only wingman).
		if (this.droneFormationLeaderUuid != null && this.casWaypointOverride == null) {
			this.tudursvehiclemod$updateDroneFormationFollow(def);
			return;
		}
		// Later extended by a direct request for a Drone Center dummy-pilot aircraft attack feature to also cover a normal (non-CAS/Carrier) Drone-Center-bound aircraft, not just a CAS/Carrier wingman's own player-initiated lock: an active lock (carrierLockedTargetUuid != null, however it got there - tudursvehiclemod$tryLockCarrierTarget()'s own crosshair-based assignment, OR entity.DummyPilotEntity's own combat AI via tudursvehiclemod$updateDroneCombatLock()) pursues/attacks its own assigned target INSTEAD of every other dispatch below, unconditionally, checked first before even formation-follow/CAS/Carrier-specific routing - this takes priority over everything else for as long as the lock stays active. tudursvehiclemod$updateCarrierLockPursuit() itself clears carrierLockedTargetUuid (falling through to ordinary dispatch on the very next tick) once the target is destroyed/removed, or shaken off.
		if (this.carrierLockedTargetUuid != null) {
			this.tudursvehiclemod$updateCarrierLockPursuit(def);
			return;
		}
		// Closes canopy parts, mirroring onPilotMounted()'s own current behavior for a real pilot boarding - a drone never actually triggers that method (isDroneActive() bypasses normal boarding entirely), so canopy was staying at whatever state it was in before activation instead of closing like it would for a real pilot. Hatch is no longer force-closed here either, matching onPilotMounted()'s own current behavior (see that method's own doc) - a drone's own hatch simply stays at whatever state it was already in.
		this.setCanopyOpen(false);
		// SetThrottleDirect() below clamps to 0 whenever isWingLockedFolded() is true (a second, independent gate alongside isOutOfFuel() - see that method's own doc) - a drone spawned/parked with wings folded had no pilot able to manually unfold them either. uses tudursvehiclemod$forceWingFoldOpen() rather than tryToggleWingFold() - see that method's own doc for why tryToggleWingFold()'s own speed gate (correct for its INTENDED player-initiated use case) isn't appropriate for this automatic, no-pilot-available case.
		if (!this.isWingFoldOpen()) {
			this.tudursvehiclemod$forceWingFoldOpen(true);
		}
		net.minecraft.util.math.BlockPos center = this.tudursvehiclemod$getDroneCenterPos();
		if (center == null) {
			// Defensive: the dispatch in updateVehicleMovement() already checks this before calling here, but guards again in case this method is ever reached some other way.
			return;
		}
		double targetY = center.getY() + this.tudursvehiclemod$getDroneOrbitAltitude();
		// Revised to use altitude relative to the Drone Center directly (the same value the orbit math below already needs) instead of isOnGround(), in case that check has some quirk/lag that never actually registers this vehicle as airborne. Still a one-shot "just changed state" trigger, same as the existing automatic auto-deploy-ON-LANDING behavior (see updateLandingGear()'s own doc) - just for the opposite transition, and gated on having climbed a modest amount (not the full orbit altitude) so it retracts promptly after leaving the ground rather than waiting for the whole climb to finish.
		if (this.isGearDeployed() && this.getY() > center.getY() + 2.0) {
			this.toggleLandingGear();
		}
		// A non-empty route on the linked Drone Center takes over entirely - looked up fresh each tick (not cached on this vehicle) since the route can be edited live from the detailed settings screen while the drone is already flying. An empty route (the default, and everything before this feature existed) falls through unchanged to the circular-orbit logic below.
		// Per casWaypointOverride's own doc: a CAS strike aircraft's own fixed route takes priority over any block-based lookup entirely (there is no physical Drone Center block at a CAS target position to query in the first place).
		if (this.casWaypointOverride != null) {
			// See casTimeoutTicksRemaining's own doc. Paused (not decremented) while carrierWaitingToLand/carrierOrbitingForFormation is active - both are deliberate, designed queuing behavior (see their own doc), not the "stuck/lost" scenario this safety net exists to catch, and for a large formation the last aircraft's own cumulative wait could otherwise exhaust this timeout before it ever gets a chance to begin landing at all.
			if (!this.carrierWaitingToLand && !this.carrierOrbitingForFormation) {
				this.casTimeoutTicksRemaining--;
				if (this.casTimeoutTicksRemaining <= 0) {
					this.discard();
					return;
				}
			}
			// Per casForcedChunks's own doc: chunk-forcing maintenance itself now runs unconditionally from updateVehicleMovement() (tudursvehiclemod$updateCasForcedChunks()), already called once this same tick before this method was even reached - no longer duplicated here.
			// A CAS/Carrier formation member (carrierFormationRootLeaderUuid != null - now set for the LEAD too, not just wingmen, so every member shares the same formation key) that is NOT currently the formation's own leader-slot occupant follows whoever IS, resolved fresh every tick via the registry - droneWaypointIndex >= carrierLaunchWaypointCount excludes Carrier's own launch waypoints specifically (always true immediately for CAS, which has no launch phase of its own at all). The current slot occupant itself simply falls through this whole block untouched, flying its own independent route exactly like the original pre-formation lead's own behavior.
			if (this.carrierFormationRootLeaderUuid != null
					&& !this.getUuid().equals(FORMATION_LEADER_SLOT.get(this.carrierFormationRootLeaderUuid))
					&& this.droneWaypointIndex >= this.carrierLaunchWaypointCount) {
				if (!(this.getEntityWorld() instanceof ServerWorld formationServerWorld)) {
					this.discard();
					return;
				}
				AircraftEntity leaderAircraft = this.tudursvehiclemod$resolveFormationLeader(formationServerWorld);
				if (leaderAircraft == null) {
					// Every member of this formation's own roster is gone - no viable leader left at all.
					this.discard();
					return;
				}
				// Mirrors the leader's own progress through the SAME route (both copies have identical length/attack-flag content, just no longer used for THIS wingman's own navigation at all) - firing/timeout/route-completion decisions all naturally follow the leader's own actual progress this way.
				this.droneWaypointIndex = Math.min(leaderAircraft.droneWaypointIndex, Math.max(0, this.casWaypointOverride.size() - 1));
				boolean formationAttackActive = this.casAttackFlags != null
						&& this.droneWaypointIndex < this.casAttackFlags.size()
						&& this.casAttackFlags.get(this.droneWaypointIndex);
				this.tudursvehiclemod$updateCasAutoFire(formationAttackActive, this.casWeaponIndex);
				// Once the LEADER itself begins its own landing sequence, this wingman stops following it and independently begins its own (using its own existing carrierFormationIndex-based queue stagger, completely unchanged from before this feature existed).
				if (leaderAircraft.carrierReturning || leaderAircraft.carrierWaitingToLand) {
					if (this.carrierLaunchMothershipUuid == null) {
						// A CAS wingman has no mothership/landing sequence at all - route completion simply means it's done.
						this.discard();
						return;
					}
					if (this.carrierFormationIndex > 0) {
						this.carrierWaitingToLand = true;
						this.carrierLandingQueueTicksRemaining = this.carrierFormationIndex * CARRIER_LANDING_QUEUE_DELAY_TICKS;
					} else {
						this.carrierReturning = true;
					}
					return;
				}
				// updateDroneFormationFollow() still reads its own target leader via droneFormationLeaderUuid internally - kept in sync with whatever the registry just resolved, every tick, rather than refactoring that method's own internals.
				this.droneFormationLeaderUuid = leaderAircraft.getUuid();
				this.tudursvehiclemod$updateDroneFormationFollow(def);
				return;
			}
			if (this.carrierReturning) {
				this.tudursvehiclemod$updateCarrierReturnToBase(def);
				return;
			}
			if (this.carrierWaitingToLand) {
				this.carrierLandingQueueTicksRemaining--;
				if (this.carrierLandingQueueTicksRemaining <= 0) {
					this.carrierWaitingToLand = false;
					this.carrierReturning = true;
					this.tudursvehiclemod$updateCarrierReturnToBase(def);
				} else {
					this.tudursvehiclemod$updateCarrierFormationWaitOrbit(def);
				}
				return;
			}
			if (this.carrierOrbitingForFormation) {
				if (this.carrierFormationPending) {
					this.tudursvehiclemod$updateCarrierFormationWaitOrbit(def);
					return;
				}
				this.carrierOrbitingForFormation = false;
			}
			if (!this.casWaypointOverride.isEmpty()) {
				int waypointIndexBeforeTick = this.droneWaypointIndex;
				this.tudursvehiclemod$updateDroneWaypointAutopilot(def, center, this.casWaypointOverride);
				if (this.droneWaypointIndex != waypointIndexBeforeTick) {
					this.casTicksSinceLastProgress = 0;
				} else {
					this.casTicksSinceLastProgress++;
					if (this.casStuckTimeoutTicks > 0 && this.casTicksSinceLastProgress >= this.casStuckTimeoutTicks) {
						this.discard();
						return;
					}
				}
				// Firing is active exactly while the CURRENT target waypoint (the one this leg is flying toward) is itself marked attack=true - since consecutive attack=true waypoints chain together naturally this way, this already matches "keep firing through the attack run until reaching a waypoint that isn't marked attack" without needing any separate latched/stateful window at all.
				boolean attackActive = this.casAttackFlags != null
						&& this.droneWaypointIndex < this.casAttackFlags.size()
						&& this.casAttackFlags.get(this.droneWaypointIndex);
				this.tudursvehiclemod$updateCasAutoFire(attackActive, this.casWeaponIndex);
			}
			return;
		}
		if (this.getEntityWorld().getBlockEntity(center) instanceof com.example.tudursvehiclemod.block.DroneCenterBlockEntity droneCenterEntity) {
			java.util.List<com.example.tudursvehiclemod.block.DroneWaypoint> waypoints = droneCenterEntity.tudursvehiclemod$getWaypoints();
			if (!waypoints.isEmpty()) {
				this.tudursvehiclemod$updateDroneWaypointAutopilot(def, center, waypoints);
				return;
			}
		}
		float cruiseSpeedTarget = this.tudursvehiclemod$rampAutopilotThrottle(def, this.tudursvehiclemod$getDroneSpeedFraction(), def.maxSpeed());
		// Uses this vehicle's own ACTUAL yaw turn rate (getYawFollowRateDegrees() - what a manned pilot's own input is actually clamped to in updateOrientation(), equal to def.turnSpeed() * YAW_RATE_MULTIPLIER) rather than the raw, un-multiplied def.turnSpeed() - the drone was previously only using 1/3 of what a manned pilot could actually achieve with the same vehicle.
		float effectiveTurnRate = this.getYawFollowRateDegrees();
		// Natural circular-motion radius for this vehicle's own turn rate at this cruise speed, rather than an arbitrary uniform value - matches real circular motion (angularVelocity = speed / radius).
		double turnRateRadPerTick = Math.toRadians(Math.max(effectiveTurnRate, 0.1f));
		double naturalRadius = (cruiseSpeedTarget / turnRateRadPerTick) * this.tudursvehiclemod$getDroneRadiusMultiplier();

		double dx = this.getX() - (center.getX() + 0.5);
		double dz = this.getZ() - (center.getZ() + 0.5);
		double currentRadius = Math.sqrt(dx * dx + dz * dz);
		double currentAngleRad = currentRadius < 1.0e-4 ? 0.0 : Math.atan2(dz, dx);

		// Tangential direction (perpendicular to the radius, in the direction of orbital travel) plus a gentle radial correction - pulls toward naturalRadius over time instead of an artificial lookahead-point hack, which would otherwise let the vehicle's actual velocity diverge from wherever yaw is aimed (see this method's own doc on why that specifically matters here).
		Vec3d tangential = new Vec3d(-Math.sin(currentAngleRad), 0, Math.cos(currentAngleRad));
		double radiusError = naturalRadius - currentRadius;
		Vec3d radialCorrection = new Vec3d(Math.cos(currentAngleRad), 0, Math.sin(currentAngleRad))
				.multiply(radiusError * DRONE_ORBIT_RADIAL_CORRECTION_GAIN);
		Vec3d horizontalTarget = tangential.multiply(cruiseSpeedTarget).add(radialCorrection);
		// The radial correction term above (proportional to how far off the natural orbit radius the vehicle currently is - largest right after takeoff, starting near radius 0) was never capped once combined with the tangential component, and could push the resulting target magnitude far past the intended cruise speed - verified via simulation to reach over 150% of maxSpeed for a less-nimble (lower turnSpeed, so larger naturalRadius) vehicle. Hard-caps the combined vector to this vehicle's own real maxSpeed() - the autopilot should never demand more speed than the vehicle is actually capable of, no matter how large a transient correction need might be.
		double horizontalTargetMagnitude = Math.sqrt(horizontalTarget.x * horizontalTarget.x + horizontalTarget.z * horizontalTarget.z);
		if (horizontalTargetMagnitude > def.maxSpeed()) {
			horizontalTarget = horizontalTarget.multiply(def.maxSpeed() / horizontalTargetMagnitude);
		}

		double altitudeError = targetY - this.getY();
		double targetVerticalSpeed = MathHelper.clamp(altitudeError * DRONE_ORBIT_ALTITUDE_CORRECTION_GAIN,
				-cruiseSpeedTarget * 0.5, cruiseSpeedTarget * 0.5);

		// Computing desiredYaw from the BLENDED velocity (as an earlier version of this method did) double-lags behind the target direction (once from the velocity blend below, again from the yaw rate-limit chasing that already-lagged value) - this converged to a noticeably WIDER radius than naturalRadius intends. Computing desiredYaw directly from the unblended target direction instead, and blending only the SPEED MAGNITUDE separately (direction is now handled directly via newYaw below), converges within a few percent of naturalRadius while keeping the same perfect yaw/velocity-direction consistency.
		float desiredYaw = (float) Math.toDegrees(Math.atan2(-horizontalTarget.x, horizontalTarget.z));
		float currentTrackedYaw = this.tudursvehiclemod$droneYawForNavigation();
		// See tudursvehiclemod$computeCoordinatedTurn()'s own doc.
		float[] coordinatedTurn = this.tudursvehiclemod$computeCoordinatedTurn(currentTrackedYaw, this.droneOrbitTrackedRoll, desiredYaw, effectiveTurnRate, this.getPitchFollowRateDegrees());
		float newYaw = coordinatedTurn[0];
		float newRoll = coordinatedTurn[1];
		this.droneTrackedYaw = newYaw;
		this.droneOrbitTrackedRoll = newRoll;

		double targetSpeedMagnitude = Math.sqrt(horizontalTarget.x * horizontalTarget.x + horizontalTarget.z * horizontalTarget.z);
		Vec3d currentVelocity = this.getVelocity();
		double currentSpeedMagnitude = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
		double blendedSpeedMagnitude = this.tudursvehiclemod$blendCruiseSpeedToward(def, (float) currentSpeedMagnitude, (float) targetSpeedMagnitude, this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION), def.maxSpeed());
		float orbitSpeedResponseBlend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
		double blendedVerticalSpeed = currentVelocity.y + (targetVerticalSpeed - currentVelocity.y) * orbitSpeedResponseBlend;

		double newYawRad = Math.toRadians(newYaw);
		Vec3d finalVelocity = new Vec3d(-Math.sin(newYawRad) * blendedSpeedMagnitude, blendedVerticalSpeed, Math.cos(newYawRad) * blendedSpeedMagnitude);
		double horizontalSpeed = blendedSpeedMagnitude;

		float newPitch = horizontalSpeed < 1.0e-4 ? this.getPitch()
				: (float) -Math.toDegrees(Math.atan2(finalVelocity.y, horizontalSpeed));

		this.setOrientationFromEuler(newYaw, newPitch, newRoll);
		this.orientation.normalize();
		super.setYaw(newYaw);
		super.setPitch(newPitch);
		this.dataTracker.set(SYNCED_YAW, newYaw);
		this.dataTracker.set(SYNCED_PITCH, newPitch);
		this.dataTracker.set(SYNCED_ROLL, newRoll);
		this.cruiseSpeed = (float) finalVelocity.length();
		setThrottleDirect(this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION));

		double preMoveSpeed = finalVelocity.length();
		this.setVelocity(finalVelocity);
		this.move(MovementType.SELF, this.getVelocity());
		this.tudursvehiclemod$checkBlockCrashDamage(preMoveSpeed, newPitch);
		this.tudursvehiclemod$checkEntityCrashDamage(preMoveSpeed);
	}

	/** Current waypoint (index into whatever route is currently configured) this vehicle is flying toward - a plain, server-side-only field (not persisted or networked), same as the circular orbit's own in-memory-only state. Clamped defensively each call in case the route was shortened out from under it (e.g. edited down to fewer stops while already flying). */
	private int droneWaypointIndex;

	/** CAS ("call in an airstrike") weapon type: a CAS-spawned aircraft has no physical Drone Center block at its target position to query waypoints from (see the dispatch check just below), so it instead carries its own fixed, independent route directly - set once by network.WeaponStats's own CAS spawn logic, never edited live the way a real Drone Center's own route can be. Null means "not a CAS strike aircraft" - falls through to the normal Drone Center block lookup unchanged. */
	private java.util.List<com.example.tudursvehiclemod.block.DroneWaypoint> casWaypointOverride;

	/** A CAS/Carrier-launched aircraft's own weapon does NOT auto-reload mid-flight from its own onboard reserve (see AbstractVehicleEntity's own reload-initiation logic, both call sites) - the one-time full magazine+reserve load this class's own tudursvehiclemod$fillAllWeaponAmmoAndFuel()/fillCasWeaponAmmo() apply at spawn is still required (there's no other way for this kind of autonomous aircraft to ever be resupplied at all), but the SAME reserve shouldn't ALSO keep silently refilling the magazine every time it runs dry and the weapon's own reload timer elapses - once the magazine is empty, it stays empty until the aircraft actually returns to and lands on its own Carrier for resupply. */
	public boolean tudursvehiclemod$isCasOrCarrierAutonomous() {
		return this.casWaypointOverride != null;
	}
	/** Parallel to casWaypointOverride (same index meaning) - block.DroneWaypoint itself has no "attack" concept at all (it's the generic format a real Drone Center's own route also uses), so this project's own asset.CasWaypoint's own attack flag is carried here separately instead. See tudursvehiclemod$updateCasAutoFire()'s own doc for how this is actually consumed. */
	private java.util.List<Boolean> casAttackFlags;
	/** Parallel to casWaypointOverride/casAttackFlags (same index, same length) - Optional.empty() for "no change", or a fixed true/false the aircraft's own landing_gear/weapon_bay toggle_parts state gets force-set to the INSTANT droneWaypointIndex advances onto that waypoint (a one-time trigger applied in tudursvehiclemod$updateDroneWaypointAutopilot()'s own "passedWaypoint" handling, not a continuous per-tick state like casAttackFlags's own attack flag). Always present (even for the normal, non-launch portion of a Carrier route, or for CAS) as a same-length list of Optional.empty() entries - simplifies the index-matching logic elsewhere to not need a separate null/empty-list check. */
	private java.util.List<java.util.Optional<Boolean>> carrierGearFlags = java.util.List.of();
	private java.util.List<java.util.Optional<Boolean>> carrierBayFlags = java.util.List.of();
	/** Per CarrierLaunchWaypoint's own speedBoostKmh doc (the current catapult mechanic, replacing an earlier player-boarding-based design): parallel to carrierGearFlags/carrierBayFlags (same index, same length, same always-present-as-a-same-length-list convention) - Optional.empty() for "no boost", or a fixed speed (km/h) this aircraft's own velocity magnitude gets instantly snapped to (current horizontal direction preserved) the INSTANT droneWaypointIndex advances onto that waypoint. UNLIKE carrierGearFlags/carrierBayFlags, this one IS actually consumed for every launch-route waypoint (not just waypoint 0) - see tudursvehiclemod$applyCarrierSpeedBoost()'s own doc for exactly where. */
	private java.util.List<java.util.Optional<Float>> carrierSpeedBoostFlags = java.util.List.of();
	/** How many of this aircraft's own combined route waypoints (config.launchWaypoints() + config.waypoints(), same order tudursvehiclemod$setCasWaypointRoute() receives them in) are the launch segment specifically - droneWaypointIndex < this value means still launching. 0 (the default) for any aircraft that was never Carrier-launched, or hasn't had this set at all. */
	private int carrierLaunchWaypointCount;

	/** Vehicle-config-driven transition (same rate manual throttle-up/down input already ramps at: 0.015f * def.throttleUpDown()), rather than directly blending a target SPEED at some fixed, autopilot-only rate (the previous design) - ramps CAS_WAYPOINT_SPEED_FRACTION (see that field's own doc) toward targetThrottleFraction by that same rate, then returns def.maxSpeed() times the RESULTING (already-ramped) throttle, ready to blend cruiseSpeed toward via def.acceleration() exactly the way approachThrottledVelocity() already does for piloted flight. Reused by all three autopilot speed sources (CAS/Carrier waypoints, Carrier landing approach, generic Drone Center orbit) so they all share the identical throttle-ramping behavior and the identical underlying state field, regardless of which one happens to be driving this aircraft at any given moment. */
	private float tudursvehiclemod$rampAutopilotThrottle(VehicleDefinition def, float targetThrottleFraction, float maxSpeed) {
		float throttleStep = 0.015f * def.throttleUpDown().orElse(1.0f);
		float current = this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION);
		float updated = current + MathHelper.clamp(targetThrottleFraction - current, -throttleStep, throttleStep);
		this.dataTracker.set(CAS_WAYPOINT_SPEED_FRACTION, updated);
		return maxSpeed * updated;
	}

	@Override
	public boolean tudursvehiclemod$isUsingRampedAutopilotThrottle() {
		return this.tudursvehiclemod$isDroneActive() || this.carrierGlideMinSpeed > 0f || this.droneFormationLeaderUuid != null;
	}

	@Override
	public float tudursvehiclemod$getCasWaypointSpeedFraction() {
		return this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION);
	}

	/** See carrierGearFlags/carrierBayFlags's own doc. Also applies waypoint 0's own flags immediately, since the aircraft spawns AT that waypoint and never "advances" onto it through the normal passed-waypoint detection the way every later waypoint does. */
	public void tudursvehiclemod$setCarrierGearBayFlags(java.util.List<java.util.Optional<Boolean>> gearFlags, java.util.List<java.util.Optional<Boolean>> bayFlags) {
		this.carrierGearFlags = gearFlags;
		this.carrierBayFlags = bayFlags;
		if (!gearFlags.isEmpty()) {
			gearFlags.get(0).ifPresent(this::tudursvehiclemod$setGearDeployed);
		}
		if (!bayFlags.isEmpty()) {
			bayFlags.get(0).ifPresent(open -> this.tudursvehiclemod$setWeaponBayForcedOpen(java.util.Optional.of(open)));
		}
	}

	/** See carrierSpeedBoostFlags's own doc. Also applies waypoint 0's own boost (if any) immediately, matching tudursvehiclemod$setCarrierGearBayFlags()'s own waypoint-0 handling - but spawnForwardX/spawnForwardZ (the mothership-facing basis this aircraft's own spawn yaw was itself derived from, passed in by the caller) must be used as the direction here rather than this.getVelocity()/this.getYaw(): this setter runs from inside the entity-create spawn callback in tudursvehiclemod$fireCarrierLaunch(), BEFORE refreshPositionAndAngles() has set this aircraft's own actual yaw, and before it has taken a single physics tick of its own (velocity is still (0,0,0)) - unlike every LATER call to tudursvehiclemod$applyCarrierSpeedBoost() (from the passedWaypoint transition, well into normal ticking), where reading this aircraft's own current velocity direction is both possible and correct. */
	public void tudursvehiclemod$setCarrierSpeedBoosts(java.util.List<java.util.Optional<Float>> speedBoostFlags, double spawnForwardX, double spawnForwardZ) {
		this.carrierSpeedBoostFlags = speedBoostFlags;
		if (!speedBoostFlags.isEmpty()) {
			speedBoostFlags.get(0).ifPresent(kmh -> this.tudursvehiclemod$applyCarrierSpeedBoost(kmh, spawnForwardX, spawnForwardZ));
		}
	}

	/** 1 block ~= 1m, 20 ticks/s, so blocks/tick = (km/h / 3.6) / 20. */
	private static float tudursvehiclemod$kmhToBlocksPerTick(float kmh) {
		return kmh / 3.6f / 20f;
	}

	/** See carrierSpeedBoostFlags's own doc - the actual catapult-launch-speed mechanic. Instantly overwrites this aircraft's own velocity so its HORIZONTAL magnitude equals kmh (converted to blocks/tick), preserving both the current vertical (Y) velocity component and the current horizontal DIRECTION unchanged - only the horizontal speed jumps. fallbackDirX/fallbackDirZ (a normalized horizontal direction) is used instead whenever the current horizontal velocity is too close to zero to normalize on its own (the common case: waypoint 0's own boost, applied at spawn before this aircraft has ever moved at all - see tudursvehiclemod$setCarrierSpeedBoosts()'s own doc). Deliberately does nothing else: no cruiseSpeed/throttle bookkeeping beyond what this.setVelocity() itself implies, since tudursvehiclemod$updateDroneWaypointAutopilot()'s own per-tick blend-toward-target logic already reads this aircraft's own actual velocity fresh every tick (NOT a separately-tracked "boosted until when" state) - the very next autopilot tick picks up this elevated speed as its own new starting point and blends away from it exactly like it would from any other current speed, with no special "boost decay" of this method's own needed at all. Never called more than once per waypoint (by construction: only from the passedWaypoint transition itself, or from setCarrierSpeedBoosts()'s own one-time waypoint-0 spawn handling - never from anywhere that runs every tick), so it can never compound/double-apply the way naively re-snapping every tick while "still at" the boosted waypoint would. */
	private void tudursvehiclemod$applyCarrierSpeedBoost(float kmh, double fallbackDirX, double fallbackDirZ) {
		float boostBlocksPerTick = tudursvehiclemod$kmhToBlocksPerTick(kmh);
		Vec3d currentVelocity = this.getVelocity();
		double horizontalMag = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
		double dirX;
		double dirZ;
		if (horizontalMag > 1.0e-4) {
			dirX = currentVelocity.x / horizontalMag;
			dirZ = currentVelocity.z / horizontalMag;
		} else {
			dirX = fallbackDirX;
			dirZ = fallbackDirZ;
		}
		this.setVelocity(dirX * boostBlocksPerTick, currentVelocity.y, dirZ * boostBlocksPerTick);
		this.cruiseSpeed = (float) this.getVelocity().length();
	}

	/** Which weapon slot (on THIS aircraft's own weapon list) the auto-fire logic actually fires - copied from CasStrikeConfig.weaponIndex() once, at spawn time, alongside casWaypointOverride/casAttackFlags. */
	private int casWeaponIndex;

	/** The UUID of the mothership vehicle this aircraft was launched FROM (null if this isn't a Carrier-launched aircraft at all, or the mothership is otherwise unknown) - set once at launch time by tudursvehiclemod$setCarrierMothership(), read by entity.AbstractVehicleEntity's own tudursvehiclemod$toggleCarrierSeat() to know which vehicle to return a switching-away player TO. */
	private java.util.UUID carrierMothershipUuid;
	/** Which seat index on the mothership (carrierMothershipUuid) the player should be returned to when switching away from piloting this aircraft - the SAME seat they were originally sitting in when they fired the Carrier weapon that launched this aircraft. */
	private int carrierMothershipSeatIndex;
	/** Which weapon slot on carrierMothershipUuid this aircraft was originally launched from - the slot whose magazine gets +1 back on a successful landing, and whose own current AddWeapon mount position (recomputed live each tick, since the mothership moves) is the actual return target. Set once at launch time alongside carrierMothershipUuid/carrierMothershipSeatIndex. */
	private int carrierMothershipWeaponIndex;
	/** True once this Carrier aircraft has completed its own normal route (see casWaypointOverride's own doc) and has begun flying back to land on carrierMothershipUuid - see tudursvehiclemod$updateCarrierReturnToBase()'s own doc for the full landing sequence. False (the default) for an aircraft still on its own outbound patrol/strike route. */
	private boolean carrierReturning;
	/** The mothership's own getYaw() can disagree with its actual VISUAL facing direction for some vehicle models (the exact same quirk that historically required CasYawOffset=-90 for this same test rig) - reuses the existing CarrierYawOffset config value (previously only applied to the route's own rotation basis) to correct for this in the mothership-facing calculations too (launch spawn direction, landing approach direction). Set once at launch time from CarrierAircraftConfig.yawOffsetDegrees(). */
	/** Populated from CarrierYawOffset (CarrierAircraftConfig's own yawOffsetDegrees) - a broad correction that deliberately affects the LANDING approach direction too (alongside its own separate, pre-existing role correcting the waypoint route's own rotation basis), since some vehicle models' own getYaw() disagrees with their actual visual facing. Deliberately NOT applied to launch spawn yaw, which is handled entirely by the mothership's own AddWeapon "DefaultYaw" and needs no correction of its own. */
	private float carrierMothershipYawOffset;
	/** An ADDITIONAL correction (CarrierAircraftConfig's own landingYawOffsetDegrees) applied ONLY to the landing approach direction, stacking on top of carrierMothershipYawOffset (which already broadly affects landing too) - for cases where landing needs independent fine-tuning without also shifting the waypoint route's own rotation. */
	private float carrierLandingYawOffset;
	/** A dedicated, multi-waypoint landing approach mirroring launchWaypoints (see CarrierAircraftConfig's own landingWaypoints doc), and the configured route, copied here once at launch time - REQUIRED to be non-empty (validated at parse time), since the LAST waypoint now serves the old approach point's own exact role. */
	private java.util.List<com.example.tudursvehiclemod.asset.CarrierLaunchWaypoint> carrierLandingWaypoints = java.util.List.of();
	/** This aircraft's own flight control is turned off entirely after its last landing waypoint (rather than continuing to actively steer toward AddWeapon itself), behaving like a genuinely unpiloted aircraft from that point on - while still guaranteeing it always keeps at least this much forward speed (see updateVehicleMovement()'s own post-processing floor-enforcement doc for how). 0 (the default) means no floor is being enforced at all - this is left at 0 for any aircraft that was never a Carrier-launched one in the first place, or hasn't yet reached its own last landing waypoint. */
	private float carrierGlideMinSpeed;
	/** How far through carrierLandingWaypoints this aircraft has currently progressed - see tudursvehiclemod$updateCarrierReturnToBase()'s own doc for exactly how this advances (and each waypoint's own gear/bay trigger fires) as this aircraft reaches each one in turn. Once this reaches the LAST index (carrierLandingWaypoints.size() - 1), that final waypoint takes over as the approach anchor for the actual touchdown. */
	private int carrierLandingWaypointIndex;

	/** The mothership's own UUID, ALWAYS set at spawn time for a Carrier-launched aircraft regardless of whether a player or AI fired the weapon - unlike carrierMothershipUuid (only set when a player actually fired the shot, for the seat-switch feature specifically), this is needed purely to find the mothership's own CURRENT position to orbit around while awaiting the rest of the formation (see carrierOrbitingForFormation's own doc). */
	private java.util.UUID carrierLaunchMothershipUuid;
	/** While non-null, this aircraft is a formation WINGMAN, following the leader vehicle (this UUID) with a fixed rigid offset (droneFormationLateralOffset/droneFormationLongitudinalOffset, same relX/relZ convention CAS/Carrier's own formation offsets already use - lateral=right, longitudinal=forward, relative to the LEADER's own current heading) rather than running its own independent Drone Center orbit/patrol routine at all - see tudursvehiclemod$updateDroneFormationFollow()'s own doc for the actual follow behavior. Maintained (set/cleared) every tick by block.DroneCenterBlockEntity's own tudursvehiclemod$updateFormationWingmen(), not persisted (re-established from that block entity's own formationSlots the moment it next ticks after a reload, same "in-memory-only" convention several other pieces of transient Drone Center state already use). */
	private java.util.UUID droneFormationLeaderUuid;
	private double droneFormationLateralOffset;
	private double droneFormationLongitudinalOffset;
	/** This formation's own stable identifier, set once at spawn time for EVERY member INCLUDING the lead itself (via tudursvehiclemod$registerFormationMember() - unlike the original design, where this stayed null for the lead) - always the formation's own ORIGINAL lead aircraft's own UUID. Used as the shared key into FORMATION_ROSTER/FORMATION_LEADER_SLOT below by every member, lead and wingmen alike, to consult the exact same, single source of truth for "who is this formation's own current leader" - droneFormationLeaderUuid alone can't serve that purpose reliably on its own (see that field's own doc). Null for a Drone Center wingman (that feature has no succession concept at all - a Drone Center's own bound vehicle is manually re-bound by a player, not automatically succeeded, and continues using droneFormationLeaderUuid directly, entirely independent of this whole registry). Formations collapsed into a column after a world/server reload with CAS aircraft already in flight: this field (and carrierFormationIndex below) is now persisted (tudursvehiclemod$writeCustomData()/tudursvehiclemod$readCustomData()) - the root cause was that FORMATION_ROSTER/FORMATION_LEADER_SLOT are plain static (per-JVM) maps with no persistence of their own at all, so even restoring this field alone wouldn't have been enough; readCustomData() also actively re-registers this aircraft into both registries as it loads, so every surviving member of a formation rebuilds the exact same roster/leader-slot state it had before the reload. */
	private java.util.UUID carrierFormationRootLeaderUuid;

	/** A real, documented problem with that previous design was that a wide formation's own members could each see a DIFFERENT subset of surviving siblings from their own position (a fixed search radius smaller than the formation's own actual spread), so different wingmen could independently compute DIFFERENT "winners" and each self-promote to a conflicting, disagreeing leader - a "split-brain" collapse. A second, independent bug compounded this for CAS specifically: carrierFormationIndex (the tiebreak the old search used) was never actually set for CAS formations at all, leaving every CAS wingman tied at the same default value, so even a correctly-sized search couldn't have picked a consistent winner either.
	 *
	 * <p>The fix: a static, per-server registry (not radius-based, no distance limit or ambiguity of any kind) of every formation's own FULL membership roster, keyed by carrierFormationRootLeaderUuid, in launch/spawn order (index 0 = the formation's own original lead) - populated incrementally as each member spawns (tudursvehiclemod$registerFormationMember()). FORMATION_LEADER_SLOT below is walked against THIS roster (in order, skipping any no-longer-alive entry) whenever the slot's own current occupant is found gone - since every member of a formation consults this exact same roster (not a position-dependent search), they always compute the exact same successor, with no possibility of disagreement regardless of how widely the formation happens to be spread out.
	 *
	 * <p>Entries are never actively removed (a formation that's fully wiped out just leaks one small list plus one map entry - negligible, and avoids the complexity of correctly detecting "this formation is now permanently over" from an arbitrary member's own local perspective). */
	private static final java.util.Map<java.util.UUID, java.util.List<java.util.UUID>> FORMATION_ROSTER = new java.util.HashMap<>();
	/** Per FORMATION_ROSTER's own doc: the SINGLE aircraft UUID currently occupying carrierFormationRootLeaderUuid's own formation "leader slot" - by construction, exactly one entry per active formation, exactly one occupant at a time. Claimed the instant the lead itself spawns (tudursvehiclemod$registerFormationMember()), refilled ONLY when that occupant is confirmed no longer alive (tudursvehiclemod$resolveFormationLeader(), walking FORMATION_ROSTER) - succession happens purely reactively, on an actual, confirmed leader loss, since a wingman whose OWN leader is still alive and well has no business searching or reconsidering anything at all. */
	private static final java.util.Map<java.util.UUID, java.util.UUID> FORMATION_LEADER_SLOT = new java.util.HashMap<>();

	/** The entity UUID THIS wingman is currently locked onto and actively pursuing/attacking (assigned by its own formation lead, via tudursvehiclemod$assignCarrierLockTarget()), or null if not currently locked onto anything at all - ordinary formation-follow (tudursvehiclemod$updateDroneFormationFollow()) applies instead whenever this is null. Cleared (returning to formation-follow) once the target is destroyed/removed, "shaken off" (see carrierLockedTargetBestDistance's own doc), or the lead releases every lock at once (tudursvehiclemod$releaseAllCarrierLocks()). */
	private java.util.UUID carrierLockedTargetUuid;
	/** The weapon index this wingman attacks its own locked target with is whichever slot the LEAD had selected at the moment of locking (tudursvehiclemod$getSelectedWeaponIndex()), not this wingman's own currently-selected weapon (a wingman is never player-piloted while locked, so it has no "currently selected" weapon of its own in the first place) - captured once here at assignment time and used for every subsequent attack attempt against this same target. */
	private int carrierLockedTargetWeaponIndex;
	/** "Shaken off" (target's own movement outpacing this wingman's own ability to keep up) releases a lock exactly like a guided missile's own identical "lost target" mechanic (VehicleProjectileEntity's own guidanceBestDistance/guidanceStagnantTicks - see that field's own doc for the full reasoning) - the shortest distance to the current target achieved so far since it was assigned/reassigned, reset to Double.MAX_VALUE whenever a NEW target is assigned. */
	private double carrierLockedTargetBestDistance = Double.MAX_VALUE;
	/** Overrides tudursvehiclemod$updateCarrierLockPursuit()'s own attack-altitude thresholds (normally read from the locked weapon's own CasAttackStartAltitude/CasAttackStopAltitude file settings) with these two instead, whenever non-null - set by entity.DummyPilotEntity's own combat AI at the same time it establishes/refreshes a lock, from its own owning Drone Center's own configured values (a Drone Center setting rather than the weapon file's own, for this specific drone-controlled case), and left null for every other caller of tudursvehiclemod$updateCarrierLockPursuit() (an actual player-initiated Carrier lock), which continues reading the weapon file's own values entirely unchanged. Both null or both non-null together always - there is no scenario where only one of the pair is overridden. */
	private Double carrierLockAttackStartAltitudeOverride;
	private Double carrierLockAttackStopAltitudeOverride;
	/** Overrides tudursvehiclemod$updateCarrierLockPursuit()'s own dive-target Y offset (added to the target's own actual Y to compute where this wingman actually dives toward - see CARRIER_LOCK_FALLING_WEAPON_DIVE_TARGET_Y_OFFSET's own doc for why a falling weapon needs this at all) with this instead, whenever non-null - set by entity.DummyPilotEntity's own combat AI the same way carrierLockAttackStartAltitudeOverride/carrierLockAttackStopAltitudeOverride already are, from its own owning Drone Center's own configured value. Left null for an actual player-initiated Carrier lock, which has no such Drone Center setting to read from - falls back to CARRIER_LOCK_FALLING_WEAPON_DIVE_TARGET_Y_OFFSET for a falling weapon (BOMB/DEPTH) attacking a ground/water target, 0 (no offset at all) otherwise, exactly matching this same fallback tudursvehiclemod$updateCarrierLockPursuit() itself applies whenever a Drone-Center-sourced override happens to still be at its own unconfigured 0.0 (see that override's own doc, entity.block.DroneCenterBlockEntity's own dummyPilotDiveTargetYOffset field, for the full reasoning behind this shared "0 means still-default" convention). */
	private Double carrierLockDiveTargetYOffsetOverride;
	/** Per carrierLockedTargetBestDistance's own doc - consecutive ticks since that distance last improved. Once this reaches CARRIER_LOCK_SHAKEN_OFF_TICKS (30 seconds), the target is considered shaken off and this wingman's own lock is released. */
	private int carrierLockedTargetStagnantTicks;
	/** 30 seconds - "shaken off" uses exactly the same threshold duration as a guided missile's own identical "lost target" mechanic. */
	private static final int CARRIER_LOCK_SHAKEN_OFF_TICKS = 600;
	/** A ground/water target's own attack altitude needs real hysteresis (two separate thresholds, not one) rather than a single "maintain this altitude" target alone - a simple offset-from-target altitude target doesn't guarantee the actual flight PATH to reach it stays safe (e.g. this wingman could find itself below a safe height for other reasons - after avoiding an obstacle, right after being assigned a new target, etc.), and the intended attack course itself could still drift low. True while this wingman has stopped attacking a ground/water target and is climbing back to its own assigned weapon's own configured CasAttackStartAltitude before resuming - entered once height above the target drops below that same weapon's own CasAttackStopAltitude, cleared only once back at or above CasAttackStartAltitude (not the same, lower stop threshold - the whole point of a hysteresis band, so it doesn't immediately re-trigger right at the boundary). Reset to false whenever a fresh target is assigned (tudursvehiclemod$assignCarrierLockTarget()) - a newly-assigned target starts by simply checking its own actual height fresh, rather than inheriting whatever state applied to a completely different, previous target. */
	private boolean carrierLockClimbingToSafeAltitude;
	/** Diving toward a ground/water target immediately upon reaching CasAttackStartAltitude meant turning to actually face the target and descending toward it happened simultaneously - with a large enough turn radius relative to the target's own distance, the turn (getting the target properly ahead) often never finished before this wingman had already descended all the way to CasAttackStopAltitude, so it aborted the dive without ever having been aligned to fire at all. True while this wingman needs to first reach a dedicated approach waypoint (offset CARRIER_LOCK_APPROACH_OFFSET blocks in +X and +Z from the target, at CasAttackStartAltitude) BEFORE the actual dive begins - during this phase this wingman is free to turn (toward the waypoint, not the target) and climb/descend toward the waypoint's own altitude, but does not yet dive at or fire on the target. Set true whenever a fresh target is assigned, and again every time carrierLockClimbingToSafeAltitude clears (a dive that got aborted and climbed back up must re-approach before diving again) - cleared once this wingman actually arrives at the waypoint (within CARRIER_LOCK_WAYPOINT_ARRIVAL_RADIUS), at which point the dive itself begins. Only meaningful for a ground/water target - always false for an airborne one, which has no waypoint/dive-cycle mechanism at all. */
	private boolean carrierLockApproachingWaypoint;
	/** The fallback reassignment (tudursvehiclemod$tryLockCarrierTarget()'s own doc) always picked the SAME lowest-formation-index wingman every time, leaving every other wingman permanently stuck on whatever it was first ever assigned (no later lock command could ever reach it again, since the lowest-index one always absorbed the new instruction instead): a simple increasing counter, stamped at assignment time from CARRIER_LOCK_ASSIGNMENT_COUNTER below, letting the fallback logic instead pick whichever wingman's own CURRENT assignment is OLDEST - a round-robin effect where successive lock commands cycle through every wingman over time rather than repeatedly re-targeting one single aircraft. */
	private long carrierLockAssignmentSequence;
	/** Backs carrierLockAssignmentSequence's own doc - a single incrementing counter shared across every wingman on the server, so "oldest assignment" is a simple numeric comparison. */
	private static long CARRIER_LOCK_ASSIGNMENT_COUNTER;

	/** Called once at spawn time for EVERY CAS/Carrier formation member, lead included (isLead=true for exactly one aircraft per formation, the original lead) - see FORMATION_ROSTER's own doc for the full registry design. Registering the lead itself (previously the old design never gave the lead a carrierFormationRootLeaderUuid at all) is what lets every member, lead included, share one common key into both registries. */
	public void tudursvehiclemod$registerFormationMember(java.util.UUID formationId, boolean isLead) {
		this.carrierFormationRootLeaderUuid = formationId;
		FORMATION_ROSTER.computeIfAbsent(formationId, id -> new java.util.ArrayList<>()).add(this.getUuid());
		if (isLead) {
			FORMATION_LEADER_SLOT.put(formationId, this.getUuid());
		}
	}

	/** Called every tick by the Drone Center that owns this wingman's own formation slot - see droneFormationLeaderUuid's own doc. Entirely independent of the CAS/Carrier registry above (a Drone Center wingman never has a carrierFormationRootLeaderUuid at all). */
	public void tudursvehiclemod$setDroneFormationFollow(java.util.UUID leaderUuid, double lateralOffset, double longitudinalOffset) {
		this.droneFormationLeaderUuid = leaderUuid;
		this.droneFormationLateralOffset = lateralOffset;
		this.droneFormationLongitudinalOffset = longitudinalOffset;
	}

	/** Called by the Drone Center once this aircraft's own formation slot is emptied (stick removed, formation size shrunk past it, or the block itself no longer finds this aircraft) - lets it fall through to whatever its own normal state would otherwise be (typically just sitting idle, matching an unlinked aircraft's own existing default behavior) rather than continuing to chase a stale offset forever. */
	public void tudursvehiclemod$clearDroneFormationFollow() {
		this.droneFormationLeaderUuid = null;
	}

	/** Per FORMATION_ROSTER's own doc: resolves this aircraft's own formation's CURRENT leader - the common case is a simple O(1) lookup of the existing slot occupant (still alive, unchanged); the roster is walked (in order, deterministically) ONLY when that occupant is actually confirmed gone, at which point the first still-alive member found claims the slot for every future caller as well. Returns null if this aircraft isn't in a CAS/Carrier formation at all (carrierFormationRootLeaderUuid == null), or if literally every member of the roster has been destroyed/removed (the whole formation is wiped out) - the caller should discard itself in that case, matching the previous design's own "no viable successor" behavior exactly. */
	private AircraftEntity tudursvehiclemod$resolveFormationLeader(ServerWorld serverWorld) {
		if (this.carrierFormationRootLeaderUuid == null) {
			return null;
		}
		java.util.UUID currentOccupantUuid = FORMATION_LEADER_SLOT.get(this.carrierFormationRootLeaderUuid);
		AircraftEntity currentOccupant = currentOccupantUuid == null ? null
				: tudursvehiclemod$resolveFormationMemberIfAlive(serverWorld, currentOccupantUuid);
		if (currentOccupant != null) {
			return currentOccupant;
		}
		java.util.List<java.util.UUID> roster = FORMATION_ROSTER.get(this.carrierFormationRootLeaderUuid);
		if (roster == null) {
			return null;
		}
		for (java.util.UUID candidateUuid : roster) {
			AircraftEntity candidate = tudursvehiclemod$resolveFormationMemberIfAlive(serverWorld, candidateUuid);
			if (candidate != null) {
				FORMATION_LEADER_SLOT.put(this.carrierFormationRootLeaderUuid, candidateUuid);
				return candidate;
			}
		}
		// Every roster entry is gone - the whole formation has been wiped out.
		return null;
	}

	/** Shared lookup helper for tudursvehiclemod$resolveFormationLeader() - resolves uuid to a still-alive, still-active AircraftEntity, or null if it's gone (removed/discarded) or has since been destroyed (isDestroyed() - a wreck still technically isAlive() but not a viable formation leader). */
	private static AircraftEntity tudursvehiclemod$resolveFormationMemberIfAlive(ServerWorld serverWorld, java.util.UUID uuid) {
		if (serverWorld.getEntity(uuid) instanceof AircraftEntity candidate
				&& !candidate.isRemoved() && !candidate.tudursvehiclemod$isDestroyed()) {
			return candidate;
		}
		return null;
	}

	/** This vehicle (a Carrier formation's own lead aircraft, player-piloted, checked by the caller) attempts to lock whatever is currently under shooter's own crosshair, assigning it to a wingman to attack.
	 *
	 * <p>Candidate search: reuses AbstractVehicleEntity's own tudursvehiclemod$findLockOnTarget()-style crosshair-cone search (nearest to the shooter's own view direction, within CARRIER_LOCK_SEARCH_RANGE) but without any AIRBORNE/SURFACE restriction (this is a general target lock, not a guided-missile-specific one) - excludes this formation's own members (locking a friendly wingman would make no sense) in addition to the usual "not the shooter's own vehicle" exclusion.
	 *
	 * <p>Wingman assignment, walks FORMATION_ROSTER in order (lowest carrierFormationIndex first, per the roster's own launch-order construction), skipping this aircraft itself (the lead never attacks its own lock) and any no-longer-alive member. The FIRST wingman found that ISN'T currently tracking any target (carrierLockedTargetUuid == null) gets the new assignment; if every single wingman is already tracking something, per a further direct report/fix, the one whose own current assignment is OLDEST (lowest carrierLockAssignmentSequence) instead has its own existing target overwritten by the new one - a round-robin effect across successive lock commands, rather than always re-targeting the same lowest-formation-index wingman (which previously left every other wingman permanently stuck on its own first-ever target forever). Does nothing at all if no candidate is found under the crosshair, or if this formation has no wingmen at all. */
	@Override
	public void tudursvehiclemod$tryLockCarrierTarget(net.minecraft.server.network.ServerPlayerEntity shooter) {
		if (this.carrierFormationRootLeaderUuid == null
				|| !this.getUuid().equals(FORMATION_LEADER_SLOT.get(this.carrierFormationRootLeaderUuid))) {
			// Not currently this formation's own lead (e.g. already succeeded by another aircraft) - lock mode only makes sense while actually piloting the lead.
			return;
		}
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		Entity candidate = tudursvehiclemod$findCarrierLockCandidate(shooter);
		if (candidate == null) {
			return;
		}
		java.util.List<java.util.UUID> roster = FORMATION_ROSTER.get(this.carrierFormationRootLeaderUuid);
		if (roster == null) {
			return;
		}
		int selectedWeaponIndex = this.tudursvehiclemod$getSelectedWeaponIndex();
		AircraftEntity fallback = null;
		for (java.util.UUID memberUuid : roster) {
			if (memberUuid.equals(this.getUuid())) {
				continue; // the lead itself never attacks its own lock.
			}
			AircraftEntity wingman = tudursvehiclemod$resolveFormationMemberIfAlive(serverWorld, memberUuid);
			if (wingman == null) {
				continue;
			}
			// Picks whichever wingman's own current assignment is OLDEST (lowest carrierLockAssignmentSequence) instead - a round-robin effect over successive lock commands, rather than repeatedly re-targeting the same single aircraft.
			if (fallback == null || wingman.carrierLockAssignmentSequence < fallback.carrierLockAssignmentSequence) {
				fallback = wingman;
			}
			if (wingman.carrierLockedTargetUuid == null) {
				wingman.tudursvehiclemod$assignCarrierLockTarget(candidate.getUuid(), selectedWeaponIndex);
				return;
			}
		}
		if (fallback != null) {
			fallback.tudursvehiclemod$assignCarrierLockTarget(candidate.getUuid(), selectedWeaponIndex);
		} else {
		}
	}

	/** entity.DummyPilotEntity's own combat AI calls this once per its own target-reacquire cycle (every 20 ticks) to establish/refresh this aircraft's own lock onto a hostile mob and drive tudursvehiclemod$updateCarrierLockPursuit() the exact same way an actual player-initiated Carrier lock already does - the difference is entirely in HOW the lock got here (this method, rather than tudursvehiclemod$tryLockCarrierTarget()'s own crosshair-based assignment) and in attackStartAltitude/attackStopAltitude being Drone-Center-configured values instead of the weapon file's own (see carrierLockAttackStartAltitudeOverride's own doc for why). DummyPilotEntity's own 20-tick reacquire cadence would otherwise repeatedly reset this aircraft's own in-progress approach/dive/climb cycle before it could ever actually complete one: tudursvehiclemod$assignCarrierLockTarget() (which unconditionally resets ALL of that tracking state) is only actually called when targetUuid is genuinely a NEW target - reacquiring the SAME target on a later cycle instead leaves this aircraft's own current cycle state completely undisturbed, only refreshing the weapon index/altitude overrides (which a Drone Center's own config could in principle change while already locked). */
	void tudursvehiclemod$updateDroneCombatLock(java.util.UUID targetUuid, int weaponIndex, double attackStartAltitude, double attackStopAltitude, double diveTargetYOffset) {
		if (this.carrierLockedTargetUuid == null || !this.carrierLockedTargetUuid.equals(targetUuid)) {
			this.tudursvehiclemod$assignCarrierLockTarget(targetUuid, weaponIndex);
		} else {
			this.carrierLockedTargetWeaponIndex = weaponIndex;
		}
		this.carrierLockAttackStartAltitudeOverride = attackStartAltitude;
		this.carrierLockAttackStopAltitudeOverride = attackStopAltitude;
		this.carrierLockDiveTargetYOffsetOverride = diveTargetYOffset;
	}

	/** Sets/replaces THIS wingman's own current lock - see carrierLockedTargetUuid's own doc. Resets carrierLockedTargetBestDistance/carrierLockedTargetStagnantTicks/carrierLockClimbingToSafeAltitude/carrierLockApproachingWaypoint unconditionally (even if targetUuid happens to equal the previous one already locked - a fresh assignment starts fresh regardless), matching VehicleProjectileEntity's own tudursvehiclemod$setGuidanceTargetEntity()'s own identical reset-on-(re)assignment behavior. carrierLockApproachingWaypoint starts true - a freshly-locked target is always approached via the waypoint first, never dived on immediately. Also stamps carrierLockAssignmentSequence (see that field's own doc) so the NEXT fallback reassignment, if every wingman is busy again by then, correctly treats this as the freshest assignment (least likely to be picked again immediately). */
	private void tudursvehiclemod$assignCarrierLockTarget(java.util.UUID targetUuid, int weaponIndex) {
		this.carrierLockedTargetUuid = targetUuid;
		this.carrierLockedTargetWeaponIndex = weaponIndex;
		this.carrierLockedTargetBestDistance = Double.MAX_VALUE;
		this.carrierLockedTargetStagnantTicks = 0;
		this.carrierLockClimbingToSafeAltitude = false;
		this.carrierLockApproachingWaypoint = true;
		this.carrierLockAssignmentSequence = ++CARRIER_LOCK_ASSIGNMENT_COUNTER;
	}

	/** Releases EVERY wingman currently in this formation from whatever target they're each individually tracking, returning them all to ordinary formation-follow immediately - walks FORMATION_ROSTER exactly like tudursvehiclemod$tryLockCarrierTarget() does, clearing carrierLockedTargetUuid on every member found (the lead itself never has one to begin with, so touching it is harmless either way). */
	@Override
	public void tudursvehiclemod$releaseAllCarrierLocks() {
		if (this.carrierFormationRootLeaderUuid == null) {
			return;
		}
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		java.util.List<java.util.UUID> roster = FORMATION_ROSTER.get(this.carrierFormationRootLeaderUuid);
		if (roster == null) {
			return;
		}
		for (java.util.UUID memberUuid : roster) {
			AircraftEntity member = tudursvehiclemod$resolveFormationMemberIfAlive(serverWorld, memberUuid);
			if (member != null) {
				member.carrierLockedTargetUuid = null;
			}
		}
	}

	/** How far (blocks) tudursvehiclemod$findCarrierLockCandidate() searches - generous, matching AbstractVehicleEntity's own MISSILE_LOCK_UNLIMITED_RANGE_CEILING in spirit (a Carrier lead's own crosshair should be able to designate a target about as far away as it can actually see one at all). */
	private static final double CARRIER_LOCK_SEARCH_RANGE = 1000.0;
	/** Matches AbstractVehicleEntity's own MISSILE_LOCK_ON_CONE_DEGREES exactly - see that field's own doc for why this specific value. */
	private static final double CARRIER_LOCK_ON_CONE_DEGREES = 15.0;

	/** Per tudursvehiclemod$tryLockCarrierTarget()'s own doc: crosshair-cone search (nearest to shooter's own view direction, within CARRIER_LOCK_SEARCH_RANGE/CARRIER_LOCK_ON_CONE_DEGREES) for a general lock target - unlike AbstractVehicleEntity's own tudursvehiclemod$findLockOnTarget() (AAMissile/ATMissile's own, AIRBORNE/SURFACE-restricted), this has no such restriction (any living entity or non-destroyed vehicle is a valid candidate), but additionally excludes every member of THIS formation itself (locking a friendly wingman, or the lead aircraft's own self, would make no sense). */
	private Entity tudursvehiclemod$findCarrierLockCandidate(net.minecraft.server.network.ServerPlayerEntity shooter) {
		java.util.List<java.util.UUID> roster = FORMATION_ROSTER.get(this.carrierFormationRootLeaderUuid);
		Vec3d eyePos = shooter.getEyePos();
		Vec3d viewDir = shooter.getRotationVec(1.0f);
		double minDot = Math.cos(Math.toRadians(CARRIER_LOCK_ON_CONE_DEGREES));
		net.minecraft.util.math.Box searchBox = shooter.getBoundingBox().expand(CARRIER_LOCK_SEARCH_RANGE);
		Entity best = null;
		double bestDot = minDot;
		for (Entity candidate : this.getEntityWorld().getOtherEntities(shooter, searchBox,
				e -> (e instanceof net.minecraft.entity.LivingEntity living && living.isAlive()
						|| e instanceof AbstractVehicleEntity vehicleCandidate && !vehicleCandidate.tudursvehiclemod$isDestroyed())
						&& !(e instanceof com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity)
						&& (roster == null || !roster.contains(e.getUuid()))
						&& e != shooter.getVehicle())) {
			Vec3d toCandidate = candidate.getEntityPos().subtract(eyePos);
			double distance = toCandidate.length();
			if (distance < 1.0 || distance > CARRIER_LOCK_SEARCH_RANGE) {
				continue;
			}
			double dot = toCandidate.normalize().dotProduct(viewDir);
			if (dot > bestDot) {
				bestDot = dot;
				best = candidate;
			}
		}
		return best;
	}

	/** Entity ID this aircraft set glowing last tick as a Carrier lock candidate preview - see tudursvehiclemod$updateCarrierLockCandidatePreview()'s own doc. Server-only field (nothing here needs to be networked - the glow itself, once set, is Entity.setGlowing()'s own already-synced flag). */
	private Integer carrierLockCandidatePreviousId;

	/** The current crosshair candidate gets a visible highlight while in Carrier lock mode - see AbstractVehicleEntity's own tudursvehiclemod$setEntityHighlighted() doc for the dispatch this uses (mesh-accurate for this mod's own vehicles, setGlowing() for anything else). Highlights whatever tudursvehiclemod$findCarrierLockCandidate() would currently find (the exact same one tudursvehiclemod$tryLockCarrierTarget() would actually lock onto if the fire key were pressed right now), un-highlighting the previous candidate the instant a different one takes over, or immediately once lock mode itself is turned off. No-op (and immediately un-highlights whatever was previously highlighted, if anything) whenever this aircraft isn't in lock mode at all, OR shooter is null (no controlling player - e.g. just dismounted - must clear any leftover highlight exactly the same way turning lock mode off does, since there's no crosshair to preview a candidate for at all) - checked first, before this formation's own roster/lead status even matters, so leaving lock mode (or the seat itself) always cleans up correctly regardless of anything else. */
	private void tudursvehiclemod$updateCarrierLockCandidatePreview(net.minecraft.server.network.ServerPlayerEntity shooter) {
		Entity candidate = shooter != null && this.tudursvehiclemod$isCarrierLockModeActive()
				? this.tudursvehiclemod$findCarrierLockCandidate(shooter)
				: null;
		Integer currentId = candidate != null ? candidate.getId() : null;
		if (java.util.Objects.equals(currentId, this.carrierLockCandidatePreviousId)) {
			return;
		}
		if (this.carrierLockCandidatePreviousId != null && this.getEntityWorld() instanceof ServerWorld serverWorld) {
			Entity previous = serverWorld.getEntityById(this.carrierLockCandidatePreviousId);
			if (previous != null) {
				this.tudursvehiclemod$setEntityHighlighted(previous, false);
			}
		}
		if (candidate != null) {
			this.tudursvehiclemod$setEntityHighlighted(candidate, true);
		}
		this.carrierLockCandidatePreviousId = currentId;
	}

	/** True from spawn until the mothership's own AbstractVehicleEntity's own tudursvehiclemod$updateCarrierLaunchSequences() signals (tudursvehiclemod$clearCarrierFormationPending()) that the WHOLE formation has finished launching - false for the single-aircraft (no formation) case and for whichever aircraft happens to be the LAST one spawned (nothing left to wait for). While true AND this aircraft has already finished its own launch waypoints, carrierOrbitingForFormation (below) takes over; while still mid-launch-waypoints, this flag has no effect yet (finishing them naturally checks it). */
	private boolean carrierFormationPending;
	/** True while this aircraft has finished its own launch waypoints but carrierFormationPending is still true - diverts tudursvehiclemod$updateDroneAutopilot()'s own dispatch to orbit the mothership (tudursvehiclemod$updateCarrierFormationWaitOrbit()) instead of flying the main patrol/strike route. Cleared the instant carrierFormationPending clears (checked every tick while this is true), letting the main route resume that same tick. */
	private boolean carrierOrbitingForFormation;
	/** This aircraft's own launch-order index within its formation (0=lead, set once at spawn time). Used by tudursvehiclemod$updateDroneWaypointAutopilot()'s own route-completion check to delay each aircraft's own landing sequence progressively further behind the lead's own (a TEMPORAL stagger, not a spatial one - an earlier spatial-offset approach caused a recovery delay/failure regression, since an aircraft released into its final glide while still aimed at an offset point could take far too long, or effectively never, close the gap back to the true recovery zone at the final glide's own low floor speed), and by AbstractVehicleEntity's own leader-succession ordering (carrierLinkedAircraftBySeat) - the same ordering serves both purposes. Always 0 for the single-aircraft (no formation) case. */
	private int carrierFormationIndex;
	/** True from the moment this aircraft reaches the end of its own main route until carrierLandingQueueTicksRemaining (below) counts down to 0 - while true, orbits the mothership (reusing tudursvehiclemod$updateCarrierFormationWaitOrbit(), the exact same method the launch-side formation-wait queue already uses) instead of beginning its own landing approach, so aircraft further back in launch order queue up and land sequentially rather than all converging on AddWeapon at once. Always immediately false (no wait at all) for the lead (carrierFormationIndex 0) and the single-aircraft (no formation) case. */
	private boolean carrierWaitingToLand;
	/** Ticks remaining before carrierWaitingToLand clears and carrierReturning actually begins - see that field's own doc. */
	private int carrierLandingQueueTicksRemaining;

	/** Called once at spawn time by the mothership's own staggered-launch logic - see carrierFormationIndex's own doc. */
	public void tudursvehiclemod$setCarrierFormationIndex(int formationIndex) {
		this.carrierFormationIndex = formationIndex;
	}

	/** Called once at spawn time by the mothership's own staggered-launch logic - see carrierLaunchMothershipUuid's own doc. */
	public void tudursvehiclemod$setCarrierLaunchMothership(java.util.UUID mothershipUuid) {
		this.carrierLaunchMothershipUuid = mothershipUuid;
	}

	/** Called once at spawn time by the mothership's own staggered-launch logic - see carrierFormationPending's own doc. */
	public void tudursvehiclemod$setCarrierFormationPending(boolean pending) {
		this.carrierFormationPending = pending;
	}

	/** Called by the mothership the instant the WHOLE formation has finished launching (see AbstractVehicleEntity's own tudursvehiclemod$updateCarrierLaunchSequences() doc) - lets this aircraft resume its own main route the very next tick, regardless of whether it's currently mid-launch-waypoints (checked once it reaches the end of them) or already orbiting (checked every tick while orbiting). */
	public void tudursvehiclemod$clearCarrierFormationPending() {
		this.carrierFormationPending = false;
	}

	/** See carrierMothershipUuid/carrierMothershipSeatIndex/carrierMothershipWeaponIndex/carrierMothershipYawOffset/carrierLandingYawOffset/carrierLandingWaypoints's own doc - called once by the Carrier launch logic right after this entity is created, before it's actually added to the world. */
	public void tudursvehiclemod$setCarrierMothership(java.util.UUID mothershipUuid, int mothershipSeatIndex, int mothershipWeaponIndex, float mothershipYawOffset, float landingYawOffset, java.util.List<com.example.tudursvehiclemod.asset.CarrierLaunchWaypoint> landingWaypoints) {
		this.carrierMothershipUuid = mothershipUuid;
		this.carrierMothershipSeatIndex = mothershipSeatIndex;
		this.carrierMothershipWeaponIndex = mothershipWeaponIndex;
		this.carrierMothershipYawOffset = mothershipYawOffset;
		this.carrierLandingYawOffset = landingYawOffset;
		this.carrierLandingWaypoints = landingWaypoints;
	}

	public java.util.UUID tudursvehiclemod$getCarrierMothershipUuid() {
		return this.carrierMothershipUuid;
	}

	public int tudursvehiclemod$getCarrierMothershipSeatIndex() {
		return this.carrierMothershipSeatIndex;
	}

	/** Whether a player is CURRENTLY piloting this aircraft directly (true) or it's flying its own autonomous CAS-style route (false, the default - see casWaypointOverride's own doc). While true, the normal CAS waypoint autopilot/auto-fire dispatch is skipped entirely in favor of ordinary player-piloted vehicle behavior. Backed by CARRIER_PLAYER_CONTROLLED (DataTracker-synced - see that field's own doc for why a plain, non-synced field wasn't reliable enough here). */
	public boolean tudursvehiclemod$isCarrierPlayerControlled() {
		return this.dataTracker.get(CARRIER_PLAYER_CONTROLLED);
	}

	/** Switches this Carrier-launched aircraft between player-controlled (true) and autonomous CAS-style flight (false) - see tudursvehiclemod$isCarrierPlayerControlled()'s own doc. when RETURNING to autonomous flight (switching to false), the aircraft's own next target is set to whichever configured waypoint is CURRENTLY NEAREST to it (rather than blindly continuing from wherever droneWaypointIndex happened to be left, which could be far away or already passed while under player control), so it resumes a sensible course immediately rather than potentially flying back toward a stale, distant target first. */
	public void tudursvehiclemod$setCarrierPlayerControlled(boolean playerControlled) {
		this.dataTracker.set(CARRIER_PLAYER_CONTROLLED, playerControlled);
		if (playerControlled) {
			// Aligns yaw/pitch to the velocity's own actual direction before piloted physics takes over - approachThrottledVelocity() derives its own target heading from getYaw()/getPitch(), not from the velocity vector directly, so keeping these in agreement avoids the very first piloted-physics tick computing a target speed pointing somewhere other than the aircraft's own actual momentum.
			Vec3d currentVelocity = this.getVelocity();
			double actualSpeed = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
			if (currentVelocity.lengthSquared() > 1.0e-4) {
				float alignedYaw = (float) Math.toDegrees(Math.atan2(-currentVelocity.x, currentVelocity.z));
				double horizontalSpeed = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
				float alignedPitch = horizontalSpeed > 1.0e-4
						? (float) -Math.toDegrees(Math.atan2(currentVelocity.y, horizontalSpeed))
						: 0f;
				this.setYaw(alignedYaw);
				this.setPitch(alignedPitch);
				this.setOrientationFromEuler(alignedYaw, alignedPitch, this.getRoll());
				this.orientation.normalize();
			}
			this.setVelocity(currentVelocity);
			this.cruiseSpeed = (float) actualSpeed;
			setThrottleDirect(this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION));
			return;
		}
		net.minecraft.util.math.BlockPos center = this.tudursvehiclemod$getDroneCenterPos();
		if (this.casWaypointOverride != null && !this.casWaypointOverride.isEmpty() && center != null) {
			int nearestIndex = 0;
			double nearestDistanceSq = Double.MAX_VALUE;
			for (int i = 0; i < this.casWaypointOverride.size(); i++) {
				com.example.tudursvehiclemod.block.DroneWaypoint waypoint = this.casWaypointOverride.get(i);
				double dx = this.getX() - (center.getX() + 0.5 + waypoint.relX());
				double dy = this.getY() - (center.getY() + waypoint.relY());
				double dz = this.getZ() - (center.getZ() + 0.5 + waypoint.relZ());
				double distanceSq = dx * dx + dy * dy + dz * dz;
				if (distanceSq < nearestDistanceSq) {
					nearestDistanceSq = distanceSq;
					nearestIndex = i;
				}
			}
			this.droneWaypointIndex = nearestIndex;
		}
	}

	/** Flies this aircraft back toward carrierMothershipUuid's own CURRENT AddWeapon mount position (carrierMothershipWeaponIndex - recomputed live every tick, since the mothership moves/rotates) and lands there once close and slow enough, despawning and replenishing +1 ammo directly into that weapon's own magazine. Approaches from directly BEHIND the mothership (opposite its own current forward direction) on a straight, gentle line rather than from an arbitrary direction - targets a fixed point behind-and-above the AddWeapon position until reasonably close to it, then transitions to the AddWeapon position itself for final touchdown. If the mothership is gone (removed, or no longer has that weapon slot at all) this aircraft simply despawns without any ammo replenished - there's nowhere left to land. Called every tick (in place of the normal waypoint autopilot) once carrierReturning is true. */

	/** Flies this aircraft back toward carrierMothershipUuid's own CURRENT AddWeapon mount position (carrierMothershipWeaponIndex - recomputed live every tick, since the mothership moves/rotates) and lands there once close and slow enough, despawning and replenishing +1 ammo directly into that weapon's own magazine. Approaches from directly BEHIND the mothership (opposite its own current forward direction) on a straight, gentle line rather than from an arbitrary direction. Called every tick (in place of the normal waypoint autopilot) once carrierReturning is true. */
	private void tudursvehiclemod$updateCarrierReturnToBase(VehicleDefinition def) {
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		Entity mothershipEntity = this.carrierMothershipUuid == null ? null : serverWorld.getEntity(this.carrierMothershipUuid);
		if (!(mothershipEntity instanceof AbstractVehicleEntity mothership) || mothershipEntity.isRemoved()) {
			this.discard();
			return;
		}
		VehicleDefinition mothershipDef = mothership.getDefinition();
		if (this.carrierMothershipWeaponIndex < 0 || this.carrierMothershipWeaponIndex >= mothershipDef.weapons().size()) {
			this.discard();
			return;
		}
		com.example.tudursvehiclemod.asset.WeaponDefinition mothershipWeapon = mothershipDef.weapons().get(this.carrierMothershipWeaponIndex);
		Vec3d addWeaponPos = mothership.tudursvehiclemod$computeWeaponSpawnPos(mothershipDef, mothershipWeapon, this.carrierMothershipWeaponIndex).pos();
		// The previous computation combined the mothership's own FULL 3D body orientation (including its own pitch/roll) with the weapon's own mount direction, which could skew this purely-horizontal "behind the ship" offset even slightly off-level. This only ever needs mothership yaw + the weapon's own mount_yaw (2D, horizontal only) - mount_pitch and the mothership's own pitch/roll are irrelevant here, since each landing waypoint's own relY already handles the vertical offset separately.
		com.example.tudursvehiclemod.asset.WeaponOffset mothershipMountOffset = mothershipWeapon.offsets().isEmpty()
				? new com.example.tudursvehiclemod.asset.WeaponOffset(0, 0, 0, 0, 0, java.util.Optional.empty())
				: mothershipWeapon.offsets().get(0);
		double combinedYawRad = Math.toRadians(mothership.getYaw() + mothershipMountOffset.mountYaw() + this.carrierMothershipYawOffset + this.carrierLandingYawOffset);
		double mothershipForwardX = -Math.sin(combinedYawRad);
		double mothershipForwardZ = Math.cos(combinedYawRad);

		// Per a crash report (ArrayIndexOutOfBoundsException, index -1 on an empty list): carrierLandingWaypoints is REQUIRED to be non-empty during a normal Carrier launch (validated at parse time), but had no NBT persistence of its own - carrierReturning (which IS persisted) could survive a world/chunk reload while this field silently reset to empty, crashing the very next tick. Discards gracefully instead of crashing if this ever happens (a genuinely invalid state this aircraft can't recover a sensible landing target from at all).
		if (this.carrierLandingWaypoints.isEmpty()) {
			org.slf4j.LoggerFactory.getLogger("VehicleMod/Carrier").warn("[Carrier] Aircraft id={} was returning to base with an empty carrierLandingWaypoints list (likely lost across a world/chunk reload) - discarding instead of crashing.", this.getId());
			this.discard();
			return;
		}
		// CarrierLandingWaypoints is required (at least one - validated at parse time), and the LAST one now serves the exact role the old approach point used to (the anchor for the final straight-line approach into AddWeapon itself). Any waypoints BEFORE the last are flown through first, in order - each one's own world position recomputed fresh every tick (mothershipForwardX/Z, addWeaponPos, same rotation basis throughout), since unlike the static launch route (computed once at spawn, since the aircraft departs immediately), a landing approach can span many seconds during which the mothership may still be moving/turning.
		int lastLandingWaypointIndex = this.carrierLandingWaypoints.size() - 1;
		if (this.carrierLandingWaypointIndex < lastLandingWaypointIndex) {
			com.example.tudursvehiclemod.asset.CarrierLaunchWaypoint currentLandingWaypoint = this.carrierLandingWaypoints.get(this.carrierLandingWaypointIndex);
			double[] landingWaypointRotated = tudursvehiclemod$rotateCasOffset(currentLandingWaypoint.relX(), currentLandingWaypoint.relZ(), mothershipForwardX, mothershipForwardZ);
			Vec3d landingWaypointTarget = addWeaponPos.add(landingWaypointRotated[0], currentLandingWaypoint.relY(), landingWaypointRotated[1]);
			double lwdx = landingWaypointTarget.x - this.getX();
			double lwdy = landingWaypointTarget.y - this.getY();
			double lwdz = landingWaypointTarget.z - this.getZ();
			double lwDistance3D = Math.sqrt(lwdx * lwdx + lwdy * lwdy + lwdz * lwdz);
			// A FIXED 2-block "reached" threshold could be smaller than this aircraft's own per-tick movement distance (cruiseSpeed), meaning it could overshoot past the waypoint every single tick without ever registering "reached" at all. Scales with cruiseSpeed (plus a fixed margin) so it's always at least as large as one tick's own worth of movement, regardless of speed.
			double landingWaypointArrivalRadius = Math.max(2.0, this.cruiseSpeed * 1.5 + 1.0);
			if (lwDistance3D < landingWaypointArrivalRadius) {
				this.carrierLandingWaypointIndex++;
			} else {
				double lwHorizontalDistance = Math.sqrt(lwdx * lwdx + lwdz * lwdz);
				// Same steady, moderate-speed steering as the final-approach-anchor leg below (CARRIER_LANDING_CRUISE_SPEED_FRACTION/CARRIER_LANDING_SPEED_BLEND) - kept identical so the transition between landing waypoints feels seamless, not like two different flight behaviors stitched together.
				float landingWaypointCruiseTarget = this.tudursvehiclemod$rampAutopilotThrottle(def, CARRIER_LANDING_CRUISE_SPEED_FRACTION, this.tudursvehiclemod$getEffectiveMaxSpeed());
				this.cruiseSpeed += (landingWaypointCruiseTarget - this.cruiseSpeed) * CARRIER_LANDING_SPEED_BLEND;
				// Same fix/reasoning as this class's own earlier identical addition for the grounded branch above.
				if (Math.abs(landingWaypointCruiseTarget - this.cruiseSpeed) < 1.0e-4f) {
					this.cruiseSpeed = landingWaypointCruiseTarget;
				}
				setThrottleDirect(this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION));
				Vec3d lwDirection = lwDistance3D > 1.0e-4 ? new Vec3d(lwdx, lwdy, lwdz).normalize() : new Vec3d(0, 0, 1);
				Vec3d lwFinalVelocity = lwDirection.multiply(this.cruiseSpeed);
				float lwNewYaw = lwHorizontalDistance > 1.0e-4 ? (float) Math.toDegrees(Math.atan2(-lwdx, lwdz)) : this.getYaw();
				float lwNewPitch = lwHorizontalDistance > 1.0e-4
						? (float) -Math.toDegrees(Math.atan2(lwdy, lwHorizontalDistance))
						: this.getPitch();
				this.setOrientationFromEuler(lwNewYaw, lwNewPitch, 0f);
				this.orientation.normalize();
				super.setYaw(lwNewYaw);
				super.setPitch(lwNewPitch);
				this.dataTracker.set(SYNCED_YAW, lwNewYaw);
				this.dataTracker.set(SYNCED_PITCH, lwNewPitch);
				this.dataTracker.set(SYNCED_ROLL, 0f);
				this.setVelocity(lwFinalVelocity);
				this.setPosition(this.getX() + lwFinalVelocity.x, this.getY() + lwFinalVelocity.y, this.getZ() + lwFinalVelocity.z);
			}
			return;
		}

		// The LAST landing waypoint releases flight control entirely (tudursvehiclemod$setDroneLink(null)) - keeping drone control active would maintain altitude/heading indefinitely instead of descending, and block reload while "still in flight". carrierGlideMinSpeed guarantees forward progress; actual recovery is AbstractVehicleEntity's own tudursvehiclemod$updateCarrierLandingToAmmo().
		com.example.tudursvehiclemod.asset.CarrierLaunchWaypoint finalLandingWaypoint = this.carrierLandingWaypoints.get(lastLandingWaypointIndex);
		finalLandingWaypoint.gearState().ifPresent(this::tudursvehiclemod$setGearDeployed);
		finalLandingWaypoint.bayState().ifPresent(open -> this.tudursvehiclemod$setWeaponBayForcedOpen(java.util.Optional.of(open)));
		// Guarantees getThrottle()/casWaypointSpeedFraction are already correctly settled to the landing-approach speed by the time isDroneActive() flips false below (the engine sound manager switches its own input source at that exact instant) - a safety net for routes with only a single landing waypoint, where the intermediate-waypoint loop above (which normally keeps these in sync) never runs at all.
		setThrottleDirect(CARRIER_LANDING_CRUISE_SPEED_FRACTION);
		this.dataTracker.set(CAS_WAYPOINT_SPEED_FRACTION, CARRIER_LANDING_CRUISE_SPEED_FRACTION);
		this.carrierGlideMinSpeed = CARRIER_LANDING_FINAL_MIN_SPEED;
		this.tudursvehiclemod$setDroneLink(null);
	}

	/** Per casForcedChunks's own doc: releases every chunk in the grid whenever this entity is actually removed from the world, for ANY reason (timeout despawn, route-completion despawn, destroyed by damage, etc.) - otherwise those chunks would stay permanently requested with nothing left to ever release them. Uses ChunkForceTracker.releaseAll() (see that class's own doc) rather than un-forcing directly - a shared chunk another aircraft/block still needs stays correctly force-loaded even after this one releases its own claim on it. */
	@Override
	public void onRemoved() {
		if (this.carrierLockCandidatePreviousId != null && this.getEntityWorld() instanceof ServerWorld serverWorldForLockCleanup) {
			Entity previous = serverWorldForLockCleanup.getEntityById(this.carrierLockCandidatePreviousId);
			if (previous != null) {
				this.tudursvehiclemod$setEntityHighlighted(previous, false);
			}
			this.carrierLockCandidatePreviousId = null;
		}
		if (!this.casForcedChunks.isEmpty() && this.getEntityWorld() instanceof ServerWorld serverWorld) {
			com.example.tudursvehiclemod.ChunkForceTracker.releaseAll(serverWorld, this);
			this.casForcedChunks = java.util.Set.of();
		}
		super.onRemoved();
	}

	/** This entire CAS state was previously never persisted at all - casWaypointOverride reset to null on load, so the CAS dispatch (including BOTH timeout checks) was skipped entirely, falling through to the normal Drone Center block lookup (which finds nothing at a CAS target position), leaving the aircraft in an undefined fallback state. Persists everything needed to resume exactly where it left off: the route itself (encoded the same semicolon-joined way block.DroneCenterBlockEntity's own waypoints are), the parallel attack flags, the weapon slot, both timeout counters, and the current waypoint index. */
	@Override
	protected void readCustomData(net.minecraft.storage.ReadView view) {
		super.readCustomData(view);
		// See droneLandingCenter's own doc for why the landing state is persisted.
		this.droneLandingCenter = null;
		this.droneLandingHomePoint = null;
		this.droneLandingViaPoint = null;
		this.droneLandingReachedViaPoint = false;
		String[] landingCenter = view.getString("DroneLandingCenter", "").split(",", -1);
		if (landingCenter.length == 3) {
			try {
				this.droneLandingCenter = new net.minecraft.util.math.BlockPos(Integer.parseInt(landingCenter[0]),
						Integer.parseInt(landingCenter[1]), Integer.parseInt(landingCenter[2]));
				String homeEncoded = view.getString("DroneLandingHomePoint", "");
				this.droneLandingHomePoint = homeEncoded.isEmpty() ? null : com.example.tudursvehiclemod.block.DroneWaypoint.tudursvehiclemod$decode(homeEncoded);
				String viaEncoded = view.getString("DroneLandingViaPoint", "");
				this.droneLandingViaPoint = viaEncoded.isEmpty() ? null : com.example.tudursvehiclemod.block.DroneWaypoint.tudursvehiclemod$decode(viaEncoded);
				this.droneLandingReachedViaPoint = view.getBoolean("DroneLandingReachedViaPoint", false);
			} catch (NumberFormatException ignored) {
				// Malformed: treated as "not landing" - the owning Drone Center (whose pendingDeactivation is persisted too) then simply finishes deactivating.
				this.droneLandingCenter = null;
			}
		}
		String casWaypointsEncoded = view.getString("CasWaypoints", "");
		if (!casWaypointsEncoded.isEmpty()) {
			java.util.List<com.example.tudursvehiclemod.block.DroneWaypoint> route = new java.util.ArrayList<>();
			for (String part : casWaypointsEncoded.split(";")) {
				com.example.tudursvehiclemod.block.DroneWaypoint decoded = com.example.tudursvehiclemod.block.DroneWaypoint.tudursvehiclemod$decode(part);
				if (decoded != null) {
					route.add(decoded);
				}
			}
			this.casWaypointOverride = route;
			String attackFlagsEncoded = view.getString("CasAttackFlags", "");
			java.util.List<Boolean> attackFlags = new java.util.ArrayList<>();
			for (int i = 0; i < route.size(); i++) {
				attackFlags.add(i < attackFlagsEncoded.length() && attackFlagsEncoded.charAt(i) == '1');
			}
			this.casAttackFlags = attackFlags;
			this.casWeaponIndex = view.getInt("CasWeaponIndex", 0);
			this.casTimeoutTicksRemaining = view.getInt("CasTimeoutTicksRemaining", 1200);
			this.casStuckTimeoutTicks = view.getInt("CasStuckTimeoutTicks", 1200);
			this.casTicksSinceLastProgress = view.getInt("CasTicksSinceLastProgress", 0);
			this.droneWaypointIndex = view.getInt("DroneWaypointIndex", 0);
			// Persisted the same way CAS's own state is, so a Carrier aircraft's mothership link/player-control state survives a world/chunk reload too.
			String mothershipUuidString = view.getString("CarrierMothershipUuid", "");
			this.carrierMothershipUuid = mothershipUuidString.isEmpty() ? null : java.util.UUID.fromString(mothershipUuidString);
			this.carrierMothershipSeatIndex = view.getInt("CarrierMothershipSeatIndex", 0);
			this.carrierMothershipWeaponIndex = view.getInt("CarrierMothershipWeaponIndex", 0);
			this.carrierReturning = view.getBoolean("CarrierReturning", false);
			this.carrierMothershipYawOffset = view.getFloat("CarrierMothershipYawOffset", 0.0f);
			this.carrierLandingYawOffset = view.getFloat("CarrierLandingYawOffset", 0.0f);
			// Per a crash report (ArrayIndexOutOfBoundsException) that this list's own lack of persistence let carrierReturning (which IS persisted) survive a reload while this field silently reset to empty: encoded as "relX,relY,relZ,speedPercent,gear,bay;relX,relY,relZ,.." (semicolon-separated waypoints, same per-waypoint comma format CarrierLaunchWaypoint's own config-file parser already uses) - reuses that same parser directly.
			String landingWaypointsEncoded = view.getString("CarrierLandingWaypoints", "");
			if (!landingWaypointsEncoded.isEmpty()) {
				java.util.List<com.example.tudursvehiclemod.asset.CarrierLaunchWaypoint> decodedWaypoints = new java.util.ArrayList<>();
				for (String waypointPart : landingWaypointsEncoded.split(";")) {
					com.example.tudursvehiclemod.asset.CarrierLaunchWaypoint parsed = com.example.tudursvehiclemod.asset.CarrierLaunchWaypoint.tudursvehiclemod$parse(waypointPart);
					if (parsed != null) {
						decodedWaypoints.add(parsed);
					}
				}
				this.carrierLandingWaypoints = decodedWaypoints;
			}
			this.carrierLandingWaypointIndex = view.getInt("CarrierLandingWaypointIndex", 0);
			this.carrierGlideMinSpeed = view.getFloat("CarrierGlideMinSpeed", 0.0f);
			this.dataTracker.set(CARRIER_PLAYER_CONTROLLED, view.getBoolean("CarrierPlayerControlled", false));
			String formationRootLeaderUuidString = view.getString("CarrierFormationRootLeaderUuid", "");
			if (!formationRootLeaderUuidString.isEmpty()) {
				this.carrierFormationRootLeaderUuid = java.util.UUID.fromString(formationRootLeaderUuidString);
				this.carrierFormationIndex = view.getInt("CarrierFormationIndex", 0);
				java.util.List<java.util.UUID> restoredRoster = FORMATION_ROSTER.computeIfAbsent(this.carrierFormationRootLeaderUuid, id -> new java.util.ArrayList<>());
				if (!restoredRoster.contains(this.getUuid())) {
					restoredRoster.add(this.getUuid());
				}
				if (this.carrierFormationIndex == 0) {
					FORMATION_LEADER_SLOT.put(this.carrierFormationRootLeaderUuid, this.getUuid());
				}
			}
		}
	}

	@Override
	protected void writeCustomData(net.minecraft.storage.WriteView view) {
		super.writeCustomData(view);
		if (this.droneLandingCenter != null) {
			view.putString("DroneLandingCenter", this.droneLandingCenter.getX() + "," + this.droneLandingCenter.getY() + "," + this.droneLandingCenter.getZ());
			if (this.droneLandingHomePoint != null) {
				view.putString("DroneLandingHomePoint", this.droneLandingHomePoint.tudursvehiclemod$encode());
			}
			if (this.droneLandingViaPoint != null) {
				view.putString("DroneLandingViaPoint", this.droneLandingViaPoint.tudursvehiclemod$encode());
			}
			view.putBoolean("DroneLandingReachedViaPoint", this.droneLandingReachedViaPoint);
		}
		if (this.casWaypointOverride != null && !this.casWaypointOverride.isEmpty()) {
			StringBuilder waypointsSb = new StringBuilder();
			for (int i = 0; i < this.casWaypointOverride.size(); i++) {
				if (i > 0) {
					waypointsSb.append(';');
				}
				waypointsSb.append(this.casWaypointOverride.get(i).tudursvehiclemod$encode());
			}
			view.putString("CasWaypoints", waypointsSb.toString());
			StringBuilder attackFlagsSb = new StringBuilder();
			if (this.casAttackFlags != null) {
				for (boolean attack : this.casAttackFlags) {
					attackFlagsSb.append(attack ? '1' : '0');
				}
			}
			view.putString("CasAttackFlags", attackFlagsSb.toString());
			view.putInt("CasWeaponIndex", this.casWeaponIndex);
			view.putInt("CasTimeoutTicksRemaining", this.casTimeoutTicksRemaining);
			view.putInt("CasStuckTimeoutTicks", this.casStuckTimeoutTicks);
			view.putInt("CasTicksSinceLastProgress", this.casTicksSinceLastProgress);
			view.putInt("DroneWaypointIndex", this.droneWaypointIndex);
			if (this.carrierMothershipUuid != null) {
				view.putString("CarrierMothershipUuid", this.carrierMothershipUuid.toString());
			}
			view.putInt("CarrierMothershipSeatIndex", this.carrierMothershipSeatIndex);
			view.putInt("CarrierMothershipWeaponIndex", this.carrierMothershipWeaponIndex);
			view.putBoolean("CarrierReturning", this.carrierReturning);
			view.putFloat("CarrierMothershipYawOffset", this.carrierMothershipYawOffset);
			view.putFloat("CarrierLandingYawOffset", this.carrierLandingYawOffset);
			// Per a crash report - see the matching read-side comment for the encoding format (reuses CarrierLaunchWaypoint's own config-file parser directly on read, so this must produce exactly the format that parser expects: speedFraction*100 for speedPercent, gearState/bayState as "-"/"0"/"1").
			StringBuilder landingWaypointsBuilder = new StringBuilder();
			for (int i = 0; i < this.carrierLandingWaypoints.size(); i++) {
				com.example.tudursvehiclemod.asset.CarrierLaunchWaypoint waypoint = this.carrierLandingWaypoints.get(i);
				if (i > 0) {
					landingWaypointsBuilder.append(';');
				}
				landingWaypointsBuilder.append(waypoint.relX()).append(',').append(waypoint.relY()).append(',').append(waypoint.relZ())
						.append(',').append(waypoint.speedFraction() * 100f)
						.append(',').append(waypoint.gearState().map(state -> state ? "1" : "0").orElse("-"))
						.append(',').append(waypoint.bayState().map(state -> state ? "1" : "0").orElse("-"));
			}
			view.putString("CarrierLandingWaypoints", landingWaypointsBuilder.toString());
			view.putInt("CarrierLandingWaypointIndex", this.carrierLandingWaypointIndex);
			view.putFloat("CarrierGlideMinSpeed", this.carrierGlideMinSpeed);
			view.putBoolean("CarrierPlayerControlled", this.dataTracker.get(CARRIER_PLAYER_CONTROLLED));
			if (this.carrierFormationRootLeaderUuid != null) {
				view.putString("CarrierFormationRootLeaderUuid", this.carrierFormationRootLeaderUuid.toString());
			}
			view.putInt("CarrierFormationIndex", this.carrierFormationIndex);
		}
	}


	/** A hard safety-net countdown (see CasStrikeConfig's own timeoutTicks doc for the configured starting value), decremented once per tick while this is a CAS strike aircraft - reaching 0 force-discards this entity outright, regardless of how far along its own route it's gotten. */
	private int casTimeoutTicksRemaining;
	/** An actively-flying CAS aircraft stays loaded the same way a real Drone Center already keeps its own linked vehicle loaded (see block.DroneCenterBlockEntity's own forceLoadTicksRemaining/setChunkForced() doc) - this aircraft has no such block to do that FOR it, so it force-loads a grid of chunks around itself directly instead. Prioritizing robustness over root-causing an observed setChunkForced() failure at a single-chunk transition boundary: tracks a 3x3 GRID of chunks centered on the aircraft's own current chunk (not just that single chunk), recomputed every tick - chunks that fall out of the grid are un-forced, newly-included ones are forced (with synchronous getChunk() loading first). This gives a much larger buffer against any single-chunk boundary/timing issue than tracking just one chunk ever could. */
	private java.util.Set<net.minecraft.util.math.ChunkPos> casForcedChunks = java.util.Set.of();

	/** This used to live INSIDE tudursvehiclemod$updateDroneAutopilot() itself, which only runs while tudursvehiclemod$isDroneActive() is true - but the LAST landing waypoint deliberately clears droneLink (turning isDroneActive() false) to release this aircraft into its own final, uncontrolled glide toward actual recovery. Once that happened, this update simply stopped running at all, freezing casForcedChunks at wherever the aircraft happened to be at that exact moment, with nothing left to ever follow it as it continued gliding away (a slow process, especially for a formation aircraft queued behind others). Called unconditionally from updateVehicleMovement() itself now instead, independent of isDroneActive()/carrierGlideMinSpeed/any other sub-state, so this tracks the aircraft's own actual position all the way through to actual recovery or removal.
	 * Per a further, well-supported direct report ("編隊の機体が常時ロード対象から外れる..読み込み範囲を狭めたところ3機目や4機目にも影響する"): this used to call world.setChunkForced() directly, un-forcing a chunk the instant it fell out of THIS aircraft's own grid - with no awareness that a DIFFERENT formation member's own overlapping grid might still need that exact same chunk (formation aircraft fly close together, so grid overlap is the normal case, not an edge case). Migrated to ChunkForceTracker.request()/release() (see that class's own doc) instead - a chunk shared by multiple aircraft now only actually un-forces once EVERY aircraft (or any other requester) relying on it has released their own claim. */
	private void tudursvehiclemod$updateCasForcedChunks() {
		if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
			net.minecraft.util.math.ChunkPos currentChunk = new net.minecraft.util.math.ChunkPos(this.getBlockPos());
			java.util.Set<net.minecraft.util.math.ChunkPos> newGrid = tudursvehiclemod$computeChunkGrid(currentChunk);
			if (!newGrid.equals(this.casForcedChunks)) {
				for (net.minecraft.util.math.ChunkPos chunk : this.casForcedChunks) {
					if (!newGrid.contains(chunk)) {
						com.example.tudursvehiclemod.ChunkForceTracker.release(serverWorld, chunk, this);
					}
				}
				for (net.minecraft.util.math.ChunkPos chunk : newGrid) {
					if (!this.casForcedChunks.contains(chunk)) {
						com.example.tudursvehiclemod.ChunkForceTracker.request(serverWorld, chunk, this);
					}
				}
				this.casForcedChunks = newGrid;
			}
		}
	}

	/** The normal per-tick chunk-forcing logic (see casForcedChunks's own doc) can only ever run once this entity is ALREADY ticking, which itself requires the chunk to already be loaded - a chicken-and-egg problem. Called once by the CAS spawn logic, immediately after this entity is added to the world, to force-load the full 3x3 grid right away rather than waiting for a tick that would otherwise never come. Uses ChunkForceTracker.request() (see updateCasForcedChunks()'s own doc for why - a formation's own overlapping grids need reference counting, not a direct setChunkForced() call). */
	public void tudursvehiclemod$forceLoadSpawnChunk() {
		if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
			net.minecraft.util.math.ChunkPos centerChunk = new net.minecraft.util.math.ChunkPos(this.getBlockPos());
			java.util.Set<net.minecraft.util.math.ChunkPos> grid = tudursvehiclemod$computeChunkGrid(centerChunk);
			for (net.minecraft.util.math.ChunkPos chunk : grid) {
				com.example.tudursvehiclemod.ChunkForceTracker.request(serverWorld, chunk, this);
			}
			this.casForcedChunks = grid;
		}
	}

	/** The 3x3 grid of chunks centered on centerChunk - see casForcedChunks's own doc. */
	private static java.util.Set<net.minecraft.util.math.ChunkPos> tudursvehiclemod$computeChunkGrid(net.minecraft.util.math.ChunkPos centerChunk) {
		java.util.Set<net.minecraft.util.math.ChunkPos> grid = new java.util.HashSet<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				grid.add(new net.minecraft.util.math.ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
			}
		}
		return grid;
	}
	/** The total-lifetime timeout (casTimeoutTicksRemaining) alone didn't actually prevent indefinite circling, because this aircraft's own CAS state was previously never persisted across a world/chunk reload at all (silently losing the entire link, including that countdown, well before it ever ran out): the configured stuck-detection threshold (see CasStrikeConfig's own stuckTimeoutTicks doc), copied at spawn time AND persisted, so it survives a reload independently of anything else. */
	private int casStuckTimeoutTicks;
	/** Counts UP once per tick while this CAS aircraft's own droneWaypointIndex hasn't advanced to a new waypoint - reset to 0 the instant it does advance. Reaching casStuckTimeoutTicks force-discards this entity, regardless of the separate total-lifetime timer's own state - this is what actually catches "no progress at all" (e.g. stuck circling after a reload silently lost its own route data) even if the total-lifetime timer itself somehow also got reset/lost. */
	private int casTicksSinceLastProgress;

	/** See casWaypointOverride/casAttackFlags/casWeaponIndex's own doc - called once by the CAS spawn logic right after this entity is created, before it's actually added to the world. */
	public void tudursvehiclemod$setCasWaypointRoute(java.util.List<com.example.tudursvehiclemod.block.DroneWaypoint> waypoints, java.util.List<Boolean> attackFlags, int weaponIndex) {
		this.casWaypointOverride = waypoints;
		this.casAttackFlags = attackFlags;
		this.casWeaponIndex = weaponIndex;
	}

	/** See casTimeoutTicksRemaining's own doc - called once by the CAS spawn logic alongside tudursvehiclemod$setCasWaypointRoute(). */
	public void tudursvehiclemod$setCasTimeoutTicks(int timeoutTicks) {
		this.casTimeoutTicksRemaining = timeoutTicks;
	}

	/** See carrierLaunchWaypointCount's own doc. Called once by the Carrier spawn logic, alongside tudursvehiclemod$setCasWaypointRoute() - only ever set for a genuinely Carrier-launched aircraft (CAS strikes have no launch segment at all, so never call this). */
	public void tudursvehiclemod$setCarrierLaunchWaypointCount(int count) {
		this.carrierLaunchWaypointCount = count;
	}

	/** True while droneWaypointIndex is still within the launch segment specifically (see carrierLaunchWaypointCount's own doc), false once past it (into the normal patrol/strike/landing portion, where the runway-carry system's own ordinary footprint/height filter applies instead). Always false for a non-Carrier aircraft (carrierLaunchWaypointCount stays 0). */
	public boolean tudursvehiclemod$isLaunchingFromCarrier() {
		return this.carrierLaunchWaypointCount > 0 && this.droneWaypointIndex < this.carrierLaunchWaypointCount;
	}

	/** See casStuckTimeoutTicks's own doc - called once by the CAS spawn logic alongside tudursvehiclemod$setCasTimeoutTicks(). */
	public void tudursvehiclemod$setCasStuckTimeoutTicks(int stuckTimeoutTicks) {
		this.casStuckTimeoutTicks = stuckTimeoutTicks;
	}

	/** Whether this is a CAS strike aircraft (see casWaypointOverride's own doc) - tudursvehiclemod$updateDroneWaypointAutopilot() reads this to despawn on reaching the end of its own one-shot route, instead of looping back to the first waypoint the way an ordinary Drone Center patrol route does. */
	public boolean tudursvehiclemod$isCasStrikeAircraft() {
		return this.casWaypointOverride != null;
	}


	/** The vehicle's own position captured at the exact moment the CURRENT leg began (target just changed) - together with the target itself, defines this leg's own route direction, used by the along-track passage check below (a standard navigation technique: has the vehicle crossed the plane through the target, perpendicular to the route direction - i.e. genuinely passed it - regardless of how it curved to get there). NaN (droneLegStartX) means "not yet captured for this leg". */
	private double droneLegStartX = Double.NaN;
	private double droneLegStartY;
	private double droneLegStartZ;

	/** this.getYaw() re-derives yaw by decomposing the current orientation quaternion (which also bakes in roll) back into Euler angles each time - with a substantial roll baked in, that decomposition can drift from the actual intended heading, and next tick's navigation (which steers based on whatever getYaw() reports) would then compound that drift further. Tracks the waypoint/landing autopilots' own intended yaw directly in this plain field instead - set once per tick from the same rate-limited turn calculation as before, but read back from HERE for the next tick's own steering rather than re-extracted from the (roll-affected) orientation - decoupling navigation entirely from however roll happens to be rendered. */
	private float droneTrackedYaw;
	/** Client-only rendering state (see the "player == null && isClient()" branch's own doc in updateVehicleMovement()) - tracks each axis independently, never re-extracted from the composed orientation quaternion once seeded, to avoid a gimbal-lock-like coupling artifact confirmed via diagnostic log data when roll is near +/-180 combined with active pitch. */
	private float clientTrackedYaw;
	private float clientTrackedPitch;
	private float clientTrackedRoll;
	private boolean clientTrackedOrientationInitialized;
	/** Whether droneTrackedYaw currently holds a real, previously-computed heading (see that field's own doc) - false right when a drone autopilot mode first engages (e.g. just activated, or just started landing), so that first tick seeds it from the vehicle's own actual current yaw instead of an uninitialized 0. */
	private boolean droneTrackedYawInitialized;

	/** Returns droneTrackedYaw if already initialized (see that field's own doc), otherwise seeds it from this vehicle's own actual current yaw first - called at the start of each drone autopilot mode that needs roll-independent yaw tracking. */
	private float tudursvehiclemod$droneYawForNavigation() {
		if (!this.droneTrackedYawInitialized) {
			this.droneTrackedYaw = this.getYaw();
			this.droneTrackedYawInitialized = true;
		}
		return this.droneTrackedYaw;
	}

	/** Roll here is purely a visual/display effect spliced into the existing waypoint-circling flight (real flight physics don't need to actually couple to it at all) rather than something that needs to interact with navigation: tracked directly in this plain field, exactly the same "never read back from the orientation quaternion" reasoning as droneTrackedYaw's own doc, so blending it can never be affected by however yaw/pitch extraction happens to behave (and vice versa) - the two are now completely independent. */
	private float droneTrackedRoll;
	/** The circular-orbit autopilot's OWN turn-based banking state - deliberately separate from droneTrackedRoll (see that field's own doc) so the two autopilot modes can never corrupt each other's roll tracking even in an edge case where dispatch briefly flips between them. */
	private float droneOrbitTrackedRoll;
	/** Same "needs seeding on first use" reasoning as droneTrackedYawInitialized's own doc, just for droneTrackedRoll. */
	private boolean droneTrackedRollInitialized;

	/** Returns droneTrackedRoll if already initialized, otherwise seeds it from this vehicle's own actual current roll first - same pattern as tudursvehiclemod$droneYawForNavigation()'s own doc. */
	private float tudursvehiclemod$droneRollForRendering() {
		if (!this.droneTrackedRollInitialized) {
			this.droneTrackedRoll = this.getRoll();
			this.droneTrackedRollInitialized = true;
		}
		return this.droneTrackedRoll;
	}

	/** Absolute roll value this transition is currently converging toward - NaN means "no transition set up yet" (triggers capturing a fresh start point below, the first time this runs, or whenever the target itself changes - e.g. arriving at a new waypoint with a different rollAngle). the earlier exponential blend ("current + delta*rate", repeated every tick) mathematically only ever asymptotically approaches its target and never truly reaches it, and always depends on reading back whatever "current" state happens to be - this instead tracks elapsed ticks since the transition began and linearly interpolates, reaching EXACTLY the target once enough ticks have passed, regardless of any prior drift. */
	private float droneRollTransitionTarget = Float.NaN;
	/** Roll value captured at the exact moment the CURRENT transition began (see droneRollTransitionTarget's own doc) - the starting point for this transition's own interpolation. */
	private float droneRollTransitionStart;
	/** Ticks elapsed since the current transition began. */
	private int droneRollTransitionTicks;
	/** Roughly how many ticks a roll transition takes to fully complete at normal (1.0x) maneuverability - scaled down by a waypoint's own maneuverabilityMultiplier (higher = faster transition). */
	private static final int DRONE_ROLL_TRANSITION_BASE_TICKS = 20;

	/** Computes this tick's own absolute roll value for a drone flying toward targetRoll at the given maneuverabilityMultiplier (see droneRollTransitionTarget's own doc) - always reaches EXACTLY targetRoll once the transition (scaled by maneuverabilityMultiplier) has had enough ticks to complete, unlike the old per-tick exponential blend this replaces. */
	private float tudursvehiclemod$computeDroneRoll(float targetRoll, float maneuverabilityMultiplier) {
		boolean targetChanged = Float.isNaN(this.droneRollTransitionTarget) || this.droneRollTransitionTarget != targetRoll;
		if (targetChanged) {
			this.droneRollTransitionStart = this.tudursvehiclemod$droneRollForRendering();
			this.droneRollTransitionTarget = targetRoll;
			this.droneRollTransitionTicks = 0;
		}
		this.droneRollTransitionTicks++;
		int transitionDurationTicks = Math.max(1, Math.round(DRONE_ROLL_TRANSITION_BASE_TICKS / Math.max(maneuverabilityMultiplier, 0.01f)));
		float progress = MathHelper.clamp(this.droneRollTransitionTicks / (float) transitionDurationTicks, 0f, 1f);
		float roll = MathHelper.lerp(progress, this.droneRollTransitionStart, targetRoll);
		this.droneTrackedRoll = roll;
		return roll;
	}

	/** While non-null, this vehicle is actively trying to land gently near this position (a Drone Center that was just manually deactivated while the vehicle was still airborne) instead of simply falling out of control - see tudursvehiclemod$updateDroneLandingRouteAutopilot()'s own doc (this flow was previously driven through the shared tudursvehiclemod$updateDroneWaypointAutopilot() with a temporary route, then rebuilt as its own dedicated method mirroring Carrier's own proven landing-waypoint mechanics instead - see that method's own doc for the full reasoning). Checked FIRST in updateVehicleMovement(), independent of tudursvehiclemod$isDroneActive()/getDroneCenterPos() (which may already be false/null by the time this runs, since the center itself was already deactivated) - a plain, server-side-only field, same as droneCenterPos's own reasoning. Persisted (with droneLandingHomePoint/droneLandingViaPoint/droneLandingReachedViaPoint, and the owning center's own pendingDeactivation) so an unexpected shutdown or a chunk unload mid-landing resumes the SAME landing afterward - previously all of it was lost, the reloaded center found nothing pending and simply re-linked the vehicle, silently undoing the manual deactivation and sending it back out on patrol. Deliberately NOT set at all when the vehicle is destroyed (see tudursvehiclemod$requestDroneLanding()'s own doc) - a wreck has nothing to gently land. */
	private net.minecraft.util.math.BlockPos droneLandingCenter;
	/** This landing attempt's own Home Point/via-point, captured ONCE (see tudursvehiclemod$requestDroneLanding()'s own doc for why) rather than re-fetched from the Drone Center block every tick. droneLandingViaPoint is null exactly when no via-point was configured at the moment landing began - matching block.DroneCenterBlockEntity's own tudursvehiclemod$getReturnViaPoint() contract. */
	private com.example.tudursvehiclemod.block.DroneWaypoint droneLandingHomePoint;
	private com.example.tudursvehiclemod.block.DroneWaypoint droneLandingViaPoint;
	/** Whether THIS landing attempt has already reached droneLandingViaPoint (if one was configured at all - effectively always true when droneLandingViaPoint is null, since there's nothing to reach first). Reset to false every time tudursvehiclemod$requestDroneLanding() starts a fresh landing attempt. */
	private boolean droneLandingReachedViaPoint;

	/** Called by DroneCenterBlockEntity's own tudursvehiclemod$toggleActive() when deactivating while this vehicle is still airborne. Already-grounded vehicles skip this entirely (isDroneActive() there just goes straight to false, same as before this feature existed) - there's nothing to land. Deliberately never called from the destruction path (AbstractVehicleEntity's own onDestroyed() releases the link directly, unchanged) - a destroyed vehicle should not attempt this.
	 *
	 * <p>homePoint/viaPoint are captured HERE, directly from the DroneCenterBlockEntity that's already calling this method, rather than re-fetched from the block entity every tick during the actual landing flight (see droneLandingHomePoint's own doc for the full diagnosis) - stable for the whole landing attempt regardless of whether that block's own chunk stays loaded throughout the flight. */
	public void tudursvehiclemod$requestDroneLanding(net.minecraft.util.math.BlockPos center,
			com.example.tudursvehiclemod.block.DroneWaypoint homePoint, com.example.tudursvehiclemod.block.DroneWaypoint viaPoint) {
		this.droneLandingCenter = center;
		this.droneLandingHomePoint = homePoint;
		this.droneLandingViaPoint = viaPoint;
		this.droneLandingReachedViaPoint = false;
	}

	/** Whether this vehicle is currently mid-landing-sequence (see droneLandingCenter's own doc) - DroneCenterBlockEntity's own tick() polls this to know when it's finally safe to fully deactivate/release chunk-forcing. */
	public boolean tudursvehiclemod$isDroneLandingInProgress() {
		return this.droneLandingCenter != null;
	}

	/** Flies directly toward the current waypoint's own absolute position (the linked Drone Center's own position plus that waypoint's relative offset - see DroneWaypoint's own doc), advancing to the next one in the route once the vehicle has genuinely passed it (see droneLegStartX's own doc for the along-track detection this uses), looping back to the first after the last. Reuses the exact same general movement approach as the circular-orbit autopilot just above (yaw rate-limited direct velocity drive so nose always exactly matches actual travel direction, altitude blend, roll banking) - the only real difference is steering toward a specific point instead of a tangential circular direction. */
	private void tudursvehiclemod$updateDroneWaypointAutopilot(VehicleDefinition def, net.minecraft.util.math.BlockPos center,
			java.util.List<com.example.tudursvehiclemod.block.DroneWaypoint> waypoints) {
		this.droneWaypointIndex = MathHelper.clamp(this.droneWaypointIndex, 0, waypoints.size() - 1);
		com.example.tudursvehiclemod.block.DroneWaypoint waypoint = waypoints.get(this.droneWaypointIndex);
		double targetX = center.getX() + 0.5 + waypoint.relX();
		double targetY = center.getY() + waypoint.relY();
		double targetZ = center.getZ() + 0.5 + waypoint.relZ();

		// Per droneLegStartX's own doc: seeds the very first leg's own start point from wherever the vehicle actually is right now (subsequent legs capture this fresh each time the route advances, further down).
		if (Double.isNaN(this.droneLegStartX)) {
			this.droneLegStartX = this.getX();
			this.droneLegStartY = this.getY();
			this.droneLegStartZ = this.getZ();
		}

		double dx = targetX - this.getX();
		double dy = targetY - this.getY();
		double dz = targetZ - this.getZ();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		float cruiseSpeedTarget = this.tudursvehiclemod$rampAutopilotThrottle(def, waypoint.speedFraction(), def.maxSpeed());
		// This vehicle's own ACTUAL yaw turn rate, not the raw un-multiplied def.turnSpeed(). scaled specifically by this waypoint's own turnManeuverabilityMultiplier (see DroneWaypoint's own doc).
		float effectiveTurnRate = this.getYawFollowRateDegrees() * waypoint.turnManeuverabilityMultiplier();

		// A waypoint positioned essentially AT this leg's own start point (e.g. a launch waypoint with a 0,0,0 offset from spawn) has no real direction of its own to navigate toward at all - the vehicle drifts a small, essentially arbitrary amount off the exact target regardless of how it's actually flying, so ANY fixed distance threshold eventually gets exceeded as that drift accumulates, re-triggering the same noise-driven atan2 computation. Ties this decision instead to whether THIS LEG ITSELF is degenerate (computed once, from the fixed leg start/target pair, not the vehicle's own ever-changing current position) - holds the current tracked yaw for the ENTIRE degenerate leg, all the way until the existing along-track/close-enough passedWaypoint check (further below) actually advances past it.
		double legRouteX = targetX - this.droneLegStartX;
		double legRouteY = targetY - this.droneLegStartY;
		double legRouteZ = targetZ - this.droneLegStartZ;
		boolean legIsDegenerate = (legRouteX * legRouteX + legRouteY * legRouteY + legRouteZ * legRouteZ) <= 1.0;

		float currentTrackedYaw = this.tudursvehiclemod$droneYawForNavigation();
		float desiredYaw = legIsDegenerate ? currentTrackedYaw : (float) Math.toDegrees(Math.atan2(-dx, dz));
		// Uses the deterministic, time-based transition (see tudursvehiclemod$computeDroneRoll()'s own doc) instead of an exponential blend that only ever asymptotically approaches its target - scaled specifically by this waypoint's own rollManeuverabilityMultiplier (see DroneWaypoint's own doc for why this is now separate from turn performance). Computed here, before yaw, so this method's own turn rate also genuinely speeds up while banked (see tudursvehiclemod$computeBankBoostedYawRate()'s own doc) - this method's own roll ITSELF stays fully waypoint-configurable and completely unchanged, only now also feeding into the yaw rate below.
		float newRoll = this.tudursvehiclemod$computeDroneRoll(waypoint.rollAngle(), waypoint.rollManeuverabilityMultiplier());
		float bankBoostedYawRate = this.tudursvehiclemod$computeBankBoostedYawRate(newRoll, effectiveTurnRate, this.getPitchFollowRateDegrees());
		float newYaw = stepTowardAngle(currentTrackedYaw, desiredYaw, bankBoostedYawRate);
		this.droneTrackedYaw = newYaw;

		double targetVerticalSpeed = MathHelper.clamp(dy * DRONE_ORBIT_ALTITUDE_CORRECTION_GAIN,
				-cruiseSpeedTarget * 0.5, cruiseSpeedTarget * 0.5);

		Vec3d currentVelocity = this.getVelocity();
		double currentSpeedMagnitude = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
		double blendedSpeedMagnitude = this.tudursvehiclemod$blendCruiseSpeedToward(def, (float) currentSpeedMagnitude, cruiseSpeedTarget, this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION), def.maxSpeed());
		float speedResponseBlend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
		double blendedVerticalSpeed = currentVelocity.y + (targetVerticalSpeed - currentVelocity.y) * speedResponseBlend;

		double newYawRad = Math.toRadians(newYaw);
		Vec3d finalVelocity = new Vec3d(-Math.sin(newYawRad) * blendedSpeedMagnitude, blendedVerticalSpeed, Math.cos(newYawRad) * blendedSpeedMagnitude);
		double horizontalSpeed = blendedSpeedMagnitude;

		float newPitch = horizontalSpeed < 1.0e-4 ? this.getPitch()
				: (float) -Math.toDegrees(Math.atan2(finalVelocity.y, horizontalSpeed));

		this.setOrientationFromEuler(newYaw, newPitch, newRoll);
		this.orientation.normalize();
		super.setYaw(newYaw);
		super.setPitch(newPitch);
		this.dataTracker.set(SYNCED_YAW, newYaw);
		this.dataTracker.set(SYNCED_PITCH, newPitch);
		this.dataTracker.set(SYNCED_ROLL, newRoll);
		this.cruiseSpeed = (float) finalVelocity.length();
		setThrottleDirect(this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION));

		double preMoveSpeed = finalVelocity.length();
		this.setVelocity(finalVelocity);
		this.move(MovementType.SELF, this.getVelocity());
		this.tudursvehiclemod$checkBlockCrashDamage(preMoveSpeed, newPitch);
		this.tudursvehiclemod$checkEntityCrashDamage(preMoveSpeed);

		double distance3D = Math.sqrt(dx * dx + dy * dy + dz * dz);
		// Projects the vehicle's own position onto this leg's own route direction (start -> target) - once the vehicle is on the far side of the target along that direction (a positive dot product of target->vehicle with start->target), it has genuinely passed the waypoint, regardless of how much it curved getting there. Falls back to a plain close-enough check only in the degenerate case (legIsDegenerate, computed above with the SAME threshold this method's own yaw-holding decision already used, so the two never disagree) where the leg's own start point and target are essentially the same spot (route direction undefined).
		boolean passedWaypoint;
		if (!legIsDegenerate) {
			double alongTrackDot = (this.getX() - targetX) * legRouteX + (this.getY() - targetY) * legRouteY + (this.getZ() - targetZ) * legRouteZ;
			passedWaypoint = alongTrackDot >= 0;
		} else {
			passedWaypoint = distance3D < 3.0;
		}
		if (passedWaypoint) {
			// A CAS strike aircraft despawns once it reaches the end of its own fixed route, rather than looping back to the first waypoint the way an ordinary Drone Center patrol route does. A Carrier aircraft instead begins its own return-to-base/landing sequence (see carrierReturning's own doc) rather than despawning outright.
			if (this.casWaypointOverride != null && this.droneWaypointIndex == waypoints.size() - 1) {
				if (this.carrierMothershipUuid != null) {
					if (this.carrierFormationIndex > 0) {
						this.carrierWaitingToLand = true;
						this.carrierLandingQueueTicksRemaining = this.carrierFormationIndex * CARRIER_LANDING_QUEUE_DELAY_TICKS;
					} else {
						this.carrierReturning = true;
					}
					return;
				}
				this.discard();
				return;
			}
			if (this.carrierLaunchWaypointCount > 0 && this.droneWaypointIndex == this.carrierLaunchWaypointCount - 1 && this.carrierFormationPending) {
				this.carrierOrbitingForFormation = true;
				return;
			}
			int previousWaypointIndexForLog = this.droneWaypointIndex;
			this.droneWaypointIndex = (this.droneWaypointIndex + 1) % waypoints.size();
			// Per the same investigation as updateDroneFormationFollow()'s own identical diagnostic - lets a wingman's own convergence event be directly correlated against exactly when/how sharply this leader itself transitioned between waypoints.
			if (this.carrierFormationRootLeaderUuid == null || this.getUuid().equals(FORMATION_LEADER_SLOT.get(this.carrierFormationRootLeaderUuid))) {
				// Only meaningful for an actual formation LEADER (a wingman's own droneWaypointIndex is separately mirrored from the leader elsewhere, and logging it here too would be redundant/confusing).
			}
			this.droneLegStartX = this.getX();
			this.droneLegStartY = this.getY();
			this.droneLegStartZ = this.getZ();
			// Per carrierSpeedBoostFlags's own doc: applies the NEWLY-reached waypoint's own boost (if any) exactly once, right here at the transition itself - this "if (passedWaypoint)" block only ever runs on the single tick droneWaypointIndex actually changes, never on any of the many ticks in between while still flying toward it, so this can never compound/re-apply for the same waypoint. Empty for a plain (non-Carrier) Drone Center route (carrierSpeedBoostFlags defaults to List.of()), so the bounds check below naturally no-ops there without needing a separate casWaypointOverride != null guard.
			if (this.droneWaypointIndex < this.carrierSpeedBoostFlags.size()) {
				this.carrierSpeedBoostFlags.get(this.droneWaypointIndex).ifPresent(kmh -> this.tudursvehiclemod$applyCarrierSpeedBoost(kmh, -Math.sin(newYawRad), Math.cos(newYawRad)));
			}
		}
	}

	/** How far above the mothership's own current position this aircraft holds while orbiting, awaiting the rest of its own formation - see carrierOrbitingForFormation's own doc. */
	private static final double CARRIER_FORMATION_WAIT_ALTITUDE = 20.0;
	/** The LAST formation aircraft (longest landing-queue wait) could end up stranded outside loaded chunks around the transition to landing: caps the natural orbit radius (which otherwise grows unbounded for a fast, low-maneuverability aircraft) so a long wait never drifts this aircraft far from the mothership - comfortably within the 3x3 chunk-forcing grid's own buffer, and more sensible visually (a real carrier's own aircraft "stack" holds close by). */
	private static final double CARRIER_FORMATION_WAIT_MAX_RADIUS = 64.0;

	/** Orbits the mothership's own CURRENT (moving) position while waiting for something else to finish first - reuses the exact same circular-motion math tudursvehiclemod$updateDroneAutopilot()'s own Drone Center orbit already established (natural radius from this vehicle's own turn rate/cruise speed), just re-targeted at a dynamically-looked-up Vec3d each tick instead of a fixed BlockPos, since a ship (unlike a Drone Center block) moves. Shared by two distinct callers: the launch-side formation-wait queue (carrierFormationPending/carrierOrbitingForFormation - awaiting the rest of the formation to finish launching) and the landing-side queue (carrierWaitingToLand - formation landings could otherwise collide/delay recovery). If the mothership can no longer be found at all (destroyed/unloaded), resolves whichever of the two waiting states is actually active rather than assuming it's always the launch-side one - clears carrierFormationPending (proceeding with the main route immediately, rather than orbiting forever with no way to ever receive the "formation complete" signal) OR, for the landing queue, clears carrierWaitingToLand and sets carrierReturning=true instead (letting tudursvehiclemod$updateCarrierReturnToBase()'s own already-existing "mothership gone" handling take it from there, rather than duplicating that logic here). */
	private void tudursvehiclemod$updateCarrierFormationWaitOrbit(VehicleDefinition def) {
		Entity mothership = this.carrierLaunchMothershipUuid != null && this.getEntityWorld() instanceof ServerWorld serverWorld
				? serverWorld.getEntity(this.carrierLaunchMothershipUuid) : null;
		if (mothership == null) {
			if (this.carrierWaitingToLand) {
				this.carrierWaitingToLand = false;
				this.carrierReturning = true;
			} else {
				this.carrierFormationPending = false;
				this.carrierOrbitingForFormation = false;
			}
			return;
		}
		double centerX = mothership.getX();
		double centerZ = mothership.getZ();
		double targetY = mothership.getY() + CARRIER_FORMATION_WAIT_ALTITUDE;

		float cruiseSpeedTarget = this.tudursvehiclemod$rampAutopilotThrottle(def, 0.6f, def.maxSpeed());
		float effectiveTurnRate = this.getYawFollowRateDegrees();
		double turnRateRadPerTick = Math.toRadians(Math.max(effectiveTurnRate, 0.1f));
		double naturalRadius = Math.min(cruiseSpeedTarget / turnRateRadPerTick, CARRIER_FORMATION_WAIT_MAX_RADIUS);

		double dx = this.getX() - centerX;
		double dz = this.getZ() - centerZ;
		double currentRadius = Math.sqrt(dx * dx + dz * dz);
		double currentAngleRad = currentRadius < 1.0e-4 ? 0.0 : Math.atan2(dz, dx);

		Vec3d tangential = new Vec3d(-Math.sin(currentAngleRad), 0, Math.cos(currentAngleRad));
		double radiusError = naturalRadius - currentRadius;
		Vec3d radialCorrection = new Vec3d(Math.cos(currentAngleRad), 0, Math.sin(currentAngleRad))
				.multiply(radiusError * DRONE_ORBIT_RADIAL_CORRECTION_GAIN);
		Vec3d horizontalTarget = tangential.multiply(cruiseSpeedTarget).add(radialCorrection);
		double horizontalTargetMagnitude = Math.sqrt(horizontalTarget.x * horizontalTarget.x + horizontalTarget.z * horizontalTarget.z);
		if (horizontalTargetMagnitude > def.maxSpeed()) {
			horizontalTarget = horizontalTarget.multiply(def.maxSpeed() / horizontalTargetMagnitude);
		}

		double altitudeError = targetY - this.getY();
		double targetVerticalSpeed = MathHelper.clamp(altitudeError * DRONE_ORBIT_ALTITUDE_CORRECTION_GAIN,
				-cruiseSpeedTarget * 0.5, cruiseSpeedTarget * 0.5);

		float desiredYaw = (float) Math.toDegrees(Math.atan2(-horizontalTarget.x, horizontalTarget.z));
		float currentTrackedYaw = this.tudursvehiclemod$droneYawForNavigation();
		// See tudursvehiclemod$computeCoordinatedTurn()'s own doc.
		float[] coordinatedTurn = this.tudursvehiclemod$computeCoordinatedTurn(currentTrackedYaw, this.droneOrbitTrackedRoll, desiredYaw, effectiveTurnRate, this.getPitchFollowRateDegrees());
		float newYaw = coordinatedTurn[0];
		float newRoll = coordinatedTurn[1];
		this.droneTrackedYaw = newYaw;
		this.droneOrbitTrackedRoll = newRoll;

		double targetSpeedMagnitude = Math.sqrt(horizontalTarget.x * horizontalTarget.x + horizontalTarget.z * horizontalTarget.z);
		Vec3d currentVelocity = this.getVelocity();
		double currentSpeedMagnitude = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
		double blendedSpeedMagnitude = this.tudursvehiclemod$blendCruiseSpeedToward(def, (float) currentSpeedMagnitude, (float) targetSpeedMagnitude, this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION), def.maxSpeed());
		float orbitSpeedResponseBlend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
		double blendedVerticalSpeed = currentVelocity.y + (targetVerticalSpeed - currentVelocity.y) * orbitSpeedResponseBlend;

		double newYawRad = Math.toRadians(newYaw);
		Vec3d finalVelocity = new Vec3d(-Math.sin(newYawRad) * blendedSpeedMagnitude, blendedVerticalSpeed, Math.cos(newYawRad) * blendedSpeedMagnitude);
		double horizontalSpeed = blendedSpeedMagnitude;

		float newPitch = horizontalSpeed < 1.0e-4 ? this.getPitch()
				: (float) -Math.toDegrees(Math.atan2(finalVelocity.y, horizontalSpeed));

		this.setOrientationFromEuler(newYaw, newPitch, newRoll);
		this.orientation.normalize();
		super.setYaw(newYaw);
		super.setPitch(newPitch);
		this.dataTracker.set(SYNCED_YAW, newYaw);
		this.dataTracker.set(SYNCED_PITCH, newPitch);
		this.dataTracker.set(SYNCED_ROLL, newRoll);
		this.cruiseSpeed = (float) finalVelocity.length();
		setThrottleDirect(this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION));

		double preMoveSpeed = finalVelocity.length();
		this.setVelocity(finalVelocity);
		this.move(MovementType.SELF, this.getVelocity());
		this.tudursvehiclemod$checkBlockCrashDamage(preMoveSpeed, newPitch);
		this.tudursvehiclemod$checkEntityCrashDamage(preMoveSpeed);
	}

	/** Flies this wingman toward a target point offset from the LEADER's own current position/heading (droneFormationLateralOffset/droneFormationLongitudinalOffset, rotated by the leader's own current yaw via the exact same tudursvehiclemod$rotateCasOffset() CAS/Carrier's own formation offsets already use) - a real "follow the leader" behavior, rather than independently reproducing whatever orbit/patrol route the leader itself happens to be flying (which would require this wingman to somehow already know that route, and would drift out of sync the instant the leader's own path deviates for any reason, e.g. avoiding terrain).
	 *
	 * Mirrors tudursvehiclemod$updateCarrierFormationWaitOrbit()'s own exact structure (turn-rate-limited heading via stepTowardAngle(), throttle ramped via tudursvehiclemod$rampAutopilotThrottle(), speed blended via tudursvehiclemod$blendCruiseSpeedToward()) rather than driving velocity directly at the target the way the original version did - an aircraft that gradually banks/turns toward the target direction and gradually accelerates/decelerates, never snapping instantly to a precise heading or speed. Speed itself is driven by a SIGNED along-track error (the target's own position projected onto the leader's own forward axis - positive when this wingman is lagging BEHIND where it should be, negative when it has overshot PAST it) added as an adjustment on top of the leader's own base speed - a wingman that's overshot slows below the leader's own pace to let the gap reopen naturally, one that's fallen behind speeds up to close it, and one that's exactly on target simply matches the leader's own speed. */
	private static final double DRONE_FORMATION_CATCH_UP_GAIN = 0.05;
	/** Maximum throttle-fraction adjustment (above or below the leader's own base fraction) the along-track error above is allowed to apply - caps how aggressively a wingman can outrun/undercut the leader's own speed while correcting position, keeping the correction gradual and aircraft-like rather than an abrupt full-throttle lunge or dead stop. */
	private static final float DRONE_FORMATION_MAX_SPEED_ADJUSTMENT = 0.35f;
	/** Oscillating (left-right swaying) approach and inefficient wide turns occurred when far from the target - see this whole feature's own doc for the full reasoning. Beyond this distance (blocks), desired heading is pure bearing-to-target (shortest path there); within it, blends progressively toward the leader's own current heading instead. */
	private static final double DRONE_FORMATION_HEADING_BLEND_DISTANCE = 20.0;

	private void tudursvehiclemod$updateDroneFormationFollow(VehicleDefinition def) {
		// Mirrors tudursvehiclemod$updateDroneAutopilot()'s own exact same handling, which this method never reused at all until now.
		this.setCanopyOpen(false);
		if (!this.isWingFoldOpen()) {
			this.tudursvehiclemod$forceWingFoldOpen(true);
		}
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		Entity leader = serverWorld.getEntity(this.droneFormationLeaderUuid);
		if (leader == null || leader.isRemoved()
				|| (leader instanceof AbstractVehicleEntity leaderVehicle && leaderVehicle.tudursvehiclemod$isDestroyed())) {
			this.tudursvehiclemod$clearDroneFormationFollow();
			return;
		}
		// Per updateDroneAutopilot()'s own identical gear-retraction reasoning: uses the LEADER's own current Y as the ground reference (this aircraft has no Drone Center block position of its own to compare against while formation-following) - the leader is already airborne by the time any wingman starts following it, so this is always a sensible reference.
		if (this.isGearDeployed() && this.getY() > leader.getY() + 2.0) {
			this.toggleLandingGear();
		}
		float effectiveTurnRate = this.getYawFollowRateDegrees();
		double leaderYawRad = Math.toRadians(leader.getYaw());
		double leaderForwardX = -Math.sin(leaderYawRad);
		double leaderForwardZ = Math.cos(leaderYawRad);
		double[] rotatedOffset = tudursvehiclemod$rotateCasOffset(this.droneFormationLateralOffset, this.droneFormationLongitudinalOffset, leaderForwardX, leaderForwardZ);
		double targetX = leader.getX() + rotatedOffset[0];
		double targetY = leader.getY();
		double targetZ = leader.getZ() + rotatedOffset[1];

		double dx = targetX - this.getX();
		double dz = targetZ - this.getZ();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		Vec3d leaderVelocity = leader.getVelocity();
		double leaderHorizontalSpeed = Math.sqrt(leaderVelocity.x * leaderVelocity.x + leaderVelocity.z * leaderVelocity.z);
		float baseTargetFraction = MathHelper.clamp((float) (leaderHorizontalSpeed / Math.max(1.0e-4f, def.maxSpeed())), 0.05f, 1.0f);

		// Per this whole method's own doc: signed, not the raw (always-positive) distance - a wingman ahead of where it should be needs to SLOW DOWN (negative adjustment), not speed up further just because it's still "far" from the target in absolute terms.
		double alongTrackError = dx * leaderForwardX + dz * leaderForwardZ;
		float speedAdjustment = MathHelper.clamp((float) (alongTrackError * DRONE_FORMATION_CATCH_UP_GAIN), -DRONE_FORMATION_MAX_SPEED_ADJUSTMENT, DRONE_FORMATION_MAX_SPEED_ADJUSTMENT);
		float targetThrottleFraction = MathHelper.clamp(baseTargetFraction + speedAdjustment, 0.05f, 1.0f);
		float cruiseSpeedTarget = this.tudursvehiclemod$rampAutopilotThrottle(def, targetThrottleFraction, def.maxSpeed());

		float currentTrackedYaw = this.tudursvehiclemod$droneYawForNavigation();
		float bearingToTarget = horizontalDistance > 1.0e-4 ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : currentTrackedYaw;
		// Per this whole method's own doc: full weight toward bearingToTarget (the shortest, most direct path) once farther than this distance; below it, blends progressively toward the leader's own current heading instead, avoiding the oscillation a raw bearing-to-a-nearby-point would otherwise produce.
		float headingBlendWeight = MathHelper.clamp((float) (horizontalDistance / DRONE_FORMATION_HEADING_BLEND_DISTANCE), 0.0f, 1.0f);
		float desiredYaw = leader.getYaw() + MathHelper.wrapDegrees(bearingToTarget - leader.getYaw()) * headingBlendWeight;
		// See tudursvehiclemod$computeCoordinatedTurn()'s own doc.
		float[] coordinatedTurn = this.tudursvehiclemod$computeCoordinatedTurn(currentTrackedYaw, this.droneOrbitTrackedRoll, desiredYaw, effectiveTurnRate, this.getPitchFollowRateDegrees());
		float newYaw = coordinatedTurn[0];
		float newRoll = coordinatedTurn[1];
		this.droneTrackedYaw = newYaw;
		this.droneOrbitTrackedRoll = newRoll;

		// Throttled (once per second) diagnostic comparing this wingman's own CONFIGURED offset against its ACTUAL relative position in the leader's own body frame, plus the leader's own route progress and this wingman's own current heading-blend weight.
		if (this.age % 20 == 0) {
			double actualDx = this.getX() - leader.getX();
			double actualDz = this.getZ() - leader.getZ();
			double actualLongitudinal = actualDx * leaderForwardX + actualDz * leaderForwardZ;
			double rightX = leaderForwardZ;
			double rightZ = -leaderForwardX;
			double actualLateral = actualDx * rightX + actualDz * rightZ;
		}

		double altitudeError = targetY - this.getY();
		double targetVerticalSpeed = MathHelper.clamp(altitudeError * DRONE_ORBIT_ALTITUDE_CORRECTION_GAIN,
				-cruiseSpeedTarget * 0.5, cruiseSpeedTarget * 0.5);

		Vec3d currentVelocity = this.getVelocity();
		double currentSpeedMagnitude = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
		double blendedSpeedMagnitude = this.tudursvehiclemod$blendCruiseSpeedToward(def, (float) currentSpeedMagnitude, cruiseSpeedTarget, this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION), def.maxSpeed());
		float verticalResponseBlend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
		double blendedVerticalSpeed = currentVelocity.y + (targetVerticalSpeed - currentVelocity.y) * verticalResponseBlend;

		double newYawRad = Math.toRadians(newYaw);
		Vec3d finalVelocity = new Vec3d(-Math.sin(newYawRad) * blendedSpeedMagnitude, blendedVerticalSpeed, Math.cos(newYawRad) * blendedSpeedMagnitude);
		double horizontalSpeed = blendedSpeedMagnitude;

		float newPitch = horizontalSpeed < 1.0e-4 ? this.getPitch()
				: (float) -Math.toDegrees(Math.atan2(finalVelocity.y, horizontalSpeed));

		this.setOrientationFromEuler(newYaw, newPitch, newRoll);
		this.orientation.normalize();
		super.setYaw(newYaw);
		super.setPitch(newPitch);
		this.dataTracker.set(SYNCED_YAW, newYaw);
		this.dataTracker.set(SYNCED_PITCH, newPitch);
		this.dataTracker.set(SYNCED_ROLL, newRoll);
		this.cruiseSpeed = (float) finalVelocity.length();
		setThrottleDirect(this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION));

		double preMoveSpeed = finalVelocity.length();
		this.setVelocity(finalVelocity);
		this.move(MovementType.SELF, this.getVelocity());
		this.tudursvehiclemod$checkBlockCrashDamage(preMoveSpeed, newPitch);
		this.tudursvehiclemod$checkEntityCrashDamage(preMoveSpeed);
	}


	// --- Carrier wingman-target-lock: pursuit/attack AI. ---

	/** How far (blocks, vertical - directly below) a nearby block must be found before obstacle avoidance takes over from target pursuit entirely, and how high (blocks) this wingman climbs while doing so. A block within range of the aircraft takes priority, moving away from it; starting value chosen pending actual in-game testing/tuning. See CARRIER_LOCK_FORWARD_OBSTACLE_RANGE's own doc for why the FORWARD check uses a separate, much larger value instead of this same one. */
	private static final double CARRIER_LOCK_OBSTACLE_AVOIDANCE_RANGE = 20.0;
	/** Unlike straight down (a roughly fixed proximity to react to), the distance directly ahead closes rapidly at speed, so a forward check needs meaningfully more lead time to actually be useful - checked at this range instead of CARRIER_LOCK_OBSTACLE_AVOIDANCE_RANGE's own, considerably shorter one. */
	private static final double CARRIER_LOCK_FORWARD_OBSTACLE_RANGE = 100.0;
	/** "Shaken off" uses the exact same distance-improvement margin as guided-missile lost-target detection (VehicleProjectileEntity's own LOST_TARGET_DISTANCE_MARGIN). */
	private static final double CARRIER_LOCK_DISTANCE_MARGIN = 0.05;
	/** How close (degrees, full 3D angle) the target must be to this wingman's own current facing before its own assigned weapon actually fires - mirrors DummyPilotEntity's own FIXED_WEAPON_HIT_TOLERANCE_DEGREES (the reference this whole feature's own attack behavior was requested to follow), applied uniformly here (both turret-tracked and fixed weapons alike, for simplicity) rather than that class's own more elaborate per-weapon-type split. */
	private static final float CARRIER_LOCK_FIRE_TOLERANCE_DEGREES = 15.0f;
	/** Fallback gravity for a falling weapon (BOMB/DEPTH) whose own Gravity isn't actually configured (0.0f, absent) - matches client.hud.MortarMarkerRenderer's own CAS_CARRIER_DEFAULT_GRAVITY, a reasonable default rather than leaving this release calculation degenerate (no meaningful fall arc at all) for an unconfigured weapon. */
	private static final float CARRIER_LOCK_FALLING_WEAPON_DEFAULT_GRAVITY = 32f;
	/** How close (blocks, horizontal only) this weapon's own predicted impact point must be to the target's own actual position before it actually releases - matches client.hud.MortarMarkerRenderer's own MARKER_RADIUS (the same in-world guide circle the player themselves sees for a manually-aimed shot), so this AI's own release timing is calibrated to the same real-world precision that guide already represents. */
	private static final double CARRIER_LOCK_FALLING_WEAPON_RELEASE_TOLERANCE = 5.0;
	/** Built-in default dive-target Y offset for a falling weapon (BOMB/DEPTH) attacking a ground/water target - a straight dive toward the target's own actual position doesn't reach a workable drop position at all, so this wingman instead aims for a point this much higher, giving room for a real (still-descending, but shallower) approach. Adjustable per Drone Center - see carrierLockDiveTargetYOffsetOverride's own doc. */
	private static final double CARRIER_LOCK_FALLING_WEAPON_DIVE_TARGET_Y_OFFSET = 20.0;
	/** tudursvehiclemod$classifyTargetPosition() misclassified stationary ground targets as AIRBORNE (relying on Entity's own isOnGround()/isTouchingWater(), unreliable for this mod's own vehicle entities) - how close (blocks) a target's own actual height above ground/water (tudursvehiclemod$altitudeAboveGroundOrWater(), measured directly) must be before it's treated as a ground/water target for this whole safe-altitude mechanism. Well above AIRBORNE_TARGET_MIN_ALTITUDE's own 3.0 - a real target vehicle's own entity-position anchor point may sit meaningfully above its own visual base/wheels, and a false-negative here (an actual ground target treated as airborne) is far more dangerous (this wingman diving straight at it) than a false-positive (an actually-low-flying aircraft treated as a ground target, which merely means it's engaged from a needlessly cautious altitude). */
	private static final double GROUND_OR_WATER_TARGET_ALTITUDE_THRESHOLD = 10.0;
	/** Horizontal-throttle fraction used during ordinary (non-suppressed) pursuit - the previous, unnamed 0.8f literal, now named so CARRIER_LOCK_SUPPRESSED_THROTTLE_FRACTION's own doc can meaningfully contrast against it. */
	private static final float CARRIER_LOCK_PURSUIT_THROTTLE_FRACTION = 0.8f;
	/** Suppressed movement (obstacle avoidance / climbing to a safe altitude) is a reasonably brisk, straight climb - overall speed while suppressed, split between horizontal/vertical by CARRIER_LOCK_CLIMB_ANGLE_DEGREES's own fixed angle (so this doesn't need to be as low as an "avoid wasting effort horizontally" value alone would suggest - the angle itself already handles that). */
	private static final float CARRIER_LOCK_SUPPRESSED_THROTTLE_FRACTION = 0.6f;
	/** Matching a real aircraft's own ordinary climb-out attitude. Drives the horizontal/vertical speed split directly (cos/sin of this angle against CARRIER_LOCK_SUPPRESSED_THROTTLE_FRACTION's own overall speed) whenever pursuit is suppressed, replacing the earlier gain-based altitudeError computation entirely for that case - unambiguous, and immune to whatever was causing that computation to not actually produce a clean climb. */
	private static final float CARRIER_LOCK_CLIMB_ANGLE_DEGREES = 45.0f;
	/** How far (blocks, both +X and +Z) the approach waypoint (see carrierLockApproachingWaypoint's own doc) sits from a ground/water target - a fixed offset direction rather than one dynamically computed from this wingman's own current position, per that same direct request. Deliberately generous - the whole point is giving this wingman enough distance to actually complete its own turn before committing to the dive. */
	private static final double CARRIER_LOCK_APPROACH_OFFSET = 200.0;
	/** How close (blocks, horizontal) to the approach waypoint counts as "arrived" - once within this radius, carrierLockApproachingWaypoint clears and the actual dive begins. Generous relative to a typical, tight "arrival radius" (e.g. a plain waypoint's own couple of blocks) since this is a fast-moving, wide-turn-radius aircraft approaching at CARRIER_LOCK_APPROACH_OFFSET's own considerable distance, not a slow final landing approach. */
	private static final double CARRIER_LOCK_WAYPOINT_ARRIVAL_RADIUS = 30.0;

	/** The safe-altitude mechanism (carrierLockClimbingToSafeAltitude and the below-obstacle check) uses the SAME ground/water-surface-relative altitude metric client.hud.HudVariables' own altitudeAboveGround() already uses for the HUD's own "altitude" display, rather than a metric relative to the target's own Y - the whole point is avoiding whatever is actually directly below THIS wingman right now, which the target's own Y doesn't reliably represent (the target could be on elevated terrain while this wingman happens to be over a valley nearby, or vice versa). Unlike tudursvehiclemod$raycastBlocksBetween()'s own FluidHandling.NONE (passes straight through water - correct for THAT method's own pure block-collision purpose), this uses SOURCE_ONLY so a water surface itself also counts, matching the water-collision concern alongside ground collision. Returns 256.0 (an effectively "very high, definitely safe" sentinel) if nothing solid or water is found within that range, matching HudVariables' own identical fallback. */
	private double tudursvehiclemod$altitudeAboveGroundOrWater(Vec3d position) {
		Vec3d end = position.add(0.0, -256.0, 0.0);
		net.minecraft.util.hit.HitResult hit = this.getEntityWorld().raycast(new net.minecraft.world.RaycastContext(
				position, end, net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.SOURCE_ONLY, this));
		if (hit instanceof net.minecraft.util.hit.BlockHitResult blockHit && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
			return Math.max(0.0, position.y - blockHit.getPos().y);
		}
		return 256.0;
	}


	/** A wingman with an active lock (carrierLockedTargetUuid != null) flies toward and attacks its own assigned target with its own assigned weapon (carrierLockedTargetWeaponIndex, captured at lock time from the lead's own then-selected weapon), instead of ordinary formation-follow. The target is effectively this wingman's own equivalent of a formation "leader" during the actual dive - pursuit is pure bearing-to-target (keeping it dead ahead), no distance-based blending away from it.
	 *
	 * <p>Release conditions: the lock clears (falling through to ordinary formation-follow on the very next tick) if the target is destroyed/removed, or "shaken off" - unable to close distance for CARRIER_LOCK_SHAKEN_OFF_TICKS (30 seconds) straight, using the same tracking logic as a guided missile's own lost-target mechanic (carrierLockedTargetBestDistance/carrierLockedTargetStagnantTicks). This tracking runs during genuine pursuit AND obstacle avoidance alike (a tracked target can use terrain to shake off a pursuing wingman this way) - only during the two self-directed repositioning phases below (climbing to a safe altitude, approaching the waypoint) is this wingman instead always treated as still tracking.
	 *
	 * <p>Obstacle avoidance: this wingman goes wings-level (double the normal roll rate) and climbs straight, no turning, whenever a block is found within CARRIER_LOCK_FORWARD_OBSTACLE_RANGE directly ahead or CARRIER_LOCK_OBSTACLE_AVOIDANCE_RANGE directly below (this ground/water-relative below check reuses tudursvehiclemod$altitudeAboveGroundOrWater() - see that method's own doc).
	 *
	 * <p>Ground/water target attack cycle: a target is classified ground/water (rather than airborne) by measuring its OWN height above ground/water directly (GROUND_OR_WATER_TARGET_ALTITUDE_THRESHOLD) - reusing tudursvehiclemod$altitudeAboveGroundOrWater() rather than AA_MISSILE/AT_MISSILE's own classifyTargetPosition() (unreliable for this mod's own vehicle entities). For such a target, this wingman's own assigned weapon defines two altitude thresholds (CasAttackStartAltitude, CasAttackStopAltitude - weapon-file-configurable, defaults 100/40) driving a three-phase cycle:
	 *
	 * <p>1) Approach (carrierLockApproachingWaypoint - entered on fresh assignment, and again every time a climb finishes): diving on the target immediately upon reaching CasAttackStartAltitude often meant the turn to actually face it never finished before this wingman had already descended past CasAttackStopAltitude (turning and descending happening simultaneously, with a turn radius too wide relative to the target's own distance) - this wingman instead flies toward a fixed waypoint (CARRIER_LOCK_APPROACH_OFFSET blocks in +X and +Z from the target, at CasAttackStartAltitude), freely turning to align with a genuine coordinated turn, not yet diving or firing. Clears once within CARRIER_LOCK_WAYPOINT_ARRIVAL_RADIUS of the waypoint.
	 *
	 * <p>2) Dive (neither approaching nor climbing): pursuit dives genuinely toward the target's own actual position (altitudeError = targetPos.y - selfPos.y, exactly like an airborne target) - CasAttackStartAltitude is where the dive BEGINS, not an altitude to converge to and hold. Firing happens here, once aligned within CARRIER_LOCK_FIRE_TOLERANCE_DEGREES.
	 *
	 * <p>3) Abort/climb (carrierLockClimbingToSafeAltitude - entered once height above ground drops below CasAttackStopAltitude): same wings-level, no-turn climb as obstacle avoidance, straight at CARRIER_LOCK_CLIMB_ANGLE_DEGREES (45, a fixed-angle velocity split, not gain-derived) until back at or above CasAttackStartAltitude, at which point phase 1 (approach) begins again - the dive is never resumed directly from a climb.
	 *
	 * <p>For an airborne target, none of the above three-phase cycle applies - pursuit always dives directly at the target's own current position with no approach/climb phases at all. */
	private void tudursvehiclemod$updateCarrierLockPursuit(VehicleDefinition def) {
		this.setCanopyOpen(false);
		if (!this.isWingFoldOpen()) {
			this.tudursvehiclemod$forceWingFoldOpen(true);
		}
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		Entity target = serverWorld.getEntity(this.carrierLockedTargetUuid);
		boolean targetGone = target == null || target.isRemoved()
				|| (target instanceof AbstractVehicleEntity targetVehicle && targetVehicle.tudursvehiclemod$isDestroyed())
				|| (target instanceof LivingEntity targetLiving && !targetLiving.isAlive());
		if (targetGone) {
			this.carrierLockedTargetUuid = null;
			return;
		}
		// Relying on Entity's own isOnGround()/isTouchingWater(), already documented elsewhere in this project as unreliable for this mod's own vehicle entities - was misclassifying stationary ground targets as AIRBORNE, letting wingmen dive straight at their raw position with this whole safety mechanism never engaging at all. Measures directly instead, reusing the SAME tudursvehiclemod$altitudeAboveGroundOrWater() helper already proven correct for the wingman's own altitude (see that method's own doc), applied to the TARGET's own position here. GROUND_OR_WATER_TARGET_ALTITUDE_THRESHOLD (well above AIRBORNE_TARGET_MIN_ALTITUDE's own 3.0) accounts for a real target vehicle's own entity-position anchor point potentially sitting meaningfully above its own visual base/wheels.
		Vec3d targetPos = target.getEntityPos();
		boolean groundOrWaterTarget = this.tudursvehiclemod$altitudeAboveGroundOrWater(targetPos) < GROUND_OR_WATER_TARGET_ALTITUDE_THRESHOLD;
		// Reused for both that hysteresis and the fire-alignment lookup further below.
		com.example.tudursvehiclemod.asset.WeaponDefinition attackWeapon = def.weapons().get(
				MathHelper.clamp(this.carrierLockedTargetWeaponIndex, 0, Math.max(0, def.weapons().size() - 1)));
		double attackStartAltitude = this.carrierLockAttackStartAltitudeOverride != null
				? this.carrierLockAttackStartAltitudeOverride : attackWeapon.casAttackStartAltitude();
		double attackStopAltitude = this.carrierLockAttackStopAltitudeOverride != null
				? this.carrierLockAttackStopAltitudeOverride : attackWeapon.casAttackStopAltitude();

		Vec3d selfPos = this.getEntityPos();
		// Rather than one relative to the target's own (possibly elevated, or on different terrain entirely) Y - see tudursvehiclemod$altitudeAboveGroundOrWater()'s own doc. Computed once here, reused for the hysteresis update below AND the below-obstacle check further down.
		double heightAboveGround = groundOrWaterTarget ? this.tudursvehiclemod$altitudeAboveGroundOrWater(selfPos) : 256.0;

		// Per carrierLockApproachingWaypoint's own doc: captured BEFORE the hysteresis update below actually runs, so it reflects this tick's own STARTING state - compared against the just-updated state afterward to detect a climb that just finished (a dive that got aborted and has now climbed back up needs a fresh approach before diving again).
		boolean wasClimbing = this.carrierLockClimbingToSafeAltitude;

		// See carrierLockClimbingToSafeAltitude's own doc for the full reasoning. Updated FIRST (before anything else uses it below), from the CURRENT actual ground/water-relative altitude, so the rest of this tick's own steering reacts to the latest state.
		if (groundOrWaterTarget) {
			if (this.carrierLockClimbingToSafeAltitude) {
				if (heightAboveGround >= attackStartAltitude) {
					this.carrierLockClimbingToSafeAltitude = false;
				}
			} else if (heightAboveGround < attackStopAltitude) {
				this.carrierLockClimbingToSafeAltitude = true;
			}
		} else {
			this.carrierLockClimbingToSafeAltitude = false;
		}

		if (this.isGearDeployed() && this.getY() > target.getY() + 2.0) {
			this.toggleLandingGear();
		}

		// Per carrierLockApproachingWaypoint's own doc: a fixed offset (not dynamically computed from this wingman's own current position) - see CARRIER_LOCK_APPROACH_OFFSET's own doc. Y uses the target's own current height plus CasAttackStartAltitude as a ground-level approximation, same simplification the dive's own altitude target already relies on.
		Vec3d approachWaypointPos = new Vec3d(targetPos.x + CARRIER_LOCK_APPROACH_OFFSET, targetPos.y + attackStartAltitude, targetPos.z + CARRIER_LOCK_APPROACH_OFFSET);

		double dx = targetPos.x - selfPos.x;
		double dz = targetPos.z - selfPos.z;
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		float effectiveTurnRate = this.getYawFollowRateDegrees();
		float currentTrackedYaw = this.tudursvehiclemod$droneYawForNavigation();
		float bearingToTarget = horizontalDistance > 1.0e-4 ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : currentTrackedYaw;

		// Obstacle avoidance - checked BEFORE committing to pursuit steering, per this method's own doc. The below-obstacle check now reuses the SAME ground/water-relative altitude computed above for a non-airborne target (rather than a separate raycastBlocksBetween() call that, per tudursvehiclemod$altitudeAboveGroundOrWater()'s own doc, wouldn't have caught a water surface at all) - for an airborne target (heightAboveGround left at the 256.0 sentinel above), this simply never trips, matching that case never needing a below-check at all.
		double currentYawRad = Math.toRadians(currentTrackedYaw);
		Vec3d forwardDir = new Vec3d(-Math.sin(currentYawRad), 0.0, Math.cos(currentYawRad));
		boolean obstacleAhead = tudursvehiclemod$raycastBlocksBetween(selfPos,
				selfPos.add(forwardDir.multiply(CARRIER_LOCK_FORWARD_OBSTACLE_RANGE)), this) != null;
		boolean obstacleBelow = heightAboveGround < CARRIER_LOCK_OBSTACLE_AVOIDANCE_RANGE;
		boolean avoidingObstacle = obstacleAhead || obstacleBelow;
		// Per this method's own doc: this wingman goes wings-level and climbs straight (no turning) for either reason - a detected obstacle, or not yet having climbed back to a safe altitude above a ground/water target.
		boolean holdHeadingAndClimb = avoidingObstacle || this.carrierLockClimbingToSafeAltitude;

		// Per carrierLockApproachingWaypoint's own doc: entered on fresh assignment or whenever a climb just finished (wasClimbing was true, now false) - re-checked/cleared here, once this wingman has actually arrived within CARRIER_LOCK_WAYPOINT_ARRIVAL_RADIUS of the waypoint. Never meaningful while obstacle-avoiding/climbing (those already take full priority via holdHeadingAndClimb above) or for an airborne target (groundOrWaterTarget's own hysteresis update already keeps this false in that case).
		if (groundOrWaterTarget && wasClimbing && !this.carrierLockClimbingToSafeAltitude) {
			this.carrierLockApproachingWaypoint = true;
		}
		if (this.carrierLockApproachingWaypoint && !holdHeadingAndClimb) {
			double waypointDx = approachWaypointPos.x - selfPos.x;
			double waypointDz = approachWaypointPos.z - selfPos.z;
			double waypointHorizontalDistance = Math.sqrt(waypointDx * waypointDx + waypointDz * waypointDz);
			if (waypointHorizontalDistance < CARRIER_LOCK_WAYPOINT_ARRIVAL_RADIUS) {
				this.carrierLockApproachingWaypoint = false;
			}
		}
		boolean approachingWaypointNow = this.carrierLockApproachingWaypoint && !holdHeadingAndClimb;
		// Per this method's own doc: firing (and the actual dive itself) is additionally suppressed while still approaching the waypoint, on top of the obstacle-avoidance/climb case above.
		boolean suppressFiring = holdHeadingAndClimb || approachingWaypointNow;

		// Obstacle avoidance itself DOES count toward the shaken-off clock (giving a tracked target the ability to genuinely use terrain to shake off a pursuing wingman by forcing it to react to obstacles instead of closing distance) - climbing to a safe altitude AND approaching the waypoint (both self-directed repositioning phases entirely unrelated to the target's own movement, not something a target could tactically induce) are excluded, and this wingman is always treated as still tracking during either.
		if (!this.carrierLockClimbingToSafeAltitude && !approachingWaypointNow) {
			// Per this method's own doc: a ground/water target's own tracked "distance" (for shaken-off purposes) is horizontal-only - the wingman deliberately maintains a roughly constant vertical offset above it rather than closing the full 3D distance, which would otherwise never "improve" and falsely trip the shaken-off timeout for a target this wingman is actually tracking perfectly well.
			double trackedDistance = groundOrWaterTarget ? horizontalDistance : selfPos.distanceTo(targetPos);
			if (trackedDistance < this.carrierLockedTargetBestDistance - CARRIER_LOCK_DISTANCE_MARGIN) {
				this.carrierLockedTargetBestDistance = trackedDistance;
				this.carrierLockedTargetStagnantTicks = 0;
			} else {
				this.carrierLockedTargetStagnantTicks++;
				if (this.carrierLockedTargetStagnantTicks >= CARRIER_LOCK_SHAKEN_OFF_TICKS) {
					this.carrierLockedTargetUuid = null;
					return;
				}
			}
		}

		float newYaw;
		float newRoll;
		double horizontalSpeed;
		double blendedVerticalSpeed;
		float targetThrottleFraction;
		if (holdHeadingAndClimb) {
			// Yaw is held EXACTLY (no turn is intended here at all), and roll levels out at DOUBLE the normal coordinated-turn rate, so this wingman goes wings-level quickly and climbs cleanly, rather than gradually un-banking over many ticks while also trying to pitch up.
			newYaw = currentTrackedYaw;
			newRoll = this.droneOrbitTrackedRoll + MathHelper.clamp(0f - this.droneOrbitTrackedRoll, -ROLL_RATE_DEGREES_PER_TICK * 2f, ROLL_RATE_DEGREES_PER_TICK * 2f);
			this.droneTrackedYaw = newYaw;
			this.droneOrbitTrackedRoll = newRoll;

			// A real aircraft simply flying straight at roughly CARRIER_LOCK_CLIMB_ANGLE_DEGREES (45) climb angle is entirely sufficient. Computes the target horizontal/vertical speed split DIRECTLY from that fixed angle - unambiguous - rather than a gain-based computation, blended smoothly toward (not snapped instantly) via the same blend mechanisms ordinary pursuit already uses.
			targetThrottleFraction = CARRIER_LOCK_SUPPRESSED_THROTTLE_FRACTION;
			float suppressedCruiseSpeedTarget = this.tudursvehiclemod$rampAutopilotThrottle(def, targetThrottleFraction, def.maxSpeed());
			double climbAngleRad = Math.toRadians(CARRIER_LOCK_CLIMB_ANGLE_DEGREES);
			double targetHorizontalSpeed = suppressedCruiseSpeedTarget * Math.cos(climbAngleRad);
			double targetClimbVerticalSpeed = suppressedCruiseSpeedTarget * Math.sin(climbAngleRad);

			Vec3d currentVelocity = this.getVelocity();
			double currentHorizontalSpeedMagnitude = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
			horizontalSpeed = this.tudursvehiclemod$blendCruiseSpeedToward(def, (float) currentHorizontalSpeedMagnitude, (float) targetHorizontalSpeed, targetThrottleFraction, def.maxSpeed());
			float verticalResponseBlend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
			blendedVerticalSpeed = currentVelocity.y + (targetClimbVerticalSpeed - currentVelocity.y) * verticalResponseBlend;
		} else if (approachingWaypointNow) {
			// Reaching CasAttackStartAltitude and diving on the target immediately meant turning to actually face it and descending happened simultaneously - the turn itself often never finished before this wingman had already descended to CasAttackStopAltitude, aborting without ever having been aligned to fire. This phase flies toward approachWaypointPos instead (a fixed point offset from the target, at attack-start altitude), freely turning (a genuine coordinated turn, same as active pursuit) to get properly aligned BEFORE the dive itself ever begins.
			double waypointDx = approachWaypointPos.x - selfPos.x;
			double waypointDz = approachWaypointPos.z - selfPos.z;
			double waypointHorizontalDistance = Math.sqrt(waypointDx * waypointDx + waypointDz * waypointDz);
			float bearingToWaypoint = waypointHorizontalDistance > 1.0e-4 ? (float) Math.toDegrees(Math.atan2(-waypointDx, waypointDz)) : currentTrackedYaw;
			float[] coordinatedTurn = this.tudursvehiclemod$computeCoordinatedTurn(currentTrackedYaw, this.droneOrbitTrackedRoll, bearingToWaypoint, effectiveTurnRate, this.getPitchFollowRateDegrees());
			newYaw = coordinatedTurn[0];
			newRoll = coordinatedTurn[1];
			this.droneTrackedYaw = newYaw;
			this.droneOrbitTrackedRoll = newRoll;

			targetThrottleFraction = CARRIER_LOCK_PURSUIT_THROTTLE_FRACTION;
			float cruiseSpeedTarget = this.tudursvehiclemod$rampAutopilotThrottle(def, targetThrottleFraction, def.maxSpeed());
			double altitudeError = approachWaypointPos.y - selfPos.y;
			double targetVerticalSpeed = MathHelper.clamp(altitudeError * DRONE_ORBIT_ALTITUDE_CORRECTION_GAIN, -cruiseSpeedTarget * 0.5, cruiseSpeedTarget * 0.5);

			Vec3d currentVelocity = this.getVelocity();
			double currentSpeedMagnitude = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
			horizontalSpeed = this.tudursvehiclemod$blendCruiseSpeedToward(def, (float) currentSpeedMagnitude, cruiseSpeedTarget, targetThrottleFraction, def.maxSpeed());
			float verticalResponseBlend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
			blendedVerticalSpeed = currentVelocity.y + (targetVerticalSpeed - currentVelocity.y) * verticalResponseBlend;
		} else {
			// "Flying toward the target = keeping the target dead ahead" IS the intended behavior here (the target is effectively this wingman's own equivalent of a formation "leader" it homes in on) - pure bearing-to-target, no distance-based blending away from it.
			// See tudursvehiclemod$computeCoordinatedTurn()'s own doc.
			float[] coordinatedTurn = this.tudursvehiclemod$computeCoordinatedTurn(currentTrackedYaw, this.droneOrbitTrackedRoll, bearingToTarget, effectiveTurnRate, this.getPitchFollowRateDegrees());
			newYaw = coordinatedTurn[0];
			newRoll = coordinatedTurn[1];
			this.droneTrackedYaw = newYaw;
			this.droneOrbitTrackedRoll = newRoll;

			targetThrottleFraction = CARRIER_LOCK_PURSUIT_THROTTLE_FRACTION;
			float cruiseSpeedTarget = this.tudursvehiclemod$rampAutopilotThrottle(def, targetThrottleFraction, def.maxSpeed());
			// The dive-target Y itself is now targetPos.y PLUS a configurable offset (see CARRIER_LOCK_FALLING_WEAPON_DIVE_TARGET_Y_OFFSET's own doc for the default/override resolution) - a falling weapon (BOMB/DEPTH) attacking a ground/water target defaults to +20 (adjustable per Drone Center - carrierLockDiveTargetYOffsetOverride's own doc), every other case defaults to 0 (also adjustable, per the same direct request - "オフセット対象外の武器タイプの場合はデフォルト値を0としつつも調整可能"). The dive-toward-target STEERING itself (bearingToTarget just above) is otherwise completely unchanged from its own already-reverted behavior - this only shifts WHERE vertically the dive aims to end up.
			boolean fallingWeaponForDiveOffset = groundOrWaterTarget
					&& (attackWeapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.BOMB
							|| attackWeapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.DEPTH);
			double diveTargetYOffset = this.carrierLockDiveTargetYOffsetOverride != null
					? this.carrierLockDiveTargetYOffsetOverride : 0.0;
			// Per carrierLockDiveTargetYOffsetOverride's own doc: 0.0 (this whole feature's own shared "still at default, never explicitly configured" convention) means fall back to the built-in default for this weapon's own actual type, rather than genuinely meaning "no offset at all" - a Drone Center operator who deliberately wants exactly 0 for a falling weapon is an accepted, rare edge case this convention doesn't distinguish from "never touched it".
			if (diveTargetYOffset == 0.0 && fallingWeaponForDiveOffset) {
				diveTargetYOffset = CARRIER_LOCK_FALLING_WEAPON_DIVE_TARGET_Y_OFFSET;
			}
			// Reverted back to the original genuine dive toward the target's own actual position for EVERY weapon type - the release-point-based fire condition (see this method's own fire-condition branch further below) is kept exactly as it was, unaffected by this revert. attackStartAltitude is where the dive BEGINS, not converged/held to - between attackStartAltitude and attackStopAltitude, this wingman keeps descending toward the target's own actual position (a real attack run, firing along the way, now offset by diveTargetYOffset above), exactly like the airborne case below. carrierLockClimbingToSafeAltitude (triggered once height above ground actually drops below attackStopAltitude) is what limits how far this dive is allowed to continue, aborting back up to attackStartAltitude - not this altitude target itself.
			double altitudeError = (targetPos.y + diveTargetYOffset) - selfPos.y;
			double targetVerticalSpeed = MathHelper.clamp(altitudeError * DRONE_ORBIT_ALTITUDE_CORRECTION_GAIN, -cruiseSpeedTarget * 0.5, cruiseSpeedTarget * 0.5);

			Vec3d currentVelocity = this.getVelocity();
			double currentSpeedMagnitude = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
			horizontalSpeed = this.tudursvehiclemod$blendCruiseSpeedToward(def, (float) currentSpeedMagnitude, cruiseSpeedTarget, targetThrottleFraction, def.maxSpeed());
			float verticalResponseBlend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
			blendedVerticalSpeed = currentVelocity.y + (targetVerticalSpeed - currentVelocity.y) * verticalResponseBlend;
		}

		double newYawRad = Math.toRadians(newYaw);
		Vec3d finalVelocity = new Vec3d(-Math.sin(newYawRad) * horizontalSpeed, blendedVerticalSpeed, Math.cos(newYawRad) * horizontalSpeed);

		float newPitch = horizontalSpeed < 1.0e-4 ? this.getPitch()
				: (float) -Math.toDegrees(Math.atan2(finalVelocity.y, horizontalSpeed));

		this.setOrientationFromEuler(newYaw, newPitch, newRoll);
		this.orientation.normalize();
		super.setYaw(newYaw);
		super.setPitch(newPitch);
		this.dataTracker.set(SYNCED_YAW, newYaw);
		this.dataTracker.set(SYNCED_PITCH, newPitch);
		this.dataTracker.set(SYNCED_ROLL, newRoll);
		this.cruiseSpeed = (float) finalVelocity.length();
		setThrottleDirect(targetThrottleFraction);

		double preMoveSpeed = finalVelocity.length();
		this.setVelocity(finalVelocity);
		this.move(MovementType.SELF, this.getVelocity());
		this.tudursvehiclemod$checkBlockCrashDamage(preMoveSpeed, newPitch);
		this.tudursvehiclemod$checkEntityCrashDamage(preMoveSpeed);

		// Only fires (tryFireWeapon with shooter=null, matching that class's own exact same entry point/rate-limiting) once the target is genuinely within CARRIER_LOCK_FIRE_TOLERANCE_DEGREES of this wingman's own current actual facing, rather than the instant a lock exists at all - and never while suppressed (obstacle avoidance, climbing to a safe altitude, OR still approaching the waypoint - there's no point wasting ammo firing while not actually diving on the target). EXCEPT a falling weapon (BOMB/DEPTH) attacking a ground/water target - uses a release-point calculation instead of an aim angle at all (see the branch below).
		boolean fallingWeaponForFiring = groundOrWaterTarget
				&& (attackWeapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.BOMB
						|| attackWeapon.weaponType() == com.example.tudursvehiclemod.asset.WeaponType.DEPTH);
		if (!suppressFiring && fallingWeaponForFiring) {
			// Computes where this weapon would actually land if released RIGHT NOW - this wingman's own current muzzle position/velocity, this weapon's own gravity, the SAME drag-aware "same-height-return" ballistic model client.hud.MortarMarkerRenderer's own HUD guide already uses for a player-aimed shot ("HUDのガイド表示で既に判定方法あり") - and fires the instant that predicted point is horizontally within FALLING_WEAPON_RELEASE_TOLERANCE of the target's own actual position, rather than at any particular aim angle at all (a falling weapon's own impact point is controlled by WHEN it releases while flying level, not by pointing at the target).
			Vec3d muzzlePos = this.tudursvehiclemod$getWeaponMuzzleWorldPos(attackWeapon);
			float fallingGravity = attackWeapon.gravity() > 0f ? attackWeapon.gravity() : CARRIER_LOCK_FALLING_WEAPON_DEFAULT_GRAVITY;
			Vec3d predictedImpact = this.tudursvehiclemod$simulateFallingWeaponImpact(muzzlePos, this.getVelocity(), fallingGravity, targetPos.y);
			double impactDx = predictedImpact.x - targetPos.x;
			double impactDz = predictedImpact.z - targetPos.z;
			double impactHorizontalDistance = Math.sqrt(impactDx * impactDx + impactDz * impactDz);
			if (impactHorizontalDistance <= CARRIER_LOCK_FALLING_WEAPON_RELEASE_TOLERANCE
					&& this.carrierLockedTargetWeaponIndex >= 0 && this.carrierLockedTargetWeaponIndex < def.weapons().size()) {
				this.tryFireWeapon(this.carrierLockedTargetWeaponIndex, null);
			}
		} else if (!suppressFiring) {
			Vec3d toTargetForAim = targetPos.add(0.0, target instanceof LivingEntity livingTarget ? livingTarget.getStandingEyeHeight() : 0.0, 0.0)
					.subtract(this.tudursvehiclemod$getWeaponMuzzleWorldPos(attackWeapon));
			if (toTargetForAim.lengthSquared() > 1.0e-6) {
				Vec3d vehicleForward = this.getRotationVector();
				double cosAngle = vehicleForward.normalize().dotProduct(toTargetForAim.normalize());
				double angleDegrees = Math.toDegrees(Math.acos(MathHelper.clamp(cosAngle, -1.0, 1.0)));
				if (angleDegrees <= CARRIER_LOCK_FIRE_TOLERANCE_DEGREES
						&& this.carrierLockedTargetWeaponIndex >= 0 && this.carrierLockedTargetWeaponIndex < def.weapons().size()) {
					this.tryFireWeapon(this.carrierLockedTargetWeaponIndex, null);
				}
			}
		}
	}

	/** A falling weapon (BOMB/DEPTH) releases at the point where it would actually hit the target, using the SAME calculation already established for the player-facing HUD guide (see client.hud.MortarMarkerRenderer's own PROJECTILE_AIR_DRAG_PER_TICK doc for why this exact drag factor matters): simulates this weapon's own drag-aware ballistic fall from origin/initialVelocity under gravity, stepping forward one tick at a time (0.99x/tick air drag on every velocity component, matching the actual real projectile's own vanilla ThrownItemEntity physics) until height reaches targetY, then returns the horizontal position at that exact point (interpolated within the final tick, not snapped to a whole-tick position, for a precise result) - this method has no block-collision awareness at all (a pure "falls to this specific Y" calculation, not "falls until it hits something"), which is exactly right here since the caller already knows the target's own actual Y directly, unlike the HUD's own version (which has to raycast for the first block hit instead, since the player could be aiming at anything). Falls back to the origin's own horizontal position at targetY (no meaningful travel at all) if MAX_FALLING_WEAPON_SIMULATION_TICKS is somehow exceeded (a needlessly high initial altitude combined with near-zero fall speed) rather than returning null/throwing - this is an AI release-timing decision, not a player-facing display, so a degenerate "release now" fallback is safer than leaving the caller with nothing to act on at all. */
	private static final int MAX_FALLING_WEAPON_SIMULATION_TICKS = 3000;

	/** Vanilla's own ThrownItemEntity air drag per tick - the ACTUAL projectile (VehicleProjectileEntity extends ThrownItemEntity) decelerates by this factor every tick. Matches client.hud.MortarMarkerRenderer's own identical PROJECTILE_AIR_DRAG_PER_TICK constant exactly (same physical reality, just duplicated here rather than referenced directly - that class lives in the client-only package and can't be referenced from this server-side class at all, since a dedicated server has no client classes loaded). */
	private static final double FALLING_WEAPON_AIR_DRAG_PER_TICK = 0.99;

	private Vec3d tudursvehiclemod$simulateFallingWeaponImpact(Vec3d origin, Vec3d initialVelocity, float gravity, double targetY) {
		double stepVelocityX = initialVelocity.x;
		double stepVelocityY = initialVelocity.y;
		double stepVelocityZ = initialVelocity.z;
		double x = origin.x;
		double y = origin.y;
		double z = origin.z;
		for (int tick = 0; tick < MAX_FALLING_WEAPON_SIMULATION_TICKS; tick++) {
			stepVelocityX *= FALLING_WEAPON_AIR_DRAG_PER_TICK;
			stepVelocityZ *= FALLING_WEAPON_AIR_DRAG_PER_TICK;
			stepVelocityY = stepVelocityY * FALLING_WEAPON_AIR_DRAG_PER_TICK - gravity;
			double nextY = y + stepVelocityY;
			if (nextY <= targetY) {
				double fraction = (y - targetY) / Math.max(y - nextY, 1.0e-6);
				return new Vec3d(x + stepVelocityX * fraction, targetY, z + stepVelocityZ * fraction);
			}
			x += stepVelocityX;
			z += stepVelocityZ;
			y = nextY;
		}
		return new Vec3d(x, targetY, z);
	}


	/** Flies through this landing attempt's own via-point (if configured and not yet reached) and then the Home Point, mirroring Carrier's own proven intermediate-landing-waypoint leg (tudursvehiclemod$updateCarrierReturnToBase()'s own carrierLandingWaypointIndex < lastLandingWaypointIndex branch) as closely as possible, steady, throttle-ramped cruise speed (tudursvehiclemod$rampAutopilotThrottle(), the same mechanism Carrier's own landing leg uses), a direct (unfiltered) bearing recomputed fresh every tick with no turn-rate limiting at all (exactly like Carrier's own lwNewYaw), and a forced setPosition() update bypassing collision entirely (exactly like Carrier's own final setPosition() call) - not tudursvehiclemod$updateDroneWaypointAutopilot()'s own banked-turn/along-track design, which is built for continuous patrol/CAS/Carrier routes rather than a landing. Arrival uses a genuine 3D-distance check (Math.max(2.0, cruiseSpeed * 1.5 + 1.0), the exact same formula Carrier's own landing leg uses) rather than an along-track "passed the plane" projection - the latter only guarantees crossing a plane perpendicular to the approach, not actual proximity, which let this vehicle land in a different spot every time depending on the approach angle/speed. "Landed" is proximity-based, NOT isOnGround() (which never reliably triggered). Fully releases the drone link once landed. */
	private void tudursvehiclemod$updateDroneLandingRouteAutopilot(VehicleDefinition def, net.minecraft.util.math.BlockPos center) {
		boolean targetingViaPoint = this.droneLandingViaPoint != null && !this.droneLandingReachedViaPoint;
		com.example.tudursvehiclemod.block.DroneWaypoint currentLegTarget = targetingViaPoint
				? this.droneLandingViaPoint
				: (this.droneLandingHomePoint != null ? this.droneLandingHomePoint : com.example.tudursvehiclemod.block.DroneWaypoint.createDefault());
		double targetX = center.getX() + 0.5 + currentLegTarget.relX();
		double targetY = center.getY() + currentLegTarget.relY();
		double targetZ = center.getZ() + 0.5 + currentLegTarget.relZ();

		double dx = targetX - this.getX();
		double dy = targetY - this.getY();
		double dz = targetZ - this.getZ();
		double distance3D = Math.sqrt(dx * dx + dy * dy + dz * dz);

		// Per this method's own doc: the exact same speed-scaled arrival radius Carrier's own landing-waypoint leg uses - large enough to always be reachable regardless of this vehicle's own current speed, small enough to still mean "actually there" rather than "somewhere in the general vicinity".
		double arrivalRadius = Math.max(2.0, this.cruiseSpeed * 1.5 + 1.0);
		if (distance3D < arrivalRadius) {
			if (targetingViaPoint) {
				this.droneLandingReachedViaPoint = true;
				// Deploys the landing gear the instant this via point is actually reached, well ahead of the final approach to the home point itself.
				if (!this.isGearDeployed()) {
					this.toggleLandingGear();
				}
			} else {
				this.tudursvehiclemod$completeDroneLanding();
			}
			return;
		}

		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
		// Per this method's own doc: steady, throttle-ramped cruise speed (no distance-scaled deceleration at all until actually within arrivalRadius above) - the same mechanism Carrier's own landing leg uses.
		float cruiseSpeedTarget = this.tudursvehiclemod$rampAutopilotThrottle(def, Math.max(currentLegTarget.speedFraction(), 0.1f), this.tudursvehiclemod$getEffectiveMaxSpeed());
		this.cruiseSpeed += (cruiseSpeedTarget - this.cruiseSpeed) * CARRIER_LANDING_SPEED_BLEND;
		if (Math.abs(cruiseSpeedTarget - this.cruiseSpeed) < 1.0e-4f) {
			this.cruiseSpeed = cruiseSpeedTarget;
		}

		// Per this method's own doc: direction is the full 3D vector straight at the target (exactly like Carrier's own lwDirection) - naturally producing a glide-slope-like descent (pitch converges toward level as horizontal distance shrinks relative to altitude difference) without any separate vertical-speed computation of its own, and avoiding the turn-rate-limited yaw chase that caused this vehicle to circle instead of converging in an earlier version of this whole feature.
		Vec3d direction = distance3D > 1.0e-4 ? new Vec3d(dx, dy, dz).normalize() : new Vec3d(0, 0, 1);
		Vec3d finalVelocity = direction.multiply(this.cruiseSpeed);
		float newYaw = horizontalDistance > 1.0e-4 ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : this.getYaw();
		float newPitch = horizontalDistance > 1.0e-4
				? (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance))
				: this.getPitch();
		this.droneTrackedYaw = newYaw;
		// Per this method's own doc: levels out (roll toward 0) throughout the whole approach/landing, for a safe touchdown attitude - matching Carrier's own landing leg, which hardcodes roll to 0 outright.
		float currentTrackedRoll = this.tudursvehiclemod$droneRollForRendering();
		float newRoll = currentTrackedRoll + (0f - currentTrackedRoll) * ROTATION_SMOOTHING;
		this.droneTrackedRoll = newRoll;

		this.setOrientationFromEuler(newYaw, newPitch, newRoll);
		this.orientation.normalize();
		super.setYaw(newYaw);
		super.setPitch(newPitch);
		this.dataTracker.set(SYNCED_YAW, newYaw);
		this.dataTracker.set(SYNCED_PITCH, newPitch);
		this.dataTracker.set(SYNCED_ROLL, newRoll);
		this.cruiseSpeed = (float) finalVelocity.length();
		setThrottleDirect(this.dataTracker.get(CAS_WAYPOINT_SPEED_FRACTION));

		// Per this method's own doc: forced position update bypassing collision entirely - exactly like Carrier's own landing-waypoint leg, so this vehicle actually reaches where this autopilot intends every tick regardless of any physics interference.
		this.setVelocity(finalVelocity);
		this.setPosition(this.getX() + finalVelocity.x, this.getY() + finalVelocity.y, this.getZ() + finalVelocity.z);
	}

	/** Getter for droneLandingCenter (see that field's own doc) - protected so VtolEntity's own landing override (which needs the exact same target position but reaches it via a completely different, VTOL-specific routine) can read it directly. */
	protected net.minecraft.util.math.BlockPos tudursvehiclemod$getDroneLandingCenter() {
		return this.droneLandingCenter;
	}

	/** Extracted from this method's own former inline completion block (see droneLandingCenter's own doc) so VtolEntity's own landing override can reuse the exact same completion - the vehicle becomes an ordinary unpiloted vehicle from this point on, same as if a player had simply landed and dismounted. */
	protected void tudursvehiclemod$completeDroneLanding() {
		this.droneLandingCenter = null;
		this.droneLandingHomePoint = null;
		this.droneLandingViaPoint = null;
		this.droneTrackedYawInitialized = false;
		this.droneTrackedRollInitialized = false;
		this.droneRollTransitionTarget = Float.NaN;
		this.droneLegStartX = Double.NaN;
		// Already deployed in the normal case (gear only ever retracts once genuinely airborne during the original autopilot), but guards anyway in case landing was requested at an unusual moment.
		if (!this.isGearDeployed()) {
			this.toggleLandingGear();
		}
		this.tudursvehiclemod$setDroneLink(null);
	}

	/** Roll (bank angle) magnitude at touchdown within this range counts as a safe landing attitude - no attitude-specific damage at all (speed-based crash damage from checkBlockCrashDamage()/checkEntityCrashDamage() still applies independently, regardless of roll). */
	private static final float LANDING_ATTITUDE_SAFE_DEGREES = 20f;
	/** Roll magnitude at touchdown up to this many degrees: damaged, but survives and levels back out. Beyond this: destroyed outright, keeping whatever roll it had at touchdown. */
	private static final float LANDING_ATTITUDE_DESTROY_DEGREES = 45f;
	/** Fixed damage amount for a moderate-bank (20-45 degree) landing - a meaningful but non-fatal hit for most aircraft, on top of (not replacing) any separate speed-based crash damage. */
	private static final float LANDING_ATTITUDE_DAMAGE_AMOUNT = 15f;

	/** At the moment of touchdown, this aircraft's own roll (bank) angle determines the landing's own consequences - see the constants just above for the exact thresholds. */
	@Override
	protected void tudursvehiclemod$onJustLanded() {
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld) || this.tudursvehiclemod$isDestroyed()) {
			return;
		}
		float absRoll = Math.abs(MathHelper.wrapDegrees(this.getRoll()));
		if (absRoll <= LANDING_ATTITUDE_SAFE_DEGREES) {
			return;
		}
		if (absRoll <= LANDING_ATTITUDE_DESTROY_DEGREES) {
			this.damage(serverWorld, this.getDamageSources().flyIntoWall(), LANDING_ATTITUDE_DAMAGE_AMOUNT);
			// Levels the roll back out (yaw/pitch unchanged) - unlike the destroyed case just below which keeps whatever roll it had.
			float yaw = this.getYaw();
			float pitch = this.getPitch();
			this.setOrientationFromEuler(yaw, pitch, 0f);
			this.orientation.normalize();
			super.setYaw(yaw);
			super.setPitch(pitch);
			this.dataTracker.set(SYNCED_YAW, yaw);
			this.dataTracker.set(SYNCED_PITCH, pitch);
			this.dataTracker.set(SYNCED_ROLL, 0f);
		} else {
			// Destroyed outright, keeping whatever roll it had at touchdown - forceDestroy() itself never touches orientation, so the roll stays exactly as-is.
			this.tudursvehiclemod$forceDestroy(serverWorld);
		}
	}

	/** Heavy damage to THIS aircraft when it slams into terrain. */
	protected void tudursvehiclemod$checkBlockCrashDamage(double preMoveSpeed, float preMoveFlightPathAngle) {
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		boolean steepEnoughForGroundImpact = Math.abs(preMoveFlightPathAngle) >= CRASH_ANGLE_THRESHOLD_DEGREES;
		boolean isCrash = this.horizontalCollision || (this.verticalCollision && steepEnoughForGroundImpact);
		if (!isCrash || preMoveSpeed < CRASH_MIN_SPEED) {
			return;
		}
		float damage = (float) (preMoveSpeed * CRASH_DAMAGE_PER_SPEED);
		this.damage(serverWorld, this.getDamageSources().flyIntoWall(), damage);
		if (this.horizontalCollision) {
			this.cruiseSpeed = 0f;
		}
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
		// Sequential rather than parallel: candidate sets here are typically 0-3 entities, where
		// candidate sets here are typically 0-3 entities, where
		// parallelStream()'s own fork/join dispatch overhead exceeds
		// whatever it could possibly save - a plain sequential stream is
		// both simpler and actually cheaper at this scale.
		java.util.List<Entity> actuallyTouching = candidates.stream()
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

	/** Samples cornerSource's own current bounding box (its 8 corners plus its own center) and tests each sampled point against meshOwner's own real mesh surface (see AbstractVehicleEntity's own tudursvehiclemod$isPointNearMeshSurface() doc) - true the moment ANY one of those 9 points actually touches. A full per-vertex mesh-vs-mesh test would be far more precise but is significantly more expensive to run every tick for every nearby vehicle pair; this 9-point sampling is a deliberately cheap middle ground between that and the plain bounding-box overlap this replaced. */
	protected static boolean tudursvehiclemod$anyCornerNearMesh(AbstractVehicleEntity cornerSource, AbstractVehicleEntity meshOwner) {
		net.minecraft.util.math.Box box = cornerSource.getBoundingBox();
		Vec3d[] samples = {
				new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.minX, box.minY, box.maxZ),
				new Vec3d(box.minX, box.maxY, box.minZ), new Vec3d(box.minX, box.maxY, box.maxZ),
				new Vec3d(box.maxX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.maxZ),
				new Vec3d(box.maxX, box.maxY, box.minZ), new Vec3d(box.maxX, box.maxY, box.maxZ),
				box.getCenter(),
		};
		for (Vec3d sample : samples) {
			if (meshOwner.tudursvehiclemod$isPointNearMeshSurface(sample)) {
				return true;
			}
		}
		return false;
	}

	/** At/above this fraction of max speed, touching ground OR water damages this aircraft proportionally (gentler than checkBlockCrashDamage's steep-angle crash damage; fires for water too). */
	protected static final float LANDING_IMPACT_SPEED_FRACTION = 0.5f;
	/** Damage per unit of speed over LANDING_IMPACT_SPEED_FRACTION. */
	protected static final float LANDING_IMPACT_DAMAGE_PER_SPEED = 50.0f;
	/** Water-ditching damage multiplier. */
	protected static final float WATER_DITCHING_DAMAGE_PER_SPEED = LANDING_IMPACT_DAMAGE_PER_SPEED * 8.0f;
	/** Minimum damage fraction a water ditching always costs, regardless of the speed-based formula above. */
	protected static final float WATER_DITCHING_MIN_DAMAGE_FRACTION = 0.1f;
	/** So tudursvehiclemod$checkLandingImpactDamage() only fires once, on the actual transition from airborne to grounded/surfaced, rather than re-triggering every tick this aircraft remains on the ground/water. */
	protected boolean wasGroundedForImpact;

	protected void tudursvehiclemod$checkLandingImpactDamage(double preMoveSpeed, VehicleDefinition def) {
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		boolean groundedNow = this.isOnGround() || this.isTouchingWater();
		boolean justLanded = groundedNow && !this.wasGroundedForImpact;
		this.wasGroundedForImpact = groundedNow;
		if (!justLanded) {
			return;
		}
		double threshold = def.maxSpeed() * LANDING_IMPACT_SPEED_FRACTION;
		if (preMoveSpeed < threshold) {
			return;
		}
		// Water ditching (non-float-capable, touching water) costs far more per unit of speed than a normal ground landing.
		boolean waterDitching = this.isTouchingWater() && !def.isFloatCapable();
		float damagePerSpeed = waterDitching ? WATER_DITCHING_DAMAGE_PER_SPEED : LANDING_IMPACT_DAMAGE_PER_SPEED;
		float damageAmount = (float) ((preMoveSpeed - threshold) * damagePerSpeed);
		// Always at least WATER_DITCHING_MIN_DAMAGE_FRACTION, regardless of the speed-based formula above.
		if (waterDitching) {
			damageAmount = Math.max(damageAmount, this.getMaxHealth() * WATER_DITCHING_MIN_DAMAGE_FRACTION);
		}
		this.damage(serverWorld, this.getDamageSources().flyIntoWall(), damageAmount);
	}

	/** Returns normalBlend, sped up by DECEL_SPEEDUP_MULTIPLIER if targetSpeed is actually LOWER than the current cruiseSpeed (i.e. this tick is decelerating). */
	protected float tudursvehiclemod$effectiveBlend(float targetSpeed, float normalBlend) {
		return targetSpeed < this.cruiseSpeed ? normalBlend * DECEL_SPEEDUP_MULTIPLIER : normalBlend;
	}

	/** The aircraft's current climb/dive angle in degrees, derived directly from its actual velocity vector (vertical component vs horizontal speed) rather than from.. */
	protected float tudursvehiclemod$flightPathAngleDegrees() {
		Vec3d velocity = this.getVelocity();
		double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		if (horizontalSpeed < 1.0e-4 && Math.abs(velocity.y) < 1.0e-4) {
			return 0f;
		}
		return (float) Math.toDegrees(Math.atan2(velocity.y, horizontalSpeed));
	}

	/** The pitch value actually fed into the climb/dive target-speed formulas.. */
	protected float tudursvehiclemod$smoothedSpeedTargetPitch() {
		float raw = tudursvehiclemod$flightPathAngleDegrees();
		if (Math.abs(raw) >= Math.abs(this.smoothedSpeedTargetPitch)) {
			this.smoothedSpeedTargetPitch = raw;
		} else {
			this.smoothedSpeedTargetPitch += (raw - this.smoothedSpeedTargetPitch) * LEVEL_OUT_PITCH_BLEND;
		}
		return this.smoothedSpeedTargetPitch;
	}

	/** Two-stage floor on the climb target-speed reduction (see CLIMB_TARGET_MIN_FRACTION's own doc for the full reasoning): holds at CLIMB_TARGET_MIN_FRACTION for.. */
	protected float tudursvehiclemod$climbTargetMinFraction(float climbFactor) {
		if (climbFactor > STEEP_CLIMB_THRESHOLD) {
			this.steepClimbTicks++;
		} else {
			this.steepClimbTicks = 0;
		}
		if (this.steepClimbTicks <= STEEP_CLIMB_GRACE_TICKS) {
			return CLIMB_TARGET_MIN_FRACTION;
		}
		float decayProgress = MathHelper.clamp(
				(this.steepClimbTicks - STEEP_CLIMB_GRACE_TICKS) / (float) STEEP_CLIMB_DECAY_TICKS, 0f, 1f);
		return MathHelper.lerp(decayProgress, CLIMB_TARGET_MIN_FRACTION, 0f);
	}

	/** Eases toward dead-level flight (current heading, pitch=0, roll=0). */
	/** Forces the nose straight down (extracted pitch -90, roll 0). */
	protected void tudursvehiclemod$forceNoseDown() {
		if (this.getEntityWorld().isClient()) {
			Quaternionf syncedTarget = new Quaternionf()
					.rotationY((float) Math.toRadians(-this.dataTracker.get(SYNCED_YAW)))
					.rotateX((float) Math.toRadians(this.dataTracker.get(SYNCED_PITCH)));
			this.orientation.slerp(syncedTarget, CLIENT_SYNC_SMOOTHING);
			this.orientation.normalize();
			return;
		}
		if (!this.stallYawLocked) {
			this.stallTargetYaw = getYaw();
			this.stallYawLocked = true;
		}
		// rotateX(90) here, not rotateX(-90).
		Quaternionf noseDownTarget = new Quaternionf()
				.rotationY((float) Math.toRadians(-this.stallTargetYaw))
				.rotateX((float) Math.toRadians(90f));
		this.orientation.slerp(noseDownTarget, STALL_PITCH_SMOOTHING);
		this.orientation.normalize();
		float[] extracted = extractYawPitchRoll(this.orientation);
		super.setYaw(extracted[0]);
		super.setPitch(extracted[1]);
		this.dataTracker.set(SYNCED_YAW, extracted[0]);
		this.dataTracker.set(SYNCED_PITCH, extracted[1]);
		this.dataTracker.set(SYNCED_ROLL, extracted[2]);
	}

	protected void tudursvehiclemod$easeTowardsLevelFlight() {
		if (this.getEntityWorld().isClient()) {
			// Ease towards the synced target rather than hard-setting to it.
			Quaternionf syncedTarget = new Quaternionf()
					.rotationY((float) Math.toRadians(-this.dataTracker.get(SYNCED_YAW)))
					.rotateX((float) Math.toRadians(this.dataTracker.get(SYNCED_PITCH)));
			this.orientation.slerp(syncedTarget, CLIENT_SYNC_SMOOTHING);
			this.orientation.normalize();
			return;
		}
		if (!this.levelFlightYawLocked) {
			this.levelFlightTargetYaw = getYaw();
			this.levelFlightYawLocked = true;
		}
		Quaternionf levelTarget = new Quaternionf().rotationY((float) Math.toRadians(-this.levelFlightTargetYaw));
		this.orientation.slerp(levelTarget, LEVEL_ASSIST_SMOOTHING);
		this.orientation.normalize();
		float[] extracted = extractYawPitchRoll(this.orientation);
		super.setYaw(extracted[0]);
		super.setPitch(extracted[1]);
		this.dataTracker.set(SYNCED_YAW, extracted[0]);
		this.dataTracker.set(SYNCED_PITCH, extracted[1]);
		this.dataTracker.set(SYNCED_ROLL, extracted[2]);
	}

	/** Roll (bank) angle eases back to level this many times faster than yaw/pitch while grounded - a plane's own wheels/gear naturally keep it from staying banked once it's actually touching down, so there's no reason bank angle should take as long to settle as heading/ground-pitch do. */
	protected static final float GROUND_ROLL_SMOOTHING_MULTIPLIER = 3.0f;

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
		if (player != null && !isPivotTurnRestricted(def, this.getSyncedSidewaysInput())) {
			// The throttle boost applied at this
			// aircraft's own throttle-update call site alone never
			// actually PREVENTED taxi turning itself - gated here too, so
			// this aircraft genuinely can't turn at all while taxiing
			// until it's actually reached pivot_turn_throttle's own
			// minimum speed (matching CarEntity's own turnRate=0 while
			// restricted).
			this.groundAttitudeTargetYaw -= this.getSyncedSidewaysInput() * this.tudursvehiclemod$getEffectiveTurnSpeed();
		}
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
		if (player != null) {
			this.groundLevelTargetYaw -= this.getSyncedSidewaysInput() * this.tudursvehiclemod$getEffectiveTurnSpeed();
		}
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
