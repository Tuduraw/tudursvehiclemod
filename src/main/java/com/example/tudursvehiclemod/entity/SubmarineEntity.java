package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Submarine: key-controlled only, surfaced/diving modes (H key toggles). Full behavior detail: README.md "潜水艦(SubmarineEntity)の現在の実装". */
public class SubmarineEntity extends AbstractVehicleEntity implements FreeCameraVehicle {

	/** A submarine's own center of mass sits below the waterline, so
	 * centrifugal force during a turn leans it INWARD (toward the turn) -
	 * the opposite of ShipEntity's own outward lean. */
	protected static final float MAX_SURFACE_ROLL_DEGREES = 8f;
	/** Halved roll change rate (double the time to reach the roll limit) - see ShipEntity's own identical fix for the full derivation. 0.1 -> ~0.0513. */
	protected static final float ROLL_SMOOTHING = 0.0513f;
	protected static final float MAX_DIVE_PITCH_DEGREES = 45f;
	/** Halved (was 1.0). */
	protected static final float DIVE_PITCH_RATE_PER_TICK = 0.5f;
	/** Eases the ACTUALLY-applied pitch rate towards its target each tick, rather than snapping straight to it - gives both a gentle ramp-up when starting and a gentle ramp-down when releasing (including when reversing direction, which naturally takes a moment to ease through zero this way). */
	protected static final float PITCH_RATE_SMOOTHING = 0.08f;
	/** Attitude tolerance (degrees) for tudursvehiclemod$tryToggleHatch()'s own "must be level to surface" gate. */
	protected static final float LEVEL_ATTITUDE_TOLERANCE_DEGREES = 3f;
	/** Fixed delay (2 seconds) before the surfaced->diving PHYSICS switch takes effect on a vehicle with no hatch part to animate at all - see this class's own doc. */
	protected static final int NO_HATCH_PART_TRANSITION_TICKS = 40;

	/** The gentle, natural sinking this hull used to
	 * have (before gravity/buoyancy was disabled while diving) is now the
	 * actual descend mechanism - applied while the descend key is held
	 * down, rather than a speed the level keys directly set. */
	protected static final double DIVE_GRAVITY_SINK_RATE = 0.02;
	/** MC Heli's own SubmergedDamageHeight tick rate - see tudursvehiclemod$updateSubmergedDamage()'s own doc. */
	protected static final int SUBMERGED_DAMAGE_INTERVAL_TICKS = 20;
	protected static final float SUBMERGED_DAMAGE_PER_TICK = 2.0f;
	/** Same grace period as AircraftEntity's own level-flight assist (100 ticks = 5 seconds). */
	protected static final int LEVEL_ASSIST_GRACE_TICKS = 100;
	protected static final float LEVEL_ASSIST_SMOOTHING = 0.02f;
	/** Gentler than DIVE_PITCH_RATE_PER_TICK - like AircraftEntity's own stall, but eases rather than snapping. */
	protected static final float SURFACE_BROACH_PITCH_CORRECTION_RATE = 0.3f;
	/** Extra downforce while broaching (nose-up at the surface while diving), so the hull doesn't keep climbing into open air. */
	protected static final double SURFACE_BROACH_EXTRA_DOWNFORCE = 0.03;
	/** Forces nose-down once this fraction of the hull's own actual length (not just "any part") is above the water surface. */
	protected static final double BROACH_EMERGED_FRACTION_THRESHOLD = 1.0 / 3.0;

	/** How far above the water surface this hull tries to float; offset currently disabled (0.0). */
	protected static final double SURFACE_FLOAT_DEPTH = 0.0;

	protected static final float THROTTLE_STEP = 0.02f;
	protected static final float MAX_FORWARD_THROTTLE = 1.0f;
	/** 2 seconds at 20 ticks/second. */
	protected static final int THROTTLE_SWITCH_HOLD_TICKS = 40;
	protected static final float UNMANNED_THROTTLE_DECAY = 0.01f;
	/** This hull's own yaw turn rate is 3x def.turnSpeed() (see this constant's own call site) - submarines otherwise felt too sluggish turning compared to every other vehicle type here. */
	protected static final float YAW_TURN_SPEED_MULTIPLIER = 3f;

	public float propellerRotation;
	public float prevPropellerRotation;

	protected int noPitchInputTicks;
	protected int throttleSwitchHoldTicksRemaining;
	protected float smoothedPitchRate;
	/** Ticks remaining before the next SubmergedDamageHeight tick of damage - see tudursvehiclemod$updateSubmergedDamage()'s own doc. */
	protected int submergedDamageCooldown;
	/** Ticks since isHatchOpen() last became false - see NO_HATCH_PART_TRANSITION_TICKS's own doc. */
	protected int ticksSinceDiveRequested;

	public SubmarineEntity(EntityType<?> type, World world) {
		super(type, world);
		// HATCH_OPEN's own generic default is now true (open/deployed,
		// key-toggleable parts start deployed
		// on spawn) - which happens to already mean "surfaced" for a
		// submarine too (see this class's own doc), so this call is
		// technically redundant now, but kept explicit so this reads
		// correctly regardless of what the generic default happens to be.
		this.setHatchOpen(true);
	}

	/** Extends vanilla's collision box downward only (never the top - for why moving the top breaks isTouchingWater()). Overrides calculateDefaultBoundingBox(Vec3d), since getBoundingBox()/calculateBoundingBox() are final in Entity. */
	@Override
	protected net.minecraft.util.math.Box calculateDefaultBoundingBox(Vec3d pos) {
		net.minecraft.util.math.Box vanillaBox = super.calculateDefaultBoundingBox(pos);
		float offsetY = tudursvehiclemod$getBoundingBoxYOffset();
		if (offsetY == 0f) {
			return vanillaBox;
		}
		return new net.minecraft.util.math.Box(vanillaBox.minX, vanillaBox.minY + offsetY, vanillaBox.minZ,
				vanillaBox.maxX, vanillaBox.maxY, vanillaBox.maxZ);
	}

	/** Checks the protected definition field directly rather than calling getDefinition() (- calling getDefinition() this early permanently caches the wrong result). */
	protected float tudursvehiclemod$getBoundingBoxYOffset() {
		VehicleDefinition def = this.definition;
		if (def == null) {
			return 0f;
		}
		java.util.Optional<Float> minY = com.example.tudursvehiclemod.asset.ServerObjModelZExtent.getMinY(def.model());
		if (minY.isEmpty()) {
			return 0f;
		}
		// The 1-block margin must be added
		// AFTER scaling (i.e. in final, already-converted Minecraft block
		// units), not before. This model's own raw.obj coordinates may
		// use a completely different unit scale than Minecraft blocks
		// (e.g. a 100:1 ratio), with def.scale()
		// itself being what converts from that raw unit into actual
		// blocks - adding a plain "+1" to the RAW minY before that
		// conversion made the intended "1 block" margin shrink by that
		// exact same ratio (down to ~0.01 blocks, i.e. negligible),
		// rather than staying a real, full block regardless of whatever
		// raw unit this specific model's own file happens to use.
		float offsetY = minY.get() * def.scale() + 1f;
		return offsetY;
	}

	@Override
	protected Identifier defaultDefinitionId() {
		return Identifier.of(VehicleMod.MOD_ID, "submarine");
	}

	/** True (surfaced) = weapons work normally; false (diving) = frozen entirely EXCEPT for Torpedo (see the weapon-type-aware overload below). */
	@Override
	public boolean tudursvehiclemod$canFireWeapons() {
		return this.isHatchOpen();
	}

	/** Torpedo stays usable while diving (this project's own original behavior), same as any weapon with its own UsableWhileDiving flag set - configurable per-weapon rather than hardcoded to Torpedo alone (see WeaponStats's own usableWhileDiving doc). Every other weapon freezes (both firing and aim-tracking). */
	@Override
	public boolean tudursvehiclemod$canFireWeapons(java.util.Optional<com.example.tudursvehiclemod.asset.WeaponDefinition> weapon) {
		if (this.isHatchOpen()) {
			return true;
		}
		return weapon.isPresent() && (weapon.get().weaponType() == com.example.tudursvehiclemod.asset.WeaponType.TORPEDO
				|| weapon.get().usableWhileDiving());
	}

	/** Always true - submarines have no locked-view mode/toggle key, unlike aircraft. */
	@Override
	public boolean isFreeLook() {
		return true;
	}

	/** Vanilla's own fluid push force was fighting this vehicle's velocity control underwater (most visible as the level-ascend/descend keys seeming not to work). */
	@Override
	public boolean isPushedByFluids() {
		return false;
	}

	/** No-op - landing gear is unconditionally disabled for submarines; without this, G would still flip the raw flag the HUD reads directly. */
	@Override
	public void toggleLandingGear() {
	}

	/** Always reports stowed, since toggleLandingGear() above can no longer change gearDeployed's own default (true). */
	@Override
	public boolean isGearDeployed() {
		return false;
	}

	/** How close to the water surface (blocks) this hull can be and still surface - doesn't need to have fully broken the surface. */
	protected static final double SURFACE_TOGGLE_TOLERANCE_BLOCKS = 2.0;

	/** Surfacing requires already being at/above the surface and level; diving is always accepted immediately (physics-side transition is what's delayed). */
	@Override
	public boolean tudursvehiclemod$tryToggleHatch() {
		if (this.isHatchOpen()) {
			this.toggleHatch();
			return true;
		}
		// Surfacing acceptance:
		// this used to check the raw fluid state at this hull's own
		// exact (X, Y, Z) - which, right at the waterline (exactly where
		// surfaced mode's own buoyancy spring actually settles this hull
		// to rest - see SURFACE_FLOAT_DEPTH's own doc), is genuinely
		// ambiguous/boundary-sensitive depending on which side of a block
		// boundary this hull's own Y happens to land on, and could read
		// as "still in water" even once meaningfully surfaced. Using
		// tudursvehiclemod$findWaterSurfaceY()'s own already-more-robust
		// computation instead (empty = no water found nearby at all, i.e.
		// already clear of water entirely) avoids that boundary issue -
		// and per a further direct request, this hull no longer needs to
		// have fully broken the surface at all, just be within
		// SURFACE_TOGGLE_TOLERANCE_BLOCKS of it.
		java.util.OptionalDouble surfaceY = this.tudursvehiclemod$findWaterSurfaceY();
		boolean atOrAboveSurface = surfaceY.isEmpty() || this.getY() >= surfaceY.getAsDouble() - SURFACE_TOGGLE_TOLERANCE_BLOCKS;
		boolean levelAttitude = Math.abs(this.getPitch()) <= LEVEL_ATTITUDE_TOLERANCE_DEGREES
				&& Math.abs(this.getRoll()) <= LEVEL_ATTITUDE_TOLERANCE_DEGREES;
		if (atOrAboveSurface && levelAttitude) {
			this.toggleHatch();
			return true;
		}
		return false;
	}

	/** True once the hatch toggle-part has settled at isHatchOpen()'s own target, or NO_HATCH_PART_TRANSITION_TICKS elapsed - whichever first. Only delays surfaced->diving; the reverse is gated at the toggle itself instead. */
	protected boolean tudursvehiclemod$isHatchAnimationSettled() {
		if (this.ticksSinceDiveRequested >= NO_HATCH_PART_TRANSITION_TICKS) {
			return true;
		}
		String firstKeyPartName = null;
		for (com.example.tudursvehiclemod.asset.TogglePart part : this.getDefinition().toggleParts()) {
			if ("key".equals(part.trigger())) {
				firstKeyPartName = part.part();
				break;
			}
		}
		if (firstKeyPartName == null) {
			return false; // no hatch part - already handled by the fixed-tick cap above, so just wait for it.
		}
		// This hull's hatch part uses the OPPOSITE angle convention from other vehicles' hatch/canopy parts - must match that same inverted target.
		float target = this.isHatchOpen() ? 0f : 1f;
		float progress = this.getTogglePartProgress(firstKeyPartName);
		return Math.abs(progress - target) < 0.02f;
	}

	/** Ramps throttle towards W/S (or natural decay unpiloted), with two submarine-specific rules: reverse capped at reverseThrottle (not -1.0), and crossing between positive/negative forces a THROTTLE_SWITCH_HOLD_TICKS pause at 0% first. */
	protected float tudursvehiclemod$updateSubmarineThrottle(boolean piloted) {
		if (this.throttleSwitchHoldTicksRemaining > 0) {
			this.throttleSwitchHoldTicksRemaining--;
			this.setThrottleDirect(0f);
			return 0f;
		}

		float current = this.getThrottle();
		float next = current;
		// This method never checked fuel at all (unlike the shared updateThrottle() other vehicle types use, which zeroes current for outOfFuel||wingLocked) - added the same gate here.
		boolean outOfFuel = this.tudursvehiclemod$isOutOfFuel();

		if (piloted) {
			if (outOfFuel) {
				next = 0f;
			} else {
				float step = THROTTLE_STEP * this.getDefinition().throttleUpDown().orElse(1.0f);
				float input = this.getSyncedThrottleInput();
				if (input > 0) {
					next = Math.min(MAX_FORWARD_THROTTLE, current + step);
				} else if (input < 0) {
					next = Math.max(this.getDefinition().reverseThrottle(), current - step);
				}
				if ((current > 0 && next <= 0 && input < 0) || (current < 0 && next >= 0 && input > 0)) {
					next = 0f;
					this.throttleSwitchHoldTicksRemaining = this.getDefinition().throttleSwitchHoldTicks().orElse(THROTTLE_SWITCH_HOLD_TICKS);
				}
			}
		} else {
			// Unpiloted: eases towards 0 from either side - never crosses zero, so the switch-hold above never applies here.
			if (current > 0) {
				next = Math.max(0f, current - UNMANNED_THROTTLE_DECAY);
			} else if (current < 0) {
				next = Math.min(0f, current + UNMANNED_THROTTLE_DECAY);
			}
		}

		this.setThrottleDirect(next);
		return next;
	}

	/** Eases pitch towards the target rate for the currently-held key - see
	 * PITCH_RATE_SMOOTHING's own doc for the ramp up/down (including when
	 * reversing direction, which eases smoothly through zero rather than
	 * snapping). */
	protected void tudursvehiclemod$updateDivePitch(boolean pitchUp, boolean pitchDown) {
		float targetRate = pitchUp ? -DIVE_PITCH_RATE_PER_TICK : (pitchDown ? DIVE_PITCH_RATE_PER_TICK : 0f);
		this.smoothedPitchRate += (targetRate - this.smoothedPitchRate) * PITCH_RATE_SMOOTHING;

		if (pitchUp || pitchDown) {
			this.noPitchInputTicks = 0;
			float newPitch = MathHelper.clamp(this.getPitch() + this.smoothedPitchRate,
					-MAX_DIVE_PITCH_DEGREES, MAX_DIVE_PITCH_DEGREES);
			this.setPitch(newPitch);
		} else {
			this.noPitchInputTicks++;
			if (this.noPitchInputTicks >= LEVEL_ASSIST_GRACE_TICKS) {
				this.setPitch(this.getPitch() + (0f - this.getPitch()) * LEVEL_ASSIST_SMOOTHING);
			}
		}
	}

	/** Max speed while diving specifically (def.maxSpeed() is surfaced-only). Falls back to effective max speed/3 if dive_max_speed isn't configured. */
	protected float tudursvehiclemod$getDiveMaxSpeed(VehicleDefinition def) {
		return def.diveMaxSpeed().orElse(this.tudursvehiclemod$getEffectiveMaxSpeed() / 3f);
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
			// Unlike every other vehicle type's own version of this same "skip local physics recomputation, but still apply move() with the network-synced velocity" guard (CarEntity/ShipEntity/HelicopterEntity/StaticEmplacementEntity/VtolEntity), this one was a bare return - no move() call at all. That's exactly the FIRST, broken attempt at this guard documented elsewhere (docs/IMPLEMENTATION_NOTES.md "滑走路上エンティティの振動問題"): leaving the client relying purely on vanilla's own passive position interpolation, with none of the velocity-driven smoothing that move() itself provides - producing visible teleport-like stutter for an unpiloted submarine specifically (surfaced and drifting, being carried on a runway, settled after its own pilot disembarked mid-motion, etc.) Corrected to match every other vehicle type's own already-working version.
			// Also fixes a second, related bug this same investigation surfaced: this.roll (this hull's own visible bank angle - a plain, non-DataTracker field VehicleEntityRenderer.java's own state.roll actually renders, confirmed by direct reference there, unlike CarEntity's own since-corrected false lead) was ALSO frozen by this early return, at whatever angle it happened to hold the instant this guard first engaged - a submarine that was actively banking (surfaced, turning) the moment its own pilot disembarked would otherwise stay visually tilted forever afterward, never leveling out, even though the server's own roll correctly decays back to 0 the whole time (both this hull's own surfaced-with-no-input and diving branches below target exactly 0 roll once genuinely unpiloted). Replicated that same "decay toward level" formula here.
			this.prevRoll = this.roll;
			this.roll += (0f - this.roll) * ROLL_SMOOTHING;
			this.move(MovementType.SELF, this.getVelocity());
			// Called UNCONDITIONALLY here, before the water-detection check below - this submarine's own wake history is now still pruned/smoothed/ratcheted every tick even while unpiloted and water-surface detection genuinely fails, rather than freezing forever the moment that first happens.
			this.tudursvehiclemod$pruneWakeTrailOnly(this.tudursvehiclemod$getSignedForwardSpeed(this.getVelocity()));
			// This early return used to skip both of this method's own wake calls further down entirely (both unreachable from here) - a genuinely unpiloted but still-moving submarine generated no wake at all, surfaced or diving. tudursvehiclemod$findTrueWaterSurfaceY() (the wide-range search - see that method's own doc) is used here rather than the narrow findWaterSurfaceY(), since this branch doesn't know whether this submarine is currently surfaced or diving.
			java.util.OptionalDouble unpilotedSurfaceY = this.tudursvehiclemod$findTrueWaterSurfaceY();
			if (unpilotedSurfaceY.isPresent()) {
				this.tudursvehiclemod$updateWakeTrail(unpilotedSurfaceY.getAsDouble(), this.tudursvehiclemod$getSignedForwardSpeed(this.getVelocity()));
			}
			return;
		}
		this.prevPropellerRotation = this.propellerRotation;
		this.prevRoll = this.roll;

		boolean hatchOpen = this.isHatchOpen();
		if (hatchOpen) {
			this.ticksSinceDiveRequested = 0;
		} else {
			this.ticksSinceDiveRequested++;
		}
		// Per this class's own doc: surfaced->diving keeps using surfaced
		// physics until the hatch has actually finished closing (or, with
		// no hatch part, the fixed delay above has elapsed); diving->
		// surfaced has no equivalent delay here since it's gated at the
		// toggle itself instead (see tudursvehiclemod$tryToggleHatch()).
		boolean surfaced = hatchOpen || !this.tudursvehiclemod$isHatchAnimationSettled();

		LivingEntity pilotEntity = getControllingPassenger();
		PlayerEntity player = pilotEntity instanceof PlayerEntity p ? p : null;
		boolean brokeSurfaceWhileDiving = false;

		this.propellerRotation += 12f;
		// Per the same direct report handled in Car/ShipEntity's own equivalent gates (see CarEntity's own doc there): a submarine following a Drone Center ground route counts as piloted for throttle purposes, otherwise this method's own unpiloted branch decays throttle to zero and discards the autopilot's own input entirely. Steering below already reads getSyncedSidewaysInput() unconditionally, so it needs no equivalent change.
		float throttle = this.tudursvehiclemod$updateSubmarineThrottle(player != null || this.tudursvehiclemod$isFollowingGroundRoute());
		// A real submarine's own rudder similarly
		// needs some minimum speed to actually turn it effectively - see
		// MovementStats's own pivot_turn_throttle doc / AbstractVehicleEntity's
		// own applyPivotTurnThrottleRestriction() doc for exactly how this
		// works (a no-op whenever pivot_turn_throttle is its own default
		// 0, i.e. this submarine can still turn freely at any speed unless
		// explicitly configured otherwise).
		throttle = applyPivotTurnThrottleRestriction(def, throttle, this.getSyncedSidewaysInput(),
				THROTTLE_STEP * def.throttleUpDown().orElse(1.0f));
		// The throttle boost above alone never
		// actually PREVENTED turning itself - gated here too, so this
		// submarine genuinely can't turn at all until it's actually
		// reached pivot_turn_throttle's own minimum speed (matching
		// CarEntity's own turnRate=0 while restricted).
		if (!isPivotTurnRestricted(def, this.getSyncedSidewaysInput())) {
			float yawDelta = this.getSyncedSidewaysInput() * this.tudursvehiclemod$getEffectiveTurnSpeed() * YAW_TURN_SPEED_MULTIPLIER;
			// A real submarine's own rudder response similarly inverts with travel direction while genuinely moving backwards. Keyed off cruiseSpeed (this vehicle's own signed forward speed) rather than throttle input, so it follows ACTUAL motion, flipping only once genuinely travelling backwards - a stationary submarine turning in place is unaffected.
			if (this.cruiseSpeed < 0f) {
				yawDelta = -yawDelta;
			}
			this.setYaw(this.getYaw() - yawDelta);
			// Matching Car/ShipEntity's own identical fix:
			// every passenger's own view turns by this
			// SAME amount too, so their view stays fixed RELATIVE to the
			// submarine as it turns. Submarine implements
			// FreeCameraVehicle for its own pitch-aiming purposes, but
			// its own YAW still turns independently of the pilot's view
			// via plain A/D input, same as Car/Ship - nothing otherwise
			// keeps a passenger's own view in sync with that.
			for (Entity passenger : this.tudursvehiclemod$getRealPassengerList()) {
				passenger.setYaw(passenger.getYaw() - yawDelta);
				if (passenger instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
					serverPlayer.networkHandler.requestTeleport(
							serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
							serverPlayer.getYaw(), serverPlayer.getPitch());
				}
			}
		}

		// Everything below computes into this ONE local variable, with a
		// SINGLE this.setVelocity() call at the very end - same pattern as
		// HelicopterEntity's own updateVehicleMovement() (which sets
		// velocity exactly once, combining horizontal cruise and vertical
		// Throttle together) - this class's own
		// earlier multiple-setVelocity()-calls-per-tick structure was
		// apparently not reliably sticking for the level-ascend/descend
		// keys specifically, despite extensive investigation into vanilla
		// water-related hooks not finding a conclusive cause.
		Vec3d velocity;

		if (surfaced) {
			velocity = approachThrottledVelocity(def, throttle, false);
			float rollTarget = -this.getSyncedSidewaysInput() * MAX_SURFACE_ROLL_DEGREES;
			this.roll += (rollTarget - this.roll) * ROLL_SMOOTHING;
			this.setPitch(0f);
			this.noPitchInputTicks = 0;
			this.smoothedPitchRate = 0f;
			this.submergedDamageCooldown = 0;

			// Continuous spring towards the water surface when actually
			// near water (see this class's own doc for why, over a
			// discrete gravity switch) - falls back to plain gravity when
			// no water is found nearby at all (see
			// tudursvehiclemod$findWaterSurfaceY()'s own doc for why this
			// can't just reuse the same spring formula with a "current Y"
			// fallback).
			java.util.OptionalDouble surfaceY = this.tudursvehiclemod$findWaterSurfaceY();
			if (surfaceY.isPresent()) {
				double targetY = surfaceY.getAsDouble() + SURFACE_FLOAT_DEPTH;
				// Per tudursvehiclemod$applySurfaceFloatSpring()'s own doc: shared across every vehicle type with water buoyancy - freezes this vehicle's own Y outright once settled at a stable surface, only computing/applying spring velocity while genuinely transitioning.
				double newVelY = this.tudursvehiclemod$applySurfaceFloatSpring(targetY, velocity.y,
						SURFACE_SPRING_STRENGTH, SURFACE_VERTICAL_DAMPING, SURFACE_SETTLE_POSITION_THRESHOLD, SURFACE_SETTLE_VELOCITY_THRESHOLD);
				velocity = new Vec3d(velocity.x * 0.96, newVelY, velocity.z * 0.96);
				// See tudursvehiclemod$updateWakeTrail()'s own doc for the shared bow/stern-ripple generation this drives. Uses this tick's own about-to-be-applied velocity directly (this.getVelocity() would still read LAST tick's value at this point, since setVelocity() itself hasn't run yet this tick).
				this.tudursvehiclemod$updateWakeTrail(surfaceY.getAsDouble(), this.tudursvehiclemod$getSignedForwardSpeed(velocity));
			} else {
				this.tudursvehiclemod$resetSurfaceFloatLock();
				velocity = new Vec3d(velocity.x * 0.96, velocity.y - 0.08, velocity.z * 0.96);
			}
		} else {
			this.tudursvehiclemod$resetSurfaceFloatLock();
			boolean wouldBroach = this.tudursvehiclemod$isBroaching(def);

			// Per the same investigation as HelicopterEntity's own ascend
			// input (see tudursvehiclemod$isPilotJumping()'s own doc) -
			// player.isJumping() never reads true for a mounted pilot.
			boolean pitchUp = player != null && tudursvehiclemod$isPilotJumping(player);
			boolean pitchDown = this.isDescending();
			if (wouldBroach) {
				// Pitch key input is entirely suppressed while forcibly
				// Correcting nose-up-at-surface - the
				// correction's own rate was too weak to overcome the
				// player's own held pitch-up input otherwise.
				brokeSurfaceWhileDiving = true;
				pitchUp = false;
				pitchDown = false;
			}
			this.tudursvehiclemod$updateDivePitch(pitchUp, pitchDown);
			if (wouldBroach) {
				this.setPitch(Math.min(0f, this.getPitch() + SURFACE_BROACH_PITCH_CORRECTION_RATE));
			}

			// The seabed-slope tilt feature (pitch/
			// roll following terrain once resting on the bottom) has been
			// removed entirely - it caused real, unresolved bugs for some
			// models (premature triggering, halting descent early) even
			// after several rounds of tuning its own trigger threshold.
			// No banking underwater at all now, same as always before
			// this feature existed.
			this.roll += (0f - this.roll) * ROLL_SMOOTHING;

			// (1) Pitch-based 3D forward/backward movement - like an aircraft. uses this hull's own dedicated dive-mode max speed (falls back to maxSpeed/3 if not configured) rather than sharing surfaced mode's own maxSpeed.
			velocity = approachThrottledVelocity(def, throttle, true, tudursvehiclemod$getDiveMaxSpeed(def));

			// (2) Descend: this hull's own gentle natural sinking (the
			// gravity/buoyancy it used to always have while diving, before
			// that was disabled) - active only while the descend key is
			// Actually held down (no longer a
			// toggle).
			boolean descendKeyHeld = this.getSyncedLevelDescendInput();
			// (3) Ascend: reuses surfaced mode's own spring-towards-the-
			// surface buoyancy, but skips everything else surfacing
			// normally involves (hatch state, pitch/roll reset) - active
			// only while the ascend key is held, and simply rises.
			boolean ascendKeyHeld = this.getSyncedLevelAscendInput();

			boolean nearWater = this.tudursvehiclemod$isNearWater();
			// The wake trail used to be generated ONLY in the surfaced branch above - completely disabled while diving, even during a partial broach where part of the hull genuinely is touching the water surface.
			java.util.OptionalDouble nearSurfaceY = nearWater ? this.tudursvehiclemod$findWaterSurfaceY() : java.util.OptionalDouble.empty();
			if (ascendKeyHeld && nearWater) {
				double surfaceY = nearSurfaceY.orElse(this.getY());
				double targetY = surfaceY + SURFACE_FLOAT_DEPTH;
				double yError = targetY - this.getY();
				double newVelY = (velocity.y + yError * SURFACE_SPRING_STRENGTH) * SURFACE_VERTICAL_DAMPING;
				velocity = new Vec3d(velocity.x, newVelY, velocity.z);
				this.submergedDamageCooldown = 0;
			} else if (nearWater) {
				if (descendKeyHeld) {
					velocity = velocity.add(0, -DIVE_GRAVITY_SINK_RATE, 0);
				}
				if (brokeSurfaceWhileDiving) {
					velocity = velocity.add(0, -SURFACE_BROACH_EXTRA_DOWNFORCE, 0);
				}
				this.tudursvehiclemod$updateSubmergedDamage(def);
			} else {
				// Diving but knocked clear of any water (e.g. beached) - falls normally instead of hanging in midair.
				velocity = velocity.add(0, -0.04, 0);
				this.submergedDamageCooldown = 0;
			}
			// NearSurfaceY above (findWaterSurfaceY()) searches only a narrow window CENTERED ON this vehicle's own current position - appropriate for the ascend-spring calc just above (this vehicle IS always close to the true surface while actively near it), but wrong for the wake trail specifically: when genuinely deep, that narrow window reports a "surface" near wherever this vehicle's own current Y happens to be rather than the true, possibly-distant one, making the wake trail's own Y position appear to follow this vehicle down. tudursvehiclemod$findTrueWaterSurfaceY() searches upward for the actual water-to-air transition instead - see that method's own doc. Gated by the same cheap nearWater check as the branches above, avoiding the more expensive wider search entirely when this vehicle isn't anywhere near water at all (e.g., beached).
			java.util.OptionalDouble wakeSurfaceY = nearWater ? this.tudursvehiclemod$findTrueWaterSurfaceY() : java.util.OptionalDouble.empty();
			if (wakeSurfaceY.isPresent()) {
				this.tudursvehiclemod$updateWakeTrail(wakeSurfaceY.getAsDouble(), this.tudursvehiclemod$getSignedForwardSpeed(velocity));
			}
		}

		this.setVelocity(velocity);
		this.move(MovementType.SELF, this.getVelocity());
	}

	/** Always false. Internal code that needs nearby-water info uses tudursvehiclemod$isNearWater()/findWaterSurfaceY() instead. */
	@Override
	public boolean isTouchingWater() {
		return false;
	}

	@Override
	public boolean isSubmergedInWater() {
		return false;
	}

	/** Once per second, damages this vehicle if deeper than submergedDamageHeight() below the surface (MC Heli's own setting - without it, this framework's own vehicles take damage immediately upon any submersion). Uses findWaterSurfaceY() directly, not isTouchingWater() (always false here). */
	protected void tudursvehiclemod$updateSubmergedDamage(VehicleDefinition def) {
		double surfaceY = this.tudursvehiclemod$findWaterSurfaceYOrCurrent();
		double depthBelowSurface = surfaceY - this.getY();
		if (depthBelowSurface <= def.submergedDamageHeight()) {
			this.submergedDamageCooldown = 0;
			return;
		}
		if (this.submergedDamageCooldown > 0) {
			this.submergedDamageCooldown--;
			return;
		}
		this.submergedDamageCooldown = SUBMERGED_DAMAGE_INTERVAL_TICKS;
		if (this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
			// Routing this through the ordinary
			// damage() path could get entirely deflected depending on
			// this vehicle's own armorDamageFactor/armorMinDamage
			// combination (see tudursvehiclemod$applyRawDamage()'s own
			// doc) - a small, fixed 2.0-per-tick hit is exactly the kind
			// of amount an armored submarine's own stats could easily
			// reduce below its own armorMinDamage threshold, meaning
			// this damage would silently never actually apply at all.
			this.tudursvehiclemod$applyRawDamage(serverWorld, SUBMERGED_DAMAGE_PER_TICK);
		}
	}

	/** Direct fluid-state check (isTouchingWater() is always false here) for whether this vehicle is at/below the water surface. */
	protected boolean tudursvehiclemod$isNearWater() {
		BlockPos basePos = BlockPos.ofFloored(this.getX(), this.getY(), this.getZ());
		for (int dy = -1; dy <= 1; dy++) {
			if (this.getEntityWorld().getFluidState(basePos.add(0, dy, 0)).isIn(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** Topmost water block near this vehicle, or empty if none nearby - callers should fall back to plain gravity (not current Y) when empty. */
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

	/** findWaterSurfaceY() searches only a narrow window centered on this vehicle's own position - fine near the surface, but wrong when deeply submerged (reports a "surface" near this vehicle's own current Y instead of the true, distant one, making the wake trail appear to follow it down). This searches upward instead for the true water-to-air transition, up to WAKE_SURFACE_SEARCH_MAX_BLOCKS above (empty if not found - too deep for wake generation). Falls back to a downward search when this vehicle's own current position is just above the true surface (e.g. Used only for the wake trail while diving; findWaterSurfaceY() itself is unchanged for its own buoyancy callers. */
	protected java.util.OptionalDouble tudursvehiclemod$findTrueWaterSurfaceY() {
		BlockPos basePos = BlockPos.ofFloored(this.getX(), this.getY(), this.getZ());
		if (this.getEntityWorld().getFluidState(basePos).isIn(FluidTags.WATER)) {
			boolean sawWater = false;
			for (int dy = 0; dy <= WAKE_SURFACE_SEARCH_MAX_BLOCKS; dy++) {
				BlockPos checkPos = basePos.add(0, dy, 0);
				boolean isWater = this.getEntityWorld().getFluidState(checkPos).isIn(FluidTags.WATER);
				if (isWater) {
					sawWater = true;
				} else if (sawWater) {
					return java.util.OptionalDouble.of(checkPos.getY());
				}
			}
			return java.util.OptionalDouble.empty();
		}
		for (int dy = -1; dy >= -WAKE_SURFACE_SEARCH_MAX_BLOCKS; dy--) {
			BlockPos checkPos = basePos.add(0, dy, 0);
			if (this.getEntityWorld().getFluidState(checkPos).isIn(FluidTags.WATER)) {
				return java.util.OptionalDouble.of(checkPos.getY() + 1.0);
			}
		}
		return java.util.OptionalDouble.empty();
	}

	/** How far above this vehicle's own current position tudursvehiclemod$findTrueWaterSurfaceY() searches for the true water-to-air transition - large enough to comfortably cover this project's own largest submarine hulls' full diving depth relative to their own waterline, without an unbounded (and potentially very expensive, if this vehicle is beached deep underground with water somehow far overhead) search. */
	private static final int WAKE_SURFACE_SEARCH_MAX_BLOCKS = 64;

	/** Same as findWaterSurfaceY() but falls back to current Y - correct for updateSubmergedDamage()/isBroaching(), not for the buoyancy-spring callers. */
	protected double tudursvehiclemod$findWaterSurfaceYOrCurrent() {
		return this.tudursvehiclemod$findWaterSurfaceY().orElse(this.getY());
	}

	/** True once BROACH_EMERGED_FRACTION of this hull's own actual length (from its model's Z extent) is above the surface while nose-up. Falls back to a simpler "any part above the surface" check if the model can't be read. */
	protected boolean tudursvehiclemod$isBroaching(VehicleDefinition def) {
		if (this.getPitch() >= 0f) {
			return false;
		}
		boolean submergedAtOrigin = this.getEntityWorld()
				.getFluidState(BlockPos.ofFloored(this.getX(), this.getY(), this.getZ()))
				.isIn(FluidTags.WATER);

		java.util.Optional<Float> modelLengthZ = com.example.tudursvehiclemod.asset.ServerObjModelZExtent.getLengthZ(def.model());
		if (modelLengthZ.isEmpty() || modelLengthZ.get() <= 0f) {
			return !submergedAtOrigin;
		}

		float hullLength = modelLengthZ.get() * def.scale();
		float pitchRad = (float) Math.toRadians(this.getPitch());
		// Positive (nose-up, since getPitch() < 0 here) half-span between the nose and the entity's own Y.
		double halfVerticalSpan = (hullLength / 2.0) * -Math.sin(pitchRad);
		if (halfVerticalSpan <= 0.0) {
			return !submergedAtOrigin;
		}
		double noseY = this.getY() + halfVerticalSpan;
		double tailY = this.getY() - halfVerticalSpan;
		double surfaceY = this.tudursvehiclemod$findWaterSurfaceYOrCurrent();
		if (surfaceY <= tailY) {
			return true;
		}
		if (surfaceY >= noseY) {
			return false;
		}
		double emergedFraction = (noseY - surfaceY) / (noseY - tailY);
		return emergedFraction >= BROACH_EMERGED_FRACTION_THRESHOLD;
	}

	@Override
	public float getAnimationPhase(float tickDelta) {
		return this.prevPropellerRotation + (this.propellerRotation - this.prevPropellerRotation) * tickDelta;
	}
}
