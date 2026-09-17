package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Ground vehicle (car/tank-style): throttle-based cruise speed, eased
 * turning, step-up terrain climbing, optional pivot-turn speed
 * restriction, braking, and purely cosmetic ground-slope tilt. See
 * updateVehicleMovement()'s own doc for the full behavior. */
public class CarEntity extends AbstractVehicleEntity {

	public float wheelRotation;
	public float prevWheelRotation;

	public CarEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected Identifier defaultDefinitionId() {
		return Identifier.of(VehicleMod.MOD_ID, "car");
	}

	/** Dedicated block-collision hitbox, separate from the visual model. */
	@Override
	public net.minecraft.entity.EntityDimensions getDimensions(net.minecraft.entity.EntityPose pose) {
		return net.minecraft.entity.EntityDimensions.changing(1.6f, 1.0f);
	}

	/** Current eased turn rate (degrees/tick) - avoids yaw jumping instantly to full turnSpeed the moment A/D is pressed/released. */
	protected float turnRate;

	/** How fast getThrottle() spools back to 0 when unpiloted (can go negative for reverse, unlike aircraft). */
	protected static final float UNMANNED_THROTTLE_DECAY = 0.01f;

	/** Speed retained per tick while braking (0.75 = 25% loss/tick, near-stop in well under a second). */
	protected static final float BRAKE_RETENTION_PER_TICK = 0.75f;

	/** Tree-collision brake is half as strong as the ordinary brake (0.875 retained vs 0.75). */
	protected static final float TREE_COLLISION_BRAKE_RETENTION_PER_TICK = 0.875f;

	/** Horizontal speed retained per tick while genuinely airborne - gentler than braking (ordinary air resistance, not an active brake). */
	protected static final float AIRBORNE_MOMENTUM_RETENTION_PER_TICK = 0.98f;

	/** Visual-only Y catch-up after a step-up jump. */
	protected float stepUpVisualOffset;
	protected float prevStepUpVisualOffset;
	/** How much of the remaining visual catch-up gap closes each tick. */
	protected static final float STEP_UP_VISUAL_CATCHUP_RATE = 0.3f;

	/** Grace window absorbing isOnGround()'s own momentary flakiness. */
	protected int ticksSinceGrounded;
	protected static final int GROUNDED_GRACE_TICKS = 3;
	/** How many consecutive ticks this vehicle is
	 * allowed to sit immobile, too deep in water (see
	 * tudursvehiclemod$updateSubmersibleImmobilization()'s own doc),
	 * before being destroyed outright - 100 ticks (5 real seconds). */
	protected static final int WATER_IMMOBILE_DESTROY_TICKS = 100;
	/** Counts up towards WATER_IMMOBILE_DESTROY_TICKS while immobilized
	 * - see that field's own doc. Reset to 0 the instant this vehicle
	 * isn't actually too deep at all anymore. */
	protected int ticksSubmersibleImmobilized;
	/** How far above the water surface a float-capable Car's own hull sits while floating - see updateVehicleMovement()'s own new float branch. 0.0 (floats exactly at the water line) as a simple default; unlike Ship/Submarine/Aircraft this is a genuinely new capability for this vehicle type, with no prior real-world tuning history to match. */
	protected static final double CAR_SURFACE_FLOAT_DEPTH = 0.0;

	/** A small MINIMUM clamp on the step-detection look-ahead distance, only kicking in when this.getVelocity() itself is smaller than this (e.g. right as throttle is still ramping up from a stop) - see the step-detection logic's own doc for why a small clamp, not a full fixed distance, turned out to be the right balance. */
	protected static final double STEP_LOOK_AHEAD_MIN_DISTANCE = 0.1;

	/** How much target speed this vehicle loses per degree of uphill (nose-up) cosmetic tilt while climbing - see the uphill-deceleration block in updateVehicleMovement() for exactly how this is applied. Clamped to a 50% reduction at most (see that block's own MathHelper.clamp), so even MAX_COSMETIC_TILT_DEGREES worth of slope never fully stalls the vehicle outright - only ever "a little" slower, per that direct request's own wording. */
	protected static final float UPHILL_SPEED_PENALTY_PER_DEGREE = 0.01f;

	/** How much this vehicle's own turn_speed is scaled down for crawler-track vehicles specifically (0.5 = half) - see the turnRate-update block in updateVehicleMovement() for exactly where this applies. Only ever affects turnRate (yaw rotation) - cruiseSpeed/throttle (straight-line speed) is a completely separate mechanism, deliberately left untouched by this. */
	protected static final float CRAWLER_TRACK_TURN_SPEED_FACTOR = 0.5f;

	/** MC Heli's SubmersibleDamageHeight as a hard immobilize-then-destroy behavior. Returns true while immobilized, so the caller skips normal movement that tick. */
	protected boolean tudursvehiclemod$updateSubmersibleImmobilization(VehicleDefinition def) {
		// A float-capable amphibious vehicle should never immobilize/take submersible damage/be destroyed from water depth at all - it floats instead, see updateVehicleMovement()'s own new float branch below. Skips this whole mechanism entirely for such a vehicle.
		if (def.isFloatCapable()) {
			return false;
		}
		// This vehicle was getting immobilized/
		// destroyed even on dry land, nowhere near any actual water at
		// all. Two separate bugs, now both fixed:
		// 1) The water scan used to check only this vehicle's own
		// Single origin point - this now
		// samples multiple points across this vehicle's own actual
		// HITBOX footprint (getBoundingBox()) instead, at the
		// hitbox's own bottom.
		// 2) Far more critically: whenever NONE of those points found
		// any water at all, depthBelowSurface fell back to a plain
		// 0.0 - but the comparison below is "depthBelowSurface <
		// def.submergedDamageHeight()", and def.submergedDamageHeight()
		// itself defaults to 0.0 too (see that field's own doc for
		// why 0 specifically means "trigger on any touch" rather
		// than "disabled") - so "0.0 < 0.0" was FALSE, meaning "not
		// touching water at all" fell through to the SUBMERGED
		// branch below exactly as if it genuinely were submerged,
		// on any vehicle left at that default. Guarded explicitly
		// now: not touching water at all is ALWAYS treated as "not
		// submerged", full stop, regardless of what
		// submergedDamageHeight() happens to be configured as.
		net.minecraft.util.math.Box box = this.getBoundingBox();
		int bottomY = net.minecraft.util.math.MathHelper.floor(box.minY);
		java.util.List<double[]> samplePoints = java.util.List.of(
				new double[]{box.minX, box.minZ}, new double[]{box.maxX, box.minZ},
				new double[]{box.minX, box.maxZ}, new double[]{box.maxX, box.maxZ},
				new double[]{(box.minX + box.maxX) / 2.0, (box.minZ + box.maxZ) / 2.0});
		boolean touchingWaterSource = false;
		for (double[] point : samplePoints) {
			net.minecraft.util.math.BlockPos checkPos = net.minecraft.util.math.BlockPos.ofFloored(point[0], box.minY, point[1]);
			net.minecraft.fluid.FluidState fluidState = this.getEntityWorld().getFluidState(checkPos);
			if (fluidState.isIn(net.minecraft.registry.tag.FluidTags.WATER) && fluidState.isStill()) {
				touchingWaterSource = true;
				break;
			}
		}
		if (!touchingWaterSource) {
			this.ticksSubmersibleImmobilized = 0;
			return false;
		}
		// How deep this vehicle's own hitbox
		// actually sits in water - scans contiguously upward from the
		// hitbox's own bottom (at its own footprint center, now that
		// touching water at all has already been confirmed above) until
		// a non-water-source layer is found, same "stop at the first
		// break in contiguity" reasoning as before.
		double centerX = (box.minX + box.maxX) / 2.0;
		double centerZ = (box.minZ + box.maxZ) / 2.0;
		net.minecraft.util.math.BlockPos centerBase = net.minecraft.util.math.BlockPos.ofFloored(centerX, box.minY, centerZ);
		double surfaceY = bottomY + 1.0;
		for (int dy = 0; dy <= 20; dy++) {
			net.minecraft.util.math.BlockPos checkPos = centerBase.add(0, dy, 0);
			net.minecraft.fluid.FluidState fluidState = this.getEntityWorld().getFluidState(checkPos);
			if (!(fluidState.isIn(net.minecraft.registry.tag.FluidTags.WATER) && fluidState.isStill())) {
				break;
			}
			surfaceY = checkPos.getY() + 1.0;
		}
		double depthBelowSurface = surfaceY - box.minY;
		if (depthBelowSurface < def.submergedDamageHeight()) {
			this.ticksSubmersibleImmobilized = 0;
			return false;
		}
		this.ticksSubmersibleImmobilized++;
		net.minecraft.util.math.Vec3d velocity = this.getVelocity();
		this.setVelocity(0.0, velocity.y + def.gravity(), 0.0);
		this.move(net.minecraft.entity.MovementType.SELF, this.getVelocity());
		// Wheels/tracks kept visibly spinning even
		// while immobilized - setThrottleDirect(0f) alone wasn't enough,
		// since this.cruiseSpeed (this vehicle's own separate, gradually-
		// eased target speed value - not the same as either throttle or
		// actual velocity) stayed at whatever nonzero value it already
		// had right up until immobilization kicked in, and evidently
		// still drove the visible wheel/track animation regardless of
		// actual velocity being zeroed above. Reset here too, matching
		// the same convention entity.AircraftEntity's own "sinking"
		// state already uses for exactly this reason.
		this.cruiseSpeed = 0f;
		this.setThrottleDirect(0f);
		if (this.ticksSubmersibleImmobilized >= WATER_IMMOBILE_DESTROY_TICKS
				&& !this.tudursvehiclemod$isDestroyed()
				&& this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
			// Routing this through the ordinary
			// damage() path could get ENTIRELY deflected depending on
			// this vehicle's own armorDamageFactor/armorMinDamage
			// combination (see tudursvehiclemod$forceDestroy()'s own
			// doc) - some vehicles' own armor stats happened to let a
			// full-health "hit" through, others didn't, an inconsistency
			// a direct report specifically flagged. This bypasses that
			// entirely, guaranteeing destruction regardless of armor.
			this.tudursvehiclemod$forceDestroy(serverWorld);
		}
		return true;
	}

	@Override
	protected void updateVehicleMovement(VehicleDefinition def) {
		// If this vehicle is currently following a Drone Center's own GROUND route, this sets this tick's own steering/throttle inputs from that route and then falls straight through into the ordinary physics below with those inputs already in place - deliberately NOT a separate movement path of its own (see AbstractVehicleEntity's own tudursvehiclemod$updateGroundWaypointAutopilot() doc for why driving inputs, rather than velocity, is what keeps this vehicle type's own part animation/roll/sound behavior working unchanged).
		this.tudursvehiclemod$applyGroundWaypointAutopilotInputs(def);
		// Same stand-down as the other water-capable types - a sinking wreck's motion is driven by AbstractVehicleEntity's own tudursvehiclemod$applySinkingMotion(), which already ran this tick. See ShipEntity's own equivalent guard.
		if (this.tudursvehiclemod$isSinking()) {
			this.move(net.minecraft.entity.MovementType.SELF, this.getVelocity());
			return;
		}
		if (this.getControllingPassenger() == null && !this.tudursvehiclemod$isFollowingGroundRoute() && this.getEntityWorld().isClient()) {
			// This line's original purpose (keeping crawler track animation from freezing) no longer applies - crawler tracks now use directly-measured motion, not cruiseSpeed. Still needed: pruneWakeTrailOnly()/updateWakeTrail() below still consume cruiseSpeed directly for wake trail correctness.
			this.move(MovementType.SELF, this.getVelocity());
			// tudursvehiclemod$getActualForwardSpeed() must be called AFTER move() (see that method's own doc) - this.getX()/getZ() need to reflect this tick's own post-move position, not the pre-move position identical to lastTickX/lastTickZ.
			this.cruiseSpeed = this.tudursvehiclemod$getActualForwardSpeed();
			// Called UNCONDITIONALLY here, before EITHER of the water-detection/depth gates below - this car's own wake history is now still pruned/smoothed/ratcheted every tick even while unpiloted and parked somewhere water-surface detection genuinely fails (or too deep below the surface for def.submergedDamageHeight()), rather than freezing forever the moment that first happens.
			this.tudursvehiclemod$pruneWakeTrailOnly(this.cruiseSpeed);
			// This early return used to skip the wake call further down in this same method entirely (it's unreachable from here) - a genuinely unpiloted but still-moving car generated no wake at all while wading through shallow water. Same tudursvehiclemod$findWaterSurfaceYForWake()/submergedDamageHeight gating the normal path below uses.
			java.util.OptionalDouble unpilotedCarWaterSurfaceY = this.tudursvehiclemod$findWaterSurfaceYForWake();
			if (unpilotedCarWaterSurfaceY.isPresent()) {
				double unpilotedCarDepthBelowSurface = unpilotedCarWaterSurfaceY.getAsDouble() - this.getBoundingBox().minY;
				if (unpilotedCarDepthBelowSurface < def.submergedDamageHeight()) {
					this.tudursvehiclemod$updateWakeTrail(unpilotedCarWaterSurfaceY.getAsDouble(), this.cruiseSpeed);
				}
			}
			return;
		}
		this.prevWheelRotation = this.wheelRotation;

		// Enables MC Heli's own "SubmersibleDamageHeight"
		// setting for this vehicle type (previously only ever read by
		// entity.SubmarineEntity - see that class's own doc for the
		// original, gradual-damage-based use of this same field) - once
		// this vehicle's own water depth reaches/exceeds that configured
		// value at all, it becomes fully immobile (ignoring player
		// input entirely, same idea as AircraftEntity's own "sinking"
		// state) rather than continuing to drive around underwater, and
		// is destroyed outright (health set to 0) after remaining
		// immobile that way for WATER_IMMOBILE_DESTROY_TICKS (5 real
		// seconds) straight.
		if (this.tudursvehiclemod$updateSubmersibleImmobilization(def)) {
			return;
		}

		if (this.isOnGround()) {
			this.ticksSinceGrounded = 0;
		} else {
			this.ticksSinceGrounded++;
		}
		boolean effectivelyGrounded = this.ticksSinceGrounded <= GROUNDED_GRACE_TICKS;

		Entity driver = getControllingPassenger();
		// "HasDriver" gates the ONLY code path that actually reads this vehicle's own synced steering/throttle inputs - an unpiloted vehicle instead fell into the else branch far below, which force-decays throttle toward 0 (UNMANNED_THROTTLE_DECAY) and sets turnRate = 0 every single tick, discarding whatever the ground autopilot had just set. (The engine sound still followed the route's own speed because that reads the synced THROTTLE DataTracker field, which the autopilot's own setSyncedThrottleInput()->updateThrottle() chain was in fact updating correctly - only the actual MOVEMENT was being thrown away, which is exactly what the report described.) Following a ground route now takes this same piloted path: those inputs are already populated (see tudursvehiclemod$applyGroundWaypointAutopilotInputs() at the top of this method) and every piece of logic below reads them rather than the player object itself.
		boolean followingGroundRoute = this.tudursvehiclemod$isFollowingGroundRoute();
		boolean hasDriver = driver instanceof PlayerEntity || followingGroundRoute;
		if (hasDriver) {
			// Null while following a ground route with nobody aboard - safe because updateThrottle() below never actually dereferences this argument at all (it reads this.getSyncedThrottleInput() instead; see that method's own doc), and nothing else in this branch touches it.
			PlayerEntity player = driver instanceof PlayerEntity playerDriver ? playerDriver : null;
			// Uses this.getSyncedSidewaysInput()/-ThrottleInput() rather than player.sidewaysSpeed/forwardSpeed directly.
			double sidewaysInput = this.getSyncedSidewaysInput();
			// This vehicle's own HUD throttle display
			// was showing raw, instantaneous key input rather than a
			// gradually-ramped output percentage the way every other
			// vehicle's HUD does - because this used to compute throttle
			// directly from getSyncedThrottleInput() + a hold-only helper,
			// never actually RAMPING a persistent value at all.
			// updateThrottle() (the same "hold W/S to ramp up/down,
			// release to hold" cruise-control method Aircraft/Ship/
			// Submarine already share) fixes this - it already syncs the
			// THROTTLE DataTracker field HUD scripts read internally, and
			// already includes the same "hold at exactly 0% when crossing
			// from forward to reverse" behavior tudursvehiclemod$applyThrottleSwitchHold()
			// used to provide separately.
			float throttleOutput = updateThrottle(player, 0.03f * def.throttleUpDown().orElse(1.0f), def.reverseThrottle(), 1.0f);

			// PivotTurnThrottle (see MovementStats's
			// own doc / Readme_Aircraft.txt's own doc): 0 (the default)
			// means this vehicle can pivot-turn freely at any speed,
			// including a dead stop ("超信地旋回") - the ORIGINAL behavior
			// every ground vehicle here always had. A value above 0 means
			// a real tank-style "信地旋回" instead: turning is only
			// actually allowed once already moving at least that fast (as
			// a fraction of max_speed) - judged by ABSOLUTE speed (per a
			// direct request), so reverse counts too, not just forward.
			// While the player tries to turn (sidewaysInput != 0) without
			// having reached that speed yet, this automatically throttles
			// UP toward it (overriding whatever throttle the player
			// actually has, if lower) instead of turning immediately -
			// once that minimum speed is actually reached, steering
			// applies normally from then on.
			double currentSpeedAbs = Math.abs(this.cruiseSpeed);
			double pivotTurnThreshold = def.pivotTurnThrottle() * this.tudursvehiclemod$getEffectiveMaxSpeed();
			boolean pivotTurnRestricted = def.pivotTurnThrottle() > 0f && sidewaysInput != 0.0
					&& currentSpeedAbs < pivotTurnThreshold;
			double effectiveThrottleInput = throttleOutput;
			if (pivotTurnRestricted && !this.tudursvehiclemod$isOutOfFuel()) {
				// Even after accounting for both the
				// turn-speed penalty AND the uphill penalty in an
				// analytically "exact" required-throttle calculation (see
				// git history for that attempt), actual speed still
				// landed just short of pivotTurnThreshold - easing
				// (this.cruiseSpeed approaching its own target
				// asymptotically via def.acceleration()'s own blend
				// factor) can genuinely stall in floating point just
				// short of an exact target, no matter how much margin is
				// added on paper. Targeting FULL throttle instead sidesteps
				// this precision problem entirely - 1.0 is guaranteed to
				// produce a target speed comfortably above any reasonable
				// threshold (which must itself be some fraction below 1.0
				// of max speed) - while still only ever RAMPING towards it
				// at this vehicle's own ordinary step-per-tick rate (see
				// def.throttleUpDown()'s own doc), never jumping there
				// instantly.
				//
				// Throttle could end up pointed
				// FORWARD after reversing then trying to turn: the
				// previous "direction = throttleOutput < 0 ? -1f : 1f"
				// defaulted to FORWARD (+1) whenever throttleOutput was
				// exactly 0 - which includes the moment updateThrottle()'s
				// own reverse<->forward switch-hold is actively holding at
				// 0, right after reversing. Using the player's own raw,
				// un-ramped input sign instead reflects what they're
				// actually pressing, unaffected by any hold state; if
				// they're pressing neither W nor S at all (input is
				// exactly neutral, e.g. during that same hold, or simply
				// coasting), falling back to this vehicle's own current
				// cruiseSpeed sign instead continues boosting whatever
				// direction it's already actually moving in (e.g. still
				// reversing from momentum) rather than assuming forward.
				float rawInputSign = Math.signum((float) this.getSyncedThrottleInput());
				float direction = rawInputSign != 0f ? rawInputSign
						: (this.cruiseSpeed < 0f ? -1f : 1f);
				float requiredThrottle = direction;

				float step = 0.03f * def.throttleUpDown().orElse(1.0f);
				float currentPersisted = this.getThrottle();
				float rampedTowardRequired = requiredThrottle > currentPersisted
						? Math.min(requiredThrottle, currentPersisted + step)
						: Math.max(requiredThrottle, currentPersisted - step);
				// Never actually goes BELOW whatever the player's own
				// ordinary throttle input already independently ramped
				// to - this only ever ADDS extra push towards the
				// required minimum, never holds the vehicle back.
				effectiveThrottleInput = direction > 0
						? Math.max(throttleOutput, rampedTowardRequired)
						: Math.min(throttleOutput, rampedTowardRequired);
				// A one-time judder occurred exactly when
				// this restriction lifts: effectiveThrottleInput used to
				// be a purely LOCAL override, never actually written back
				// to the persisted THROTTLE value updateThrottle() itself
				// ramps - so the moment currentSpeedAbs finally crossed
				// pivotTurnThreshold and this restriction deactivated,
				// effectiveThrottleInput snapped from whatever it was
				// straight down to throttleOutput's OWN slow, independent
				// ramp - a sudden, one-time drop in the actual TARGET
				// speed that showed up as a momentary hitch. Writing this
				// same value back into the persisted throttle here means
				// updateThrottle()'s own ramp is already caught up by the
				// time this restriction lifts, so there's nothing left to
				// suddenly snap back to.
				this.setThrottleDirect((float) effectiveThrottleInput);
			}

			// This vehicle decelerates slightly while
			// climbing uphill - this.getPitch() already reflects the
			// cosmetic ground-slope tilt updateCosmeticTilt() sets each
			// tick (read here as it stands from the END of the PREVIOUS
			// tick, since that method itself only runs later, after
			// movement, this same tick). Per that method's own sign
			// convention, a NEGATIVE pitch means the nose is tilted up
			// (climbing) - only ever a mild reduction ("少し" - a little),
			// not anything like AircraftEntity's own actual stall/pitch
			// mechanics, which this is unrelated to.
			float uphillPitchDegrees = Math.max(0f, -this.getPitch());
			float uphillPenalty = MathHelper.clamp(1f - uphillPitchDegrees * UPHILL_SPEED_PENALTY_PER_DEGREE, 0.5f, 1f);

			// A persistent visual "judder" while
			// turning, confirmed unique to this vehicle (never seen on
			// Ship/Submarine/Aircraft, and reproducible even under light
			// server load) - traced to this yaw/turnRate update itself
			// having been gated behind "hasDriver && effectivelyGrounded"
			// (the isOnGround()-with-grace-period check) - the ONLY
			// vehicle type here that gates its own yaw update on ground
			// contact at all. isOnGround() can flicker false for a single
			// tick from ordinary terrain micro-unevenness alone (already
			// documented elsewhere in this class), completely independent
			// of actual server load - whenever that happened to land
			// during an active turn, this ENTIRE block used to be skipped
			// for that one tick, so turnRate simply didn't advance at
			// all that tick - a brief pause, then resuming normally, which
			// is exactly "smooth motion with an occasional skipped frame."
			// Moved out from behind that gate entirely (matching Ship's
			// own unconditional yaw update) - steering input is now always
			// responsive every single tick regardless of ground contact,
			// same as every other vehicle here already was.
			//
			// A persistent visual "judder" (worse
			// while turning) unique to this vehicle: yaw is updated HERE,
			// BEFORE approachThrottledVelocity() below, rather than after
			// it - approachThrottledVelocity() derives its own movement
			// HEADING from this.getYaw() as it stands AT THE MOMENT IT'S
			// CALLED, so computing velocity before updating yaw meant
			// every tick's own movement direction was based on the
			// PREVIOUS tick's own facing, one full tick stale relative to
			// where the vehicle actually ends up facing that same tick -
			// a constant off-by-one-tick misalignment between visual
			// orientation and actual movement direction, compounding
			// tick after tick while turning. ShipEntity's own
			// updateVehicleMovement() already does this in the correct
			// order (yaw, then approachThrottledVelocity) - this matches
			// that.
			if (!pivotTurnRestricted && !this.tudursvehiclemod$isOutOfFuel()) {
				float targetTurnRate = (float) sidewaysInput * this.tudursvehiclemod$getEffectiveTurnSpeed();
				// A real vehicle steers about its own front wheels, so backing up with the wheel held right swings the BODY left - the yaw response genuinely inverts with travel direction. This was previously applied identically regardless of which way the vehicle was actually moving, which is exactly what reads as inverted controls in reverse.
				//
				// Keyed off cruiseSpeed (this vehicle's own signed forward speed) rather than throttle input, so it follows ACTUAL motion: a vehicle still rolling forward while the player has just selected reverse steers as though moving forward, which is what physically happens, and it flips only once it is genuinely travelling backwards. Exactly zero leaves steering unflipped, so a stationary vehicle turning in place is unaffected.
				if (this.cruiseSpeed < 0f) {
					targetTurnRate = -targetTurnRate;
				}
				// This vehicle's own genuine
				// differential-steering crawler-track physics (see
				// tudursvehiclemod$getCrawlerTrackSpeed()'s own doc) turns
				// far more effectively than def.turnSpeed() was ever
				// actually tuned for - that value was originally set
				// against MC Heli's own considerably simpler/weaker
				// turning behavior, well before this project's own
				// tracks realistically modeled the actual left/right
				// speed difference a real tank track relies on to turn
				// at all. Only affects turnRate (yaw rotation itself) -
				// completely separate from cruiseSpeed/throttle, so
				// straight-line speed is entirely untouched by this.
				if (!def.crawlerTracks().isEmpty()) {
					targetTurnRate *= CRAWLER_TRACK_TURN_SPEED_FACTOR;
				}
				this.turnRate += (targetTurnRate - this.turnRate) * 0.4f;
				this.setYaw(this.getYaw() - this.turnRate);
				// Every passenger's own view turns
				// by this SAME amount too, so their view stays fixed
				// RELATIVE to the car as it turns (like actually sitting
				// in a turning car) - rather than staying fixed in
				// absolute world-space, silently rotating out of sync
				// with the car's own body as it steers. Car doesn't
				// implement FreeCameraVehicle at all (its own view was
				// never restricted to begin with - see
				// AbstractVehicleEntity's own tudursvehiclemod$usesAircraftStyleOrientation()
				// doc), so this is the only thing that keeps a
				// passenger's own view in sync with the car's own
				// rotation at all.
				for (Entity passenger : this.tudursvehiclemod$getRealPassengerList()) {
					passenger.setYaw(passenger.getYaw() - this.turnRate);
					if (passenger instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
						serverPlayer.networkHandler.requestTeleport(
								serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
								serverPlayer.getYaw(), serverPlayer.getPitch());
					}
				}
			} else {
				this.turnRate = 0f;
			}

			// Applies the uphillPenalty computed above to the final
			// effective throttle here.
			effectiveThrottleInput *= uphillPenalty;

			if (effectivelyGrounded) {
				Vec3d throttledVelocity = approachThrottledVelocity(def, (float) effectiveThrottleInput, false);
				this.setVelocity(throttledVelocity.x, this.getVelocity().y, throttledVelocity.z);

				// Brake (Space, default) - applies an
				// EXTRA deceleration independent of whatever the throttle
				// itself just did above, by directly retaining only
				// BRAKE_RETENTION_PER_TICK of the cruise speed
				// approachThrottledVelocity() already set, then rebuilding
				// the actual velocity vector from that same (now-braked)
				// speed and the vehicle's own current heading - so holding
				// the brake always wins out over the throttle, rather than
				// just being "release the throttle and coast".
				if (this.getSyncedBrakeInput()) {
					this.cruiseSpeed *= BRAKE_RETENTION_PER_TICK;
					double yawRad = Math.toRadians(this.getYaw());
					double brakedVx = -Math.sin(yawRad) * this.cruiseSpeed;
					double brakedVz = Math.cos(yawRad) * this.cruiseSpeed;
					this.setVelocity(brakedVx, this.getVelocity().y, brakedVz);
				}

				// Applies a gentler, HALF-strength
				// version of the same brake mechanism (see
				// TREE_COLLISION_BRAKE_RETENTION_PER_TICK's own doc) for
				// 1.5 seconds (30 ticks) after this vehicle actually fells
				// a tree (see treeCollisionBrakeTicksRemaining's own doc)
				// - independent of (and in addition to) whatever the
				// player's own actual brake input above just did.
				if (this.getTreeCollisionBrakeTicksRemaining() > 0) {
					this.setTreeCollisionBrakeTicksRemaining(this.getTreeCollisionBrakeTicksRemaining() - 1);
					this.cruiseSpeed *= TREE_COLLISION_BRAKE_RETENTION_PER_TICK;
					double yawRad = Math.toRadians(this.getYaw());
					double brakedVx = -Math.sin(yawRad) * this.cruiseSpeed;
					double brakedVz = Math.cos(yawRad) * this.cruiseSpeed;
					this.setVelocity(brakedVx, this.getVelocity().y, brakedVz);
				}
			} else {
				// Falling off a ledge (genuinely
				// airborne beyond GROUNDED_GRACE_TICKS, not just a
				// momentary flicker) used to leave this vehicle's own
				// horizontal velocity completely untouched - meaning
				// whatever forward momentum it had the instant it left
				// solid ground just stayed frozen in place for the ENTIRE
				// fall. Gently decaying this.cruiseSpeed itself (same
				// mechanism approachThrottledVelocity() already reads
				// from) produces a natural "carries forward, gradually
				// loses speed while airborne" arc instead.
				this.cruiseSpeed *= AIRBORNE_MOMENTUM_RETENTION_PER_TICK;
				double yawRad = Math.toRadians(this.getYaw());
				double airborneVx = -Math.sin(yawRad) * this.cruiseSpeed;
				double airborneVz = Math.cos(yawRad) * this.cruiseSpeed;
				this.setVelocity(airborneVx, this.getVelocity().y, airborneVz);
			}
		} else {
			// Genuinely unpiloted - gradually spool
			// the throttle back down towards 0 instead of holding
			// whatever it was left at, same idea as every other vehicle
			// here (e.g. AircraftEntity's own UNMANNED_THROTTLE_DECAY) -
			// handled symmetrically (moving the ABSOLUTE value towards 0,
			// not just clamping upward from below) since this vehicle's
			// own throttle can be negative (reverse), unlike aircraft.
			float currentThrottle = this.getThrottle();
			float decayedThrottle = currentThrottle > 0f
					? Math.max(0f, currentThrottle - UNMANNED_THROTTLE_DECAY)
					: Math.min(0f, currentThrottle + UNMANNED_THROTTLE_DECAY);
			this.setThrottleDirect(decayedThrottle);
			Vec3d throttledVelocity = approachThrottledVelocity(def, decayedThrottle, false);
			this.setVelocity(throttledVelocity.x, this.getVelocity().y, throttledVelocity.z);
			this.turnRate = 0f;
		}

		// Car-mounted weapons were firing with an
		// increasingly large, unexplained DOWNWARD velocity completely
		// unrelated to the shooter's own aim direction (confirmed by
		// working backward from tryFireWeapon's own diagnostic log: this
		// vehicle's own getVelocity().y was around -3 on one shot and -7
		// less than 15 seconds later, despite the vehicle looking
		// stationary). The bug was here: "gravity = 0" while grounded was
		// only ever being ADDED to whatever Y velocity already existed,
		// never actually RESETTING it - so any Y velocity ever accumulated
		// (even briefly, from a single tick where isOnGround() happened to
		// read false) just persisted and kept compounding indefinitely,
		// never actually clearing even while sitting on solid ground.
		if (effectivelyGrounded) {
			this.setVelocity(this.getVelocity().x, 0.0, this.getVelocity().z);
		} else if (def.isFloatCapable() && this.tudursvehiclemod$findWaterSurfaceYForWake().isPresent()) {
			// Not grounded, but float-capable and actually over water - uses the shared surface float spring (see AbstractVehicleEntity's own tudursvehiclemod$applySurfaceFloatSpring() doc) instead of falling, so this vehicle floats/drives on the water surface like a boat rather than sinking.
			double carSurfaceTargetY = this.tudursvehiclemod$findWaterSurfaceYForWake().getAsDouble() + CAR_SURFACE_FLOAT_DEPTH;
			double carNewVelY = this.tudursvehiclemod$applySurfaceFloatSpring(carSurfaceTargetY, this.getVelocity().y,
					SURFACE_SPRING_STRENGTH, SURFACE_VERTICAL_DAMPING, SURFACE_SETTLE_POSITION_THRESHOLD, SURFACE_SETTLE_VELOCITY_THRESHOLD);
			this.setVelocity(this.getVelocity().x, carNewVelY, this.getVelocity().z);
		} else {
			this.tudursvehiclemod$resetSurfaceFloatLock();
			this.setVelocity(this.getVelocity().x, this.getVelocity().y - 0.08, this.getVelocity().z);
		}

		this.prevStepUpVisualOffset = this.stepUpVisualOffset;

		// A fixed 0.3-block look-ahead (and the
		// down-step logic below it) badly over-triggered on sloped terrain
		// (constant up/down snapping, violent bouncing/floating) - reverted
		// to velocity-based look-ahead (naturally near-zero, and so
		// harmless, whenever this vehicle isn't actually pushing hard in a
		// direction) but with a SMALL clamped minimum, just enough to fix
		// the original report (getting stuck right at low speed, e.g.
		// pulling away from a stop, when the raw velocity vector alone was
		// too tiny to look far enough ahead in time) without being anywhere
		// near as aggressive as an unconditional fixed distance. Down-step
		// handling removed entirely - letting the vehicle just fall
		// naturally off a ledge (ordinary gravity, no special-casing) turned
		// out to be far more stable than actively trying to smooth it.
		// StepHeight is the MAXIMUM obstacle
		// height this vehicle can climb over, not a fixed jump amount - a
		// legitimately large value (e.g. 1.5 for an oversized vehicle)
		// just means it can climb over correspondingly tall obstacles, and
		// capping it (an earlier, incorrect fix) broke normal step-up
		// entirely for any vehicle configured that way. The ACTUAL
		// reported floating/bouncing came from always lifting by the
		// FULL stepHeight regardless of how tall the obstacle actually
		// was - if the real obstacle was only, say, 1 block tall but
		// stepHeight allowed climbing up to 1.5, the vehicle would
		// overshoot 0.5 blocks into open air above the actual terrain,
		// then have to fall back down - repeated every time this
		// triggered. Binary-searching for the MINIMUM height that
		// actually clears the obstacle (up to stepHeight) instead of
		// always using the full configured maximum fixes this.
		if (effectivelyGrounded && def.stepHeight() >= 0.05f) {
			Vec3d velocity = this.getVelocity();
			double horizontalSpeed = velocity.horizontalLength();
			Box currentBox = this.getBoundingBox();
			Box movedBox;
			if (horizontalSpeed > STEP_LOOK_AHEAD_MIN_DISTANCE) {
				movedBox = currentBox.offset(velocity.x, 0, velocity.z);
			} else if (horizontalSpeed > 1.0E-4) {
				double scale = STEP_LOOK_AHEAD_MIN_DISTANCE / horizontalSpeed;
				movedBox = currentBox.offset(velocity.x * scale, 0, velocity.z * scale);
			} else {
				movedBox = null; // essentially stationary - nothing to look ahead towards at all.
			}
			if (movedBox != null) {
				boolean blockedAhead = !this.getEntityWorld().isSpaceEmpty(this, movedBox);
				if (blockedAhead) {
					// Binary search (8 iterations - well under 0.01 block
					// precision) for the SMALLEST lift height, between 0
					// and this vehicle's own full configured stepHeight,
					// that actually clears the obstacle - rather than
					// always snapping the full configured maximum
					// regardless of the real obstacle's own actual height.
					float lo = 0f;
					float hi = def.stepHeight();
					boolean fullyClears = this.getEntityWorld().isSpaceEmpty(this, movedBox.offset(0, hi, 0));
					if (fullyClears) {
						for (int i = 0; i < 8; i++) {
							float mid = (lo + hi) / 2f;
							if (this.getEntityWorld().isSpaceEmpty(this, movedBox.offset(0, mid, 0))) {
								hi = mid;
							} else {
								lo = mid;
							}
						}
						float liftHeight = hi;
						this.setPosition(this.getX(), this.getY() + liftHeight, this.getZ());
						// The LOGICAL jump above still
						// happens instantly (needed for the collision check
						// itself), but this.getRenderYOffset() eases this
						// back to 0 over the next several ticks instead,
						// so the vehicle visually rises smoothly rather than
						// snapping - see that method's own doc.
						this.stepUpVisualOffset -= liftHeight;
					}
				}
			}
		}
		this.stepUpVisualOffset += (0f - this.stepUpVisualOffset) * STEP_UP_VISUAL_CATCHUP_RATE;

		this.wheelRotation += (float) this.getVelocity().horizontalLength() * 20f;

		this.move(MovementType.SELF, this.getVelocity());

		// A separate, deliberately lightweight water-surface check, NOT a refactor of that method's own more careful multi-corner touching logic - a cosmetic wake ripple doesn't need pixel-exact agreement with the damage boundary, and leaving that already-carefully-tuned method (see its own doc for the bugs it fixed) completely untouched avoids any risk of disturbing it. Called AFTER this tick's own move() above (not from inside updateSubmersibleImmobilization(), which itself runs much earlier in this same method, before this tick's own movement has actually happened yet) - tudursvehiclemod$getActualForwardSpeed() specifically needs that ordering, see its own doc for why.
		java.util.OptionalDouble carWaterSurfaceY = this.tudursvehiclemod$findWaterSurfaceYForWake();
		if (carWaterSurfaceY.isPresent()) {
			double carDepthBelowSurface = carWaterSurfaceY.getAsDouble() - this.getBoundingBox().minY;
			if (carDepthBelowSurface < def.submergedDamageHeight()) {
				this.tudursvehiclemod$updateWakeTrail(carWaterSurfaceY.getAsDouble(), this.tudursvehiclemod$getActualForwardSpeed());
			}
		}

		updateCosmeticTilt(def, MAX_COSMETIC_TILT_DEGREES, 0.2f);
	}

	/** A deliberately lightweight, wake-trail-only water-surface scan (center point only, no multi-corner touching check) - see tudursvehiclemod$updateSubmersibleImmobilization()'s own doc for the more careful, separate scan that method uses for actual damage purposes, which this intentionally does NOT share/refactor into. Scans upward from this vehicle's own hitbox bottom (center X/Z) for the topmost contiguous still water source layer, same "stop at the first break in contiguity" logic as that other scan. Empty if not touching any water source at this vehicle's own center point at all. */
	private java.util.OptionalDouble tudursvehiclemod$findWaterSurfaceYForWake() {
		net.minecraft.util.math.Box box = this.getBoundingBox();
		double centerX = (box.minX + box.maxX) / 2.0;
		double centerZ = (box.minZ + box.maxZ) / 2.0;
		net.minecraft.util.math.BlockPos basePos = net.minecraft.util.math.BlockPos.ofFloored(centerX, box.minY, centerZ);
		net.minecraft.fluid.FluidState baseFluid = this.getEntityWorld().getFluidState(basePos);
		if (!(baseFluid.isIn(net.minecraft.registry.tag.FluidTags.WATER) && baseFluid.isStill())) {
			return java.util.OptionalDouble.empty();
		}
		double surfaceY = net.minecraft.util.math.MathHelper.floor(box.minY) + 1.0;
		for (int dy = 0; dy <= 20; dy++) {
			net.minecraft.util.math.BlockPos checkPos = basePos.add(0, dy, 0);
			net.minecraft.fluid.FluidState fluidState = this.getEntityWorld().getFluidState(checkPos);
			if (!(fluidState.isIn(net.minecraft.registry.tag.FluidTags.WATER) && fluidState.isStill())) {
				break;
			}
			surfaceY = checkPos.getY() + 1.0;
		}
		return java.util.OptionalDouble.of(surfaceY);
	}

	protected static final float MAX_COSMETIC_TILT_DEGREES = 20f;
	/** Degrees of tilt per block of height difference between the two sample points. */
	protected static final float TILT_DEGREES_PER_BLOCK = 45f;

	/** Purely cosmetic, per your request: samples this vehicle's own FULL configured width/height (def.width()/height() * scale). */
	protected void updateCosmeticTilt(VehicleDefinition def, float maxTiltDegrees, float smoothing) {
		float fullWidth = Math.max(0.1f, def.width() * def.scale());
		double half = fullWidth / 2.0;
		// The lateral (roll) sampling's own range was
		// too wide - sampling all the way out to the vehicle's own full
		// half-width could pick up terrain features farther to the side
		// than what's actually representative of the ground directly
		// under the vehicle itself. Narrowed to a smaller fraction of the
		// full half-width, independent of the front/back (pitch)
		// sampling distance, which stays at the vehicle's own actual
		// full width.
		double rollHalf = half * 0.5;

		double yawRad = Math.toRadians(this.getYaw());
		double forwardX = -Math.sin(yawRad);
		double forwardZ = Math.cos(yawRad);
		double rightX = Math.cos(yawRad);
		double rightZ = Math.sin(yawRad);

		float frontOffset = sampleGroundOffset(this.getX() + forwardX * half, this.getZ() + forwardZ * half, def.stepHeight());
		float backOffset = sampleGroundOffset(this.getX() - forwardX * half, this.getZ() - forwardZ * half, def.stepHeight());
		float rightOffset = sampleGroundOffset(this.getX() + rightX * rollHalf, this.getZ() + rightZ * rollHalf, def.stepHeight());
		float leftOffset = sampleGroundOffset(this.getX() - rightX * rollHalf, this.getZ() - rightZ * rollHalf, def.stepHeight());

		// An unclimbable wall should be
		// EXCLUDED from the tilt calculation entirely (contributing no
		// extra height information of its own), NOT cause the whole axis
		// to freeze/hold at a stale value - freezing meant a genuinely
		// climbable slope on the OTHER (non-wall) side of the vehicle
		// stopped being tracked too, for as long as the wall stayed in
		// range. Each wall-flagged sample (Float.isNaN) is instead
		// treated as exactly level with the vehicle's own current
		// position (0 - no extra height either way) while the OTHER,
		// still-valid sample continues to be read completely normally -
		// so a real slope on that other side keeps being tracked
		// immediately and continuously, while the wall itself never
		// contributes any tilt bias of its own.
		float frontForPitch = Float.isNaN(frontOffset) ? 0f : frontOffset;
		float backForPitch = Float.isNaN(backOffset) ? 0f : backOffset;
		// Front HIGHER than back (climbing) should raise the nose.
		float pitchTarget = MathHelper.clamp(-(frontForPitch - backForPitch) * TILT_DEGREES_PER_BLOCK,
				-maxTiltDegrees, maxTiltDegrees);
		this.setPitch(this.getPitch() + (pitchTarget - this.getPitch()) * smoothing);

		this.prevRoll = this.roll;
		float rightForRoll = Float.isNaN(rightOffset) ? 0f : rightOffset;
		float leftForRoll = Float.isNaN(leftOffset) ? 0f : leftOffset;
		// Sign flipped once already reported backwards.
		float rollTarget = MathHelper.clamp((rightForRoll - leftForRoll) * TILT_DEGREES_PER_BLOCK,
				-maxTiltDegrees, maxTiltDegrees);
		this.roll += (rollTarget - this.roll) * smoothing;
	}

	/** How far up (positive) or down (negative) this vehicle would actually need to move, relative to its own current Y, to sit flush with the ground near (x, z). Returns Float.NaN - a sentinel meaning "unclimbable wall, ignore this sample entirely" rather than any specific height - if the vehicle's own current level is blocked here AND raising up to maxClimbableOffset (this vehicle's own def.stepHeight(), i.e. the exact same height the real step-up logic itself would ever actually climb) still doesn't clear it; see updateCosmeticTilt()'s own doc for how callers are expected to handle this sentinel (treating it as contributing no tilt, while the OTHER sample keeps being read normally). Deliberately mirrors the real step-up logic's own "does raising up to this height actually clear it" reasoning directly, rather than a separate, more roundabout geometric analysis - the same question ("can the vehicle actually get past this") answered the same way in both places. */
	protected float sampleGroundOffset(double x, double z, float maxClimbableOffset) {
		double half = 0.2;
		Box atCurrentLevel = new Box(x - half, this.getY(), z - half, x + half, this.getY() + 0.1, z + half);
		if (this.getEntityWorld().isSpaceEmpty(this, atCurrentLevel)) {
			// Not blocked at the vehicle's own current level - check for a
			// DROP (descending terrain/ledge) by scanning downward for
			// where solid ground actually starts.
			for (float offset = 0f; offset >= -1.0f; offset -= 0.1f) {
				Box probe = new Box(x - half, this.getY() + offset - 0.1, z - half,
						x + half, this.getY() + offset, z + half);
				if (!this.getEntityWorld().isSpaceEmpty(this, probe)) {
					return offset;
				}
			}
			return 0f;
		}
		// Blocked at the vehicle's own current level - incrementally check
		// whether raising up to maxClimbableOffset (exactly mirroring the
		// real step-up logic's own height) ever actually clears it.
		for (float testHeight = 0.1f; testHeight <= maxClimbableOffset + 0.001f; testHeight += 0.1f) {
			Box raised = new Box(x - half, this.getY() + testHeight, z - half,
					x + half, this.getY() + testHeight + 0.1, z + half);
			if (this.getEntityWorld().isSpaceEmpty(this, raised)) {
				return testHeight;
			}
		}
		return Float.NaN;
	}

	@Override
	public float getAnimationPhase(float tickDelta) {
		return this.prevWheelRotation + (this.wheelRotation - this.prevWheelRotation) * tickDelta;
	}

	/** See AbstractVehicleEntity's own doc for the full explanation - eases out this vehicle's own step-up logic's instant position jump, purely for rendering. */
	@Override
	public float getRenderYOffset(float tickDelta) {
		return this.prevStepUpVisualOffset + (this.stepUpVisualOffset - this.prevStepUpVisualOffset) * tickDelta;
	}

	/** Current (non-interpolated) tick value - see AbstractVehicleEntity's own getRenderYOffsetCurrent() doc. */
	@Override
	public float getRenderYOffsetCurrent() {
		return this.stepUpVisualOffset;
	}
}
