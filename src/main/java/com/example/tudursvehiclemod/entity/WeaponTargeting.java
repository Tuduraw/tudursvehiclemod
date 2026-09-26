package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.asset.WeaponType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Function;
import java.util.function.Predicate;

/** Shooter-side targeting math shared by vehicle weapons and any external shooter (an addon's
 * handheld weapon, for instance): the AAMissile/ATMissile/Missile crosshair lock-on search, the
 * AS-missile ground-point raycast, and Accuracy spread.
 *
 * None of this needs a vehicle - AbstractVehicleEntity's own instance methods of the same names
 * delegate here, passing in only what is genuinely vehicle-specific (which entities to exclude,
 * and its own overridable target classification). */
public final class WeaponTargeting {

	private WeaponTargeting() {
	}

	/** How far (blocks) a raycast for ASMissile's own ground-point target, or AAMissile/ATMissile's own crosshair lock-on, searches. */
	public static final double MISSILE_TARGET_SEARCH_RANGE = 128.0;
	/** Lock-on range defaults to unlimited when a weapon's own LockRange isn't configured at all - see tudursvehiclemod$findLockOnTarget()'s own doc for why a genuinely finite ceiling is still needed even so (Box.expand()/getOtherEntities() both need one to build a search volume from at all). Chosen generously large enough that no real gameplay scenario would ever actually hit it. */
	public static final double MISSILE_LOCK_UNLIMITED_RANGE_CEILING = 4096.0;
	/** How far off-center (degrees, from the shooter's own view direction) an entity can be and still count as "under the crosshair" for AAMissile/ATMissile's own.. */
	// 5 degrees was an impractically narrow cone to hold continuously on a moving target (see tudursvehiclemod$updateMissileLockOnIndicators()'s own doc for why ANY target swap resets lock progress to 0) long enough to accumulate a weapon's own configured LockTime. Widened to a more forgiving 15.
	public static final double MISSILE_LOCK_ON_CONE_DEGREES = 15.0;
	/** Per tudursvehiclemod$classifyTargetPosition()'s own doc: minimum measured altitude (blocks) above the nearest solid block below before a candidate counts as genuinely airborne for AA_MISSILE/AT_MISSILE's own ground/air filtering, rather than merely a brief moment of not touching ground. */
	public static final double AIRBORNE_TARGET_MIN_ALTITUDE = 3.0;

	/** See AbstractVehicleEntity's own TargetPosition doc - AIRBORNE (AAMissile), SURFACE (ATMissile), SUBMERGED (neither). */
	public enum TargetPosition { AIRBORNE, SURFACE, SUBMERGED }

	/** Classifies a candidate as AIRBORNE, SURFACE or SUBMERGED - see AbstractVehicleEntity's own
	 * tudursvehiclemod$classifyTargetPosition() doc for why airborne also requires a measured
	 * altitude rather than trusting isOnGround() alone. */
	public static TargetPosition classifyTargetPosition(Entity candidate) {
		if (candidate.isSubmergedInWater()) {
			return TargetPosition.SUBMERGED;
		}
		if (candidate.isTouchingWater() || candidate.isOnGround()) {
			return TargetPosition.SURFACE;
		}
		Vec3d start = candidate.getEntityPos();
		Vec3d end = start.add(0, -256, 0);
		net.minecraft.util.hit.HitResult hit = candidate.getEntityWorld().raycast(new RaycastContext(
				start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, candidate));
		double altitude = hit instanceof net.minecraft.util.hit.BlockHitResult blockHit && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
				? Math.max(0.0, start.y - blockHit.getPos().y) : 256.0;
		return altitude > AIRBORNE_TARGET_MIN_ALTITUDE ? TargetPosition.AIRBORNE : TargetPosition.SURFACE;
	}

	/** The crosshair lock-on search with the default target classification - the entity closest
	 * to the shooter's own view direction, within MISSILE_LOCK_ON_CONE_DEGREES and lockRange.
	 *
	 * @param lockRange the weapon's own LockRange (0 = unlimited, see MISSILE_LOCK_UNLIMITED_RANGE_CEILING)
	 * @param excluded extra entities never to lock (the shooter itself is always excluded) - null for none
	 * @return the best candidate, or null if nothing qualifies */
	public static Entity findLockOnTarget(World world, LivingEntity shooter, WeaponType weaponType, double lockRange,
			Predicate<Entity> excluded) {
		return findLockOnTarget(world, shooter, weaponType, lockRange, excluded, WeaponTargeting::classifyTargetPosition);
	}

	/** Same as the overload above, with a caller-supplied target classification (AbstractVehicleEntity
	 * passes its own overridable tudursvehiclemod$classifyTargetPosition() through here). */
	public static Entity findLockOnTarget(World world, LivingEntity shooter, WeaponType weaponType, double lockRange,
			Predicate<Entity> excluded, Function<Entity, TargetPosition> classifier) {
		// 0 (LockRange's own parsed default whenever that key is absent) means unlimited, using MISSILE_LOCK_UNLIMITED_RANGE_CEILING instead - a genuinely finite value is still needed to build a search box/AABB at all, and no real gameplay scenario needs locking something literally thousands of blocks away regardless of what "unlimited" is meant to convey.
		double effectiveLockRange = lockRange > 0.0 ? lockRange : MISSILE_LOCK_UNLIMITED_RANGE_CEILING;
		Vec3d eyePos = shooter.getEyePos();
		Vec3d viewDir = shooter.getRotationVec(1.0f);
		double minDot = Math.cos(Math.toRadians(MISSILE_LOCK_ON_CONE_DEGREES));
		Box searchBox = shooter.getBoundingBox().expand(effectiveLockRange);
		Entity best = null;
		double bestDot = minDot;
		// This project's own vehicles (AbstractVehicleEntity) extend Entity directly, NOT LivingEntity - the original LivingEntity-only filter meant every aircraft/helicopter/tank in the game was silently invisible to this search, leaving only players and mobs lockable. isDestroyed() (rather than LivingEntity's own isAlive()) is the correct "still a valid target" check for a vehicle - a destroyed hull sitting there sinking/burning shouldn't be lockable.
		// CarrierRunwayPlatformEntity (a runway's own invisible support tile) extends PathAwareEntity - a genuine LivingEntity/MobEntity - so it was unintentionally matching the plain LivingEntity branch below and showing up as a lockable target, despite being an implementation detail with no meaningful existence as an actual target. Excluded explicitly.
		for (Entity candidate : world.getOtherEntities(shooter, searchBox,
				e -> (e instanceof LivingEntity living && living.isAlive()
						|| e instanceof AbstractVehicleEntity vehicleCandidate && !vehicleCandidate.tudursvehiclemod$isDestroyed())
						&& !(e instanceof CarrierRunwayPlatformEntity)
						&& (excluded == null || !excluded.test(e)))) {
			// Per Readme_Weapon.txt's own documented distinction ("AAMissile 空中にいるモブを追跡するミサイル" / "ATMissile 地上にいるモブを追跡するミサイル" - based on the TARGET's own current physical state, not its entity/vehicle type): a single shared classification (tudursvehiclemod$classifyTargetPosition()) determines whether a candidate is currently AIRBORNE, on the SURFACE (ground, or floating/standing on top of water), or SUBMERGED (underwater) - AA_MISSILE requires AIRBORNE, AT_MISSILE requires SURFACE; SUBMERGED is excluded from both (a submerged target is its own distinct category now, reserved for WeaponType.ASWeapon - see that enum's own doc). MISSILE (this project's own type, see that enum's own doc) and everything else stays unrestricted.
			if (weaponType == WeaponType.AA_MISSILE || weaponType == WeaponType.AT_MISSILE) {
				TargetPosition required = weaponType == WeaponType.AA_MISSILE ? TargetPosition.AIRBORNE : TargetPosition.SURFACE;
				if (classifier.apply(candidate) != required) {
					continue;
				}
			}
			Vec3d toCandidate = candidate.getEntityPos().subtract(eyePos);
			double distance = toCandidate.length();
			if (distance < 1.0 || distance > effectiveLockRange) {
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

	/** AS-missile / MkRocket target point: the first block under the shooter's own crosshair, or the
	 * point MISSILE_TARGET_SEARCH_RANGE blocks out if nothing is hit. */
	public static Vec3d raycastGroundPoint(World world, Entity shooter) {
		Vec3d start = shooter.getEyePos();
		Vec3d viewDir = shooter.getRotationVec(1.0f);
		Vec3d end = start.add(viewDir.multiply(MISSILE_TARGET_SEARCH_RANGE));
		RaycastContext context = new RaycastContext(start, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter);
		net.minecraft.util.hit.BlockHitResult hit = world.raycast(context);
		return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? end : hit.getPos();
	}

	/** Accuracy: tilts velocity by a random angle within a cone of half-angle accuracyDegrees,
	 * preserving speed. 0 (or less) returns velocity unchanged. */
	public static Vec3d applyAccuracySpread(Vec3d velocity, float accuracyDegrees, Random random) {
		double speed = velocity.length();
		if (speed < 1.0E-6 || accuracyDegrees <= 0f) {
			return velocity;
		}
		Vector3f dir = new Vector3f((float) (velocity.x / speed), (float) (velocity.y / speed), (float) (velocity.z / speed));
		Vector3f arbitrary = Math.abs(dir.x) < 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		Vector3f perpendicular = new Vector3f(dir).cross(arbitrary).normalize();
		float spinAngleRad = (float) Math.toRadians(random.nextFloat() * 360f);
		Quaternionf spin = new Quaternionf().rotationAxis(spinAngleRad, dir.x, dir.y, dir.z);
		Vector3f tiltAxis = spin.transform(new Vector3f(perpendicular));
		float tiltAngleRad = (float) Math.toRadians(random.nextFloat() * accuracyDegrees);
		Quaternionf tilt = new Quaternionf().rotationAxis(tiltAngleRad, tiltAxis.x, tiltAxis.y, tiltAxis.z);
		Vector3f result = tilt.transform(new Vector3f(dir));
		return new Vec3d(result.x, result.y, result.z).multiply(speed);
	}
}
