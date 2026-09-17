package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Surface vessel - ports SubmarineEntity's own
 * "surfaced" navigation mode wholesale (throttle + direct A/D yaw turn,
 * continuous spring-towards-the-water-surface buoyancy - see
 * tudursvehiclemod$findWaterSurfaceY()'s own doc - rather than a discrete,
 * 3-state gravity switch based on a single-point fluid check at this
 * entity's own origin, which was the likely cause of
 * unstable, suddenly-jumping speed behavior on at least one large converted
 * ship: that single point flickering between "submerged"/"touching but not
 * submerged"/"clear of water" as the hull rides waves or just from block-
 * grid granularity would flip gravity between two OPPOSITE, full-magnitude
 * values every time, rather than smoothly correcting towards a stable
 * depth). Only the turn-lean ROLL DIRECTION is kept as this class's own
 * pre-existing (outward-lean) convention, the opposite of submarine's own
 * inward lean - see MAX_ROLL_DEGREES's own doc for why. */
public class ShipEntity extends AbstractVehicleEntity {

	/** Ship's own center of mass sits above the waterline, so centrifugal
	 * force during a turn leans it OUTWARD (away from the turn) - the
	 * opposite of SubmarineEntity's own surface-mode roll, whose center of
	 * mass sits below the waterline instead. */
	protected static final float MAX_ROLL_DEGREES = 8f;
	/** For the exponential blend formula roll += (target - roll) * k used everywhere below, the number of ticks to reach a given fraction of target is proportional to 1/ln(1-k) - doubling that time requires k_new = 1 - sqrt(1-k_old), not simply half of the previous 0.1 value (which would only approximate, not exactly double, the settle time). 0.1 -> ~0.0513. */
	protected static final float ROLL_SMOOTHING = 0.0513f;

	/** How fast getThrottle() spools back down to 0
	 * when unpiloted - same idea as CarEntity's own identical constant. */
	protected static final float UNMANNED_THROTTLE_DECAY = 0.01f;

	/** How far ABOVE the located water surface this hull tries to float -
	 * the waterline sat slightly too low with the
	 * opposite (below) sign, matching this vehicle's own model-relative Y=0
	 * reference exactly once reversed. */
	protected static final double SURFACE_FLOAT_DEPTH = 0.0;
	// The offset itself is disabled (0.0) for now,
	// but this constant and the spring formula that uses it are
	// deliberately left in place in case future tuning wants it back -
	// 0.125 was the previous, actually-used value.

	public float wheelRotation; // reused as "propeller/paddle" animation phase
	public float prevWheelRotation;

	public ShipEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected Identifier defaultDefinitionId() {
		return Identifier.of(VehicleMod.MOD_ID, "ship");
	}

	@Override
	protected void updateVehicleMovement(VehicleDefinition def) {
		// If this vehicle is currently following a Drone Center's own GROUND route, this sets this tick's own steering/throttle inputs from that route and then falls straight through into the ordinary physics below with those inputs already in place - deliberately NOT a separate movement path of its own (see AbstractVehicleEntity's own tudursvehiclemod$updateGroundWaypointAutopilot() doc for why driving inputs, rather than velocity, is what keeps this vehicle type's own part animation/roll/sound behavior working unchanged).
		this.tudursvehiclemod$applyGroundWaypointAutopilotInputs(def);
		// A sinking wreck's attitude and descent are driven entirely by AbstractVehicleEntity's own tudursvehiclemod$applySinkingMotion(), which already ran this tick. Returning here leaves that untouched - this vehicle's own buoyancy would otherwise spring it straight back to the surface and the wreck would never go under. move() is still applied so the descent actually happens.
		if (this.tudursvehiclemod$isSinking()) {
			this.move(net.minecraft.entity.MovementType.SELF, this.getVelocity());
			return;
		}
		if (this.getControllingPassenger() == null && !this.tudursvehiclemod$isFollowingGroundRoute() && this.getEntityWorld().isClient()) {
			// Skips this method's own local physics recomputation for an unpiloted vehicle on the client, but still applies move() with the current (network-synced) velocity - matching AircraftEntity's own established pattern, since a full early return (skipping move() entirely) would leave the client relying purely on vanilla's passive position interpolation with none of the velocity-driven smoothing.
			this.move(MovementType.SELF, this.getVelocity());
			// This.roll is a plain, non-networked field, and this branch otherwise never touches it at all (unlike the piloted/unpiloted-but-server branches below, which both ease it toward 0/rollTarget every tick) - so it freezes at whatever value it held the instant this early return first engages, exactly matching the same category of bug this project has already fixed once for CarEntity's own cruiseSpeed under an identical client-side skip. Eases toward level here too, matching the SAME rollTarget=0 formula (sideways input is always synced-zero for a genuinely unpiloted vehicle) the server-side unpiloted branch below already uses.
			this.prevRoll = this.roll;
			this.roll += (0f - this.roll) * ROLL_SMOOTHING;
			// Called UNCONDITIONALLY here, before the water-detection check below - this ship's own wake history is now still pruned/smoothed/ratcheted every tick even while unpiloted and water-surface detection genuinely fails (stranded on land, drifted out of a narrow detection window, etc.), rather than freezing forever the moment that first happens.
			this.tudursvehiclemod$pruneWakeTrailOnly(this.tudursvehiclemod$getSignedForwardSpeed(this.getVelocity()));
			// This early return used to skip the wake call further down in this same method entirely (it's unreachable from here) - a genuinely unpiloted but still-moving (e.g. coasting/drifting on synced velocity) ship generated no wake at all. Added here too, using the same tudursvehiclemod$getSignedForwardSpeed(velocity) pattern the normal path below uses (safe to call already - move() just ran above).
			java.util.OptionalDouble unpilotedSurfaceY = this.tudursvehiclemod$findWaterSurfaceY();
			if (unpilotedSurfaceY.isPresent()) {
				this.tudursvehiclemod$updateWakeTrail(unpilotedSurfaceY.getAsDouble(), this.tudursvehiclemod$getSignedForwardSpeed(this.getVelocity()));
			}
			return;
		}
		this.prevWheelRotation = this.wheelRotation;

		Entity driver = getControllingPassenger();
		Vec3d velocity;
		// Per the same direct report handled in CarEntity's own equivalent gate (see that class's own doc there): a ship following a Drone Center ground route must take this same input-reading path, otherwise the unmanned branch below discards the autopilot's own inputs entirely and it never moves. player is null in that case - safe because updateThrottle() never dereferences it (see that method's own doc).
		boolean shipFollowingGroundRoute = this.tudursvehiclemod$isFollowingGroundRoute();
		if (driver instanceof PlayerEntity || shipFollowingGroundRoute) {
			PlayerEntity player = driver instanceof PlayerEntity playerDriver ? playerDriver : null;
			float throttle = updateThrottle(player, 0.02f * def.throttleUpDown().orElse(1.0f), def.reverseThrottle(), 1.0f);
			// A real ship's own rudder similarly
			// needs some minimum speed to actually turn it effectively -
			// see MovementStats's own pivot_turn_throttle doc / AbstractVehicleEntity's
			// own applyPivotTurnThrottleRestriction() doc for exactly how
			// this works (a no-op whenever pivot_turn_throttle is its own
			// default 0, i.e. this ship can still turn freely at any
			// speed unless explicitly configured otherwise).
			throttle = applyPivotTurnThrottleRestriction(def, throttle, this.getSyncedSidewaysInput(),
					0.02f * def.throttleUpDown().orElse(1.0f));

			// turn_speed is a direct cap: at most this many degrees per tick, full stop. Uses this.getSyncedSidewaysInput() rather than player.sidewaysSpeed directly.
			// The throttle boost above alone never
			// actually PREVENTED turning itself - gated here too, so this
			// ship genuinely can't turn at all until it's actually reached
			// pivot_turn_throttle's own minimum speed (matching CarEntity's
			// own turnRate=0 while restricted).
			if (!isPivotTurnRestricted(def, this.getSyncedSidewaysInput())) {
				float yawDelta = this.getSyncedSidewaysInput() * this.tudursvehiclemod$getEffectiveTurnSpeed();
				// A real ship's own rudder response similarly inverts with travel direction while genuinely moving backwards. Keyed off cruiseSpeed (this vehicle's own signed forward speed, set below by approachThrottledVelocity() on the PREVIOUS tick) rather than throttle input, so it follows ACTUAL motion - a ship still coasting forward while the player has just selected reverse steers as though moving forward, flipping only once genuinely travelling backwards. Exactly zero leaves steering unflipped, so a stationary ship turning in place is unaffected.
				if (this.cruiseSpeed < 0f) {
					yawDelta = -yawDelta;
				}
				this.setYaw(this.getYaw() - yawDelta);
				// Matching CarEntity's own
				// identical fix): every passenger's own view turns by
				// this SAME amount too, so their view stays fixed
				// RELATIVE to the ship as it turns, rather than staying
				// fixed in absolute world-space. Ship doesn't implement
				// FreeCameraVehicle at all (its own view was never
				// restricted to begin with), so this is the only thing
				// that keeps a passenger's own view in sync with the
				// ship's own rotation at all.
				for (Entity passenger : this.tudursvehiclemod$getRealPassengerList()) {
					passenger.setYaw(passenger.getYaw() - yawDelta);
					if (passenger instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
						serverPlayer.networkHandler.requestTeleport(
								serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
								serverPlayer.getYaw(), serverPlayer.getPitch());
					}
				}
			}

			velocity = approachThrottledVelocity(def, throttle, false);

			// Outward lean (opposite sign from SubmarineEntity's own inward lean - see this class's own doc): same sign as sideways input (turning left banks the top to the right, away from the turn).
			this.prevRoll = this.roll;
			float rollTarget = this.getSyncedSidewaysInput() * MAX_ROLL_DEGREES;
			this.roll += (rollTarget - this.roll) * ROLL_SMOOTHING;
		} else {
			// Velocity already eased towards 0 here,
			// but the STORED throttle value itself (getThrottle(), what
			// updateThrottle() actually steps FROM the next time a pilot
			// boards) was never actually touched - so it stayed frozen at
			// whatever it was left at, then applied instantly (skipping
			// the usual gradual ramp-up entirely) the moment a new pilot
			// took the controls again. Decayed here too now, same
			// gradual, symmetric idea as CarEntity's own
			// UNMANNED_THROTTLE_DECAY.
			float currentThrottle = this.getThrottle();
			float decayedThrottle = currentThrottle > 0f
					? Math.max(0f, currentThrottle - UNMANNED_THROTTLE_DECAY)
					: Math.min(0f, currentThrottle + UNMANNED_THROTTLE_DECAY);
			this.setThrottleDirect(decayedThrottle);
			// Unpiloted: eases towards 0 like every other unpiloted vehicle here, rather than just leaving whatever velocity already existed (this class's own previous behavior).
			velocity = approachThrottledVelocity(def, decayedThrottle, false);
			this.prevRoll = this.roll;
			// Reuses the exact same rollTarget formula the piloted branch above already uses - getSyncedSidewaysInput() is correctly reset to 0 on dismount (see AbstractVehicleEntity's own removePassenger() doc), so this naturally eases toward level with no separate unmanned-only roll formula needed.
			float rollTarget = this.getSyncedSidewaysInput() * MAX_ROLL_DEGREES;
			this.roll += (rollTarget - this.roll) * ROLL_SMOOTHING;
		}
		this.setPitch(0f);

		// Continuous spring towards the water surface when actually near
		// water - ported from SubmarineEntity's own surfaced-mode buoyancy
		// (see this class's own doc for why this replaces the discrete
		// gravity switch this used to have). Falls back to plain gravity
		// when no water is found nearby at all (beached, knocked into the
		// air,..) - see tudursvehiclemod$findWaterSurfaceY()'s own doc
		// for why this can't just reuse the same spring formula with a
		// "current Y" fallback.
		java.util.OptionalDouble surfaceY = this.tudursvehiclemod$findWaterSurfaceY();
		if (surfaceY.isPresent()) {
			double targetY = surfaceY.getAsDouble() + SURFACE_FLOAT_DEPTH;
			// Per tudursvehiclemod$applySurfaceFloatSpring()'s own doc: shared across every vehicle type with water buoyancy - freezes this vehicle's own Y outright once settled at a stable surface (see that method's own doc for the full reasoning), only computing/applying spring velocity while genuinely transitioning.
			double newVelY = this.tudursvehiclemod$applySurfaceFloatSpring(targetY, velocity.y,
					SURFACE_SPRING_STRENGTH, SURFACE_VERTICAL_DAMPING, SURFACE_SETTLE_POSITION_THRESHOLD, SURFACE_SETTLE_VELOCITY_THRESHOLD);
			velocity = new Vec3d(velocity.x * 0.96, newVelY, velocity.z * 0.96);
		} else {
			this.tudursvehiclemod$resetSurfaceFloatLock();
			velocity = new Vec3d(velocity.x * 0.96, velocity.y - 0.08, velocity.z * 0.96);
		}

		this.setVelocity(velocity);
		this.wheelRotation += (float) this.getVelocity().horizontalLength() * 15f;
		this.move(MovementType.SELF, this.getVelocity());
		// A Ship is always "on the water surface" whenever surfaceY itself was found at all (unlike Submarine, which also needs to check it's actually surfaced rather than diving) - see tudursvehiclemod$updateWakeTrail()'s own doc for the shared bow/stern-ripple generation this drives.
		if (surfaceY.isPresent()) {
			this.tudursvehiclemod$updateWakeTrail(surfaceY.getAsDouble(), this.tudursvehiclemod$getSignedForwardSpeed(velocity));
		}
	}

	/** Scans a small vertical range around this vehicle's own position for
	 * the topmost water block - returns empty if no water is found nearby
	 * At all (an earlier version of this fell back to
	 * this vehicle's own current Y in that case, which - combined with a
	 * non-zero SURFACE_FLOAT_DEPTH offset - made the "target" float
	 * position permanently chase "current Y + offset" every tick, a target
	 * that can never actually be caught, causing perpetual climbing (or,
	 * before SURFACE_FLOAT_DEPTH's own sign was reversed per an earlier
	 * request, perpetual sinking) for a ship or submarine that's nowhere
	 * near any water at all). Callers should skip the surface-spring logic
	 * entirely and fall back to plain gravity when this is empty. */
	protected java.util.OptionalDouble tudursvehiclemod$findWaterSurfaceY() {
		BlockPos basePos = BlockPos.ofFloored(this.getX(), this.getY(), this.getZ());
		double surfaceY = this.getY();
		boolean foundWater = false;
		for (int dy = -2; dy <= 3; dy++) {
			BlockPos checkPos = basePos.add(0, dy, 0);
			if (this.getEntityWorld().getFluidState(checkPos).isIn(FluidTags.WATER)) {
				surfaceY = checkPos.getY() + 1.0;
				foundWater = true;
			}
		}
		return foundWater ? java.util.OptionalDouble.of(surfaceY) : java.util.OptionalDouble.empty();
	}

	@Override
	public float getAnimationPhase(float tickDelta) {
		return this.prevWheelRotation + (this.wheelRotation - this.prevWheelRotation) * tickDelta;
	}
}
