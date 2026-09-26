package com.example.tudursvehiclemod.entity.projectile;

import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Builds a projectile from a weapon file's own stats, independent of who (or what) fires it.
 *
 * This is the stats-driven half of AbstractVehicleEntity's own tryFireWeapon(), split out so the
 * vehicle path and any external shooter (an addon's handheld weapon, for instance) configure a
 * shot through exactly the same code rather than two copies drifting apart. Everything that
 * depends on the FIRING SITUATION rather than the weapon file stays with the caller:
 * <ul>
 *   <li>position, velocity and initial angles (setPosition/setVelocity/setAngles)</li>
 *   <li>guidance (setGuidanceTargetPos/setGuidanceTargetEntity/setMissileGuidanceTuning/
 *       setTopAttack/setUnderwaterCruiseCapable/setAntiSubmarineDive/setTvControlled)</li>
 *   <li>tudursvehiclemod$setFiringVehicle() - only meaningful for a vehicle-mounted shot</li>
 *   <li>world.spawnEntity() followed by tudursvehiclemod$forceLoadSpawnChunk()</li>
 * </ul>
 *
 * The returned projectile is NOT spawned yet. */
public final class WeaponProjectileFactory {

	private WeaponProjectileFactory() {
	}

	/** Creates and configures (but does not spawn) one projectile for the given weapon.
	 *
	 * @param owner the shooter - may be null (e.g. an autonomous CAS aircraft's own weapon)
	 * @param projectileStack the item stack shown as the projectile (a vehicle weapon's own
	 *        projectile_item) - only a display/cartridge item, never consumed here
	 * @param mode the weapon's currently selected ModeNum mode index (0 for a weapon without modes).
	 *        Per Readme_Weapon.txt's own ModeNum doc: a MachineGun's mode 1 is the HE round (mode 0
	 *        fires without its Explosion), and a Rocket's mode 0 fires without its Bomblet. Ignored
	 *        by every other weapon type, and by any weapon whose file has no second mode. */
	public static VehicleProjectileEntity create(World world, LivingEntity owner, ItemStack projectileStack,
			WeaponStats stats, int mode) {
		boolean isMachineGunWithModes = stats.weaponType() == WeaponType.MACHINE_GUN && stats.hasModes();
		boolean isMachineGunSecondMode = isMachineGunWithModes && mode == 1;
		boolean suppressExplosion = isMachineGunWithModes && !isMachineGunSecondMode;
		float effectiveExplosionPower = suppressExplosion ? 0f : stats.explosionPower();
		boolean effectiveExplosionDestroysBlocks = suppressExplosion ? false : stats.explosionDestroysBlocks();
		boolean effectiveFlaming = suppressExplosion ? false : stats.flaming();
		VehicleProjectileEntity projectile;
		if (stats.bulletModel().isPresent() && stats.bulletTexture().isPresent()) {
			projectile = new VehicleModelProjectileEntity(
					world, owner, projectileStack, stats.damage(), stats.gravity(),
					effectiveExplosionPower, effectiveExplosionDestroysBlocks, effectiveFlaming,
					stats.bulletModel().get(), stats.bulletTexture().get(), stats.bulletScale());
		} else {
			projectile = new VehicleProjectileEntity(
					world, owner, projectileStack, stats.damage(), stats.gravity(),
					effectiveExplosionPower, effectiveExplosionDestroysBlocks, effectiveFlaming);
		}
		projectile.tudursvehiclemod$setExplosionPowerInWater(suppressExplosion ? 0f : stats.explosionPowerInWater());
		projectile.tudursvehiclemod$setExplosionBlockPower(stats.explosionBlockPower());
		projectile.tudursvehiclemod$setExplosionAltitude(stats.explosionAltitude());
		projectile.tudursvehiclemod$setFuseTicks(stats.delayFuseTicks(), stats.timeFuseTicks());
		projectile.tudursvehiclemod$setBounceStrength(stats.bounceStrength());
		projectile.tudursvehiclemod$setGravityInWater(stats.gravityInWater());
		projectile.tudursvehiclemod$setGuidedTorpedo(stats.guidedTorpedo());
		projectile.tudursvehiclemod$setPiercingCount(stats.piercingCount());
		projectile.tudursvehiclemod$setFuelAirExplosive(stats.fuelAirExplosive());
		projectile.tudursvehiclemod$setTrajectoryParticle(stats.trajectoryParticle(), stats.trajectoryParticleStartTick(), stats.disableSmoke());
		projectile.tudursvehiclemod$setBulletColors(stats.bulletColor(), stats.bulletColorInWater());
		projectile.tudursvehiclemod$setExplodeOnWaterContact(
				stats.weaponType() == WeaponType.BOMB || stats.weaponType() == WeaponType.DROP_TANK);
		projectile.tudursvehiclemod$setSplashScale(stats.bulletScale());
		// Empty/default for every weapon type other than Dispenser, which is the only one that ever
		// checks it - see VehicleProjectileEntity's own tudursvehiclemod$dispenseIfConfigured() doc.
		projectile.tudursvehiclemod$setDispenseItem(stats.dispenseItem(), stats.dispenseRange());
		boolean isRocketFirstMode = stats.weaponType() == WeaponType.ROCKET && stats.hasModes() && mode == 0;
		if (stats.hasBomblets() && !isRocketFirstMode) {
			projectile.tudursvehiclemod$setBomblets(stats.bombletCount(), stats.bombletDeployTicks(),
					stats.bombletSpreadRate(), stats.bombletModel(), stats.bombletTexture());
		}
		return projectile;
	}
}
