package com.example.tudursvehiclemod.entity.projectile;

import com.example.tudursvehiclemod.registry.ModEntityTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fired by AbstractVehicleEntity#tryFireWeapon. */
public class VehicleProjectileEntity extends ThrownItemEntity {

	protected static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/ExplosionFlash");

	/** The specific vehicle that fired this projectile (set right after
	 * construction, in AbstractVehicleEntity's own tryFireWeapon()) - NOT
	 * the same thing as getOwner() (the PLAYER who fired it), which this
	 * mod's own tudursvehiclemod$updateCustomHitDetection() can't use to
	 * reliably exclude a vehicle's own just-fired shot from itself, since
	 * a projectile's owner is always the shooting player, never the
	 * vehicle itself. */
	protected com.example.tudursvehiclemod.entity.AbstractVehicleEntity firingVehicle;

	/** Set once, right after construction. */
	public void tudursvehiclemod$setFiringVehicle(com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
		this.firingVehicle = vehicle;
	}

	/** The specific vehicle that fired this projectile, or null if it wasn't fired from a vehicle at all (shouldn't normally happen for this class, but defensively allowed). */
	public com.example.tudursvehiclemod.entity.AbstractVehicleEntity tudursvehiclemod$getFiringVehicle() {
		return this.firingVehicle;
	}

	/** WeaponType.BOMB (true) vs WeaponType.DEPTH (false) - see WeaponType's own doc for the distinction. Set once, right after construction, by tryFireWeapon; irrelevant (never checked) for any other weapon type. */
	protected boolean explodeOnWaterContact;
	/** So the water-contact check below only fires once, the FIRST tick this projectile is actually touching water, rather than re-triggering (or re-discarding an already-discarded projectile) every subsequent tick it remains submerged. */
	protected boolean wasTouchingWaterLastTick;

	/** Called once, right after construction, by tryFireWeapon - see this field's own doc. */
	public void tudursvehiclemod$setExplodeOnWaterContact(boolean value) {
		this.explodeOnWaterContact = value;
	}

	/** How big a splash this specific projectile's own first water contact produces (see tudursvehiclemod$updateWaterContactExplosion()'s own doc) - scaled to the firing weapon's own configured bulletScale (1.0 = an ordinary, unscaled bullet's own splash size) rather than being a fixed size regardless of the projectile's own visual size. */
	protected float splashScale = 1.0f;

	/** Called once, right after construction, by tryFireWeapon - see this field's own doc. */
	public void tudursvehiclemod$setSplashScale(float value) {
		this.splashScale = value;
	}

	protected float damage = 4.0f;

	/** Public accessor for this projectile's own base damage - used by AbstractVehicleEntity's own tudursvehiclemod$estimateProjectileDamage() (custom hit detection). */
	public float tudursvehiclemod$getDamage() {
		return this.damage;
	}
	/** Matches vanilla's own thrown-item gravity (snowballs/eggs) by default. */
	protected float gravity = 0.03f;
	/** Explosion = <power> from the firing weapon's own stats (see WeaponStats.explosionPower()'s own doc). */
	protected float explosionPower = 0.0f;
	/** ExplosionInWater - used INSTEAD of explosionPower when detonating while touching water - see WeaponStats's own doc. Defaults to matching explosionPower until tudursvehiclemod$setExplosionPowerInWater() is called, so a weapon with none configured still behaves consistently regardless of environment. */
	protected float explosionPowerInWater = 0.0f;
	protected boolean explosionDestroysBlocks = false;
	/** ExplosionBlock's own independent block-destruction power - see WeaponStats's own doc. -1 (the default here, matching WeaponStats.FALLBACK's own sentinel) means "not explicitly set in the weapon's own file", resolved at detonation time in tudursvehiclemod$explodeIfConfigured() by falling back to the normal explosionPower/explosionPowerInWater instead. */
	protected float explosionBlockPower = -1f;
	/** Flaming - only actually applied if explosionPower > 0, per Readme_Weapon.txt's own documented note that Flaming is meaningless without an explosion. */
	protected boolean flaming = false;

	/** ExplosionAltitude - see WeaponStats's own doc. 0 (the default) disables this feature entirely. */
	protected float explosionAltitude = 0.0f;
	/** 20 ticks (1 second) after firing before tudursvehiclemod$updateExplosionAltitude() can trigger at all - see that method's own doc for why. */
	protected static final int EXPLOSION_ALTITUDE_GRACE_TICKS = 20;
	/** DelayFuse - see WeaponStats's own -1-sentinel doc. */
	protected int delayFuseTicks = -1;
	/** TimeFuse - see WeaponStats's own -1-sentinel doc. */
	protected int timeFuseTicks = -1;
	/** Ticks since this projectile actually HIT something, once tudursvehiclemod$startFuseCountdown() is called - -1 means "hasn't hit anything (that started a fuse countdown) yet". */
	protected int ticksSinceImpact = -1;
	/** Bound - see WeaponStats's own doc. 0 (the default) means no bounce at all - the original stop-on-first-hit behavior. */
	protected float bounceStrength = 0.0f;
	/** GravityInWater - see WeaponStats's own doc. Defaults to matching plain gravity until tudursvehiclemod$setGravityInWater() is called, same "stay consistent with the ordinary case" reasoning as explosionPowerInWater's own default above. */
	protected float gravityInWater = 0.03f;
	/** Piercing - see WeaponStats's own doc. 0 (the default) is the original, unpierced behavior. */
	protected int piercingCount = 0;
	/** How many blocks this projectile has already pierced through so far - see tudursvehiclemod$tryPierceBlock()'s own doc. */
	protected int piercedSoFar = 0;
	/** FAE - see WeaponStats's own doc. */
	protected boolean fuelAirExplosive = false;
	/** BulletColor - see WeaponStats's own doc. Opaque white (no tint) is the default, matching this project's own original, uncolored bullet appearance before this field existed. */
	protected int bulletColor = 0xFFFFFFFF;
	/** BulletColorInWater - see WeaponStats's own doc. Defaults to matching bulletColor until tudursvehiclemod$setBulletColors() is called. */
	protected int bulletColorInWater = 0xFFFFFFFF;
	/** RigidityTime - AAMissile/ATMissile only, see WeaponStats's own doc. 7 is Readme_Weapon.txt's own documented default. */
	protected int rigidityTimeTicks = 7;
	/** TrajectoryParticle - see WeaponStats's own doc. Empty (the default) means no trail at all. */
	protected java.util.Optional<String> trajectoryParticle = java.util.Optional.empty();
	/** TrajectoryParticleStartTick - see WeaponStats's own doc. */
	protected int trajectoryParticleStartTick = 0;
	/** DisableSmoke - see WeaponStats's own doc. */
	protected boolean disableSmoke = false;
	/** ProximityFuseDist - AAMissile/ATMissile only, see WeaponStats's own doc. 0 (the default) disables this entirely - only an actual direct hit detonates a guided missile. */
	protected float proximityFuseDist = 0.0f;

	/** Set via tudursvehiclemod$setGuidanceTargetEntity. */
	protected Integer guidanceTargetEntityId;
	/** A guided missile self-destructs rather than flying forever when it genuinely can't close on its own target (out-turned or outrun) - the closest this missile has gotten to guidanceTargetEntityId/guidanceTargetPos SINCE the current guidance attempt began (reset whenever tudursvehiclemod$setGuidanceTargetEntity() is called with a new target, or a flare/lost-lock event similarly restarts tracking - see LOST_TARGET_TIMEOUT_TICKS's own doc for how this gets used). Double.MAX_VALUE (the initial value) means "no distance recorded yet this attempt". */
	private double guidanceBestDistance = Double.MAX_VALUE;
	/** Consecutive ticks guidanceBestDistance hasn't actually improved (gotten smaller) by at least LOST_TARGET_DISTANCE_MARGIN - see LOST_TARGET_TIMEOUT_TICKS's own doc. */
	private int guidanceStagnantTicks;
	/** 3 seconds (60 ticks) of the target's own distance never meaningfully improving is treated as "lost" and triggers tudursvehiclemod$explodeAsLostTarget(). Long enough that ordinary tracking noise (a target briefly maneuvering away before the missile's own turn catches back up) doesn't false-positive, short enough that a genuinely un-catchable target doesn't drag the missile along for its own entire remaining flight time first. */
	private static final int LOST_TARGET_TIMEOUT_TICKS = 60;
	/** A distance improvement smaller than this (blocks) doesn't count as "closing" for LOST_TARGET_TIMEOUT_TICKS's own purposes - without this floor, an extremely slow, technically-still-shrinking distance (a near-exact speed match, barely gaining) would reset the stagnant-tick counter every single tick and never actually trigger, despite being functionally the same "can't catch it" situation the whole feature exists to detect. */
	private static final double LOST_TARGET_DISTANCE_MARGIN = 0.05;
	/** An off-boresight angle wider than this, combined with being inside the missile's own turn radius (see WIDE_ORBIT_RADIUS_MARGIN's own doc), is the trigger for flying straighter instead of turning at full rate - roughly "the steering point is behind the missile's own effective turning ability", not just "slightly off to one side" (which ordinary full-rate pursuit already handles fine on its own). */
	private static final double WIDE_ORBIT_ANGLE_THRESHOLD_DEGREES = 90.0;
	/** A small safety margin (>1.0) on top of the missile's own bare minimum turn radius (speed/turnRate) - triggers the straighter-flight correction a little before the missile is ALREADY hopelessly circling, rather than only once it's undeniably too late for full-rate turning to have ever worked from here. */
	private static final double WIDE_ORBIT_RADIUS_MARGIN = 1.5;
	/** The reduced turn-rate fraction used while WIDE_ORBIT_ANGLE_THRESHOLD_DEGREES/WIDE_ORBIT_RADIUS_MARGIN are both true - small enough that the missile flies close to straight (letting it actually separate and open the geometry back up), but non-zero so it still drifts gradually toward better alignment the whole time, rather than needing a separate "now turn again" trigger of its own. */
	private static final float WIDE_ORBIT_TURN_RATE_FRACTION = 0.15f;
	/** Non-null while this missile's own guidance has been disabled by a target's flare, counting DOWN each tick - reaching 0 triggers tudursvehiclemod$explodeAsLostTarget(), same as a genuinely lost target. Null (the default) means "not flare-disabled" - a missile that was never guided at all, or one still actively homing, is entirely unaffected by this field. */
	private Integer flareDisabledTicksRemaining;
	/** "無効化されたミサイルは誘導なしで数秒飛翔したあとに..自爆する" (a flare-disabled missile flies unguided for several seconds, then self-destructs) - 3 seconds (60 ticks), matching LOST_TARGET_TIMEOUT_TICKS's own duration for consistency between this project's two "give up and self-destruct" timers. */
	private static final int FLARE_DISABLE_SELF_DESTRUCT_TICKS = 60;
	/** Set via tudursvehiclemod$setGuidanceTargetPos. */
	protected Vec3d guidanceTargetPos;
	/** ATMissile's ModeNum=2 "TAモード" (Top Attack): climbs toward a point above the target instead of heading straight at it, then dives steeply once close - see tudursvehiclemod$updateGuidance()'s own doc. */
	protected boolean topAttack;
	/** How high above the target tudursvehiclemod$updateGuidance() aims while climbing in Top Attack mode. */
	protected static final double TOP_ATTACK_CLIMB_HEIGHT = 15.0;
	/** Horizontal distance to the target at which Top Attack mode switches from climbing to diving straight at it. */
	protected static final double TOP_ATTACK_DIVE_TRIGGER_DISTANCE = 8.0;
	/** WeaponType.AS_WEAPON only (see that enum's own doc) - set via tudursvehiclemod$setAntiSubmarineDive(). Non-null only for an actual ASWeapon shot. How close (blocks, DiveDistance) to guidanceTargetPos this projectile must get before actually diving - see tudursvehiclemod$updateGuidance()'s own doc for the two-phase (level flight above water, then dive) logic this drives. */
	protected Float antiSubmarineDiveDistance;
	/** WeaponType.AS_WEAPON only - the weapon's own AccelerationInWater/VelocityInWater/TargetDepth, captured at fire time but deliberately NOT applied to tudursvehiclemod$setUnderwaterCruiseCapable() until the dive actually begins (see antiSubmarineDiveDistance's own doc) - applying them immediately at launch would let this projectile transition into TORPEDO-style underwater cruise the instant it merely CLIPPED any water at all (a wave, a puddle) while still flying its own level cruise phase toward the target, long before it was actually meant to dive. */
	protected float antiSubmarineAccelerationInWater;
	protected float antiSubmarineVelocityInWater;
	protected float antiSubmarineTargetDepthOffset;
	/** How far (blocks) above the local water surface an ASWeapon's own level cruise phase flies - see tudursvehiclemod$updateGuidance()'s own doc. Purely cosmetic/practical margin so it visibly skims above the waves rather than exactly at surface height (which risks isTouchingWater() intermittently flickering true on wave crests). */
	protected static final double ANTI_SUBMARINE_CRUISE_ALTITUDE_ABOVE_WATER = 5.0;
	/** TVMissile control gradually slowed to a
	 * dead stop mid-flight (see tudursvehiclemod$updateTvMissileControl()'s
	 * own tvCruiseSpeed doc for the full explanation - vanilla's own
	 * ThrownItemEntity-derived drag, and tudursvehiclemod$steerTowards()
	 * used to just read back and preserve whatever CURRENT, already-
	 * decayed speed it found each tick instead of ever correcting for
	 * it): the exact same bug pattern turned out to affect EVERY guided
	 * missile type here (AS_MISSILE/AA_MISSILE/AT_MISSILE/MK_ROCKET),
	 * not just TVMissile - they just usually hit their own target before
	 * the decay became severe enough to actually notice. Captured once,
	 * the instant guidance actually starts (see
	 * tudursvehiclemod$setGuidanceTargetPos()/tudursvehiclemod$setGuidanceTargetEntity()'s
	 * own doc) - the launch speed, before drag has had any chance to act
	 * on it at all - and tudursvehiclemod$steerTowards() now uses THIS
	 * fixed value for its own final reconstruction instead of the
	 * current (possibly already-decayed) speed it's passed. */
	protected float guidedCruiseSpeed;
	/** How many degrees per tick a guided projectile can adjust its own heading by. */
	protected float turnRateDegreesPerTick = 3.0f;
	/** Per Readme_Weapon.txt's own TVMissile doc: which player (if any) is
	 * currently steering this specific missile directly via their own
	 * mouse (see tudursvehiclemod$setTvControlled()'s own doc) - null
	 * once control has never started, OR has already been released (a
	 * hit, or straying too far/out of a loaded chunk - see
	 * tudursvehiclemod$updateTvMissileControl()'s own doc). Deliberately
	 * NOT synced via DataTracker - the CLIENT learns which entity ID
	 * it's now controlling via network.TvMissileControlStartPayload
	 * instead (see that payload's own doc), a one-time, explicit
	 * notification rather than a continuously-synced value, since only
	 * the ONE controlling player's own client actually needs this at
	 * all. */
	protected Integer tvControllingPlayerId;
	/** Raw mouse deltas relayed from network.TvMissileInputPayload since
	 * this was last consumed (every tick, in
	 * tudursvehiclemod$updateTvMissileControl()) - same "accumulate on
	 * the network thread, consume once per tick" shape as
	 * AircraftEntity's own pendingYawInput/pendingPitchInput. */
	public float pendingTvYawInput;
	public float pendingTvPitchInput;
	/** How many degrees/tick tudursvehiclemod$updateTvMissileControl() can steer this missile by by per unit of accumulated player input - see that method's own doc. */
	protected static final float TV_MISSILE_TURN_RATE_DEGREES_PER_TICK = 4.0f;
	/** Once a TV-guided missile strays this far
	 * (blocks) from wherever it was actually FIRED from, control is
	 * released (see tudursvehiclemod$updateTvMissileControl()'s own doc)
	 * even if its own current chunk still happens to be loaded - a
	 * generous, but still finite, real-world "you've lost the video
	 * feed" range. */
	protected static final double TV_MISSILE_MAX_CONTROL_RANGE = 250.0;
	/** Where this missile was actually fired from - the origin point tudursvehiclemod$updateTvMissileControl()'s own max-range check measures against. */
	protected Vec3d tvLaunchPos;
	/** A TV-guided missile gradually slowed to a
	 * dead stop mid-flight: vanilla's own ThrownItemEntity-derived drag
	 * (applied every tick by super.tick(), same as an ordinary snowball/
	 * egg losing speed through the air) was quietly decelerating this
	 * missile's own velocity - and tudursvehiclemod$updateTvMissileControl()
	 * used to just read back whatever that CURRENT (already-decayed)
	 * speed happened to be each tick and preserve THAT going forward,
	 * compounding the same drag tick after tick instead of ever
	 * correcting for it - the exact same reasoning
	 * tudursvehiclemod$updateUnderwaterCruise()'s own doc already
	 * explains for a torpedo's own CRUISING phase, which actively RAMPS
	 * back toward a fixed target speed every tick rather than merely
	 * preserving whatever it currently reads as. Captured once, at
	 * tudursvehiclemod$setTvControlled()'s own call time (the launch
	 * speed, before drag has had any chance to act on it at all) - see
	 * that method's own doc. */
	protected float tvCruiseSpeed;
	/** This missile's own displayed orientation
	 * (and, since client.mixin.CameraMixin's own camera override reads
	 * directly from it, the controlling player's own camera too)
	 * gradually pitched downward during otherwise-level flight, self-
	 * correcting instantly the moment the player actually moved their
	 * mouse: tudursvehiclemod$updateTvMissileControl() used to
	 * RE-EXTRACT its own "current" yaw/pitch from this.getVelocity()
	 * every single tick, rather than maintaining its own independent
	 * running value - since that extraction happens AFTER super.tick()
	 * (vanilla's own ThrownItemEntity-derived physics) has already had a
	 * chance to act on the previous tick's velocity, any tiny asymmetry
	 * in how that physics treats the vertical component versus the
	 * horizontal ones would quietly compound into the SAME steering
	 * baseline tick after tick, even with literally zero player input at
	 * all - moving the mouse simply overwrote that drifted value with a
	 * fresh, correct one again, momentarily masking the issue rather
	 * than fixing it. These two track this missile's own COMMANDED
	 * orientation independently instead - initialized once from the
	 * actual launch direction, then updated ONLY by the player's own
	 * accumulated input deltas from that point on - so nothing about
	 * vanilla's own downstream velocity physics can ever feed back into
	 * the steering baseline at all. */
	protected float tvCurrentYaw;
	protected float tvCurrentPitch;
	/** This missile's own view shook violently
	 * and was nearly impossible to actually control: tudursvehiclemod$updateTvMissileControl()
	 * used to only ever run inside the SERVER-only half of tick() at
	 * all (same as tudursvehiclemod$updateGuidance()'s own placement) -
	 * meaning the CLIENT never locally predicted this missile's own
	 * steering at all, purely waiting on however often rotation/position
	 * sync packets happen to arrive (and fighting its OWN separate,
	 * still-active generic velocity-derived yaw/pitch recompute at the
	 * bottom of tick() in the meantime, unlike the server's copy, which
	 * had that skipped) - exactly the kind of jarring, stepped visual
	 * update a torpedo's own tudursvehiclemod$updateUnderwaterCruise()
	 * doc already describes needing client-side prediction to avoid.
	 * This flag (a plain, common field - deliberately NOT a reference to
	 * client.TvMissileControlState directly, since this class lives in
	 * the COMMON source set and loads on a dedicated server too, which
	 * has no client module - or client classes - to reference at all) is
	 * what actually lets tudursvehiclemod$updateTvMissileSteering() run
	 * identically on both sides: true the instant EITHER
	 * tudursvehiclemod$setTvControlled() (server) or
	 * tudursvehiclemod$initTvClientTracking() (client, called externally
	 * by client.VehicleModClient's own TvMissileControlStartPayload
	 * receiver) has actually initialized tvCruiseSpeed/tvCurrentYaw/
	 * tvCurrentPitch for real. */
	protected boolean tvSteeringActive;
	/** Once a TV-guided missile's own control ends
	 * (see tudursvehiclemod$releaseTvControl()'s own doc), it should
	 * gently sink towards the ground and eventually crash/detonate
	 * there, rather than just falling back to vanilla's own ordinary
	 * ThrownEntity physics (the same drag/rotation-lerp behavior this
	 * class's own steering routes AROUND while actively controlled -
	 * see tick()'s own doc - which this project's own testing found
	 * unstable for a fast, long-lived guided munition in the first
	 * place). Set true the instant tvSteeringActive transitions from
	 * true to false (see tudursvehiclemod$releaseTvControl()'s/
	 * tudursvehiclemod$clearTvClientTracking()'s own doc) - stays true
	 * for the rest of this missile's own remaining lifetime (a released
	 * missile never resumes normal flight). */
	protected boolean tvPostControlDescentActive;

	/** Public accessor for tvSteeringActive - lets client.VehicleModClient's
	 * own tick handler check whether THIS specific entity has actually
	 * had tudursvehiclemod$initTvClientTracking() called on it yet, as a
	 * fallback retry for the (observed) case where
	 * network.TvMissileControlStartPayload's own receiver ran before
	 * this missile's own spawn packet had actually been processed by
	 * this same client yet. */
	public boolean tudursvehiclemod$isTvSteeringActive() {
		return this.tvSteeringActive;
	}

	/** Client-side counterpart to tudursvehiclemod$setTvControlled()'s
	 * own tvCruiseSpeed/tvCurrentYaw/tvCurrentPitch initialization (see
	 * tvSteeringActive's own doc for why this needs its own, separately-
	 * callable entry point rather than just reusing that same server-
	 * only method directly) - called by client.VehicleModClient's own
	 * TvMissileControlStartPayload receiver, on whichever local copy of
	 * this missile the payload's own entityId resolves to. */
	public void tudursvehiclemod$initTvClientTracking() {
		Vec3d launchVelocity = this.getVelocity();
		double launchSpeed = launchVelocity.length();
		this.tvCruiseSpeed = (float) launchSpeed;
		if (launchSpeed > 1.0E-6) {
			this.tvCurrentYaw = (float) Math.toDegrees(Math.atan2(-launchVelocity.x, launchVelocity.z));
			this.tvCurrentPitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(launchVelocity.y / launchSpeed, -1.0, 1.0)));
		} else {
			this.tvCurrentYaw = this.getYaw();
			this.tvCurrentPitch = this.getPitch();
		}
		this.tvSteeringActive = true;
	}

	/** Client-side counterpart to tudursvehiclemod$releaseTvControl() -
	 * called by client.VehicleModClient's own TvMissileControlEndPayload
	 * receiver, so this missile's own local prediction actually stops
	 * the instant the server says control has ended, rather than
	 * continuing to predict (harmlessly, but pointlessly) forever
	 * afterward. */
	public void tudursvehiclemod$clearTvClientTracking() {
		this.tvSteeringActive = false;
		this.tvPostControlDescentActive = true;
		this.pendingTvYawInput = 0f;
		this.pendingTvPitchInput = 0f;
	}
	/** Torpedo only (see WeaponType's own doc) - see tudursvehiclemod$updateUnderwaterCruise()'s own doc for the 2 phases this cycles through. */
	protected enum UnderwaterCruisePhase {FALLING, CRUISING}
	/** Synced via DataTracker (unlike the other underwater-cruise fields below, which only ever need to be correct on the SERVER) - this flag being a plain, unsynced field meant the CLIENT's own copy of this entity never learned it should stop applying gravity once touching water (see getGravity()'s own doc), so the client kept independently simulating a continued fall/pitch-down locally, out of step with the server, until the next position-sync packet briefly corrected it. */
	protected static final net.minecraft.entity.data.TrackedData<Boolean> UNDERWATER_CRUISE_CAPABLE =
			net.minecraft.entity.data.DataTracker.registerData(VehicleProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.BOOLEAN);
	/** Synced via DataTracker - same exact reasoning as UNDERWATER_CRUISE_CAPABLE's
	 * own doc just above, just for AS_MISSILE/AA_MISSILE/AT_MISSILE/
	 * MK_ROCKET's own guidance instead of a torpedo's own underwater
	 * cruise: guidanceTargetPos/guidanceTargetEntityId (what
	 * getGravity() used to check directly) are only ever populated
	 * SERVER-side (see tudursvehiclemod$setGuidanceTargetPos()'s/
	 * tudursvehiclemod$setGuidanceTargetEntity()'s own doc) - the
	 * CLIENT's own copy of this entity never learned it should stop
	 * applying gravity at all, so gravity's own contribution kept
	 * getting baked into the client's own locally-simulated position
	 * every tick regardless, causing exactly the same kind of
	 * persistent, client-only downward drift TVMissile's own
	 * tvSteeringActive doc describes in detail. */
	protected static final net.minecraft.entity.data.TrackedData<Boolean> GUIDED_MISSILE_ACTIVE =
			net.minecraft.entity.data.DataTracker.registerData(VehicleProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.BOOLEAN);
	/** True the instant CRUISING begins - the
	 * torpedo's own visual orientation didn't match its actual travel
	 * direction (particularly noticeable on an angled shot): only the
	 * SERVER ever flips this (see tudursvehiclemod$updateUnderwaterCruise()'s
	 * own doc) - the CLIENT only ever reads it. The FALLING->CRUISING
	 * transition captures a specific "locked in" snapshot of this
	 * torpedo's own velocity at one exact instant (see
	 * CRUISE_YAW_DEGREES/CRUISE_INITIAL_PITCH's own doc) - letting the
	 * client independently decide FOR ITSELF when that instant occurs
	 * (which it used to, before this field existed) meant the client
	 * could lock in a snapshot from a slightly different tick than the
	 * server's own transition, capturing a slightly different velocity
	 * value - and unlike ordinary position/rotation, which are corrected
	 * by ordinary sync, this was a ONE-TIME decision baked in permanently
	 * at that moment, so any client/server difference at that single
	 * instant then persisted, uncorrected, for the torpedo's entire
	 * remaining CRUISING lifetime - visibly diverging the client's own
	 * displayed yaw from the server's own actual movement direction. */
	protected static final net.minecraft.entity.data.TrackedData<Boolean> CRUISE_STARTED =
			net.minecraft.entity.data.DataTracker.registerData(VehicleProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.BOOLEAN);
	/** Locked the instant CRUISING begins (i.e. the moment this torpedo first touches water) - the fixed yaw (degrees) it cruises along afterward; only PITCH keeps changing after that (see tudursvehiclemod$updateUnderwaterCruise()'s own doc). Synced via DataTracker - see CRUISE_STARTED's own doc for why. */
	protected static final net.minecraft.entity.data.TrackedData<Float> CRUISE_YAW_DEGREES =
			net.minecraft.entity.data.DataTracker.registerData(VehicleProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.FLOAT);
	/** This torpedo's own pitch at the exact moment CRUISING begins - this is preserved and eased towards level (0) as this torpedo approaches its own target depth, rather than being flattened to level immediately. Synced via DataTracker - see CRUISE_STARTED's own doc for why. */
	protected static final net.minecraft.entity.data.TrackedData<Float> CRUISE_INITIAL_PITCH =
			net.minecraft.entity.data.DataTracker.registerData(VehicleProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.FLOAT);
	/** (Y at the moment CRUISING begins) - (target depth Y) - the total depth error this torpedo needs to close before reaching its own target depth; used purely as the denominator for how much of that error has closed so far (see tudursvehiclemod$updateUnderwaterCruise()'s own doc). Synced via DataTracker - see CRUISE_STARTED's own doc for why. */
	protected static final net.minecraft.entity.data.TrackedData<Float> CRUISE_INITIAL_DEPTH_ERROR =
			net.minecraft.entity.data.DataTracker.registerData(VehicleProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.FLOAT);
	/** This torpedo's own target cruise depth (an absolute world Y), computed once at the moment CRUISING begins as (water surface Y at that moment) - (the firing weapon's own TargetDepth stat - see WeaponStats's own doc). Synced via DataTracker - see CRUISE_STARTED's own doc for why. */
	protected static final net.minecraft.entity.data.TrackedData<Float> CRUISE_TARGET_DEPTH_Y =
			net.minecraft.entity.data.DataTracker.registerData(VehicleProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.FLOAT);
	protected UnderwaterCruisePhase underwaterCruisePhase = UnderwaterCruisePhase.FALLING;
	/** Ramps from 0 up to accelerationInWaterTarget once CRUISING begins - see tudursvehiclemod$updateUnderwaterCruise()'s own doc. */
	protected float currentCruiseSpeed;
	/** Target cruise speed (blocks/tick) once fully underway - set via tudursvehiclemod$setUnderwaterCruiseCapable, from the firing weapon's own AccelerationInWater stat. Synced via DataTracker (see ACCELERATION_IN_WATER_TARGET's own doc for why this now needs to be, same as UNDERWATER_CRUISE_CAPABLE above). */
	protected static final net.minecraft.entity.data.TrackedData<Float> ACCELERATION_IN_WATER_TARGET =
			net.minecraft.entity.data.DataTracker.registerData(VehicleProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.FLOAT);
	/** Per-tick response rate (0-1ish) for ramping up toward accelerationInWaterTarget once CRUISING begins - from the firing weapon's own VelocityInWater stat. Synced via DataTracker - see ACCELERATION_IN_WATER_TARGET's own doc. */
	protected static final net.minecraft.entity.data.TrackedData<Float> VELOCITY_IN_WATER_RATE =
			net.minecraft.entity.data.DataTracker.registerData(VehicleProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.FLOAT);
	/** How far below the water surface this torpedo's own target cruise depth sits - from the firing weapon's own TargetDepth stat (see WeaponStats's own doc), 2.0 default. Synced via DataTracker - see ACCELERATION_IN_WATER_TARGET's own doc. now that tudursvehiclemod$updateUnderwaterCruise() runs on the CLIENT too (see tick()'s own doc), the client needs these ACTUAL configured values (not just the hard-coded field defaults below, which tudursvehiclemod$setUnderwaterCruiseCapable's own caller - tryFireWeapon() - only ever overwrites on the SERVER) to compute the exact same cruising trajectory as the server, rather than merely approximating it with generic defaults. */
	protected static final net.minecraft.entity.data.TrackedData<Float> TARGET_DEPTH_OFFSET =
			net.minecraft.entity.data.DataTracker.registerData(VehicleProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.FLOAT);
	/** Synced via DataTracker, same reasoning as TARGET_DEPTH_OFFSET's own doc - the CLIENT's own copy of tudursvehiclemod$updateUnderwaterCruise() needs this exact configured value too, not just the plain field default (true) tudursvehiclemod$setGuidedTorpedo()'s own caller only ever overwrites on the SERVER, to compute the exact same trajectory as the server for an unguided torpedo specifically. */
	protected static final net.minecraft.entity.data.TrackedData<Boolean> GUIDED_TORPEDO =
			net.minecraft.entity.data.DataTracker.registerData(VehicleProjectileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.BOOLEAN);
	/** Fallback yaw (degrees) for the unlikely edge case of a torpedo fired with essentially zero horizontal velocity (e.g. dropped straight down) - there's no real heading to lock onto in that case. */
	protected static final float UNDERWATER_CRUISE_FALLBACK_YAW_DEGREES = 0f;

	/** A plain,
	 * global count of every currently-alive VehicleProjectileEntity (and
	 * subclass, e.g. VehicleModelProjectileEntity) instance across every
	 * loaded world - incremented once here (the one constructor every
	 * other constructor overload below actually chains through, including
	 * vanilla's own entity-deserialization path, so this covers every
	 * possible way an instance can actually come into existence) and
	 * decremented in tudursvehiclemod$remove()'s own override below (see
	 * that method's own doc). AbstractVehicleEntity's own
	 * tudursvehiclemod$updateCustomHitDetection() checks this FIRST, for
	 * every vehicle, every tick, and skips its own (comparatively
	 * expensive) per-vehicle world query for nearby projectiles entirely
	 * whenever this is 0 - overwhelmingly the common case (nobody's
	 * actually firing anything at any given moment), and a plain int read
	 * is essentially free compared to that query. */
	protected static final java.util.concurrent.atomic.AtomicInteger ACTIVE_COUNT = new java.util.concurrent.atomic.AtomicInteger(0);

	/** See ACTIVE_COUNT's own doc. */
	public static boolean tudursvehiclemod$anyActiveAnywhere() {
		return ACTIVE_COUNT.get() > 0;
	}

	/** Required by EntityType deserialization (e.g. */
	public VehicleProjectileEntity(EntityType<? extends ThrownItemEntity> type, World world) {
		super(type, world);
		ACTIVE_COUNT.incrementAndGet();
	}

	/** The same way CAS/Carrier aircraft already keep themselves loaded (see AircraftEntity's own casForcedChunks doc) - this has no linked block to do that FOR it either, so it force-loads a small grid of chunks around itself directly, same idea as that class's own implementation. Per a further direct correction, uses the SAME 3x3 grid (not a single chunk) - a projectile is often EVEN faster-moving than a CAS aircraft, so the same "single-chunk tracking can't keep up with a genuinely fast mover crossing a boundary mid-tick" reasoning that motivated CAS's own 3x3 grid applies here too, if not more so. Routed through entity.projectile.ProjectileChunkLoadTracker (not ChunkForceTracker directly) - see that class's own doc for the server-wide, configurable total-chunk-count budget (VehicleModServerConfig#projectileForcedChunkLimit) this now enforces, deliberately kept separate from every OTHER always-loaded feature (CAS/Carrier included). */
	private java.util.Set<net.minecraft.util.math.ChunkPos> projectileForcedChunks = java.util.Set.of();

	/** Force-loads a 3x3 grid of chunks around this projectile's own current position (see projectileForcedChunks's own doc) - called once per tick from tick() itself, and once immediately at spawn time (tudursvehiclemod$forceLoadSpawnChunk()) to cover the same chicken-and-egg problem AircraftEntity's own identical spawn-time call solves (a projectile fired from far outside any player's own loaded range would otherwise never tick at all, since ticking itself requires the chunk to already be loaded). */
	private void tudursvehiclemod$updateProjectileForcedChunks() {
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		net.minecraft.util.math.ChunkPos currentChunk = new net.minecraft.util.math.ChunkPos(this.getBlockPos());
		java.util.Set<net.minecraft.util.math.ChunkPos> newGrid = new java.util.HashSet<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				newGrid.add(new net.minecraft.util.math.ChunkPos(currentChunk.x + dx, currentChunk.z + dz));
			}
		}
		// If the CURRENT chunk wasn't already covered by the PREVIOUS grid (before this update), this projectile crossed more than one chunk boundary within a single tick - the 3x3 grid's own radius (1 chunk in every direction from where it was last tick) wasn't enough to cover where it actually ended up. Logged as a warning (not merely info) since this is specifically the "tracking is falling behind" case being investigated, with the exact chunk-distance it fell behind by so severity is directly visible rather than just a bare boolean.
		if (!this.projectileForcedChunks.isEmpty() && !this.projectileForcedChunks.contains(currentChunk)) {
			net.minecraft.util.math.ChunkPos previousCenter = null;
			double nearestDistance = Double.MAX_VALUE;
			for (net.minecraft.util.math.ChunkPos chunk : this.projectileForcedChunks) {
				double distance = Math.hypot(chunk.x - currentChunk.x, chunk.z - currentChunk.z);
				if (distance < nearestDistance) {
					nearestDistance = distance;
					previousCenter = chunk;
				}
			}
		}
		if (newGrid.equals(this.projectileForcedChunks)) {
			return;
		}
		ProjectileChunkLoadTracker.update(this, serverWorld, newGrid);
		this.projectileForcedChunks = newGrid;
	}

	/** Called once by AbstractVehicleEntity's own tryFireWeapon(), immediately after this entity is actually spawned into the world - see tudursvehiclemod$updateProjectileForcedChunks()'s own doc for why this is needed at all. */
	public void tudursvehiclemod$forceLoadSpawnChunk() {
		this.tudursvehiclemod$updateProjectileForcedChunks();
	}

	/** Releases any chunk claims held by this projectile (see projectileForcedChunks's own doc) - called from remove() itself (the single, guaranteed choke point every one of this class's own several discard() call sites ultimately funnels through), so a projectile's own forced chunks are always correctly released the instant it actually goes away, regardless of which specific code path (impact, explosion, timeout, etc.) triggered that. */
	private void tudursvehiclemod$releaseProjectileForcedChunk() {
		if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
			ProjectileChunkLoadTracker.remove(this, serverWorld);
			this.projectileForcedChunks = java.util.Set.of();
		}
	}

	@Override
	public void remove(net.minecraft.entity.Entity.RemovalReason reason) {
		// Guards against double-decrementing ACTIVE_COUNT if remove() were
		// ever somehow called more than once for the same instance (not
		// expected in normal vanilla usage, but cheap enough to guard
		// against outright rather than risk this counter drifting
		// positive over a long-running server's own lifetime).
		boolean wasAlreadyRemoved = this.isRemoved();
		super.remove(reason);
		if (!wasAlreadyRemoved) {
			ACTIVE_COUNT.decrementAndGet();
			// Guarantees
			// tudursvehiclemod$releaseTvControl() (see that method's own
			// doc) is ALWAYS called the instant this entity actually
			// goes away, no matter which of this class's own several
			// discard() call sites (onEntityHit/onBlockHit/
			// updateWaterContactExplosion/updateExplosionAltitude/
			// updateTimeFuse/updateImpactFuse) actually triggered it -
			// remove() (unlike discard(), which is final and can't be
			// overridden at all) is the one place EVERY one of those
			// ultimately funnels through, so this is the single,
			// guaranteed choke point rather than needing every one of
			// those call sites to separately remember to release
			// control themselves.
			this.tudursvehiclemod$releaseTvControl();
			this.tudursvehiclemod$releaseProjectileForcedChunk();
		}
	}

	/** Used by AbstractVehicleEntity#tryFireWeapon to actually fire one. */
	public VehicleProjectileEntity(World world, LivingEntity owner, ItemStack stack, float damage, float gravity,
			float explosionPower, boolean explosionDestroysBlocks, boolean flaming) {
		this(ModEntityTypes.VEHICLE_PROJECTILE, world, owner, stack, damage, gravity,
				explosionPower, explosionDestroysBlocks, flaming);
	}

	/** Same as the eight-argument constructor above, but lets a subclass (e.g. */
	protected VehicleProjectileEntity(EntityType<? extends VehicleProjectileEntity> type, World world,
			LivingEntity owner, ItemStack stack, float damage, float gravity,
			float explosionPower, boolean explosionDestroysBlocks, boolean flaming) {
		this(type, world);
		this.setOwner(owner);
		this.setItem(stack);
		this.damage = damage;
		this.gravity = gravity;
		this.explosionPower = explosionPower;
		this.explosionDestroysBlocks = explosionDestroysBlocks;
		this.flaming = flaming;
	}

	/** Called once, right after firing, by tryFireWeapon for WeaponType.AS_MISSILE. */
	public void tudursvehiclemod$setGuidanceTargetPos(Vec3d targetPos, float turnRateDegreesPerTick) {
		this.guidanceTargetPos = targetPos;
		this.turnRateDegreesPerTick = turnRateDegreesPerTick;
		this.guidedCruiseSpeed = (float) this.getVelocity().length();
		this.dataTracker.set(GUIDED_MISSILE_ACTIVE, true);
	}

	/** Called once, right after firing, by tryFireWeapon for WeaponType.AA_MISSILE/AT_MISSILE/MISSILE - see WeaponStats's own rigidityTimeTicks/proximityFuseDist doc. */
	public void tudursvehiclemod$setMissileGuidanceTuning(int rigidityTimeTicks, float proximityFuseDist) {
		this.rigidityTimeTicks = rigidityTimeTicks;
		this.proximityFuseDist = proximityFuseDist;
	}

	/** Called once, right after firing, by tryFireWeapon for WeaponType.AS_WEAPON - see that enum's own doc and antiSubmarineDiveDistance's own doc for why the underwater-cruise parameters are captured here but not actually activated until tudursvehiclemod$updateGuidance() detects the dive threshold has actually been reached. */
	public void tudursvehiclemod$setAntiSubmarineDive(float diveDistance, float accelerationInWater, float velocityInWater, float targetDepthOffset) {
		this.antiSubmarineDiveDistance = diveDistance;
		this.antiSubmarineAccelerationInWater = accelerationInWater;
		this.antiSubmarineVelocityInWater = velocityInWater;
		this.antiSubmarineTargetDepthOffset = targetDepthOffset;
	}

	/** Called once, right after firing, by tryFireWeapon - see WeaponStats's own trajectoryParticle/trajectoryParticleStartTick/disableSmoke doc. */
	public void tudursvehiclemod$setTrajectoryParticle(java.util.Optional<String> trajectoryParticle, int trajectoryParticleStartTick, boolean disableSmoke) {
		this.trajectoryParticle = trajectoryParticle;
		this.trajectoryParticleStartTick = trajectoryParticleStartTick;
		this.disableSmoke = disableSmoke;
	}

	/** Whether this missile is CURRENTLY homing on the given entity ID - used by AbstractVehicleEntity's own tudursvehiclemod$deployFlares() to find every missile it needs to call tudursvehiclemod$disableGuidanceFromFlare() on. guidanceTargetEntityId itself stays protected (this class and that one share no inheritance relationship at all), so this exposes only the specific check actually needed rather than the raw field. */
	public boolean tudursvehiclemod$isGuidingTowardEntity(int entityId) {
		return this.guidanceTargetEntityId != null && this.guidanceTargetEntityId == entityId;
	}

	/** Called once, right after firing, by tryFireWeapon for WeaponType.AA_MISSILE/AT_MISSILE/MISSILE. */
	public void tudursvehiclemod$setGuidanceTargetEntity(int targetEntityId, float turnRateDegreesPerTick) {
		this.guidanceTargetEntityId = targetEntityId;
		this.turnRateDegreesPerTick = turnRateDegreesPerTick;
		this.guidedCruiseSpeed = (float) this.getVelocity().length();
		this.dataTracker.set(GUIDED_MISSILE_ACTIVE, true);
		this.guidanceBestDistance = Double.MAX_VALUE;
		this.guidanceStagnantTicks = 0;
	}

	/** Disables this missile's own guidance entirely - no re-acquiring the same or any other target afterward, a real flare-defeated missile doesn't recover - and starts the delayed self-destruct countdown (see flareDisabledTicksRemaining's own doc). Called from AbstractVehicleEntity's own tudursvehiclemod$deployFlares() for every missile it finds currently guiding on the flare-deploying vehicle. A no-op if this missile wasn't actually guiding at all (nothing to defeat), so a flare deployed with no missile currently inbound is harmless. */
	public void tudursvehiclemod$disableGuidanceFromFlare() {
		if (this.guidanceTargetEntityId == null && this.guidanceTargetPos == null) {
			return;
		}
		this.guidanceTargetEntityId = null;
		this.guidanceTargetPos = null;
		this.flareDisabledTicksRemaining = FLARE_DISABLE_SELF_DESTRUCT_TICKS;
	}

	/** Called once, right after firing, by tryFireWeapon for WeaponType.AT_MISSILE with ModeNum's own Top Attack mode selected (ModeNum=2, mode index 1) - see tudursvehiclemod$updateGuidance()'s own doc. */
	public void tudursvehiclemod$setTopAttack(boolean topAttack) {
		this.topAttack = topAttack;
	}

	/** Called once, right after firing, by tryFireWeapon for WeaponType.TORPEDO - accelerationInWater/velocityInWater/targetDepthOffset come directly from the firing weapon's own stats (see WeaponStats's own doc). */
	public void tudursvehiclemod$setUnderwaterCruiseCapable(float accelerationInWater, float velocityInWater, float targetDepthOffset) {
		this.dataTracker.set(UNDERWATER_CRUISE_CAPABLE, true);
		this.dataTracker.set(ACCELERATION_IN_WATER_TARGET, accelerationInWater);
		this.dataTracker.set(VELOCITY_IN_WATER_RATE, velocityInWater);
		this.dataTracker.set(TARGET_DEPTH_OFFSET, targetDepthOffset);
	}

	/** Called once, right after firing, by tryFireWeapon for every weapon (not just torpedoes) - see WeaponStats's own explosionPowerInWater doc. */
	public void tudursvehiclemod$setExplosionPowerInWater(float explosionPowerInWater) {
		this.explosionPowerInWater = explosionPowerInWater;
	}

	/** Called once, right after firing, by tryFireWeapon for every weapon - see WeaponStats's own explosionBlockPower doc. */
	public void tudursvehiclemod$setExplosionBlockPower(float explosionBlockPower) {
		this.explosionBlockPower = explosionBlockPower;
	}

	/** Called once, right after firing, by tryFireWeapon - see WeaponStats's own explosionAltitude doc. */
	public void tudursvehiclemod$setExplosionAltitude(float explosionAltitude) {
		this.explosionAltitude = explosionAltitude;
	}

	/** Called once, right after firing, by tryFireWeapon - see WeaponStats's own delayFuseTicks/timeFuseTicks doc. */
	public void tudursvehiclemod$setFuseTicks(int delayFuseTicks, int timeFuseTicks) {
		this.delayFuseTicks = delayFuseTicks;
		this.timeFuseTicks = timeFuseTicks;
	}

	/** Called once, right after firing, by tryFireWeapon - see WeaponStats's own bounceStrength doc. */
	public void tudursvehiclemod$setBounceStrength(float bounceStrength) {
		this.bounceStrength = bounceStrength;
	}

	/** Called once, right after firing, by tryFireWeapon for every weapon - see WeaponStats's own gravityInWater doc. */
	public void tudursvehiclemod$setGravityInWater(float gravityInWater) {
		this.gravityInWater = gravityInWater;
	}

	/** Called once, right after firing, by tryFireWeapon for WeaponType.TORPEDO - see WeaponStats's own guidedTorpedo doc. Written through GUIDED_TORPEDO's own synced DataTracker field (see that field's own doc for why this needs to reach the CLIENT too, not just stay server-side). */
	public void tudursvehiclemod$setGuidedTorpedo(boolean guidedTorpedo) {
		this.dataTracker.set(GUIDED_TORPEDO, guidedTorpedo);
	}

	/** Called once, right after firing, by tryFireWeapon - see WeaponStats's own piercingCount doc. */
	public void tudursvehiclemod$setPiercingCount(int piercingCount) {
		this.piercingCount = piercingCount;
	}

	/** Called once, right after firing, by tryFireWeapon - see WeaponStats's own fuelAirExplosive doc. */
	public void tudursvehiclemod$setFuelAirExplosive(boolean fuelAirExplosive) {
		this.fuelAirExplosive = fuelAirExplosive;
	}

	/** Called once, right after firing, by tryFireWeapon - see WeaponStats's own bulletColor/bulletColorInWater doc. */
	public void tudursvehiclemod$setBulletColors(int bulletColor, int bulletColorInWater) {
		this.bulletColor = bulletColor;
		this.bulletColorInWater = bulletColorInWater;
	}

	/** This projectile's own current tint (packed ARGB) for rendering
	 * purposes - bulletColorInWater while submerged, bulletColor
	 * otherwise. Public so client.render.VehicleProjectileRenderer can
	 * read it directly (see that class's own render() doc for where this
	 * actually gets used). */
	public int tudursvehiclemod$getBulletColor() {
		return this.isTouchingWater() ? this.bulletColorInWater : this.bulletColor;
	}

	/** How many child submunitions to scatter (0 = disabled - the ordinary case) - see WeaponStats's own Bomblet doc. */
	protected int bombletCount;
	/** Ticks after launch before the submunitions actually deploy. */
	protected int bombletDeployTicks;
	/** The submunitions' own spread rate. */
	protected float bombletSpreadRate = 0.7f;
	/** The submunitions' own model/texture, if different from this projectile's own (see WeaponStats's own bombletModel doc) - null falls back to reusing whatever model/texture THIS projectile itself already has. */
	protected Identifier bombletModel;
	protected Identifier bombletTexture;

	/** Called once, right after firing, by tryFireWeapon - see WeaponStats's own Bomblet/BombletSTime/BombletDiff/ModelBomblet doc. */
	public void tudursvehiclemod$setBomblets(int bombletCount, int bombletDeployTicks, float bombletSpreadRate,
			java.util.Optional<Identifier> bombletModel, java.util.Optional<Identifier> bombletTexture) {
		this.bombletCount = bombletCount;
		this.bombletDeployTicks = bombletDeployTicks;
		this.bombletSpreadRate = bombletSpreadRate;
		this.bombletModel = bombletModel.orElse(null);
		this.bombletTexture = bombletTexture.orElse(null);
	}

	/** Type=Dispenser only - see WeaponStats's own dispenseItem/dispenseRange doc / tudursvehiclemod$dispenseIfConfigured()'s own doc. Empty means this isn't actually a Dispenser-type shot at all. */
	protected java.util.Optional<String> dispenseItem = java.util.Optional.empty();
	protected float dispenseRange = 4.0f;

	/** Called once, right after firing, by tryFireWeapon for WeaponType.DISPENSER - see WeaponStats's own dispenseItem/dispenseRange doc. Dispenser is a NORMAL projectile (unlike an earlier version of this, which hand-simulated a ballistic arc with no real entity at all and so never got any of the common features - Bomblet-based wide-area scatter, cooldown, magazine, sound - every OTHER weapon type already gets for free) - this just tags an ordinary VehicleProjectileEntity with what to actually DO on impact instead of exploding. */
	public void tudursvehiclemod$setDispenseItem(java.util.Optional<String> dispenseItem, float dispenseRange) {
		this.dispenseItem = dispenseItem;
		this.dispenseRange = dispenseRange;
	}

	protected boolean tudursvehiclemod$isUnderwaterCruiseCapable() {
		return this.dataTracker.get(UNDERWATER_CRUISE_CAPABLE);
	}

	@Override
	protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(UNDERWATER_CRUISE_CAPABLE, false);
		builder.add(GUIDED_MISSILE_ACTIVE, false);
		builder.add(CRUISE_STARTED, false);
		builder.add(CRUISE_YAW_DEGREES, 0f);
		builder.add(CRUISE_INITIAL_PITCH, 0f);
		builder.add(CRUISE_INITIAL_DEPTH_ERROR, 0f);
		builder.add(CRUISE_TARGET_DEPTH_Y, 0f);
		builder.add(ACCELERATION_IN_WATER_TARGET, 4.0f);
		builder.add(VELOCITY_IN_WATER_RATE, 0.5f);
		builder.add(TARGET_DEPTH_OFFSET, 2.0f);
		builder.add(GUIDED_TORPEDO, true);
	}

	@Override
	protected Item getDefaultItem() {
		return net.minecraft.item.Items.IRON_NUGGET;
	}

	@Override
	protected double getGravity() {
		// Guided missiles are self-propelled and fly under their own guidance, not ballistic trajectories. A torpedo stops falling the instant it touches water (see tudursvehiclemod$updateUnderwaterCruise()'s own doc) - checked via isTouchingWater() (a plain world-state query, correct on BOTH client and server with no networking needed) rather than the CRUISING-phase fields below, which are only ever meaningful on the server - see UNDERWATER_CRUISE_CAPABLE's own doc for why that distinction actually matters here.
		// TVMissile control felt badly unstable
		// (visible wobble, occasional violent snap-rotation): this used
		// to NOT actually exempt a TV-controlled missile from gravity at
		// all, unlike every other guided type here - tudursvehiclemod$updateTvMissileControl()
		// derives its own "current" yaw/pitch straight from THIS
		// missile's own current velocity every tick, so gravity quietly
		// pulling that same velocity downward, tick after tick,
		// compounded directly into the steering baseline itself - a
		// slow pitch-down drift even with no player input at all, and
		// once speed got low enough for gravity's own contribution to
		// dominate the vector, the yaw/pitch extraction itself became
		// numerically noisy (a near-vertical velocity has no stable
		// horizontal direction to derive yaw from at all), which is
		// almost certainly what the occasional sudden, violent rotation
		// actually was.
		// This missile kept pitching downward on
		// its own, even after every previous attempt to fix its own
		// steering: this used to check tvControllingPlayerId != null
		// here - a field that's ONLY EVER POPULATED SERVER-SIDE (see
		// that field's own doc) and stays permanently null on the
		// CLIENT's own predicted copy of this entity. Gravity was
		// therefore NEVER actually exempted on the client at all, even
		// while tudursvehiclemod$updateTvMissileSteering() was actively
		// steering it - and since gravity's own contribution gets baked
		// into THIS tick's own position update (inside super.tick(),
		// which runs BEFORE this class's own steering correction) before
		// that correction ever gets a chance to touch velocity again,
		// the CLIENT's own locally-predicted position quietly
		// accumulated a real, persistent downward drift every single
		// tick regardless - this class's own steering was correcting
		// the STORED velocity for the NEXT tick's use, but always one
		// tick too late to undo what gravity had already baked into
		// THIS tick's own actual movement. tvSteeringActive (a plain,
		// common flag correctly set on BOTH sides - see that field's
		// own doc) is the fix.
		if ((this.tudursvehiclemod$isUnderwaterCruiseCapable() && this.isTouchingWater())
				|| this.dataTracker.get(GUIDED_MISSILE_ACTIVE)
				|| this.tvSteeringActive || this.tvPostControlDescentActive) {
			return 0.0;
		}
		// Per Readme_Weapon.txt's own GravityInWater doc: a genuinely
		// separate fall speed while submerged, independent of plain
		// gravity (used everywhere else) - defaults to matching gravity
		// itself (see tudursvehiclemod$setGravityInWater()'s own doc) for
		// any weapon that never actually configures this, so ordinary
		// (non-torpedo) projectiles keep falling normally underwater
		// exactly as before this field existed at all.
		if (this.isTouchingWater()) {
			return this.gravityInWater;
		}
		return this.gravity;
	}

	/** Overridden purely to guarantee
	 * tudursvehiclemod$releaseTvControl() (see that method's own doc) is
	 * ALWAYS called the instant this entity actually goes away, no
	 * matter which of this class's own several discard() call sites
	 * (onEntityHit/onBlockHit/updateWaterContactExplosion/
	 * updateExplosionAltitude/updateTimeFuse/updateImpactFuse) actually
	 * triggered it, rather than needing every one of those to
	 * separately remember to release control themselves (and risk one
	 * getting missed, leaving a controlling player's own camera stuck
	 * on a now-vanished entity). Handled in remove() instead of
	 * discard() itself - discard() is final (can't be overridden at
	 * all), but it just calls remove(RemovalReason.DISCARDED)
	 * internally, which every one of those call sites already funnels
	 * through either way - see that method's own doc, just above. */

	@Override
	public void tick() {
		// The torpedo's own orientation was
		// specifically unstable compared to every OTHER projectile type -
		// this used to run only inside the server-only block below,
		// meaning the CLIENT's own copy of this entity never locally
		// recomputed anything at all once CRUISING began (see that
		// method's own doc), relying entirely on however often rotation
		// happens to get synced over the network for its own visual
		// orientation in the meantime - unlike every other projectile
		// type, whose yaw/pitch instead gets recomputed from velocity
		// every single tick on BOTH sides (see the bottom of this method).
		// Running this unconditionally, the same way updateVehicleMovement()
		// already does for ordinary vehicles (client-side prediction),
		// lets the client compute the same smooth, continuous CRUISING
		// behavior locally instead of just idling until the next sync.
		this.tudursvehiclemod$updateUnderwaterCruise();
		if (!this.getEntityWorld().isClient()) {
			this.tudursvehiclemod$updateProjectileForcedChunks();
			this.tudursvehiclemod$updateTvMissileServerChecks();
			this.tudursvehiclemod$updateTrajectoryParticle();
			if (this.tudursvehiclemod$updateBombletDeployment()) {
				return;
			}
			if (this.tudursvehiclemod$updateWaterContactExplosion()) {
				return;
			}
			if (this.tudursvehiclemod$updateImpactFuse()) {
				return;
			}
			if (this.tudursvehiclemod$updateTimeFuse()) {
				return;
			}
			if (this.tudursvehiclemod$updateExplosionAltitude()) {
				return;
			}
		}
		super.tick();
		// Every guided missile type (TVMissile,
		// but also AS_MISSILE/AA_MISSILE/AT_MISSILE/MK_ROCKET) eventually
		// stopped mid-air and/or spun erratically, even after this
		// project's own earlier attempts at a fixed-cruise-speed fix:
		// vanilla's own ThrownEntity.tick() (called via super.tick()
		// just above) applies its OWN, unconditional, non-overridable
		// per-tick drag (0.99x in air, 0.8x in water - see that class's
		// own applyDrag() for the exact values) AND its OWN rotation
		// update (a SEPARATE, hard-coded LERP towards whatever direction
		// the - already drag-reduced - velocity happens to point,
		// completely independent of and unaware of this class's own
		// steering) - BOTH of which used to run AFTER this class's own
		// steering used to set velocity/yaw/pitch (when that logic
		// still ran further up, before this super.tick() call), meaning
		// vanilla's own drag and rotation-lerp were the LAST word every
		// tick, silently undoing this class's own explicit corrections
		// each time rather than the other way around. tudursvehiclemod$updateTvMissileSteering()/
		// tudursvehiclemod$updateGuidance() now run HERE instead - AFTER
		// super.tick() - specifically so THEY are the final, authoritative
		// word on this tick's own velocity/yaw/pitch, actively
		// overriding whatever vanilla's own drag/rotation-lerp already
		// did, rather than being overridden by it.
		this.tudursvehiclemod$updateTvMissileSteering();
		this.tudursvehiclemod$updateTvPostControlDescent();
		if (!this.getEntityWorld().isClient()) {
			this.tudursvehiclemod$updateGuidance();
		}
		// Keep this projectile's own yaw/pitch matching its CURRENT velocity
		// Direction every tick - torpedoes
		// used to be special-cased here (skipped entirely while CRUISING,
		// relying instead on tudursvehiclemod$updateUnderwaterCruise()'s
		// own explicit setYaw/setPitch calls) specifically to avoid a
		// fight with super.tick()'s own water drag - but that
		// special-casing gave up the exact robustness every OTHER
		// projectile type already has for free: deriving orientation
		// from whatever the CURRENT velocity actually is, unconditionally,
		// means a torpedo's own displayed orientation now automatically
		// tracks ANY change to its own velocity - including sudden
		// external perturbations like a nearby explosion's own knockback -
		// the exact same way a MachineGun-type bullet already does,
		// rather than staying locked to a separately computed "intended"
		// heading that could drift out of sync with whatever is actually
		// moving this entity. tudursvehiclemod$updateUnderwaterCruise()
		// no longer calls setYaw/setPitch itself at all (see that
		// method's own doc) - this is now the ONLY place any projectile's
		// own yaw/pitch ever gets set, torpedoes included.
		Vec3d velocity = this.getVelocity();
		double speed = velocity.length();
		// A small nonzero floor (rather than the practically-zero 1.0E-6
		// this used to be) - deriving a direction back out of an
		// extremely tiny velocity vector is numerically noisy (tiny,
		// near-equal x/z components can point almost any direction), and
		// this could occasionally snap a projectile's
		// own MODEL to a visibly wrong yaw for a moment (while its own
		// actual movement/hit detection stayed completely correct, since
		// those never depend on this derived yaw/pitch at all). Simply
		// not updating at such tiny speeds leaves the last GOOD
		// orientation in place instead of snapping to noise.
		// TV-controlled missiles now use this
		// exact same generic mechanism too, rather than maintaining a
		// separately-tracked orientation of their own - see
		// tudursvehiclemod$updateTvMissileSteering()'s own doc for why
		// that's both simpler and more robust, now that this whole
		// method runs AFTER super.tick() (see tick()'s own doc): this
		// recompute, reading whatever velocity that steering just set,
		// always matches its exact intended heading already.
		// This missile's own displayed model
		// kept pitching back downward no matter what, even once its own
		// ACTUAL velocity/movement was confirmed correct (constant speed,
		// no unwanted gravity): plain setYaw()/setPitch() (used here
		// previously) only ever change yaw/pitch themselves - they don't
		// touch Entity's own separate lastYaw/lastPitch fields at all,
		// which are what getYaw(tickProgress)/getPitch(tickProgress) -
		// used for EVERY frame's own RENDER-time interpolation, by both
		// client.render.VehicleProjectileRenderer and
		// client.mixin.CameraMixin's own camera override - actually lerp
		// FROM. Those two fields are normally only ever snapped to match
		// current yaw/pitch by Entity's own setAngles() (typically used
		// for teleports, to avoid an interpolation artifact) or by
		// ProjectileEntity's own separate calculateVelocity-based
		// setVelocity() overload (which this class's own code never
		// actually calls) - meaning they were effectively still stuck at
		// whatever this missile's own very first tick happened to leave
		// them at, and every single render frame since was quietly
		// blending PART of the way back towards that stale, original
		// value, regardless of how much this missile's own actual
		// heading had since changed. Calling setAngles() here instead
		// snaps lastYaw/lastPitch to match every tick, the same way a
		// teleport would - so each frame's own interpolation is always
		// between last tick's own already-correct heading and this
		// tick's own, with nothing stale left to pull back towards at
		// all.
		if (speed > 0.05) {
			float newPitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(velocity.y / speed, -1.0, 1.0)));
			float newYaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
			this.setAngles(newYaw, newPitch);
		}
	}

	/** Per Readme_Weapon.txt's own doc: once bombletDeployTicks have passed
	 * since launch (and bombletCount > 0 at all - the ordinary, disabled
	 * case, for almost every weapon), scatters bombletCount fresh
	 * VehicleProjectileEntity (or VehicleModelProjectileEntity, if a
	 * distinct bombletModel/bombletTexture is set) instances from this
	 * projectile's own current position, each with this projectile's own
	 * current velocity spread out by bombletSpreadRate, then discards
	 * this projectile itself (the "parent" bomb/rocket doesn't itself
	 * detonate at this point - see Readme_Weapon.txt's own doc, the whole
	 * point of a cluster weapon is the SUBMUNITIONS doing the actual
	 * damage, not the parent shell). Returns true if this happened this
	 * tick (the caller should skip the rest of its own tick() in that
	 * case, since this entity is already discarded). */
	protected boolean tudursvehiclemod$updateBombletDeployment() {
		if (this.bombletCount <= 0 || this.age < this.bombletDeployTicks) {
			return false;
		}
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return false;
		}
		Vec3d origin = this.getEntityPos();
		Vec3d baseVelocity = this.getVelocity();
		Entity ownerEntity = this.getOwner();
		LivingEntity owner = ownerEntity instanceof LivingEntity living ? living : null;
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity firingVehicle = this.tudursvehiclemod$getFiringVehicle();
		ItemStack stack = this.getDefaultItem().getDefaultStack();
		boolean useBombletModel = this.bombletModel != null && this.bombletTexture != null;
		for (int i = 0; i < this.bombletCount; i++) {
			Vec3d spreadVelocity = tudursvehiclemod$applyBombletSpread(baseVelocity, this.bombletSpreadRate);
			VehicleProjectileEntity bomblet;
			if (useBombletModel) {
				bomblet = new com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity(
						this.getEntityWorld(), owner, stack, this.damage, this.gravity,
						this.explosionPower, this.explosionDestroysBlocks, this.flaming,
						this.bombletModel, this.bombletTexture, 1.0f);
			} else {
				bomblet = new VehicleProjectileEntity(this.getEntityWorld(), owner, stack, this.damage, this.gravity,
						this.explosionPower, this.explosionDestroysBlocks, this.flaming);
			}
			bomblet.setPosition(origin.x, origin.y, origin.z);
			bomblet.setVelocity(spreadVelocity);
			bomblet.tudursvehiclemod$setExplosionPowerInWater(this.explosionPowerInWater);
			bomblet.tudursvehiclemod$setExplosionBlockPower(this.explosionBlockPower);
			bomblet.tudursvehiclemod$setExplodeOnWaterContact(this.explodeOnWaterContact);
			bomblet.tudursvehiclemod$setSplashScale(this.splashScale);
			bomblet.tudursvehiclemod$setDispenseItem(this.dispenseItem, this.dispenseRange);
			if (firingVehicle != null) {
				bomblet.tudursvehiclemod$setFiringVehicle(firingVehicle);
			}
			serverWorld.spawnEntity(bomblet);
		}
		this.discard();
		return true;
	}

	/** EVERY projectile gets a small water-splash
	 * effect (see tudursvehiclemod$spawnWaterSplash()'s own doc) the FIRST
	 * tick it's actually touching water, scaled to this projectile's own
	 * splashScale (see that field's own doc) - regardless of weapon type.
	 * On top of that, WeaponType.BOMB specifically (explodeOnWaterContact=
	 * true) ALSO explodes right there instead of continuing to sink
	 * (WeaponType.DEPTH's own explodeOnWaterContact=false means it just
	 * splashes and continues sinking/cruising like everything else).
	 * wasTouchingWaterLastTick's own doc explains why this only fires
	 * once, on the actual transition into water, rather than every tick a
	 * projectile happens to remain submerged. Returns true if this
	 * projectile actually exploded/discarded itself this tick (the caller
	 * should skip the rest of its own tick() in that case) - false
	 * otherwise, INCLUDING the ordinary case where a splash happened but
	 * this projectile itself keeps going. */
	protected boolean tudursvehiclemod$updateWaterContactExplosion() {
		boolean touchingWaterNow = this.isTouchingWater();
		boolean justEnteredWater = touchingWaterNow && !this.wasTouchingWaterLastTick;
		this.wasTouchingWaterLastTick = touchingWaterNow;
		if (!justEnteredWater) {
			return false;
		}
		if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
			tudursvehiclemod$spawnWaterSplash(serverWorld, this.getX(), this.getY(), this.getZ(), this.splashScale, SMALL_SPLASH_PARTICLE_COUNT);
		}
		if (!this.explodeOnWaterContact) {
			return false;
		}
		this.tudursvehiclemod$explodeIfConfigured(this.getX(), this.getY(), this.getZ());
		this.discard();
		return true;
	}

	/** Per Readme_Weapon.txt's own ExplosionAltitude doc: detonates the
	 * instant this projectile's own height above whatever solid ground
	 * is directly below it drops to (or below) explosionAltitude,
	 * regardless of whether it's actually hit anything yet - meant for a
	 * missile that should airburst above its own target rather than
	 * needing a direct hit. Scans straight down from this projectile's
	 * own current position for the first solid (non-air, non-fluid)
	 * block - same downward-scan idea as tudursvehiclemod$findWaterSurfaceY(),
	 * just looking for solid ground instead of a water surface. Returns
	 * true if this projectile actually exploded/discarded itself this
	 * tick (the caller should skip the rest of its own tick() in that
	 * case). */
	protected boolean tudursvehiclemod$updateExplosionAltitude() {
		if (this.explosionAltitude <= 0f) {
			return false;
		}
		// A fixed grace period after firing, matching the direct request exactly.
		if (this.age < EXPLOSION_ALTITUDE_GRACE_TICKS) {
			return false;
		}
		net.minecraft.util.math.BlockPos.Mutable scanPos = new net.minecraft.util.math.BlockPos.Mutable(
				net.minecraft.util.math.MathHelper.floor(this.getX()),
				net.minecraft.util.math.MathHelper.floor(this.getY()),
				net.minecraft.util.math.MathHelper.floor(this.getZ()));
		int maxScanHeight = 320;
		int scanned = 0;
		while (scanned < maxScanHeight && this.getEntityWorld().getBlockState(scanPos).isAir()
				&& this.getEntityWorld().getFluidState(scanPos).isEmpty()) {
			scanPos.move(net.minecraft.util.math.Direction.DOWN);
			scanned++;
		}
		double groundY = scanPos.getY() + 1.0;
		if (this.getY() - groundY > this.explosionAltitude) {
			return false;
		}
		this.tudursvehiclemod$explodeIfConfigured(this.getX(), this.getY(), this.getZ());
		this.discard();
		return true;
	}

	/** Per Readme_Weapon.txt's own TimeFuse doc: this projectile
	 * disappears (exploding first, if Explosion/ExplosionInWater is set)
	 * this many ticks after being FIRED, win or lose, regardless of
	 * whether it's hit anything at all yet - a simple total-lifetime
	 * countdown, independent of DelayFuse's own post-impact one (see
	 * tudursvehiclemod$updateImpactFuse()'s own doc for that one). Uses
	 * this.age (already ticking since spawn for every Entity) rather
	 * than a separately-tracked counter. Returns true if this projectile
	 * actually exploded/discarded itself this tick. */
	protected boolean tudursvehiclemod$updateTimeFuse() {
		if (this.timeFuseTicks < 0 || this.age < this.timeFuseTicks) {
			return false;
		}
		this.tudursvehiclemod$explodeIfConfigured(this.getX(), this.getY(), this.getZ());
		this.discard();
		return true;
	}

	/** Per Readme_Weapon.txt's own DelayFuse doc: once tudursvehiclemod$startImpactFuse()
	 * has actually been called (an impact happened - see onBlockHit()'s/
	 * onEntityHit()'s own doc), counts ticksSinceImpact up each tick
	 * instead of discarding immediately, and only actually explodes/
	 * discards once it reaches delayFuseTicks - together with Bound (see
	 * tudursvehiclemod$tryBounce()'s own doc), this is what lets a
	 * projectile visibly bounce/sit around for a moment after impact
	 * instead of vanishing the same tick it hit. Returns true if this
	 * projectile actually exploded/discarded itself this tick. */
	protected boolean tudursvehiclemod$updateImpactFuse() {
		if (this.ticksSinceImpact < 0) {
			return false;
		}
		if (this.ticksSinceImpact < this.delayFuseTicks) {
			this.ticksSinceImpact++;
			return false;
		}
		this.tudursvehiclemod$explodeIfConfigured(this.getX(), this.getY(), this.getZ());
		this.discard();
		return true;
	}

	/** Scans straight up from (x, y, z) through water blocks to find the actual water surface Y. Falls back to y itself (no scan needed/possible) if not actually in water at all, or if the surface isn't found within 40 blocks. Deliberately takes a plain World (not ServerWorld) - see tudursvehiclemod$updateUnderwaterCruise()'s own doc for why this now needs to run correctly on the CLIENT too. */
	protected static double tudursvehiclemod$findWaterSurfaceY(net.minecraft.world.World world, double x, double y, double z) {
		net.minecraft.util.math.BlockPos.Mutable scanPos = new net.minecraft.util.math.BlockPos.Mutable(
				net.minecraft.util.math.MathHelper.floor(x), net.minecraft.util.math.MathHelper.floor(y), net.minecraft.util.math.MathHelper.floor(z));
		int height = 0;
		int maxScanHeight = 40;
		while (height < maxScanHeight && world.getFluidState(scanPos).isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
			scanPos.move(net.minecraft.util.math.Direction.UP);
			height++;
		}
		return y + height;
	}

	/** How big a splash a bare/unscaled bullet (splashScale=1.0, the default - see that field's own doc) produces on first touching water. Scales linearly with splashScale. */
	protected static final int SMALL_SPLASH_PARTICLE_COUNT = 6;
	/** How many splash particles make up the large water column at an explosion's own detonation point (see tudursvehiclemod$explodeIfConfigured()'s own doc) - scaled further by that explosion's own power on top of this base count. */
	protected static final int LARGE_COLUMN_BASE_PARTICLE_COUNT = 20;

	/** Spawns `count` WaterSplashParticle instances
	 * (see that class's own doc) at (x, y, z), each individually sized by
	 * `scale` - used for both the small splash on an ordinary projectile's
	 * own first contact with the water surface (see
	 * tudursvehiclemod$updateWaterContactExplosion()'s own doc, scale =
	 * this projectile's own splashScale) and the large column at an
	 * explosion that happens at/in water (see
	 * tudursvehiclemod$explodeIfConfigured()'s own doc, scale grows with
	 * that explosion's own power instead). Each particle's own burst
	 * velocity is randomized independently, client-side (see
	 * WaterSplashParticle's own doc for why), so spawning several of them
	 * at the same point is what actually produces a "spray"/"column" look
	 * rather than a single particle needing to look like one on its own. */
	protected static void tudursvehiclemod$spawnWaterSplash(ServerWorld world, double x, double y, double z, float scale, int count) {
		com.example.tudursvehiclemod.particle.WaterSplashEffect effect =
				new com.example.tudursvehiclemod.particle.WaterSplashEffect(scale);
		for (int i = 0; i < count; i++) {
			world.spawnParticles(effect, true, true, x, y, z, 0, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** The water column's own peak height now comes
	 * directly from the explosion's own power (ExplosionInWater when
	 * underwater, which is what actually applies here - see
	 * tudursvehiclemod$explodeIfConfigured()'s own doc for the
	 * power/Explosion-vs-ExplosionBlock distinction) via this one fixed
	 * ratio - e.g. ExplosionInWater=8 gives a 2-block peak height,
	 * ExplosionInWater=16 gives 4, matching the exact example given for
	 * this feature. */
	protected static final float WATER_COLUMN_HEIGHT_PER_POWER = 0.25f;
	/** How many ticks the water column's own full rise-then-fall animation takes per (block of peak height)^0.5 - see tudursvehiclemod$scheduleWaterColumnWaves()'s own doc for why this uses a square-root relationship (a taller column takes longer to complete its own round trip, but not linearly so - the same broad-strokes relationship an actual falling/rising object's own travel time has to the height it covers). */
	protected static final float WATER_COLUMN_TICKS_PER_SQRT_HEIGHT = 40f;
	/** How often (in ticks) a fresh, smaller wave of splash particles spawns during the column's own full animation - a plain tick counter, not a Minecraft "scheduled tick". */
	protected static final int WATER_COLUMN_WAVE_INTERVAL_TICKS = 5;

	/** Re-fires tudursvehiclemod$spawnWaterSplash() every
	 * WATER_COLUMN_WAVE_INTERVAL_TICKS, so the water column visibly
	 * persists rather than vanishing after its own initial burst - same
	 * END_SERVER_TICK-listener, self-limiting-instead-of-unregistering
	 * approach as tudursvehiclemod$scheduleGroundSmokeWaves() below (see
	 * that method's own doc for why).
	 *
	 * Rather than every wave spawning at the exact
	 * same fixed height, each wave's own spawn Y now follows a sine curve
	 * over the FULL animation (0 at the very start, peaking at the
	 * midpoint, back to 0 at the very end) - since this is a "staged"
	 * particle-based effect (many short-lived bursts positioned over
	 * time) rather than one single object, adjusting WHERE each wave's own
	 * particles spawn is what actually makes the whole column visibly
	 * rise up out of the water and then sink back down, rather than just
	 * holding at one constant height throughout.
	 *
	 * Per a further direct request: the animation's own TOTAL DURATION
	 * (how many ticks the whole rise-then-fall takes, and so how many
	 * waves actually fire) now scales with peakHeight itself too (see
	 * WATER_COLUMN_TICKS_PER_SQRT_HEIGHT's own doc), rather than a fixed
	 * duration regardless of the explosion's own size - so a bigger
	 * explosion's own column both reaches higher AND stays visible longer
	 * to actually complete that taller round trip, while a small one both
	 * stays low and wraps up quickly. The display keeps running for
	 * exactly this whole up-and-back-down animation, never longer and
	 * never cut short partway through.
	 *
	 * Per an even earlier direct report: spawning EVERY particle at that
	 * single current peak height made the whole effect visibly detach
	 * from the water surface while rising, which looked wrong - each wave
	 * spreads its own particles across HEIGHT_LEVELS evenly-spaced heights
	 * from the surface (y itself) up to that same current peak, so the
	 * column's own base stays populated at the water surface throughout,
	 * with the rest of the spray extending up to wherever the peak
	 * currently is - a genuine column FROM the surface TO the peak, not a
	 * single point floating at the peak alone. */
	protected static final int WATER_COLUMN_HEIGHT_LEVELS = 4;

	protected static void tudursvehiclemod$scheduleWaterColumnWaves(ServerWorld world, double x, double y, double z, float scale, float peakHeight, int perWaveCount) {
		int totalTicks = Math.max(WATER_COLUMN_WAVE_INTERVAL_TICKS,
				MathHelper.floor(WATER_COLUMN_TICKS_PER_SQRT_HEIGHT * Math.sqrt(Math.max(0.01f, peakHeight))));
		int totalWaves = Math.max(1, totalTicks / WATER_COLUMN_WAVE_INTERVAL_TICKS);
		int countPerLevel = Math.max(1, perWaveCount / WATER_COLUMN_HEIGHT_LEVELS);
		int[] tickCount = {0};
		int[] wavesSpawned = {0};
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (wavesSpawned[0] >= totalWaves) {
				return;
			}
			tickCount[0]++;
			if (tickCount[0] % WATER_COLUMN_WAVE_INTERVAL_TICKS == 0) {
				float progress = (float) wavesSpawned[0] / (float) totalWaves;
				double currentPeakHeight = peakHeight * Math.sin(Math.PI * progress);
				for (int level = 0; level < WATER_COLUMN_HEIGHT_LEVELS; level++) {
					double levelFraction = (double) level / (WATER_COLUMN_HEIGHT_LEVELS - 1);
					double levelY = y + currentPeakHeight * levelFraction;
					tudursvehiclemod$spawnWaterSplash(world, x, levelY, z, scale, countPerLevel);
				}
				wavesSpawned[0]++;
			}
		});
	}

	/** How many expanding rings make up the ripple effect - each one bigger (larger radius) and slightly later than the last, so it reads as an outward-expanding ripple rather than a single static ring. */
	protected static final int RIPPLE_RING_COUNT = 4;
	/** Ticks between each successive ring - short, since the whole ripple should expand out over roughly a second, not linger anywhere near as long as the column itself. */
	protected static final int RIPPLE_RING_INTERVAL_TICKS = 3;

	/** An expanding ripple at the water surface,
	 * alongside the vertical column (see
	 * tudursvehiclemod$scheduleWaterColumnWaves()'s own doc) - several
	 * rings of this mod's own WaterSplashParticle (the same one the
	 * column/small-bullet-splash both already use - see that class's own
	 * doc), arranged in a growing circle around the impact point at the
	 * water surface itself (not shooting upward, unlike the column),
	 * spawned a few ticks apart so each successive, larger ring reads as
	 * the previous one's own outward expansion rather than several
	 * unrelated rings appearing at once. Not a true flat "decal" ripple
	 * (BillboardParticle's own rendering always faces the camera, not a
	 * fixed horizontal plane - see that class's own doc), but several
	 * small splashes arranged in an expanding ring reads as a reasonable
	 * approximation without needing genuinely new, untested rendering
	 * code for a fixed-orientation particle. */
	protected static void tudursvehiclemod$scheduleWaterRipple(ServerWorld world, double x, double y, double z, float columnScale) {
		float ringScale = Math.max(0.5f, columnScale * 0.35f);
		int particlesPerRing = 10;
		int[] tickCount = {0};
		int[] ringsSpawned = {0};
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ringsSpawned[0] >= RIPPLE_RING_COUNT) {
				return;
			}
			tickCount[0]++;
			if (tickCount[0] % RIPPLE_RING_INTERVAL_TICKS == 0) {
				double ringRadius = 1.0 + ringsSpawned[0] * (1.0 + columnScale * 0.5);
				com.example.tudursvehiclemod.particle.WaterSplashEffect effect =
						new com.example.tudursvehiclemod.particle.WaterSplashEffect(ringScale);
				for (int i = 0; i < particlesPerRing; i++) {
					double angle = (2 * Math.PI * i) / particlesPerRing;
					double px = x + ringRadius * Math.cos(angle);
					double pz = z + ringRadius * Math.sin(angle);
					world.spawnParticles(effect, true, true, px, y, pz, 0, 0.0, 0.0, 0.0, 0.0);
				}
				ringsSpawned[0]++;
			}
		});
	}

	/** Bomblet+Dispenser barely affected the
	 * ground (child submunitions seeming to "vanish" mid-air): simulating
	 * the OLD formula (an unconstrained random offset added to the
	 * velocity vector, then renormalized) at the actual documented
	 * BombletDiff example value (0.7) showed about 24% of bomblets ending
	 * up moving UPWARD instead of continuing toward the ground - an
	 * unconstrained linear offset can flip the vertical component
	 * entirely once it's large relative to the original velocity, rather
	 * than just scattering the landing area the way a real cluster
	 * munition does. This instead rotates the ORIGINAL direction by a
	 * random angle (up to spreadRate * MAX_CONE_ANGLE_DEGREES) around a
	 * random axis perpendicular to it - a cone around the original
	 * heading - which keeps every bomblet's own general direction
	 * (downward/forward) intact while still spreading the actual landing
	 * points out, matching "拡散率" (a spread RATIO/rate, not an
	 * unconstrained random kick) per Readme_Weapon.txt's own doc.
	 * Re-simulating this same 0.7 case with a cone approach dropped the
	 * upward-moving fraction to about 8%, and that residual mostly comes
	 * from shots fired at a shallow angle to begin with (by the time
	 * Bomblet deployment actually happens, gravity has usually already
	 * pulled a real shot's own trajectory more steeply downward than
	 * that, further reducing this in practice). */
	protected static final float MAX_CONE_ANGLE_DEGREES = 45f;

	protected Vec3d tudursvehiclemod$applyBombletSpread(Vec3d baseVelocity, float spreadRate) {
		double speed = baseVelocity.length();
		if (speed < 1.0E-6 || spreadRate <= 0f) {
			return baseVelocity;
		}
		Vector3f dir = new Vector3f((float) (baseVelocity.x / speed), (float) (baseVelocity.y / speed), (float) (baseVelocity.z / speed));
		// An arbitrary vector not parallel to dir, to derive a perpendicular axis from.
		Vector3f arbitrary = Math.abs(dir.x) < 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		Vector3f perpendicular = new Vector3f(dir).cross(arbitrary).normalize();
		var random = this.getEntityWorld().random;
		// Randomizes WHICH perpendicular direction the tilt happens in (spin the perpendicular axis itself around dir first), then tilts dir by a random angle within the cone.
		float spinAngleRad = (float) Math.toRadians(random.nextFloat() * 360f);
		Quaternionf spin = new Quaternionf().rotationAxis(spinAngleRad, dir.x, dir.y, dir.z);
		Vector3f tiltAxis = spin.transform(new Vector3f(perpendicular));
		float tiltAngleRad = (float) Math.toRadians(random.nextFloat() * spreadRate * MAX_CONE_ANGLE_DEGREES);
		Quaternionf tilt = new Quaternionf().rotationAxis(tiltAngleRad, tiltAxis.x, tiltAxis.y, tiltAxis.z);
		Vector3f result = tilt.transform(new Vector3f(dir));
		return new Vec3d(result.x, result.y, result.z).multiply(speed);
	}

	/** Steers this projectile's own velocity toward whichever guidance target is set (entity or fixed point), by at most turnRateDegreesPerTick degrees this tick. */
	/** Called once, right after firing, by tryFireWeapon for WeaponType.TV_MISSILE - hands control of this missile directly to player's own mouse (see network.TvMissileInputPayload's own doc for how their input actually reaches here), and notifies their OWN client (via network.TvMissileControlStartPayload) to start rendering its own camera from this missile's own position/orientation instead of the vehicle's (see client.mixin.CameraMixin's own doc) - all WITHOUT ever touching the player's own entity position at all (unlike vanilla's own Entity#setCameraEntity(), which forcibly teleports the controlling player's own entity to match the camera target - exactly the kind of surprising side effect a passenger who's still actually seated in their own vehicle should never experience). */
	/** Per Readme_Weapon.txt's own TVMissile doc: playerId -> the entity ID
	 * of whichever missile that player is CURRENTLY steering (see
	 * tudursvehiclemod$setTvControlled()'s own doc) - a small, static
	 * registry (rather than, say, scanning every loaded
	 * VehicleProjectileEntity in the world each time) so ModNetworking's
	 * own TvMissileInputPayload handler can find the right entity to
	 * actually route a given player's own mouse input to. Kept in sync
	 * with each entry's own actual lifetime by tudursvehiclemod$setTvControlled()/
	 * tudursvehiclemod$releaseTvControl() themselves - a player is only
	 * ever in here while genuinely controlling something. */
	protected static final java.util.Map<Integer, Integer> ACTIVE_TV_CONTROL = new java.util.concurrent.ConcurrentHashMap<>();

	/** Looks up which missile (if any) playerId is currently steering (see ACTIVE_TV_CONTROL's own doc), resolved against world's own entity list. Null if that player isn't controlling anything right now, or whatever they WERE controlling has already gone away. */
	public static VehicleProjectileEntity tudursvehiclemod$getControlledMissile(ServerWorld world, int playerId) {
		Integer missileId = ACTIVE_TV_CONTROL.get(playerId);
		if (missileId == null) {
			return null;
		}
		return world.getEntityById(missileId) instanceof VehicleProjectileEntity missile ? missile : null;
	}

	public void tudursvehiclemod$setTvControlled(net.minecraft.server.network.ServerPlayerEntity player) {
		this.tvControllingPlayerId = player.getId();
		this.tvLaunchPos = this.getEntityPos();
		this.tudursvehiclemod$initTvClientTracking();
		ACTIVE_TV_CONTROL.put(player.getId(), this.getId());
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
				new com.example.tudursvehiclemod.network.TvMissileControlStartPayload(this.getId()));
	}

	/** Releases control (see tudursvehiclemod$setTvControlled()'s own doc) if this missile is currently actually being controlled by anyone at all - notifies that SAME player's own client (via network.TvMissileControlEndPayload) to revert its own camera back to normal (whatever it was showing before - the vehicle, or nothing special at all), then clears tvControllingPlayerId (and its own ACTIVE_TV_CONTROL entry) so this becomes a permanent no-op for the rest of this missile's own remaining flight (a released missile never re-acquires control, even if - implausibly - it somehow drifts back into range). A safe no-op if nobody's actually controlling this missile right now. */
	protected void tudursvehiclemod$releaseTvControl() {
		if (this.tvControllingPlayerId == null) {
			return;
		}
		// Only actually clears the
		// registry entry if it STILL points to THIS missile - a stale
		// call (this missile's own control was already released once,
		// then somehow called again) shouldn't accidentally erase a
		// DIFFERENT, newer missile the same player has since started
		// controlling instead.
		ACTIVE_TV_CONTROL.remove(this.tvControllingPlayerId, this.getId());
		if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
			if (serverWorld.getEntityById(this.tvControllingPlayerId) instanceof net.minecraft.server.network.ServerPlayerEntity player) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
						new com.example.tudursvehiclemod.network.TvMissileControlEndPayload());
			}
		}
		this.tvControllingPlayerId = null;
		// Per tvSteeringActive's own doc: stops this missile's own
		// SERVER-side steering right away too (the client's own copy
		// stops separately, once client.VehicleModClient's own
		// TvMissileControlEndPayload receiver - triggered by the very
		// payload sent just above - calls tudursvehiclemod$clearTvClientTracking()
		// on it).
		this.tvSteeringActive = false;
		this.tvPostControlDescentActive = true;
	}

	/** Per Readme_Weapon.txt's own TVMissile doc: while tudursvehiclemod$setTvControlled()
	 * is actively in effect, steers this missile's own current direction
	 * by the controlling player's own accumulated mouse input each tick
	 * (already converted to degrees client-side, same convention as
	 * AircraftEntity's own pendingYawInput/pendingPitchInput - clamped
	 * here to TV_MISSILE_TURN_RATE_DEGREES_PER_TICK so a single large,
	 * abrupt mouse swipe can't snap-turn this missile past whatever a
	 * real guided munition could actually maneuver), preserving its own
	 * current speed exactly - this missile has no separate throttle of
	 * its own at all, purely direction control. Releases control (see
	 * tudursvehiclemod$releaseTvControl()'s own doc) the instant this
	 * missile either strays beyond TV_MISSILE_MAX_CONTROL_RANGE of
	 * wherever it was actually launched from, or its own current chunk
	 * isn't loaded anymore - either one meaning the controlling player
	 * couldn't plausibly still be watching a coherent, up-to-date video
	 * feed from it at all. */
	/** Server-only half of what used to be a single tudursvehiclemod$updateTvMissileControl()
	 * method - the release-condition checks (out of range/unloaded
	 * chunk/no longer riding the firing vehicle - see each condition's
	 * own comment below) genuinely only make sense as a SERVER-side,
	 * authoritative decision (the client has no business independently
	 * deciding control has ended - it just reacts to
	 * network.TvMissileControlEndPayload once the server actually says
	 * so). The STEERING itself is a separate method now (see
	 * tudursvehiclemod$updateTvMissileSteering()'s own doc) specifically
	 * so it can run on BOTH sides for client-side prediction, without
	 * dragging any of this server-only logic along with it. */
	protected void tudursvehiclemod$updateTvMissileServerChecks() {
		if (this.tvControllingPlayerId == null) {
			return;
		}
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		net.minecraft.util.math.BlockPos pos = this.getBlockPos();
		boolean outOfRange = this.tvLaunchPos != null && this.getEntityPos().distanceTo(this.tvLaunchPos) > TV_MISSILE_MAX_CONTROL_RANGE;
		boolean chunkUnloaded = !serverWorld.isChunkLoaded(pos);
		// Control used to stay on this missile even
		// after the controlling player dismounted their own vehicle
		// entirely, regardless of this missile's own state - checked
		// here too now, alongside the existing range/loaded-chunk
		// checks, so leaving the vehicle this was fired from (dismount
		// outright, or switching to some OTHER vehicle) always hands
		// the camera/input back to normal, independent of whatever this
		// missile itself is still doing.
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity firingVehicle = this.tudursvehiclemod$getFiringVehicle();
		Entity controllingPlayerEntity = serverWorld.getEntityById(this.tvControllingPlayerId);
		boolean noLongerRidingFiringVehicle = firingVehicle == null
				|| !(controllingPlayerEntity instanceof net.minecraft.server.network.ServerPlayerEntity)
				|| controllingPlayerEntity.getVehicle() != firingVehicle;
		if (outOfRange || chunkUnloaded || noLongerRidingFiringVehicle) {
			this.tudursvehiclemod$releaseTvControl();
		}
	}

	/** This missile's own view shook violently
	 * and was nearly impossible to actually control: this used to only
	 * ever run inside tick()'s own SERVER-only half - see
	 * tvSteeringActive's own doc for the full explanation of why that
	 * meant the CLIENT never predicted this missile's own steering
	 * locally at all, relying purely on however often sync packets
	 * happened to arrive instead. Runs UNCONDITIONALLY now (both client
	 * and server - see tick()'s own call site), gated by tvSteeringActive
	 * (a plain, common flag both sides can set independently - see that
	 * field's own doc) instead of tvControllingPlayerId (server-only
	 * truth the client never actually receives at all). Consumes
	 * whatever's accumulated in pendingTvYawInput/pendingTvPitchInput
	 * regardless of which side actually populated them (the SERVER's own
	 * copy via network.TvMissileInputPayload's own handler, the CLIENT's
	 * own copy directly, by client.VehicleModClient's own tick handler -
	 * see that class's own doc) - this method itself doesn't need to
	 * know or care which side it's running on at all. */
	protected void tudursvehiclemod$updateTvMissileSteering() {
		if (!this.tvSteeringActive) {
			return;
		}
		Vec3d velocity = this.getVelocity();
		if (velocity.lengthSquared() < 1.0E-6 && this.tvCruiseSpeed < 1.0E-6) {
			this.pendingTvYawInput = 0f;
			this.pendingTvPitchInput = 0f;
			return;
		}
		float yawDelta = MathHelper.clamp(this.pendingTvYawInput, -TV_MISSILE_TURN_RATE_DEGREES_PER_TICK, TV_MISSILE_TURN_RATE_DEGREES_PER_TICK);
		float pitchDelta = MathHelper.clamp(this.pendingTvPitchInput, -TV_MISSILE_TURN_RATE_DEGREES_PER_TICK, TV_MISSILE_TURN_RATE_DEGREES_PER_TICK);
		this.tvCurrentYaw += yawDelta;
		this.tvCurrentPitch = MathHelper.clamp(this.tvCurrentPitch + pitchDelta, -89.0f, 89.0f);
		double yawRad = Math.toRadians(this.tvCurrentYaw);
		double pitchRad = Math.toRadians(this.tvCurrentPitch);
		double headingX = -Math.sin(yawRad) * Math.cos(pitchRad);
		double headingY = -Math.sin(pitchRad);
		double headingZ = Math.cos(yawRad) * Math.cos(pitchRad);
		// This missile gradually slowed to a
		// dead stop mid-flight: multiplies by tvCruiseSpeed (the FIXED
		// speed captured once at launch - see that field's own doc) here
		// instead of the "speed" extracted from THIS tick's own current
		// (possibly already drag-decayed) velocity - actively holding a
		// constant thrust/cruise speed every tick, the same "ramp/hold
		// towards a fixed target instead of merely preserving whatever
		// it currently reads as" idea as a torpedo's own CRUISING phase.
		this.setVelocity(new Vec3d(headingX, headingY, headingZ).multiply(this.tvCruiseSpeed));
		// This missile's own displayed
		// orientation is no longer set explicitly here at all -
		// tick()'s own generic, velocity-derived recompute at the
		// bottom of that method (the exact same mechanism every OTHER
		// projectile type here already uses) handles it instead, now
		// that this method runs AFTER super.tick() (see tick()'s own
		// doc) and is therefore the LAST thing to touch velocity each
		// tick - meaning that generic recompute, reading velocity right
		// afterward, always reflects this exact intended heading
		// already, with no need for a separate, independently-tracked
		// orientation of its own at all. tvCurrentYaw/tvCurrentPitch
		// still exist purely as this method's own STEERING state (the
		// commanded direction driving velocity above) - they just no
		// longer get pushed onto the entity's own yaw/pitch directly.
		this.pendingTvYawInput = 0f;
		this.pendingTvPitchInput = 0f;
	}

	/** How fast horizontal velocity bleeds off during the post-control
	 * descent (see tvPostControlDescentActive's own doc) - a released
	 * missile coasts to a horizontal stop rather than continuing to
	 * fly level or veer off on whatever heading it last had. */
	protected static final float TV_POST_CONTROL_HORIZONTAL_DECAY = 0.92f;
	/** Fixed (not accelerating) sink speed (blocks/tick) during the
	 * post-control descent - a gentle, controlled glide down towards a
	 * crash, rather than either continuing level flight or plummeting
	 * under full gravity. */
	protected static final double TV_POST_CONTROL_SINK_SPEED = -0.36;

	/** Once a TV-guided missile's own control has
	 * ended (see tvPostControlDescentActive's own doc), it gently sinks
	 * towards the ground at a fixed, controlled rate - rather than
	 * falling back to vanilla's own ordinary ThrownEntity physics (the
	 * same drag/rotation-lerp behavior this class's own steering
	 * deliberately routes around while actively controlled - see tick()'s
	 * own doc) - until it eventually crashes into the ground or
	 * whatever's in its own way, exploding/discarding there exactly like
	 * any other projectile's own ordinary onBlockHit()/onEntityHit()
	 * already handles. Runs on BOTH sides (like
	 * tudursvehiclemod$updateTvMissileSteering()) for the exact same
	 * client-side prediction reasoning - this flag is a plain, common
	 * field set identically on both sides (see its own doc), so no
	 * client-only state needs to be referenced here at all. */
	protected void tudursvehiclemod$updateTvPostControlDescent() {
		if (!this.tvPostControlDescentActive) {
			return;
		}
		Vec3d velocity = this.getVelocity();
		this.setVelocity(velocity.x * TV_POST_CONTROL_HORIZONTAL_DECAY, TV_POST_CONTROL_SINK_SPEED,
				velocity.z * TV_POST_CONTROL_HORIZONTAL_DECAY);
	}

	/** Per Readme_Weapon.txt's own TrajectoryParticle doc: spawns this
	 * weapon's own configured trail particle at this projectile's
	 * current position, once trajectoryParticleStartTick ticks have
	 * passed since launch - server-side only (world.spawnParticles() on
	 * a ServerWorld already broadcasts to every nearby client on its
	 * own, the same established convention this class's own other
	 * particle effects already use). DisableSmoke (see that field's own
	 * doc) suppresses this specifically when the configured particle is
	 * one of the "smoke"-category values (smoke/largesmoke/explode/
	 * largeexplode/hugeexplosion all render as smoke-like puffs per
	 * that field's own doc) - flame is unaffected either way. */
	protected void tudursvehiclemod$updateTrajectoryParticle() {
		if (this.trajectoryParticle.isEmpty()) {
			return;
		}
		if (this.age < this.trajectoryParticleStartTick) {
			return;
		}
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		String particleName = this.trajectoryParticle.get();
		boolean isSmokeCategory = switch (particleName) {
			case "smoke", "largesmoke", "explode", "largeexplode", "hugeexplosion" -> true;
			default -> false;
		};
		if (this.disableSmoke && isSmokeCategory) {
			return;
		}
		net.minecraft.particle.ParticleEffect effect = switch (particleName) {
			case "flame" -> net.minecraft.particle.ParticleTypes.FLAME;
			case "smoke" -> net.minecraft.particle.ParticleTypes.SMOKE;
			case "largesmoke" -> net.minecraft.particle.ParticleTypes.LARGE_SMOKE;
			case "explode" -> net.minecraft.particle.ParticleTypes.POOF;
			case "largeexplode" -> net.minecraft.particle.ParticleTypes.EXPLOSION;
			case "hugeexplosion" -> net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER;
			default -> null;
		};
		if (effect == null) {
			return;
		}
		serverWorld.spawnParticles(effect, true, true, this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
	}

	protected void tudursvehiclemod$updateGuidance() {
		Vec3d targetPoint = null;
		if (this.guidanceTargetEntityId != null) {
			if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
				Entity target = serverWorld.getEntityById(this.guidanceTargetEntityId);
				if (target instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicleTarget && vehicleTarget.tudursvehiclemod$isDestroyed()) {
					// A destroyed vehicle target (still technically isAlive()==true - never actually discarded, e.g. sitting as wreckage) should self-destruct this missile rather than let it keep homing in on a wreck indefinitely.
					this.tudursvehiclemod$explodeAsLostTarget();
					return;
				}
				if (target != null && target.isAlive()) {
					targetPoint = target.getEntityPos().add(0, target.getHeight() * 0.5, 0);
				} else {
					// Target died/unloaded.
					this.guidanceTargetEntityId = null;
				}
			}
		} else if (this.guidanceTargetPos != null) {
			targetPoint = this.guidanceTargetPos;
		}

		if (targetPoint == null) {
			// GUIDED_MISSILE_ACTIVE (see getGravity()'s own doc) was set once at launch and never cleared anywhere - resumes normal gravity here so it actually falls, matching what a real missile with a lost lock would do.
			this.dataTracker.set(GUIDED_MISSILE_ACTIVE, false);
			// Counts down the delayed self-destruct started by tudursvehiclemod$disableGuidanceFromFlare() - null (never flare-disabled at all, the overwhelmingly common case) skips this entirely, so an ordinary unguided/proximity-fused weapon is completely unaffected.
			if (this.flareDisabledTicksRemaining != null) {
				this.flareDisabledTicksRemaining--;
				if (this.flareDisabledTicksRemaining <= 0) {
					this.tudursvehiclemod$explodeAsLostTarget();
				}
			}
			return;
		}
		Vec3d toTarget = targetPoint.subtract(this.getEntityPos());
		double distanceToTarget = toTarget.length();
		// A constant-speed, turn-rate-limited missile whose own minimum turn radius exceeds its initial offset from even a perfectly STATIONARY point can orbit it forever - purely a launch-geometry/maneuverability mismatch, unrelated to whether the target itself ever moves at all, so guidanceTargetPos-based guidance (MkRocket/ASMissile, and ASWeapon once it starts diving) needs this exact same safeguard too, not just entity-locked guidance. Still excludes ASWeapon's own pre-dive level-cruise phase (antiSubmarineDiveDistance set, dive not yet started) - see updateGuidance()'s own cruisePoint branch below for why "not closing on the real target" is an expected, correct part of that specific phase alone. See guidanceBestDistance's own doc for the tracking itself.
		boolean trackGuidanceProgress = this.guidanceTargetEntityId != null
				|| (this.guidanceTargetPos != null
						&& (this.antiSubmarineDiveDistance == null || this.tudursvehiclemod$isUnderwaterCruiseCapable()));
		if (trackGuidanceProgress) {
			if (this.age < this.rigidityTimeTicks) {
				// Still rigid/unguided - keeps the running minimum current (so the actual stagnant-tick clock starts fresh, fair, and from wherever distance genuinely stands the instant steering begins) without accumulating any stagnant ticks at all during a phase where "not closing" is expected, correct behavior, not a sign of anything actually wrong.
				this.guidanceBestDistance = Math.min(this.guidanceBestDistance, distanceToTarget);
			} else if (distanceToTarget < this.guidanceBestDistance - LOST_TARGET_DISTANCE_MARGIN) {
				this.guidanceBestDistance = distanceToTarget;
				this.guidanceStagnantTicks = 0;
			} else {
				this.guidanceStagnantTicks++;
				if (this.guidanceStagnantTicks >= LOST_TARGET_TIMEOUT_TICKS) {
					this.tudursvehiclemod$explodeAsLostTarget();
					return;
				}
			}
		}
		// Per Readme_Weapon.txt's own ProximityFuseDist doc: detonates the
		// instant a guided shot comes within this many blocks of its own
		// locked target, even without an actual direct hit - checked
		// ahead of the "already on top of it" near-zero-distance guard
		// below, since that guard's own 0.5-block threshold could
		// otherwise let a shot with a LARGER configured proximity
		// distance slip through undetonated.
		if (this.proximityFuseDist > 0f && distanceToTarget <= this.proximityFuseDist) {
			this.tudursvehiclemod$explodeIfConfigured(this.getX(), this.getY(), this.getZ());
			this.discard();
			return;
		}
		if (toTarget.lengthSquared() < 0.25) {
			// Per the same investigation as targetPoint==null's own doc above: reached the target's own position without an actual direct hit or a proximity fuse triggering (e.g. proximityFuseDist=0, or a narrow miss) - resumes normal gravity/falls from here rather than hanging in place, same reasoning.
			this.dataTracker.set(GUIDED_MISSILE_ACTIVE, false);
			return; // already essentially on top of the target - avoid a degenerate near-zero direction
		}
		// Per Readme_Weapon.txt's own RigidityTime doc: flies dead straight
		// (no course-correction at all) for this many ticks after launch,
		// before actually starting to home in - a real anti-air missile
		// needs a moment of unguided flight to physically clear its own
		// launch platform first.
		if (this.age < this.rigidityTimeTicks) {
			return;
		}
		Vec3d steeringDirection;
		double steeringPointDistance;
		// Per WeaponType.AS_WEAPON's own doc and antiSubmarineDiveDistance's own doc: while still cruising above water (not yet close enough to dive), steers toward a level point above the LOCAL water surface near the target's own XZ, rather than the real target point itself (which may be underwater, at depth). The instant distanceToTarget crosses within DiveDistance, activates the deferred underwater-cruise-capable setup (see tudursvehiclemod$setAntiSubmarineDive()'s own doc) - from that point on this branch is skipped (tudursvehiclemod$isUnderwaterCruiseCapable() is now true), so steering falls through to the normal toTarget.normalize() case below, diving straight at the real (possibly underwater) target for the remainder of the approach; the moment it actually touches water, the existing torpedo-style CRUISING transition (tudursvehiclemod$updateUnderwaterCruise(), already fully independent of this method - see that method's own GUIDED_TORPEDO doc for its own separate, self-contained depth/pitch steering) takes over completely.
		if (this.antiSubmarineDiveDistance != null && !this.tudursvehiclemod$isUnderwaterCruiseCapable() && distanceToTarget <= this.antiSubmarineDiveDistance) {
			this.tudursvehiclemod$setUnderwaterCruiseCapable(this.antiSubmarineAccelerationInWater, this.antiSubmarineVelocityInWater, this.antiSubmarineTargetDepthOffset);
		}
		if (this.antiSubmarineDiveDistance != null && !this.tudursvehiclemod$isUnderwaterCruiseCapable()) {
			double waterSurfaceY = tudursvehiclemod$findWaterSurfaceY(this.getEntityWorld(), targetPoint.x, targetPoint.y, targetPoint.z);
			Vec3d cruisePoint = new Vec3d(targetPoint.x, waterSurfaceY + ANTI_SUBMARINE_CRUISE_ALTITUDE_ABOVE_WATER, targetPoint.z);
			Vec3d toCruisePoint = cruisePoint.subtract(this.getEntityPos());
			steeringDirection = toCruisePoint.normalize();
			steeringPointDistance = toCruisePoint.length();
		} else if (this.topAttack) {
			// Top Attack (ATMissile's own ModeNum=2 mode): climbs toward a point above the target instead of heading straight at it, only diving steeply once within TOP_ATTACK_DIVE_TRIGGER_DISTANCE.
			double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
			if (horizontalDistance > TOP_ATTACK_DIVE_TRIGGER_DISTANCE) {
				Vec3d climbPoint = targetPoint.add(0, TOP_ATTACK_CLIMB_HEIGHT, 0);
				Vec3d toClimbPoint = climbPoint.subtract(this.getEntityPos());
				steeringDirection = toClimbPoint.normalize();
				steeringPointDistance = toClimbPoint.length();
			} else {
				steeringDirection = toTarget.normalize();
				steeringPointDistance = distanceToTarget;
			}
		} else {
			steeringDirection = toTarget.normalize();
			steeringPointDistance = distanceToTarget;
		}
		// Detects whether this missile's own minimum turn radius (speed/turnRate) already exceeds its own current distance to whatever steeringDirection is aiming at THIS tick, combined with a wide off-boresight angle - the exact geometric condition that causes endless circling around a point in the first place (see the earlier direct report's own diagnosis - "ミサイル発射位置とミサイル側の運動性能との相性"). Turning at full rate while already "inside" this radius just spins it in a tight, useless loop forever; flying straighter instead (a small fraction of the normal turn rate, not zero) lets it separate and open the geometry back up until the remaining distance genuinely exceeds the turn radius again, at which point normal full-rate pursuit resumes and can actually converge. Recomputed against whatever point this tick's own phase (cruise/climb/direct) is actually steering toward, not always the real target - a wide-orbit risk exists around ANY point this missile is currently trying to converge on, not only the final one.
		float effectiveTurnRate = this.turnRateDegreesPerTick;
		Vec3d currentDirection = this.getVelocity().lengthSquared() > 1.0e-6 ? this.getVelocity().normalize() : steeringDirection;
		double offBoresightDegrees = Math.toDegrees(Math.acos(MathHelper.clamp(currentDirection.dotProduct(steeringDirection), -1.0, 1.0)));
		double speedPerTick = this.getVelocity().length();
		double turnRadiusBlocks = speedPerTick / Math.toRadians(Math.max(this.turnRateDegreesPerTick, 0.01f));
		if (offBoresightDegrees > WIDE_ORBIT_ANGLE_THRESHOLD_DEGREES && steeringPointDistance < turnRadiusBlocks * WIDE_ORBIT_RADIUS_MARGIN) {
			effectiveTurnRate = this.turnRateDegreesPerTick * WIDE_ORBIT_TURN_RATE_FRACTION;
		}
		this.setVelocity(tudursvehiclemod$steerTowards(this.getVelocity(), steeringDirection, effectiveTurnRate));
	}

	/** Rotates `current` toward `desiredDirection` by at most maxAngleDegrees this call, preserving current's own magnitude (speed). */
	protected Vec3d tudursvehiclemod$steerTowards(Vec3d current, Vec3d desiredDirection, float maxAngleDegrees) {
		double speed = current.length();
		if (speed < 1.0E-6) {
			return desiredDirection.multiply(speed);
		}
		Vector3f fromVec = new Vector3f((float) (current.x / speed), (float) (current.y / speed), (float) (current.z / speed));
		Vector3f toVec = new Vector3f((float) desiredDirection.x, (float) desiredDirection.y, (float) desiredDirection.z);
		Quaternionf fullRotation = new Quaternionf().rotationTo(fromVec, toVec);
		float totalAngleDegrees = (float) Math.toDegrees(2.0 * Math.acos(MathHelper.clamp(fullRotation.w, -1.0f, 1.0f)));
		float t = totalAngleDegrees > 1.0E-3f ? Math.min(1f, maxAngleDegrees / totalAngleDegrees) : 0f;
		Quaternionf partialRotation = new Quaternionf().slerp(fullRotation, t);
		Vector3f result = new Vector3f(fromVec);
		partialRotation.transform(result);
		// Multiplies by guidedCruiseSpeed (the FIXED
		// speed captured once guidance started - see that field's own
		// doc) here instead of the local "speed" this function's own
		// caller happened to pass in (this tick's own current, possibly
		// already drag-decayed velocity) - actively holding a constant
		// cruise speed every tick instead of merely preserving whatever
		// it currently reads as.
		return new Vec3d(result.x, result.y, result.z).multiply(this.guidedCruiseSpeed);
	}

	/** Torpedo only - transitions from "falling under gravity" to "depth-
	 * seeking underwater cruise" the moment it first touches water. Per a
	 * direct report, simply zeroing gravity on water entry (getGravity()'s
	 * own override, above) wasn't enough on its own: vanilla's own thrown-
	 * item water drag still rapidly decayed whatever velocity the torpedo
	 * had left to essentially nothing within a tick or two, leaving it to
	 * just sit in place and slowly sink rather than actually cruising
	 * anywhere.
	 *
	 * 1. FALLING - ordinary ballistic fall under gravity, same as before,
	 * until the torpedo first touches water.
	 * 2. CRUISING - begins the instant the torpedo touches water (or, for
	 * one already in the water at launch, immediately).
	 * An earlier version of this FLATTENED pitch to
	 * level immediately on water entry, which (a) looked visually wrong
	 * for a torpedo fired at an angle (a sudden, hard snap to level)
	 * and (b) meant a torpedo fired at an angle from a diving submarine
	 * never actually behaved any differently from a level shot at all.
	 * Instead, this torpedo's own pitch AT THE MOMENT CRUISING BEGINS
	 * (whatever it actually was - preserved, not flattened) is eased
	 * towards level (0) as this torpedo approaches its own TARGET DEPTH
	 * (see targetDepthOffset's own doc) - specifically, proportionally
	 * to how much of its own STARTING depth error has closed so far:
	 * torpedoPitch = torpedoInitialPitch * (remainingDepthError /
	 * initialDepthError), clamped to [0, 1] for that ratio so it can
	 * never overshoot past level or reverse sign. Yaw stays fixed at
	 * whatever it was at the moment CRUISING began - only pitch keeps
	 * changing afterward. Forward speed itself still ramps up from 0
	 * towards accelerationInWaterTarget (the firing weapon's own
	 * AccelerationInWater stat) at a rate controlled by
	 * velocityInWaterRate (the firing weapon's own VelocityInWater
	 * stat), same "engine spooling up" behavior as before - only the
	 * PITCH handling actually changed. */
	protected void tudursvehiclemod$updateUnderwaterCruise() {
		if (!this.tudursvehiclemod$isUnderwaterCruiseCapable()) {
			return;
		}
		// A torpedo's own visual orientation
		// didn't match its actual travel direction (most noticeable on an
		// angled shot): the actual FALLING->CRUISING transition below -
		// which locks in a specific snapshot of this torpedo's own
		// current velocity at one exact instant - is now SERVER-ONLY (see
		// CRUISE_STARTED's own doc for exactly why). The CLIENT only ever
		// reads whatever the server already locked in and synced down,
		// via this.dataTracker.get(CRUISE_STARTED) below, rather than
		// deciding for itself (based on its own, potentially
		// slightly-different-tick copy of isTouchingWater()) when that
		// one-time capture should happen.
		if (!this.dataTracker.get(CRUISE_STARTED)) {
			if (this.getEntityWorld().isClient() || !this.isTouchingWater()) {
				return;
			}
			Vec3d velocity = this.getVelocity();
			Vec3d horizontalVelocity = new Vec3d(velocity.x, 0.0, velocity.z);
			float cruiseYawDegrees = horizontalVelocity.length() > 1.0E-6
					? (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z))
					: UNDERWATER_CRUISE_FALLBACK_YAW_DEGREES;
			// An upward-angled shot flattened
			// to level immediately: this.getPitch() can still hold a
			// stale/unsynced value here if this torpedo transitions
			// straight from FALLING to CRUISING on its own very FIRST
			// tick (e.g. fired from a vehicle already at/near the
			// waterline, so isTouchingWater() is already true before
			// this projectile's own pitch has ever actually been
			// synced to its real launch velocity - that sync only
			// happens later, in THIS SAME tick, at the bottom of
			// tick() itself). Deriving pitch directly from velocity
			// here instead (same formula as tick()'s own recompute)
			// is always reliable, regardless of tick ordering.
			double speed = velocity.length();
			float initialPitch = speed > 1.0E-6
					? (float) Math.toDegrees(-Math.asin(MathHelper.clamp(velocity.y / speed, -1.0, 1.0)))
					: this.getPitch();
			double waterSurfaceY = tudursvehiclemod$findWaterSurfaceY(this.getEntityWorld(), this.getX(), this.getY(), this.getZ());
			float targetDepthY = (float) (waterSurfaceY - this.dataTracker.get(TARGET_DEPTH_OFFSET));
			float initialDepthError = (float) (this.getY() - targetDepthY);
			this.dataTracker.set(CRUISE_YAW_DEGREES, cruiseYawDegrees);
			this.dataTracker.set(CRUISE_INITIAL_PITCH, initialPitch);
			this.dataTracker.set(CRUISE_TARGET_DEPTH_Y, targetDepthY);
			this.dataTracker.set(CRUISE_INITIAL_DEPTH_ERROR, initialDepthError);
			this.currentCruiseSpeed = 0f;
			this.underwaterCruisePhase = UnderwaterCruisePhase.CRUISING;
			this.setVelocity(Vec3d.ZERO);
			this.dataTracker.set(CRUISE_STARTED, true);
			return;
		}

		// CRUISING - see this method's own doc above for the overall
		// design; everything from here on reads only the already-synced
		// snapshot above, so client and server always compute the exact
		// same trajectory from here on, and can safely run this every
		// tick on BOTH sides (matching every other projectile type's own
		// yaw/pitch-from-velocity recompute at the bottom of tick(), for
		// the same smooth, continuously-updated local visuals).
		this.underwaterCruisePhase = UnderwaterCruisePhase.CRUISING;
		this.currentCruiseSpeed = MathHelper.lerp(this.dataTracker.get(VELOCITY_IN_WATER_RATE), this.currentCruiseSpeed, this.dataTracker.get(ACCELERATION_IN_WATER_TARGET));

		float cruiseYawDegrees = this.dataTracker.get(CRUISE_YAW_DEGREES);
		float initialPitch = this.dataTracker.get(CRUISE_INITIAL_PITCH);
		float initialDepthError = this.dataTracker.get(CRUISE_INITIAL_DEPTH_ERROR);
		float targetDepthY = this.dataTracker.get(CRUISE_TARGET_DEPTH_Y);

		double currentDepthError = this.getY() - targetDepthY;
		// Per Readme_Weapon.txt's own GuidedTorpedo doc: false means this
		// torpedo travels in a straight line once submerged, with no
		// homing/course-correction behavior at all - stays at its own
		// initialPitch (the angle it actually entered the water at)
		// throughout, rather than gradually correcting towards its own
		// target depth the way a guided torpedo does. Still gets
		// AccelerationInWater/VelocityInWater's own underwater speed
		// ramp either way - that's a separate concern from steering.
		float torpedoPitch;
		if (!this.dataTracker.get(GUIDED_TORPEDO)) {
			torpedoPitch = initialPitch;
		} else {
			// How much of the STARTING depth error is still remaining (1 = none closed yet, 0 = fully closed/at target depth) - clamped so this can never exceed the starting error's own magnitude or flip sign (which would otherwise happen right as this torpedo crosses past its own target depth).
			float remainingFraction;
			if (Math.abs(initialDepthError) < 1.0E-3) {
				remainingFraction = 0f; // already essentially at target depth the instant CRUISING began - stays level throughout.
			} else {
				remainingFraction = MathHelper.clamp((float) (currentDepthError / initialDepthError), 0f, 1f);
			}
			torpedoPitch = initialPitch * remainingFraction;
		}

		float yawRad = (float) Math.toRadians(cruiseYawDegrees);
		float pitchRad = (float) Math.toRadians(torpedoPitch);
		double headingX = -Math.sin(yawRad) * Math.cos(pitchRad);
		double headingY = -Math.sin(pitchRad);
		double headingZ = Math.cos(yawRad) * Math.cos(pitchRad);
		// This torpedo continued to climb forever
		// once it actually breached the water surface: an upward-angled
		// shot (or a target depth close enough to the surface to overshoot
		// past it) could leave currentDepthError permanently positive once
		// airborne (this torpedo now flying further and further AWAY from
		// its own target depth, rather than towards it), which pins
		// remainingFraction at its own maximum (1) - and therefore
		// torpedoPitch at its own full initialPitch - indefinitely, since
		// nothing above ever actually re-checks whether this torpedo is
		// still genuinely submerged at all. Rather than letting that
		// runaway climb happen and only reacting afterward, this clamps
		// the VERTICAL component directly: once this torpedo's own next
		// position would put it at or above waterSurfaceY (reconstructed
		// from this torpedo's own already-synced targetDepthY plus its own
		// configured depth offset - the surface level AT THE SPOT cruising
		// actually began, close enough for a torpedo's own generally
		// near-straight path), any further upward pull is zeroed out
		// entirely - it can still move level or dive, just never actually
		// break the surface in the first place.
		double waterSurfaceY = targetDepthY + this.dataTracker.get(TARGET_DEPTH_OFFSET);
		double projectedNextY = this.getY() + headingY * this.currentCruiseSpeed;
		if (headingY > 0.0 && projectedNextY >= waterSurfaceY) {
			headingY = 0.0;
		}
		this.setVelocity(new Vec3d(headingX, headingY, headingZ).multiply(this.currentCruiseSpeed));
	}

	/** Public entry point for AbstractVehicleEntity's own custom mesh-based
	 * hit detection (see that class's own tudursvehiclemod$applyProjectileHits()
	 * Doc) - a hit registered through that path
	 * (rather than vanilla's own onEntityHit/onBlockHit, both of which
	 * already call tudursvehiclemod$explodeIfConfigured() themselves) was
	 * missing this weapon's own explosion/effects entirely, going
	 * straight to discard() with no detonation at all. This gives that
	 * path the exact same explosion/effects treatment. */
	public void tudursvehiclemod$explodeAndDiscardForVehicleHit() {
		this.tudursvehiclemod$explodeIfConfigured(this.getX(), this.getY(), this.getZ());
		this.discard();
	}

	/** Explosion (if the applicable power > 0) and/or setting the struck entity/area on fire (if flaming). */
	/** Type=Dispenser only - see WeaponStats's own dispenseItem/dispenseRange
	 * Doc / WeaponType.DISPENSER's own doc. "uses"
	 * dispenseItem AT this projectile's own actual impact point, exactly
	 * as if a player had used it on the block there (e.g. flint_and_steel
	 * ignites it, a hoe tills farmland,..) - water_bucket is special-
	 * cased to extinguish nearby fire/lava instead of placing an actual
	 * water source, since a water-bucket USE on a solid block would
	 * otherwise just place a source block, not "put out fire" the way
	 * this weapon is actually meant to. Also spawns a spread of falling
	 * particles across dispenseRange, the visual "raining down over an
	 * area" this weapon is documented to have. A no-op entirely if
	 * dispenseItem is empty (i.e. this projectile isn't actually a
	 * Dispenser-type shot at all - see tudursvehiclemod$setDispenseItem()'s
	 * own doc). */
	protected void tudursvehiclemod$dispenseIfConfigured(double x, double y, double z) {
		tudursvehiclemod$dispenseIfConfigured(x, y, z, null);
	}

	/** blockHitResult, when available (from a real onBlockHit - see that method's own doc), gives the EXACT solid block actually struck and which face it was struck on - both needed for item.useOnBlock() to correctly resolve where its own effect actually applies (e.g. flint_and_steel ignites the AIR block adjacent to the struck face, not the struck block itself - a hardcoded "look straight up from the projectile's own current (x,y,z)" guess, which is what an earlier version of this did, is wrong on anything but a flat top-down hit, and even then was targeting the wrong BlockPos entirely - see this method's own risk of hitting air, explained above). Null (an entity hit, or any other path with no real raycast) falls back to a straight-up guess from (x,y,z) directly - less accurate, but still gives the falling-particle visual and water_bucket extinguish effect something reasonable to work from. */
	protected void tudursvehiclemod$dispenseIfConfigured(double x, double y, double z, net.minecraft.util.hit.BlockHitResult blockHitResult) {
		if (this.dispenseItem.isEmpty() || !(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		net.minecraft.util.math.BlockPos impactPos = blockHitResult != null
				? blockHitResult.getBlockPos()
				: net.minecraft.util.math.BlockPos.ofFloored(x, y, z);
		// This weapon's own configured DispenseRange
		// (read all the way from the.txt file - see WeaponStats's own doc)
		// is used for BOTH the falling-particle visual AND the actual
		// item-use effect area below - an earlier version of this
		// introduced a separate fixed 2.0f constant for the effect radius
		// specifically, overriding whatever DispenseRange was actually
		// configured to; that was simply wrong to add given the full
		// reading chain (parser -> WeaponStats -> WeaponDefinition ->
		// AbstractVehicleEntity -> this field) was already correct.
		float range = this.dispenseRange;
		for (int i = 0; i < 12; i++) {
			double offsetX = (serverWorld.random.nextDouble() - 0.5) * 2.0 * range;
			double offsetZ = (serverWorld.random.nextDouble() - 0.5) * 2.0 * range;
			serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE, true, true,
					impactPos.getX() + 0.5 + offsetX, impactPos.getY() + 3.0, impactPos.getZ() + 0.5 + offsetZ,
					1, 0.0, -0.05, 0.0, 0.02);
		}
		String itemId = this.dispenseItem.get();
		if (itemId.equals("water_bucket")) {
			tudursvehiclemod$extinguishNear(serverWorld, impactPos, range);
			return;
		}
		net.minecraft.util.Identifier itemIdentifier = net.minecraft.util.Identifier.tryParse(itemId);
		if (itemIdentifier == null || !net.minecraft.registry.Registries.ITEM.containsId(itemIdentifier)) {
			return;
		}
		Entity ownerEntity = this.getOwner();
		if (!(ownerEntity instanceof net.minecraft.server.network.ServerPlayerEntity shooter)) {
			return; // Only a real player-owned shot can "use" an item - matches every other player-attributed action here (damage source, etc).
		}
		net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(itemIdentifier);
		// Applies to every block within this weapon's own configured
		// DispenseRange of the actual impact point, not just the single
		// block struck - the EXACT impact block itself (when known) uses
		// the real BlockHitResult (correct struck face); every other
		// candidate block in range uses a reasonable "straight up" guess
		// instead (matching the doc's own admission that whether a given
		// item actually has any effect at all is inherently a "try it and
		// see" situation, even in MC Heli itself, so a perfectly accurate
		// face for every single nearby block isn't really the point here).
		int r = Math.max(1, MathHelper.ceil(range));
		net.minecraft.util.math.BlockPos.Mutable cursor = new net.minecraft.util.math.BlockPos.Mutable();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					cursor.set(impactPos.getX() + dx, impactPos.getY() + dy, impactPos.getZ() + dz);
					if (cursor.getSquaredDistance(impactPos) > (double) range * range) {
						continue;
					}
					net.minecraft.util.math.BlockPos candidatePos = cursor.toImmutable();
					net.minecraft.util.hit.BlockHitResult effectiveHitResult =
							(blockHitResult != null && candidatePos.equals(impactPos))
									? blockHitResult
									: new net.minecraft.util.hit.BlockHitResult(
											net.minecraft.util.math.Vec3d.ofCenter(candidatePos).add(0, 0.5, 0),
											net.minecraft.util.math.Direction.UP, candidatePos, false);
					net.minecraft.item.ItemUsageContext context = new net.minecraft.item.ItemUsageContext(
							serverWorld, shooter, net.minecraft.util.Hand.MAIN_HAND, item.getDefaultStack(), effectiveHitResult);
					item.useOnBlock(context);
				}
			}
		}
	}

	/** Extinguishes fire/(a small amount of) lava within range blocks of centerPos - see tudursvehiclemod$dispenseIfConfigured()'s own doc for the water_bucket special case this serves. */
	protected static void tudursvehiclemod$extinguishNear(ServerWorld world, net.minecraft.util.math.BlockPos centerPos, float range) {
		int r = Math.max(1, MathHelper.ceil(range));
		net.minecraft.util.math.BlockPos.Mutable cursor = new net.minecraft.util.math.BlockPos.Mutable();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					cursor.set(centerPos.getX() + dx, centerPos.getY() + dy, centerPos.getZ() + dz);
					if (cursor.getSquaredDistance(centerPos) > (double) range * range) {
						continue;
					}
					net.minecraft.block.BlockState state = world.getBlockState(cursor);
					if (state.isOf(net.minecraft.block.Blocks.FIRE) || state.isOf(net.minecraft.block.Blocks.SOUL_FIRE)) {
						world.setBlockState(cursor, net.minecraft.block.Blocks.AIR.getDefaultState());
					}
				}
			}
		}
	}

	/** When it either loses a target it can genuinely no longer catch (see guidanceStagnantTicks's own doc), or its target turns out to have already been destroyed (see tudursvehiclemod$updateGuidance()'s own doc), or its own guidance was disabled entirely by a target's flare (see WeaponType's own FlareType doc) - mirrors the existing ProximityFuseDist self-destruct exactly (an explosion at this projectile's own current position, then discard()), the single shared choke point all three of those cases route through. */
	protected void tudursvehiclemod$explodeAsLostTarget() {
		this.tudursvehiclemod$explodeIfConfigured(this.getX(), this.getY(), this.getZ());
		this.discard();
	}

	protected void tudursvehiclemod$explodeIfConfigured(double x, double y, double z) {
		if (this.getEntityWorld().isClient()) {
			return;
		}
		// ExplosionInWater is used instead of the
		// plain Explosion power specifically when this torpedo is
		// underwater, matching Readme_Weapon.txt's own documented
		// distinction between the two. Uses isTouchingWater() (the
		// entity's own bounding-box-based water check, already used
		// elsewhere in this class) rather than sampling the fluid state at
		// the single exact explosion point - the
		// latter kept reporting "not underwater" even for a clearly
		// submerged detonation: hitting a SOLID surface (a hull, a wall,
		// the seafloor) means the explosion's own x/y/z sits essentially
		// ON that solid block, not in the adjacent water, so a single-
		// point sample there would land on the wrong block entirely.
		boolean underwater = this.isTouchingWater();
		// EVERY visual effect below
		// (flash/smoke/water column/ripple) is scaled from this single
		// "power" value - Explosion/ExplosionInWater (this.explosionPower/
		// this.explosionPowerInWater) - and NEVER from
		// this.explosionBlockPower (ExplosionBlock's own, separate,
		// block-destruction-only radius, used only a little further down
		// for tudursvehiclemod$destroyBlocksManually()). A weapon with a
		// much larger ExplosionBlock than Explosion will still blow out a
		// correspondingly bigger crater (that's what ExplosionBlock is
		// FOR), but the flash/smoke/column/ripple themselves stay sized to
		// Explosion alone, regardless of that crater's own size.
		float power = underwater ? this.explosionPowerInWater : this.explosionPower;
		if (power <= 0f) {
			return;
		}
		// Damage/knockback/sound/vanilla's own default particles - but
		// NEVER lets vanilla handle block destruction itself (always
		// NONE here, regardless of explosionDestroysBlocks) - per a
		// direct report, vanilla's own explosion block-destruction is
		// silently reduced or disabled entirely underwater (a genuine,
		// documented vanilla mechanic, not a bug in this mod), which made
		// a torpedo detonation underwater look like it did nothing to the
		// surrounding terrain even with ExplosionDestroysBlocks
		// configured. Block destruction (if actually configured) is
		// instead always handled manually below, independent of whether
		// the explosion happens to be underwater or not.
		this.getEntityWorld().createExplosion(this, x, y, z, power, this.flaming, net.minecraft.world.World.ExplosionSourceType.NONE);
		if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
			// ExplosionBlock's own value (if explicitly set - see its
			// own field doc) is used directly as the destruction
			// power/radius here, since it wasn't
			// being reflected at all: an earlier version only checked
			// whether it was greater than 0 and then always used the
			// entity-damage power (power, above) for the actual radius
			// regardless of what ExplosionBlock itself specified.
			// Per Readme_Weapon.txt's own FAE doc: a fuel-air explosive
			// NEVER destroys blocks at all, regardless of whatever
			// ExplosionBlock/explosionDestroysBlocks is otherwise
			// configured to (a real fuel-air blast is a wide-area,
			// low-cratering explosion, unlike a normal high-explosive
			// one) - checked first, ahead of explosionDestroysBlocks
			// itself, so it always wins over that setting rather than
			// the other way around.
			if (this.explosionDestroysBlocks && !this.fuelAirExplosive) {
				float blockPower = this.explosionBlockPower >= 0f ? this.explosionBlockPower : power;
				tudursvehiclemod$destroyBlocksManually(serverWorld, x, y, z, blockPower);
			}
			// This mod's own custom explosion flash (see
			// tudursvehiclemod$spawnBigExplosionParticles()'s own doc) -
			// scaled to the explosion's own power so a power-7 torpedo
			// actually reads as dramatically bigger than a power-1 one,
			// rather than both looking the same size.
			tudursvehiclemod$spawnBigExplosionParticles(serverWorld, x, y, z, power);
			tudursvehiclemod$applyVehicleBlastDamage(serverWorld, x, y, z, power);
			if (underwater) {
				// A big water column/splash burst
				// (this mod's own custom WaterSplashParticle, matching the
				// same "custom, not just vanilla" treatment as the
				// explosion flash itself). Scaled up from the small
				// per-bullet splash's own base count/size by the
				// explosion's own power, and positioned at the actual water
				// SURFACE (scanned upward from here) rather than wherever
				// underwater the detonation itself happened, since a real
				// water column rises from the surface regardless of how
				// Deep the actual blast was. this now
				// lingers for exactly as long as its own full rise-then-
				// fall animation takes (see
				// tudursvehiclemod$scheduleWaterColumnWaves()'s own doc for
				// how that duration itself scales with the column's own
				// peak height) via repeating waves rather than a single
				// instantaneous burst, even though each individual
				// droplet's own lifespan stays short.
				double surfaceY = tudursvehiclemod$findWaterSurfaceY(serverWorld, x, y, z);
				float columnScale = 1.5f + power * 0.3f;
				float peakHeight = power * WATER_COLUMN_HEIGHT_PER_POWER;
				int columnCount = LARGE_COLUMN_BASE_PARTICLE_COUNT + MathHelper.floor(power * 4f);
				tudursvehiclemod$spawnWaterSplash(serverWorld, x, surfaceY, z, columnScale, columnCount);
				tudursvehiclemod$scheduleWaterColumnWaves(serverWorld, x, surfaceY, z, columnScale * 0.6f, peakHeight,
						Math.max(2, columnCount / 6));
				// An expanding ripple at the water
				// surface, on top of the vertical column above - see
				// tudursvehiclemod$scheduleWaterRipple()'s own doc.
				tudursvehiclemod$scheduleWaterRipple(serverWorld, x, surfaceY, z, columnScale);
			} else {
				// Ground (non-underwater) explosions
				// also get their own dedicated smoke burst - this mod's own
				// custom SmokePuffParticle (see AbstractVehicleEntity's own
				// tudursvehiclemod$updateDamageSmoke() doc for the original
				// use of this same particle), scattered a little around
				// the blast point rather than all at one exact spot, so it
				// reads as a genuine billowing cloud instead of a single
				// puff - scaled up with the explosion's own power like
				// Every other effect here. this
				// lingers for a duration that ITSELF scales with the
				// explosion's own power (see GROUND_SMOKE_BASE_TICKS's
				// own doc) via repeating waves (see
				// tudursvehiclemod$scheduleGroundSmokeWaves()'s own doc),
				// same overall approach as the water column's own
				// persistence, rather than trying to stretch each
				// individual puff's own lifespan out that long (which,
				// given SmokePuffParticle's own continuous growth over its
				// life, would make each puff balloon to an enormous size).
				tudursvehiclemod$spawnGroundExplosionSmoke(serverWorld, x, y, z, power);
				tudursvehiclemod$scheduleGroundSmokeWaves(serverWorld, x, y, z, power);
			}
		}
	}

	/** A small explosion produced just as much
	 * lingering smoke as a large one: the ground explosion smoke's own
	 * total duration now scales with the explosion's own power, rather
	 * than always lasting the same fixed ~10 seconds regardless of size.
	 * GROUND_SMOKE_BASE_TICKS is the floor (even the smallest qualifying
	 * explosion still gets a brief puff, not nothing), GROUND_SMOKE_MAX_TICKS
	 * is the ceiling (the previous fixed duration, now only reached by a
	 * genuinely large explosion), and GROUND_SMOKE_TICKS_PER_POWER is how
	 * much duration each point of power actually adds in between. */
	protected static final int GROUND_SMOKE_BASE_TICKS = 20;
	protected static final float GROUND_SMOKE_TICKS_PER_POWER = 11.25f;
	protected static final int GROUND_SMOKE_MAX_TICKS = 200;
	/** How often (in ticks) a fresh, smaller wave of smoke puffs spawns during the explosion's own total smoke duration - slower than the water column's own wave interval, since a lingering smoke cloud doesn't need to be as continuously dense as a splash column. */
	protected static final int GROUND_SMOKE_WAVE_INTERVAL_TICKS = 10;

	/** Re-fires a smaller version of tudursvehiclemod$spawnGroundExplosionSmoke()
	 * every GROUND_SMOKE_WAVE_INTERVAL_TICKS, for a total duration that
	 * itself scales with the explosion's own power (see
	 * GROUND_SMOKE_BASE_TICKS's own doc) - same
	 * END_SERVER_TICK-listener, self-limiting-instead-of-unregistering
	 * approach as tudursvehiclemod$scheduleWaterColumnWaves() (see that
	 * method's own doc for why). */
	protected static void tudursvehiclemod$scheduleGroundSmokeWaves(ServerWorld world, double x, double y, double z, float power) {
		int totalTicks = Math.min(GROUND_SMOKE_MAX_TICKS,
				GROUND_SMOKE_BASE_TICKS + MathHelper.floor(power * GROUND_SMOKE_TICKS_PER_POWER));
		int totalWaves = Math.max(1, totalTicks / GROUND_SMOKE_WAVE_INTERVAL_TICKS);
		int perWaveCount = Math.max(2, (2 + MathHelper.floor(power)));
		double spread = 1.0 + power * 0.3;
		int[] tickCount = {0};
		int[] wavesSpawned = {0};
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (wavesSpawned[0] >= totalWaves) {
				return;
			}
			tickCount[0]++;
			if (tickCount[0] % GROUND_SMOKE_WAVE_INTERVAL_TICKS == 0) {
				for (int i = 0; i < perWaveCount; i++) {
					double offsetX = (world.random.nextDouble() - 0.5) * spread;
					double offsetZ = (world.random.nextDouble() - 0.5) * spread;
					double offsetY = world.random.nextDouble() * (0.5 + power * 0.2);
					world.spawnParticles(com.example.tudursvehiclemod.registry.ModParticleTypes.SMOKE_PUFF,
							true, true, x + offsetX, y + offsetY, z + offsetZ, 1, 0.05, 0.05, 0.05, 0.0);
				}
				wavesSpawned[0]++;
			}
		});
	}

	/** Ground (non-underwater) explosions get their
	 * own smoke burst on top of the existing flash/emitter/blast-damage
	 * effects - see tudursvehiclemod$explodeIfConfigured()'s own doc for
	 * where this is actually called from. Spreads the individual puffs out
	 * a little (rather than all stacking on the exact same point) and
	 * scales both the spread radius and the puff count with the
	 * explosion's own power, matching this class's own established scaling
	 * philosophy for every other effect. */
	protected static void tudursvehiclemod$spawnGroundExplosionSmoke(ServerWorld world, double x, double y, double z, float power) {
		int count = 6 + MathHelper.floor(power * 3f);
		double spread = 1.0 + power * 0.3;
		for (int i = 0; i < count; i++) {
			double offsetX = (world.random.nextDouble() - 0.5) * spread;
			double offsetZ = (world.random.nextDouble() - 0.5) * spread;
			double offsetY = world.random.nextDouble() * (0.5 + power * 0.2);
			world.spawnParticles(com.example.tudursvehiclemod.registry.ModParticleTypes.SMOKE_PUFF,
					true, true, x + offsetX, y + offsetY, z + offsetZ, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	/** How far out (relative to the explosion's own power) tudursvehiclemod$applyVehicleBlastDamage()'s own falloff reaches 0 - same value as tudursvehiclemod$destroyBlocksManually()'s own block-destruction radius, for consistency (a target right at the edge of the visible/breakable blast radius shouldn't still take significant "invisible" damage beyond it). */
	protected static final double BLAST_DAMAGE_RADIUS_MULTIPLIER = 2.0;

	/** On top of whatever vanilla's own
	 * createExplosion() call (just above, in
	 * tudursvehiclemod$explodeIfConfigured()) already did to nearby
	 * entities, this applies EXTRA damage specifically to any nearby
	 * AbstractVehicleEntity - this weapon's own configured base damage
	 * (this.damage, the same value a direct surface hit already uses),
	 * linearly reduced by distance from the explosion's own center,
	 * reaching 0 at BLAST_DAMAGE_RADIUS_MULTIPLIER * power blocks out.
	 * This guarantees a vehicle actually takes blast damage regardless of
	 * whether vanilla's own explosion-to-entity damage path happens to
	 * reach it at all - a vehicle's own getDimensions() box (used for
	 * vanilla's own distance/exposure calculations) can be very different
	 * from its own actual visual size (e.g. CarEntity's own fixed, tiny
	 * collision box), which could otherwise make vanilla's own explosion
	 * damage under-count or miss it entirely. */
	protected void tudursvehiclemod$applyVehicleBlastDamage(ServerWorld serverWorld, double x, double y, double z, float power) {
		if (this.damage <= 0f) {
			return;
		}
		double maxRadius = power * BLAST_DAMAGE_RADIUS_MULTIPLIER;
		if (maxRadius <= 0.0) {
			return;
		}
		net.minecraft.util.math.Box searchBox = new net.minecraft.util.math.Box(
				x - maxRadius, y - maxRadius, z - maxRadius, x + maxRadius, y + maxRadius, z + maxRadius);
		net.minecraft.entity.damage.DamageSource damageSource = serverWorld.getDamageSources().explosion(this, this.getOwner());
		for (com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle : this.getEntityWorld().getEntitiesByClass(
				com.example.tudursvehiclemod.entity.AbstractVehicleEntity.class, searchBox, v -> !v.isRemoved())) {
			double distance = vehicle.getEntityPos().distanceTo(new Vec3d(x, y, z));
			if (distance >= maxRadius) {
				continue;
			}
			float falloffDamage = (float) (this.damage * (1.0 - distance / maxRadius));
			if (falloffDamage <= 0f) {
				continue;
			}
			vehicle.damage(serverWorld, damageSource, falloffDamage);
		}
	}

	/** Manually breaks blocks in a roughly spherical radius around the
	 * explosion point, entirely independent of vanilla's own explosion
	 * block-destruction logic - see tudursvehiclemod$explodeIfConfigured()'s
	 * own doc for why. Silently skips only truly unbreakable blocks
	 * (bedrock, barrier, etc. - vanilla's own convention for these is a
	 * negative block hardness) and doesn't drop any items, matching a
	 * typical explosion's own usual behavior. */
	protected static void tudursvehiclemod$destroyBlocksManually(ServerWorld world, double x, double y, double z, float power) {
		int radius = Math.max(1, Math.round(power * 2f));
		net.minecraft.util.math.BlockPos center = net.minecraft.util.math.BlockPos.ofFloored(x, y, z);
		double radiusSq = (double) radius * radius;
		for (net.minecraft.util.math.BlockPos pos : net.minecraft.util.math.BlockPos.iterate(
				center.add(-radius, -radius, -radius), center.add(radius, radius, radius))) {
			if (pos.getSquaredDistance(x, y, z) > radiusSq) {
				continue;
			}
			net.minecraft.block.BlockState state = world.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (state.getHardness(world, pos) < 0f) {
				continue;
			}
			world.breakBlock(pos, false);
		}
	}

	/** The vanilla EXPLOSION_EMITTER burst and the
	 * circular CLOUD ring/column this method used to also spawn have both
	 * been removed entirely - the underwater "column" portion of that CLOUD
	 * effect was already redundant with this mod's own custom
	 * WaterSplashParticle-based column (see
	 * tudursvehiclemod$explodeIfConfigured()'s own doc), and the vanilla
	 * emitter/ring were reported as visually competing with (and
	 * obscuring) this mod's own custom flash/smoke effects rather than
	 * complementing them. This is now just the entry point for the custom
	 * flash itself. */
	protected static void tudursvehiclemod$spawnBigExplosionParticles(ServerWorld world, double x, double y, double z, float power) {
		tudursvehiclemod$spawnCustomExplosionFlash(world, x, y, z, power);
	}

	/** This mod's own custom explosion visual effect
	 * (see registry.ModParticleTypes/client.particle.ExplosionFlashParticle's
	 * own doc), spawned once per detonation right at its own center -
	 * peakScale grows with the explosion's own power so a big weapon's own
	 * flash actually reads as dramatically bigger than a small one's,
	 * matching the same scaling philosophy as the ring/column/emitter
	 * effects around it. */
	protected static void tudursvehiclemod$spawnCustomExplosionFlash(ServerWorld world, double x, double y, double z, float power) {
		float peakScale = 2.0f + power * 0.6f;
		// peakScale wasn't actually arriving intact
		// on the client (server logged 10.4, client received 1.0) despite
		// the earlier count=0/force=true fix - the "smuggle it through
		// deltaX" trick itself was unreliable. peakScale is now carried
		// directly as real, codec-serialized data on the ExplosionFlashEffect
		// object itself (see that record's own doc) - constructed here and
		// passed AS the particle argument, instead of the plain
		// ModParticleTypes.EXPLOSION_FLASH type object used previously.
		LOGGER.info("Spawning custom explosion flash at ({}, {}, {}), power={}, peakScale={}", x, y, z, power, peakScale);
		com.example.tudursvehiclemod.particle.ExplosionFlashEffect effect =
				new com.example.tudursvehiclemod.particle.ExplosionFlashEffect(peakScale);
		world.spawnParticles(effect, true, true, x, y, z, 0, 0.0, 0.0, 0.0, 0.0);
	}

	@Override
	protected boolean canHit(Entity entity) {
		if (tudursvehiclemod$isIgnorableFish(entity)) {
			return false;
		}
		return super.canHit(entity);
	}

	/** A torpedo (or any other projectile) should pass straight through ordinary fish rather than treating them as a valid hit/detonation target. */
	protected static boolean tudursvehiclemod$isIgnorableFish(Entity entity) {
		EntityType<?> type = entity.getType();
		return type == EntityType.COD || type == EntityType.SALMON
				|| type == EntityType.PUFFERFISH || type == EntityType.TROPICAL_FISH;
	}

	/** Starts the DelayFuse countdown (see tudursvehiclemod$updateImpactFuse()'s own doc) if one is actually configured - a no-op (this projectile's caller should fall back to its own original immediate explode+discard) if delayFuseTicks was never actually present in the file (see WeaponStats's own -1-sentinel doc) at all, or if the countdown has already been started by an earlier impact this same flight (a piercing/bouncing projectile that's already hit something once shouldn't restart its own fuse on every subsequent hit too). Returns true if the countdown was (already, or just now) started - the caller should hold off on exploding/discarding in that case. */
	protected boolean tudursvehiclemod$startImpactFuse() {
		if (this.delayFuseTicks < 0) {
			return false;
		}
		if (this.ticksSinceImpact < 0) {
			this.ticksSinceImpact = 0;
		}
		return true;
	}

	/** Glass and glass panes (every color,
	 * plain, and tinted - see this method's own doc for the exact block
	 * ID patterns matched) are ALWAYS destructible by ANY bullet at all,
	 * completely unlimited, regardless of whether Piercing is even
	 * configured for this weapon in the first place - and never counts
	 * against piercingCount's own separate budget either way. Matches by
	 * block ID path rather than a block class or vanilla tag - the
	 * vanilla "impermeable" tag was checked first but turned out to
	 * both miss glass panes entirely AND incorrectly include barrier
	 * blocks, so isn't actually suitable here. */
	protected static boolean tudursvehiclemod$isGlass(net.minecraft.block.BlockState state) {
		String path = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath();
		return path.equals("glass") || path.equals("tinted_glass")
				|| path.endsWith("_stained_glass") || path.equals("glass_pane")
				|| path.endsWith("_stained_glass_pane");
	}

	/** Piercing no longer destroys the block it
	 * pierces through at all - it just continues flying through that
	 * space, leaving the block itself completely intact. Since the
	 * block is still solid and would otherwise stop this projectile
	 * dead at its own surface, this instead nudges this projectile's own
	 * position forward, past the struck block, along its own current
	 * velocity direction - clearing a typical single-block-thick wall
	 * without needing to actually remove anything. Glass/glass panes
	 * (see tudursvehiclemod$isGlass()'s own doc) are a special case,
	 * unconditionally destroyed regardless of Piercing at all, and
	 * don't count against piercingCount's own budget - checked and
	 * handled FIRST, ahead of the ordinary Piercing budget check below.
	 * Returns false (the caller should fall through to its own normal
	 * Bound/DelayFuse/explode-and-discard handling instead) if neither
	 * of those apply, or the struck block turns out to be unbreakable
	 * (bedrock and the like) - a projectile doesn't get to cheat through
	 * solid bedrock just because Piercing happens to be set. */
	protected boolean tudursvehiclemod$tryPierceBlock(net.minecraft.util.hit.BlockHitResult blockHitResult) {
		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return false;
		}
		net.minecraft.util.math.BlockPos pos = blockHitResult.getBlockPos();
		net.minecraft.block.BlockState state = serverWorld.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		if (tudursvehiclemod$isGlass(state)) {
			serverWorld.breakBlock(pos, false);
			return true;
		}
		if (this.piercingCount <= 0 || this.piercedSoFar >= this.piercingCount) {
			return false;
		}
		if (state.getBlock().getBlastResistance() >= 3600000f) {
			// Unbreakable (bedrock, end portal frames,..) - doesn't pierce.
			return false;
		}
		Vec3d velocity = this.getVelocity();
		double speed = velocity.length();
		if (speed > 1.0E-6) {
			// Nudges forward by a bit more than one block's own diagonal
			// (sqrt(3) =~ 1.73, rounded up) - comfortably clears a single-
			// block-thick wall hit at any angle, without needing an exact
			// per-shape exit-point calculation.
			Vec3d nudge = velocity.multiply(1.8 / speed);
			this.setPosition(this.getX() + nudge.x, this.getY() + nudge.y, this.getZ() + nudge.z);
		}
		this.piercedSoFar++;
		return true;
	}

	/** Per Readme_Weapon.txt's own Bound doc: reflects this projectile's
	 * own velocity off hitNormal (the struck block face's own outward
	 * normal), scaled down by bounceStrength (1.0 = a perfectly elastic
	 * bounce retaining full speed, lower values lose energy each bounce -
	 * Readme_Weapon.txt's own documented range is 0.1). A no-op (the
	 * caller should fall back to its own original immediate
	 * explode+discard) if bounceStrength is 0 (the default - Bound not
	 * actually configured for this weapon at all). Per that same doc's
	 * own warning, using this without ALSO setting DelayFuse is close to
	 * pointless - the caller is expected to ALSO call
	 * tudursvehiclemod$startImpactFuse() itself right alongside this,
	 * so a projectile that bounces but has no configured DelayFuse
	 * still explodes/discards the very same tick regardless (matching
	 * MC Heli's own documented behavior for that specific combination). */
	protected boolean tudursvehiclemod$tryBounce(net.minecraft.util.math.Direction hitNormal) {
		if (this.bounceStrength <= 0f) {
			return false;
		}
		Vec3d velocity = this.getVelocity();
		Vec3d normal = new Vec3d(hitNormal.getOffsetX(), hitNormal.getOffsetY(), hitNormal.getOffsetZ());
		double dot = velocity.dotProduct(normal);
		Vec3d reflected = velocity.subtract(normal.multiply(2.0 * dot));
		this.setVelocity(reflected.multiply(this.bounceStrength));
		return true;
	}

	@Override
	protected void onEntityHit(EntityHitResult hitResult) {
		super.onEntityHit(hitResult);
		if (!this.getEntityWorld().isClient()) {
			DamageSource source = this.getDamageSources().thrown(this, this.getOwner());
			hitResult.getEntity().damage((net.minecraft.server.world.ServerWorld) this.getEntityWorld(), source, this.damage);
			if (this.flaming && this.explosionPower <= 0f) {
				// Explosion (below) already sets things on fire itself when flaming.
				hitResult.getEntity().setFireTicks(100);
			}
			this.tudursvehiclemod$dispenseIfConfigured(this.getX(), this.getY(), this.getZ());
			// Per Readme_Weapon.txt's own DelayFuse doc: an entity hit
			// that started a fuse countdown holds off on exploding/
			// discarding immediately - tudursvehiclemod$updateImpactFuse()
			// (called from tick()) takes over from here instead.
			if (this.tudursvehiclemod$startImpactFuse()) {
				return;
			}
			this.tudursvehiclemod$explodeIfConfigured(this.getX(), this.getY(), this.getZ());
			this.discard();
		}
	}

	@Override
	protected void onBlockHit(net.minecraft.util.hit.BlockHitResult blockHitResult) {
		super.onBlockHit(blockHitResult);
		if (!this.getEntityWorld().isClient()) {
			// An earlier version of this guard skipped
			// explosion+discard for EVERY block hit once underwater cruise
			// began (not just the water surface itself, which was the
			// original intent) - meaning a torpedo never actually exploded
			// on a real block collision (a wall, the seafloor, etc.) for
			// the rest of its journey. Kelp specifically is skipped instead
			// (a torpedo should pass straight through decorative
			// vegetation, not detonate on it), and everything else now
			// explodes/discards normally regardless of cruise phase.
			net.minecraft.block.BlockState hitState = this.getEntityWorld().getBlockState(blockHitResult.getBlockPos());
			if (hitState.isOf(net.minecraft.block.Blocks.KELP) || hitState.isOf(net.minecraft.block.Blocks.KELP_PLANT)) {
				return;
			}
			this.tudursvehiclemod$dispenseIfConfigured(this.getX(), this.getY(), this.getZ(), blockHitResult);
			// Per Readme_Weapon.txt's own Piercing doc: punches straight
			// through this block (destroying it) and keeps flying,
			// instead of stopping here at all, while pierce budget
			// remains - see tudursvehiclemod$tryPierceBlock()'s own doc.
			if (this.tudursvehiclemod$tryPierceBlock(blockHitResult)) {
				return;
			}
			// Per Readme_Weapon.txt's own Bound doc: bounces off this
			// block's own struck face instead of stopping here, while
			// Bound is actually configured.
			boolean bounced = this.tudursvehiclemod$tryBounce(blockHitResult.getSide());
			// Per Readme_Weapon.txt's own DelayFuse doc: a block hit that
			// started a fuse countdown (whether it also bounced or not)
			// holds off on exploding/discarding immediately -
			// tudursvehiclemod$updateImpactFuse() (called from tick())
			// takes over from here instead. Per Bound's own doc, a
			// projectile that bounced but has NO DelayFuse configured at
			// all still falls through to the original immediate
			// explode+discard below regardless (bouncing without a fuse
			// to actually wait out is otherwise pointless).
			if (this.tudursvehiclemod$startImpactFuse()) {
				return;
			}
			if (bounced) {
				return;
			}
			this.tudursvehiclemod$explodeIfConfigured(this.getX(), this.getY(), this.getZ());
			this.discard();
		}
	}
}
