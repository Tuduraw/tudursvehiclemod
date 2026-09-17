package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;

/** Free-flight vehicle, controlled like the classic "MC Helicopter" mod:. */
public class HelicopterEntity extends AbstractVehicleEntity implements FreeCameraVehicle {

	protected static final float MAX_ROLL_DEGREES = 20f;

	@Override
	public float getYawFollowRateDegrees() {
		return this.tudursvehiclemod$getEffectiveTurnSpeed();
	}

	/** This vehicle's own free-look was incomplete -
	 * only getYawFollowRateDegrees() was overridden, so PlayerLookRateMixin's
	 * own generic clamping (see that class's own doc) only ever applied to
	 * yaw - pitch inherited AbstractVehicleEntity's own base -1f ("doesn't
	 * link to the player's view at all"), meaning the pilot's own camera
	 * pitch stayed completely free-look-equivalent ALL the time, regardless
	 * of whether free-look was actually toggled on or off. Same rate as yaw. */
	@Override
	public float getPitchFollowRateDegrees() {
		return this.tudursvehiclemod$getEffectiveTurnSpeed();
	}

	protected static final float MAX_THROTTLE_TILT_DEGREES = 15f;

	/** Vertical speed at full vertical throttle, in blocks/tick. */
	protected static final double MAX_VERTICAL_SPEED = 0.12;
	protected static final float VERTICAL_THROTTLE_STEP = 0.03f;
	protected static final float VERTICAL_THROTTLE_RETURN_STEP = 0.05f;

	/** How gently the vertical throttle drifts back
	 * towards -100% specifically while still grounded (not yet taken off)
	 * with no ascend/descend input at all - see the comment where this is
	 * used in updateVehicleMovement() for the full rationale. Deliberately
	 * much smaller than VERTICAL_THROTTLE_STEP (the rate throttle actually
	 * INCREASES at while the player holds ascend), so a genuine, sustained
	 * ascend attempt always nets a real increase over time regardless of
	 * this. */
	protected static final float GROUNDED_RETURN_STEP = 0.01f;
	protected static final float VERTICAL_SPEED_BLEND = 0.08f;

	/** This vehicle's own rotor should always be
	 * spinning at a reasonable baseline rate while piloted, regardless of
	 * how the vertical throttle happens to sit at any given moment (a real
	 * helicopter's rotor is always spinning fast while a pilot's aboard,
	 * not slowing to a near-stop while simply hovering, and never visibly
	 * reversing direction just because the throttle driving ascend/descend
	 * happens to be negative). See getSpinningPartSpeedMultiplier()'s own
	 * doc for exactly how this and MAX below are actually used - this is
	 * the FRACTION (not a raw degrees value) of this rotor's own
	 * full-speed rate (its own declared degreesPerTick()) it spins at when
	 * verticalThrottle is at its own minimum (-100%). */
	protected static final float MIN_ROTOR_SPEED_FRACTION = 0.3f;
	/** The fraction of full speed at verticalThrottle's own maximum (+100%) - see MIN_ROTOR_SPEED_FRACTION's own doc. 1.0 (full declared speed). */
	protected static final float MAX_ROTOR_SPEED_FRACTION = 1.0f;
	/** This vehicle's own rotor speed fraction while genuinely unpiloted - 0 (no rotation at all), matching a real helicopter's rotor actually stopping once nobody's flying it. */
	protected static final float UNMANNED_ROTOR_SPEED_FRACTION = 0.0f;

	/** This vehicle still visibly turned (yaw following
	 * the pilot's own view) while grounded, despite already gating that off
	 * in the isOnGround() branch below - tracked down to the exact same,
	 * already-documented isOnGround() flicker CarEntity's own
	 * ticksSinceGrounded/GROUNDED_GRACE_TICKS fixes (see that class's own
	 * doc) - a momentary, single-tick false reading from ordinary terrain
	 * micro-unevenness, completely independent of server load, briefly took
	 * the AIRBORNE branch (which DOES follow the pilot's own view) even
	 * while genuinely sitting still on the ground. Same fix here: several
	 * CONSECUTIVE ungrounded ticks are required before this vehicle is
	 * treated as genuinely airborne, riding out a brief flicker instead of
	 * reacting to it. */
	protected int ticksSinceGrounded;
	protected static final int GROUNDED_GRACE_TICKS = 3;
	/** See this field's own doc just above - exposed for client.mixin.PlayerLookRateMixin. */
	public boolean tudursvehiclemod$isEffectivelyGrounded() {
		return this.ticksSinceGrounded <= GROUNDED_GRACE_TICKS;
	}
	/** How far above the water surface a float-capable Helicopter's own hull settles while floating - see updateVehicleMovement()'s own new float override. 0.0 (settles exactly at the water line) as a simple default; a genuinely new capability for this vehicle type. */
	protected static final double HELI_SURFACE_FLOAT_DEPTH = 0.0;

	/** How fast verticalThrottle spools back down to
	 * 0 when unpiloted - same idea as CarEntity's own identical constant. */
	protected static final float UNMANNED_THROTTLE_DECAY = 0.01f;
	/** Only used once effectivelyGrounded - the gentle airborne descent (UNMANNED_THROTTLE_DECAY, toward -0.5) is unaffected. */
	protected static final float GROUNDED_THROTTLE_DECAY = UNMANNED_THROTTLE_DECAY * 10f;
	/** Once verticalThrottle has eased to within this distance of its own current target (-1 grounded, -0.5 airborne), snaps the rest of the way there exactly, since the asymptotic ease alone can approach but never actually reach that target on its own. */
	protected static final float VERTICAL_THROTTLE_SNAP_THRESHOLD = 0.02f;

	/** Return-to-center throttle for ascend/descend. */
	protected float verticalThrottle;

	/** Accumulates mouse-look delta the same way AircraftEntity's own fields of the same name do (see network.ModNetworking's own AircraftOrientationInputPayload handler, extended to also target this vehicle whenever isManualMode() is true). Public for that same network handler to reach directly. */
	public float pendingYawInput;
	public float pendingPitchInput;
	/** Eased (not applied-instantly) per-tick yaw/pitch deltas for manual mode - same smoothing approach as AircraftEntity's own smoothedYawDelta/smoothedPitchDelta/smoothedRollDelta. */
	private float manualSmoothedYawDelta;
	private float manualSmoothedPitchDelta;
	private float manualSmoothedRollDelta;

	/** Current horizontal (forward + strafe combined) cruise velocity. */
	protected Vec3d horizontalCruise = Vec3d.ZERO;

	public HelicopterEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected Identifier defaultDefinitionId() {
		return Identifier.of(VehicleMod.MOD_ID, "helicopter");
	}

	@Override
	protected void onPilotMounted() {
		super.onPilotMounted();
		this.verticalThrottle = 0f;
		this.horizontalCruise = Vec3d.ZERO;
	}

	@Override
	protected void updateVehicleMovement(VehicleDefinition def) {
		if (this.getControllingPassenger() == null && this.getEntityWorld().isClient()) {
			// Skips this method's own local physics recomputation for an unpiloted vehicle on the client, but still applies move() with the current (network-synced) velocity - matching AircraftEntity's own established pattern.
			this.move(MovementType.SELF, this.getVelocity());
			// TicksSinceGrounded is otherwise never touched at all on the client while this guard is active, leaving getSpinningPartSpeedMultiplier() (read by both the rotor animation and engine sound) stuck reporting "still airborne" forever - kept up to date here too, using isOnGround() as it stands immediately after the move() call just above, without resurrecting this method's own full movement recomputation on the client.
			if (this.isOnGround()) {
				this.ticksSinceGrounded = 0;
			} else {
				this.ticksSinceGrounded++;
			}
			return;
		}
		// Without this, floating on water still behaved as fully airborne throughout the rest of this method, since none of that existing logic had any awareness of water at all. Reuses the SAME threshold/depth as the float-spring override further below, so both stay in agreement about what counts as "on the water".
		boolean floatingOnWater = false;
		if (def.isFloatCapable()) {
			java.util.OptionalDouble groundedCheckSurfaceY = this.tudursvehiclemod$findWaterSurfaceY();
			floatingOnWater = groundedCheckSurfaceY.isPresent()
					&& this.getY() <= groundedCheckSurfaceY.getAsDouble() + HELI_SURFACE_FLOAT_DEPTH + 0.5;
		}
		if (this.isOnGround() || floatingOnWater) {
			this.ticksSinceGrounded = 0;
		} else {
			this.ticksSinceGrounded++;
		}
		boolean effectivelyGrounded = this.ticksSinceGrounded <= GROUNDED_GRACE_TICKS;

		LivingEntity pilot = getControllingPassenger();
		if (pilot instanceof PlayerEntity player) {
			// Altitude hold: ramps toward +/-1 while ascend/descend is
			// held. eases back towards MINIMUM
			// (-1, not 0) while still grounded (i.e. hasn't actually taken
			// off yet) - keeping the helicopter pinned firmly down rather
			// than idling at a neutral throttle that could bounce it back
			// into the air - and only towards the ORIGINAL 0% ("hold
			// current altitude") the instant it's actually airborne.
			// Computed HERE (before getSpinningPartSpeedMultiplier() reads
			// it) rather than its own previous spot further down, so the
			// rotor's own speed this same tick can already reflect it
			// directly, rather than lagging a tick behind.
			boolean hasTakenOff = !effectivelyGrounded;
			float verticalReturnTarget = hasTakenOff ? 0f : -1f;
			// Pulling towards -1 with the SAME step
			// used for the original 0%-return case felt far too strong -
			// overpowering the player's own active attempt to ascend
			// whenever anything (even briefly) fell through to this
			// branch instead of the dedicated "isJumping" branch below.
			// The intent was only ever "drifts back down to -100% when
			// there's genuinely no input at all", not "-100% is
			// constantly forced" - a much gentler step here (well under
			// VERTICAL_THROTTLE_STEP, so genuinely holding ascend always
			// nets a real increase, tick over tick, regardless of any
			// stray tick or two this branch might still catch) keeps
			// that original intent without being so aggressive it can
			// ever fight the player's own input.
			float verticalReturnStep = hasTakenOff ? VERTICAL_THROTTLE_RETURN_STEP : GROUNDED_RETURN_STEP;
			// Uses tudursvehiclemod$isPilotJumping()
			// instead of player.isJumping() directly - see that method's own
			// doc for why the latter never reads true for a mounted pilot.
			boolean pilotJumping = tudursvehiclemod$isPilotJumping(player);
			// This vehicle's own vertical throttle logic never checked fuel at all, letting it keep flying (or even climbing) with an empty tank. Out of fuel, the engine has no power to produce lift at all - verticalThrottle can only ever decrease (autorotation-style controlled descent), regardless of whatever the player is actually pressing.
			if (this.tudursvehiclemod$isOutOfFuel()) {
				this.verticalThrottle = Math.max(-1f, this.verticalThrottle - VERTICAL_THROTTLE_STEP);
			} else if (pilotJumping && !this.isDescending()) {
				this.verticalThrottle = Math.min(1f, this.verticalThrottle + VERTICAL_THROTTLE_STEP);
			} else if (this.isDescending() && !pilotJumping) {
				this.verticalThrottle = Math.max(-1f, this.verticalThrottle - VERTICAL_THROTTLE_STEP);
			} else if (this.verticalThrottle > verticalReturnTarget) {
				this.verticalThrottle = Math.max(verticalReturnTarget, this.verticalThrottle - verticalReturnStep);
			} else if (this.verticalThrottle < verticalReturnTarget) {
				this.verticalThrottle = Math.min(verticalReturnTarget, this.verticalThrottle + verticalReturnStep);
			}
			setThrottleDirect(this.verticalThrottle);

			// Direct input, not a cruise throttle: releasing W/S/A/D just lets horizontalCruise ease back towards 0 below.
			// Reverse is capped at this vehicle's own
			// configured reverseThrottle (0.0 by default - no backward
			// flight at all - unless explicitly configured otherwise).
			// Unlike every other vehicle here, this
			// one does NOT hold at 0% for a moment when crossing from
			// forward to reverse (or back) via
			// tudursvehiclemod$applyThrottleSwitchHold() - ascend/descend
			// (this vehicle's own actual throttle) can already reverse
			// direction instantly via the separate verticalThrottle logic
			// above, so an added delay specifically on the forward/strafe
			// input here would be an inconsistent, arbitrary restriction
			// rather than a meaningful "gear change" the way it is for a
			// car.
			float forwardInput = MathHelper.clamp(this.getSyncedThrottleInput(), def.reverseThrottle(), 1.0f);
			float lateralInput = this.getSyncedSidewaysInput();

			// Mouse controls pitch/yaw and A/D controls roll instead of WASD directly setting travel direction - see tudursvehiclemod$updateManualModeMovement()'s own doc. Only while genuinely airborne - grounded behavior stays exactly as before (fixed yaw/pitch, roll levels) regardless of manual mode, for safety/consistency with how every other vehicle here already treats being grounded.
			if (this.isManualMode() && !effectivelyGrounded) {
				this.tudursvehiclemod$updateManualModeMovement(def, player);
			} else if (effectivelyGrounded) {
				this.wasManualModeLastTick = false;
				// Continuously syncs the pilot's own real view yaw to match this vehicle's own fixed facing while grounded (unless they're in free-look, which is explicitly exempt from following the vehicle at all), so by the time this vehicle actually takes off, player.getYaw() is already identical to this.getYaw() - nothing left to snap to at all.
				if (!this.isFreeLook() && player.getYaw() != this.getYaw()) {
					player.setYaw(this.getYaw());
					if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
						serverPlayer.networkHandler.requestTeleport(
								serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), serverPlayer.getYaw(), serverPlayer.getPitch());
					}
				}
				// Yaw/pitch are now fixed (left
				// entirely untouched) once landed, rather than continuing
				// to rotate the whole vehicle to match the pilot's own
				// current view direction while grounded - it stays facing
				// exactly whatever way it was when it touched down. Roll
				// still eases level though, since a helicopter genuinely
				// settling onto the ground should still level out from
				// whatever roll it had while banking in for the landing.
				this.prevRoll = this.roll;
				this.roll += (0f - this.roll) * 0.3f;
			} else {
				this.wasManualModeLastTick = false;
				if (!this.isFreeLook()) {
					this.setYaw(player.getYaw());
				}
				// Lean into the direction of travel instead of following the pilot's look pitch: nose down moving forward, nose up in reverse.
				float targetPitch = forwardInput * MAX_THROTTLE_TILT_DEGREES;
				this.setPitch(stepTowardAngle(this.getPitch(), targetPitch, this.tudursvehiclemod$getEffectiveTurnSpeed()));
				updateRoll(player, MAX_ROLL_DEGREES, 0.2f);
			}

			// Forward/strafe combined into one target vector, relative to current facing.
			double yawRad = Math.toRadians(this.getYaw());
			Vec3d forward = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
			Vec3d right = new Vec3d(Math.cos(yawRad), 0, Math.sin(yawRad));

			// This vehicle could still dart around
			// briskly via WASD while sitting on the ground - a real
			// helicopter has no independent means of ground travel at all
			// (no wheels/tracks of its own), so horizontal movement input
			// is only actually applied while genuinely airborne; while
			// grounded, this instead eases horizontalCruise back towards
			// a dead stop, same as if forwardInput/lateralInput were both
			// simply 0.
			// Travel direction/speed comes from the resulting PITCH attitude instead (nose-down = forward, matching the existing "lean into direction of travel" pitch convention above) rather than forwardInput/lateralInput directly - no strafe component at all, same as a real helicopter's own cyclic-tilt-driven flight.
			Vec3d targetHorizontal;
			float effectiveMaxSpeed = this.tudursvehiclemod$getEffectiveMaxSpeed();
			if (effectivelyGrounded) {
				targetHorizontal = Vec3d.ZERO;
			} else if (this.isManualMode()) {
				float manualSpeedFraction = (float) Math.sin(Math.toRadians(this.getPitch()));
				float manualSidewaysFraction = (float) -Math.sin(Math.toRadians(this.roll));
				targetHorizontal = forward.multiply(manualSpeedFraction * effectiveMaxSpeed)
						.add(right.multiply(manualSidewaysFraction * effectiveMaxSpeed));
			} else {
				targetHorizontal = forward.multiply(forwardInput * effectiveMaxSpeed).add(right.multiply(lateralInput * effectiveMaxSpeed));
			}
			float blend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
			this.horizontalCruise = this.horizontalCruise.add(targetHorizontal.subtract(this.horizontalCruise).multiply(blend));
			// Absent for an entity that had never moved at all - the same known pattern (see AbstractVehicleEntity's own tudursvehiclemod$blendCruiseSpeedToward() doc), applied here since horizontalCruise is likewise a plain, non-networked Vec3d field whose own asymptotic blend never mathematically reaches exactly targetHorizontal, leaving a persistent, ever-shrinking-but-never-zero residual that can gradually diverge between client and server.
			if (this.horizontalCruise.subtract(targetHorizontal).lengthSquared() < 1.0e-8) {
				this.horizontalCruise = targetHorizontal;
			}

			// Interpolates the EFFECTIVE vertical throttle (not the raw stored verticalThrottle itself, which still separately drives blade-spin display/animation unaffected by this) towards a fixed descent value as bank increases past 45 degrees, reaching full effect at 90 degrees or beyond.
			float bankMagnitude = Math.abs(MathHelper.wrapDegrees(this.roll));
			float effectiveVerticalThrottle = this.verticalThrottle;
			if (bankMagnitude > STEEP_BANK_LIFT_LOSS_START_DEGREES) {
				float liftLossFraction = MathHelper.clamp(
						(bankMagnitude - STEEP_BANK_LIFT_LOSS_START_DEGREES) / STEEP_BANK_LIFT_LOSS_START_DEGREES, 0f, 1f);
				effectiveVerticalThrottle = MathHelper.lerp(liftLossFraction, this.verticalThrottle, STEEP_BANK_FORCED_DESCENT_THROTTLE);
			}
			double targetVerticalSpeed = effectiveVerticalThrottle * MAX_VERTICAL_SPEED;
			double newY = this.getVelocity().y + (targetVerticalSpeed - this.getVelocity().y) * VERTICAL_SPEED_BLEND;

			this.setVelocity(this.horizontalCruise.x, newY, this.horizontalCruise.z);
		} else {
			// No pilot: glide to a gentle stop instead of accelerating downward forever.
			this.wasManualModeLastTick = false;
			this.prevRoll = this.roll = 0f;
			// Pitch was never reset here at all (unlike roll just above) - whatever forward/backward tilt the player last held via W/S persisted indefinitely after dismount, since the manned branch's own setPitch() call above is the only place this ever gets touched.
			this.setPitch(0f);

			// Keeps easing toward -1 (or -0.5 while still airborne) exactly as before - since that asymptotic ease alone can approach but never actually reach -1 (see this fix's own earlier diagnosis), still snaps to exactly -1 once close enough (within VERTICAL_THROTTLE_SNAP_THRESHOLD) that finishing the ease the rest of the way no longer matters for smoothness, rather than the moment this vehicle is merely grounded at all.
			float verticalTarget = effectivelyGrounded ? -1f : -0.5f;
			float verticalDecayRate = effectivelyGrounded ? GROUNDED_THROTTLE_DECAY : UNMANNED_THROTTLE_DECAY;
			this.verticalThrottle += (verticalTarget - this.verticalThrottle) * verticalDecayRate;
			if (Math.abs(this.verticalThrottle - verticalTarget) < VERTICAL_THROTTLE_SNAP_THRESHOLD) {
				this.verticalThrottle = verticalTarget;
			}
			// This branch never synced the generic throttle field to verticalThrottle at all, unlike the piloted branch's own identical call above - so it just sat frozen at whatever it was the instant the last player dismounted, untouched by any of those fixes.
			setThrottleDirect(this.verticalThrottle);

			double targetVerticalSpeed = this.verticalThrottle * MAX_VERTICAL_SPEED;
			double newY = this.getVelocity().y + (targetVerticalSpeed - this.getVelocity().y) * VERTICAL_SPEED_BLEND;

			// Horizontal movement was never driven by anything except WASD
			// input in the first place (see the piloted branch's own
			// "no independent means of ground travel" doc just above) -
			// simply easing horizontalCruise towards zero the same way an
			// still-piloted-but-merely-grounded helicopter already does
			// (targetHorizontal = Vec3d.ZERO, same blend factor) covers
			// this with no separate unmanned-only formula needed.
			float blend = MathHelper.clamp(def.acceleration(), 0.01f, 1.0f);
			this.horizontalCruise = this.horizontalCruise.add(Vec3d.ZERO.subtract(this.horizontalCruise).multiply(blend));
			// Same fix/reasoning as the piloted branch's own identical addition just above.
			if (this.horizontalCruise.lengthSquared() < 1.0e-8) {
				this.horizontalCruise = Vec3d.ZERO;
			}

			this.setVelocity(this.horizontalCruise.x, newY, this.horizontalCruise.z);
		}

		// This vehicle has no independent way to stop itself sinking through water at all (unlike solid ground, which vanilla collision naturally stops it against) - if float-capable, this vehicle's own current position is already at or below a nearby water surface, AND the velocity just computed above isn't already ascending, overrides the Y velocity to use the shared surface float spring instead (see AbstractVehicleEntity's own tudursvehiclemod$applySurfaceFloatSpring() doc), settling at the water line rather than continuing to sink.
		//
		// The float lock's own release condition only tracks whether the WATER surface itself has genuinely moved, never whether this vehicle is actively climbing away from it under its own power - so it kept forcibly resetting Y back to the frozen surface value every tick even with full ascend throttle held. The additional "velocity.y <= 0.0" check here is the fix: a POSITIVE (already-ascending, from the pilot's own throttle input just computed above) velocity is left completely untouched instead of ever reaching the lock/spring logic at all, so holding ascend climbs away from water exactly like it would from solid ground - the float spring only ever engages for a non-positive (sinking/level) velocity, and naturally re-engages later if this vehicle comes back down within range of the surface again.
		if (def.isFloatCapable()) {
			java.util.OptionalDouble heliSurfaceY = this.tudursvehiclemod$findWaterSurfaceY();
			if (heliSurfaceY.isPresent() && this.getY() <= heliSurfaceY.getAsDouble() + HELI_SURFACE_FLOAT_DEPTH + 0.5
					&& this.getVelocity().y <= 0.0) {
				double heliTargetY = heliSurfaceY.getAsDouble() + HELI_SURFACE_FLOAT_DEPTH;
				double heliNewVelY = this.tudursvehiclemod$applySurfaceFloatSpring(heliTargetY, this.getVelocity().y,
						SURFACE_SPRING_STRENGTH, SURFACE_VERTICAL_DAMPING, SURFACE_SETTLE_POSITION_THRESHOLD, SURFACE_SETTLE_VELOCITY_THRESHOLD);
				this.setVelocity(this.getVelocity().x, heliNewVelY, this.getVelocity().z);
			} else {
				this.tudursvehiclemod$resetSurfaceFloatLock();
			}
		}

		this.move(MovementType.SELF, this.getVelocity());
	}

	/** Manual mode for this vehicle (same MANUAL_MODE toggle as AircraftEntity, but a fresh plain-yaw/pitch/roll implementation rather than AircraftEntity's quaternion-based one -): mouse drives yaw/pitch directly, A/D drives continuous roll with inertia - replacing the normal WASD-direct scheme entirely. Pitch clamped to the same +/-MAX_THROTTLE_TILT_DEGREES range the normal scheme uses. Only called while airborne. */
	private void tudursvehiclemod$updateManualModeMovement(VehicleDefinition def, PlayerEntity player) {
		// Re-seeds the persistent manual-mode quaternion from the current plain yaw/pitch/roll fields the FIRST tick manual mode becomes active after not being active (a rising-edge capture, same general idea as VtolEntity's own wasTransitioningLastTick) - every subsequent tick composes directly onto this same quaternion instead, never round-tripping back through Euler angles at all.
		if (!this.wasManualModeLastTick) {
			this.manualOrientation.rotationY((float) Math.toRadians(-this.getYaw()))
					.rotateX((float) Math.toRadians(this.getPitch()))
					.rotateZ((float) Math.toRadians(this.roll));
		}
		this.wasManualModeLastTick = true;

		boolean freeLook = this.isFreeLook();
		float yawRate = this.tudursvehiclemod$getEffectiveTurnSpeed() * MANUAL_MODE_YAW_RATE_MULTIPLIER;
		float pitchRate = this.tudursvehiclemod$getEffectiveTurnSpeed() * MANUAL_MODE_PITCH_RATE_MULTIPLIER;
		float targetYawDelta = freeLook ? 0f : MathHelper.clamp(this.pendingYawInput, -yawRate, yawRate);
		float targetPitchDelta = freeLook ? 0f : MathHelper.clamp(this.pendingPitchInput, -pitchRate, pitchRate);
		// Discard anything beyond the rate limit rather than saving it for a future tick to keep draining - same convention as AircraftEntity's own updateOrientation().
		this.pendingYawInput = 0f;
		this.pendingPitchInput = 0f;

		// Ease the ACTUALLY-APPLIED delta towards the target rate each tick, rather than jumping straight to it - same smoothing approach as AircraftEntity's own smoothedYawDelta/smoothedPitchDelta.
		this.manualSmoothedYawDelta += (targetYawDelta - this.manualSmoothedYawDelta) * MANUAL_MODE_ROTATION_SMOOTHING;
		this.manualSmoothedPitchDelta += (targetPitchDelta - this.manualSmoothedPitchDelta) * MANUAL_MODE_ROTATION_SMOOTHING;

		// Eases the ACTUALLY-APPLIED roll rate towards the target rate each tick, same general approach as the yaw/pitch easing just above.
		float targetRollRate = this.getSyncedSidewaysInput() * MANUAL_MODE_ROLL_RATE_DEGREES_PER_TICK;
		this.manualSmoothedRollRate += (targetRollRate - this.manualSmoothedRollRate) * MANUAL_MODE_ROLL_INERTIA_SMOOTHING;
		float dRoll = this.manualSmoothedRollRate;
		if (this.getSyncedSidewaysInput() == 0 && Math.abs(this.manualSmoothedRollRate) <= 0.01f) {
			// Auto-levels toward the nearest 90-degree increment while no roll input at all and the eased rate has already settled near zero - same general behavior updateContinuousRoll() itself provides, applied here as an EXTRA delta this same tick rather than a separate branch, so it still composes cleanly through the same quaternion multiplication below.
			float currentRoll = extractYawPitchRoll(this.manualOrientation)[2];
			float nearest90 = Math.round(currentRoll / 90f) * 90f;
			dRoll -= MathHelper.wrapDegrees(nearest90 - currentRoll) * 0.1f;
		}

		// Applies this tick's own rotation as a LOCAL-FRAME rotation (same technique AircraftEntity's own updateOrientation() uses) - composing via quaternion multiplication instead of adding to Euler angles directly is what actually avoids gimbal lock here, regardless of how far pitch/yaw/roll have already accumulated.
		Quaternionf inputDelta = new Quaternionf()
				.rotateY((float) Math.toRadians(-this.manualSmoothedYawDelta))
				.rotateX((float) Math.toRadians(this.manualSmoothedPitchDelta))
				.rotateZ((float) Math.toRadians(-dRoll));
		this.manualOrientation.mul(inputDelta).normalize();

		float[] extracted = extractYawPitchRoll(this.manualOrientation);
		this.setYaw(extracted[0]);
		this.setPitch(extracted[1]);
		this.prevRoll = this.roll;
		this.roll = extracted[2];
	}

	/** Decomposes a quaternion assumed to equal Ry(-yaw)*Rx(pitch)*Rz(roll) back into (yaw, pitch, roll) degrees - copied from AircraftEntity's own method of the same name/doc (not directly reusable across the two classes' different inheritance branches). */
	private static float[] extractYawPitchRoll(Quaternionf q) {
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

	/** Smoothing rate for manual mode's own mouse-driven yaw/pitch easing - same value as AircraftEntity's own ROTATION_SMOOTHING. */
	private static final float MANUAL_MODE_ROTATION_SMOOTHING = 0.2f;
	/** Raw def.turnSpeed() (tuned for body-rotation/steering, not fast mouse look) rate-limited and discarded most of the actual mouse movement each tick - boosted the same way AircraftEntity's own YAW_RATE_MULTIPLIER/PITCH_RATE_MULTIPLIER already are, same values. */
	private static final float MANUAL_MODE_YAW_RATE_MULTIPLIER = 3.0f;
	private static final float MANUAL_MODE_PITCH_RATE_MULTIPLIER = 9.0f;
	/** Full roll rate (degrees/tick) once actually rolling - same value as AircraftEntity's own ROLL_RATE_DEGREES_PER_TICK. */
	private static final float MANUAL_MODE_ROLL_RATE_DEGREES_PER_TICK = 6f;
	/** How quickly the ACTUAL roll rate eases towards the target rate above - lower is more sluggish/inertia-heavy, matching manualSmoothedYawDelta/manualSmoothedPitchDelta's own general easing approach but tracking a RATE rather than the angle itself. */
	private static final float MANUAL_MODE_ROLL_INERTIA_SMOOTHING = 0.15f;
	/** The persistent quaternion manual mode's own rotation actually composes onto - the single source of truth while manual mode is continuously active, never round-tripped back through Euler angles mid-flight. */
	private final Quaternionf manualOrientation = new Quaternionf();
	/** Whether manual mode's own movement ran last tick too - a rising-edge check so manualOrientation only ever gets re-seeded from the plain yaw/pitch/roll fields the FIRST tick after manual mode was NOT active (including the very first tick ever), never on a tick where it was already continuously driving this vehicle's own rotation. */
	private boolean wasManualModeLastTick;
	/** Currently eased roll rate (degrees/tick) for manual mode - see MANUAL_MODE_ROLL_INERTIA_SMOOTHING's own doc. */
	private float manualSmoothedRollRate;
	/** Bank magnitude (degrees) beyond which lift starts weakening - full effect reached at double this value (90 degrees). */
	private static final float STEEP_BANK_LIFT_LOSS_START_DEGREES = 45f;
	/** Per this same request: the effective vertical throttle a full (90-degree-or-beyond) bank forces regardless of actual throttle input - a clear, forced descent (not merely "no climb"). */
	private static final float STEEP_BANK_FORCED_DESCENT_THROTTLE = -0.6f;

	/** This vehicle's own rotor should always be
	 * spinning while piloted (never stopping or reversing based on
	 * verticalThrottle's own sign - see the constants' own docs above for
	 * exactly why and MIN_ROTOR_SPEED_FRACTION/MAX_ROTOR_SPEED_FRACTION's
	 * own doc for the actual range) - mapped linearly and MONOTONICALLY
	 * from verticalThrottle (-100%.+100%) to a speed FRACTION (not raw
	 * degrees - this multiplies directly against each spinning_part's own
	 * declared degreesPerTick(), an already-absolute "speed at full
	 * throttle" constant, in the shared updateSpinningParts() this
	 * overrides), always positive, with no dip anywhere along the way. */
	@Override
	protected float getSpinningPartSpeedMultiplier() {
		if (!(getControllingPassenger() instanceof PlayerEntity) && !this.tudursvehiclemod$isDroneActive()) {
			// Uses this vehicle's own already-correct landed-state detection directly instead - spins whenever not landed, regardless of vertical motion, stops only once actually landed.
			return this.ticksSinceGrounded <= GROUNDED_GRACE_TICKS ? UNMANNED_ROTOR_SPEED_FRACTION : MIN_ROTOR_SPEED_FRACTION;
		}
		float throttleFraction = (this.verticalThrottle + 1f) / 2f;
		return MIN_ROTOR_SPEED_FRACTION + throttleFraction * (MAX_ROTOR_SPEED_FRACTION - MIN_ROTOR_SPEED_FRACTION);
	}

	@Override
	public boolean tudursvehiclemod$supportsLandingGearDisplay() {
		return true;
	}
}
