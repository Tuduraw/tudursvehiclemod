package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/** One weapon mount on a vehicle (a machine gun, a cannon, a torpedo tube,..). A vehicle can have zero or more of these; each is tied to a seat index. */
public record WeaponDefinition(
		int seatIndex,
		// One or more firing positions.
		List<WeaponOffset> offsets,
		// If present, this weapon mount tracks the shooter's current view (clamped to the given range) instead of staying fixed at each offset's own mountYaw/mountPitch.
		Optional<WeaponAimRange> aimRange,
		Identifier projectileItem,
		// Which weapon's own.txt file (assets/<namespace>/weapons/<weaponName>.txt, any namespace.
		String weaponName,
		// pilot_usable - per Readme_Aircraft.txt's own doc ("true, N" for AddWeapon's own pilotUsable+seat pair): when this weapon's own seatIndex (a gunner seat) has no occupant, the PILOT (seat 0) may fire it instead. Only meaningful for a non-pilot seatIndex - see AbstractVehicleEntity's own tryFireWeapon doc for how this is actually applied.
		boolean pilotUsable,
		// turret_rotation_speed - degrees/second limit on how fast this weapon mount's own tracking WeaponPart(s) rotate toward the shooter's view (instead of snapping instantly). Per-mount (not vehicle-wide or weapon-file-level -). Empty (default) means no limit.
		Optional<Float> turretRotationSpeed
) {

	// Delegating accessors.
	public float damage() {
		return WeaponStatsLoader.get(weaponName).damage();
	}

	public float velocity() {
		return WeaponStatsLoader.get(weaponName).velocity();
	}

	public int cooldownTicks() {
		return WeaponStatsLoader.get(weaponName).cooldownTicks();
	}

	public float gravity() {
		return WeaponStatsLoader.get(weaponName).gravity();
	}

	public Optional<String> sound() {
		return WeaponStatsLoader.get(weaponName).sound();
	}

	public float soundVolume() {
		return WeaponStatsLoader.get(weaponName).soundVolume();
	}

	public float soundPitch() {
		return WeaponStatsLoader.get(weaponName).soundPitch();
	}

	public float soundPitchRandom() {
		return WeaponStatsLoader.get(weaponName).soundPitchRandom();
	}

	public int soundDelayTicks() {
		return WeaponStatsLoader.get(weaponName).soundDelayTicks();
	}

	public Optional<Identifier> bulletModel() {
		return WeaponStatsLoader.get(weaponName).bulletModel();
	}

	public Optional<Identifier> bulletTexture() {
		return WeaponStatsLoader.get(weaponName).bulletTexture();
	}

	public float bulletScale() {
		return WeaponStatsLoader.get(weaponName).bulletScale();
	}

	public String displayName() {
		return WeaponStatsLoader.get(weaponName).displayName();
	}

	public int magazineSize() {
		return WeaponStatsLoader.get(weaponName).magazineSize();
	}

	public int reloadTicks() {
		return WeaponStatsLoader.get(weaponName).reloadTicks();
	}

	/** MaxAmmo - see WeaponStats's own doc. 0 = unlimited reserve. */
	public int maxAmmo() {
		return WeaponStatsLoader.get(weaponName).maxAmmo();
	}

	public WeaponType weaponType() {
		return WeaponStatsLoader.get(weaponName).weaponType();
	}

	/** See WeaponStats's own doc. */
	public boolean usableWhileDiving() {
		return WeaponStatsLoader.get(weaponName).usableWhileDiving();
	}

	/** See WeaponStats's own doc. */
	public float fuelPerAmmo() {
		return WeaponStatsLoader.get(weaponName).fuelPerAmmo();
	}

	public float explosionPower() {
		return WeaponStatsLoader.get(weaponName).explosionPower();
	}

	public boolean explosionDestroysBlocks() {
		return WeaponStatsLoader.get(weaponName).explosionDestroysBlocks();
	}

	public boolean flaming() {
		return WeaponStatsLoader.get(weaponName).flaming();
	}

	public float heatPerShot() {
		return WeaponStatsLoader.get(weaponName).heatPerShot();
	}

	public float maxHeat() {
		return WeaponStatsLoader.get(weaponName).maxHeat();
	}

	public boolean isHeatBased() {
		return WeaponStatsLoader.get(weaponName).isHeatBased();
	}

	public int suppliedNum() {
		return WeaponStatsLoader.get(weaponName).suppliedNum();
	}

	public int resupplyIronIngotCost() {
		return WeaponStatsLoader.get(weaponName).resupplyIronIngotCost();
	}

	public int resupplyGunpowderCost() {
		return WeaponStatsLoader.get(weaponName).resupplyGunpowderCost();
	}

	public int resupplyRedstoneCost() {
		return WeaponStatsLoader.get(weaponName).resupplyRedstoneCost();
	}

	public boolean isResuppliable() {
		return WeaponStatsLoader.get(weaponName).isResuppliable();
	}

	/** Torpedo only - see WeaponStats's own doc. */
	public float accelerationInWater() {
		return WeaponStatsLoader.get(weaponName).accelerationInWater();
	}

	/** Torpedo only - see WeaponStats's own doc. */
	public float velocityInWater() {
		return WeaponStatsLoader.get(weaponName).velocityInWater();
	}

	/** Torpedo only - see WeaponStats's own doc / VehicleProjectileEntity's own tudursvehiclemod$updateUnderwaterCruise() doc. */
	public float targetDepthOffset() {
		return WeaponStatsLoader.get(weaponName).targetDepthOffset();
	}

	/** WeaponType.AS_WEAPON only - see WeaponStats's own doc / VehicleProjectileEntity's own tudursvehiclemod$updateGuidance() doc. */
	public float diveDistance() {
		return WeaponStatsLoader.get(weaponName).diveDistance();
	}

	/** AddPartRotWeapon-linked parts only - see WeaponStats's own doc / WeaponPart's own spinsWhileFiring doc. */
	public float rotationSpeedPerSecond() {
		return WeaponStatsLoader.get(weaponName).rotationSpeedPerSecond();
	}

	/** ExplosionAltitude - see WeaponStats's own doc. */
	public float explosionAltitude() {
		return WeaponStatsLoader.get(weaponName).explosionAltitude();
	}

	/** True if ExplosionAltitude is actually configured - see WeaponStats's own hasExplosionAltitude() doc. */
	public boolean hasExplosionAltitude() {
		return WeaponStatsLoader.get(weaponName).hasExplosionAltitude();
	}

	/** DelayFuse - see WeaponStats's own -1-sentinel doc. */
	public int delayFuseTicks() {
		return WeaponStatsLoader.get(weaponName).delayFuseTicks();
	}

	/** True if DelayFuse was actually present in the file at all - see WeaponStats's own hasDelayFuse() doc. */
	public boolean hasDelayFuse() {
		return WeaponStatsLoader.get(weaponName).hasDelayFuse();
	}

	/** TimeFuse - see WeaponStats's own -1-sentinel doc. */
	public int timeFuseTicks() {
		return WeaponStatsLoader.get(weaponName).timeFuseTicks();
	}

	/** True if TimeFuse was actually present in the file at all - see WeaponStats's own hasTimeFuse() doc. */
	public boolean hasTimeFuse() {
		return WeaponStatsLoader.get(weaponName).hasTimeFuse();
	}

	/** Bound - see WeaponStats's own doc. */
	public float bounceStrength() {
		return WeaponStatsLoader.get(weaponName).bounceStrength();
	}

	/** True if Bound is actually configured - see WeaponStats's own hasBounce() doc. */
	public boolean hasBounce() {
		return WeaponStatsLoader.get(weaponName).hasBounce();
	}

	/** GravityInWater - see WeaponStats's own doc. */
	public float gravityInWater() {
		return WeaponStatsLoader.get(weaponName).gravityInWater();
	}

	/** Torpedo only - see WeaponStats's own doc. */
	public boolean guidedTorpedo() {
		return WeaponStatsLoader.get(weaponName).guidedTorpedo();
	}

	/** Piercing - see WeaponStats's own doc. */
	public int piercingCount() {
		return WeaponStatsLoader.get(weaponName).piercingCount();
	}

	/** True if Piercing is actually configured - see WeaponStats's own hasPiercing() doc. */
	public boolean hasPiercing() {
		return WeaponStatsLoader.get(weaponName).hasPiercing();
	}

	/** Accuracy (degrees) - see WeaponStats's own doc. */
	public float accuracyDegrees() {
		return WeaponStatsLoader.get(weaponName).accuracyDegrees();
	}

	/** FAE - see WeaponStats's own doc. */
	public boolean fuelAirExplosive() {
		return WeaponStatsLoader.get(weaponName).fuelAirExplosive();
	}

	/** BulletColor (packed ARGB) - see WeaponStats's own doc. */
	public int bulletColor() {
		return WeaponStatsLoader.get(weaponName).bulletColor();
	}

	/** BulletColorInWater (packed ARGB) - see WeaponStats's own doc. */
	public int bulletColorInWater() {
		return WeaponStatsLoader.get(weaponName).bulletColorInWater();
	}

	/** Sight - see WeaponStats's own doc / SightType's own doc. */
	public com.example.tudursvehiclemod.asset.SightType sight() {
		return WeaponStatsLoader.get(weaponName).sight();
	}

	/** LockTime (ticks) - AAMissile/ATMissile only, see WeaponStats's own doc. */
	public int lockTimeTicks() {
		return WeaponStatsLoader.get(weaponName).lockTimeTicks();
	}

	/** LockRange (blocks) - AAMissile/ATMissile/MISSILE only, see WeaponStats's own doc. 0 means unlimited. */
	public double lockRange() {
		return WeaponStatsLoader.get(weaponName).lockRange();
	}

	/** LockTimePerBlock (ticks/block) - AAMissile/ATMissile/MISSILE only, see WeaponStats's own doc. */
	public double lockTimePerBlock() {
		return WeaponStatsLoader.get(weaponName).lockTimePerBlock();
	}

	/** TurnRate (degrees/tick) - every guided weapon type only, see WeaponStats's own doc. */
	public float turnRateDegreesPerTick() {
		return WeaponStatsLoader.get(weaponName).turnRateDegreesPerTick();
	}

	/** CasTargetMode - CAS/CARRIER only, see WeaponStats's own doc / CasTargetMode's own enum doc. */
	public com.example.tudursvehiclemod.asset.CasTargetMode casTargetMode() {
		return WeaponStatsLoader.get(weaponName).casTargetMode();
	}

	/** CasAttackStartAltitude - CAS/CARRIER only, see WeaponStats's own doc. */
	public float casAttackStartAltitude() {
		return WeaponStatsLoader.get(weaponName).casAttackStartAltitude();
	}

	/** CasAttackStopAltitude - CAS/CARRIER only, see WeaponStats's own doc. */
	public float casAttackStopAltitude() {
		return WeaponStatsLoader.get(weaponName).casAttackStopAltitude();
	}

	/** RidableOnly - see WeaponStats's own doc. */
	public boolean ridableOnly() {
		return WeaponStatsLoader.get(weaponName).ridableOnly();
	}

	/** CameraRotationSpeedPitch - see WeaponStats's own doc. */
	public float cameraRotationSpeedPitch() {
		return WeaponStatsLoader.get(weaponName).cameraRotationSpeedPitch();
	}

	/** FixCameraPitch - see WeaponStats's own doc. */
	public boolean fixCameraPitch() {
		return WeaponStatsLoader.get(weaponName).fixCameraPitch();
	}

	/** DisplayMortarDistance - see WeaponStats's own doc. */
	public boolean displayMortarDistance() {
		return WeaponStatsLoader.get(weaponName).displayMortarDistance();
	}

	/** Cas - see CasStrikeConfig's own doc. */
	public java.util.Optional<CasStrikeConfig> casStrike() {
		return WeaponStatsLoader.get(weaponName).casStrike();
	}

	/** Carrier - see CarrierAircraftConfig's own doc. */
	public java.util.Optional<CarrierAircraftConfig> carrierAircraft() {
		return WeaponStatsLoader.get(weaponName).carrierAircraft();
	}

	/** ProximityFuseDist - AAMissile/ATMissile only, see WeaponStats's own doc. */
	public float proximityFuseDist() {
		return WeaponStatsLoader.get(weaponName).proximityFuseDist();
	}

	/** RigidityTime (ticks) - AAMissile/ATMissile only, see WeaponStats's own doc. */
	public int rigidityTimeTicks() {
		return WeaponStatsLoader.get(weaponName).rigidityTimeTicks();
	}

	/** Group - see WeaponStats's own doc. */
	public java.util.Optional<String> group() {
		return WeaponStatsLoader.get(weaponName).group();
	}

	/** ModeNum - see WeaponStats's own doc. */
	public int modeNum() {
		return WeaponStatsLoader.get(weaponName).modeNum();
	}

	/** True if this weapon actually has a second selectable mode - see WeaponStats's own hasModes() doc. */
	public boolean hasModes() {
		return WeaponStatsLoader.get(weaponName).hasModes();
	}

	/** TrajectoryParticle - see WeaponStats's own doc. */
	public java.util.Optional<String> trajectoryParticle() {
		return WeaponStatsLoader.get(weaponName).trajectoryParticle();
	}

	/** TrajectoryParticleStartTick - see WeaponStats's own doc. */
	public int trajectoryParticleStartTick() {
		return WeaponStatsLoader.get(weaponName).trajectoryParticleStartTick();
	}

	/** DisableSmoke - see WeaponStats's own doc. */
	public boolean disableSmoke() {
		return WeaponStatsLoader.get(weaponName).disableSmoke();
	}

	/** AddMuzzleFlash - see WeaponStats's own doc / MuzzleFlashConfig's own doc. */
	public java.util.Optional<com.example.tudursvehiclemod.asset.MuzzleFlashConfig> muzzleFlash() {
		return WeaponStatsLoader.get(weaponName).muzzleFlash();
	}

	/** AddMuzzleFlashSmoke - see WeaponStats's own doc / MuzzleFlashSmokeConfig's own doc. */
	public java.util.Optional<com.example.tudursvehiclemod.asset.MuzzleFlashSmokeConfig> muzzleFlashSmoke() {
		return WeaponStatsLoader.get(weaponName).muzzleFlashSmoke();
	}

	/** SetCartridge - see WeaponStats's own doc / CartridgeConfig's own doc. */
	public java.util.Optional<com.example.tudursvehiclemod.asset.CartridgeConfig> cartridge() {
		return WeaponStatsLoader.get(weaponName).cartridge();
	}

	/** Recoil - see WeaponStats's own doc. */
	public float recoil() {
		return WeaponStatsLoader.get(weaponName).recoil();
	}

	/** True if Recoil is actually configured - see WeaponStats's own hasRecoil() doc. */
	public boolean hasRecoil() {
		return WeaponStatsLoader.get(weaponName).hasRecoil();
	}

	/** RecoilBufCount's own recoilDurationTicks - see WeaponStats's own doc. */
	public int recoilDurationTicks() {
		return WeaponStatsLoader.get(weaponName).recoilDurationTicks();
	}

	/** RecoilBufCount's own recoilRecessionRateMultiplier - see WeaponStats's own doc. */
	public int recoilRecessionRateMultiplier() {
		return WeaponStatsLoader.get(weaponName).recoilRecessionRateMultiplier();
	}

	/** Destruct - Type=Bomb + UAV only, see WeaponStats's own doc. */
	public boolean destruct() {
		return WeaponStatsLoader.get(weaponName).destruct();
	}

	/** Type=Dispenser only - see WeaponStats's own doc. */
	public java.util.Optional<String> dispenseItem() {
		return WeaponStatsLoader.get(weaponName).dispenseItem();
	}

	/** Type=Dispenser only - see WeaponStats's own doc. */
	public float dispenseRange() {
		return WeaponStatsLoader.get(weaponName).dispenseRange();
	}

	/** Type=Smoke only - see WeaponStats's own doc. Packed 0xAARRGGBB. */
	public int smokeColor() {
		return WeaponStatsLoader.get(weaponName).smokeColor();
	}

	/** Type=Smoke only - see WeaponStats's own doc. */
	public float smokeSize() {
		return WeaponStatsLoader.get(weaponName).smokeSize();
	}

	/** Type=Smoke only - see WeaponStats's own doc. */
	public int smokeMaxAge() {
		return WeaponStatsLoader.get(weaponName).smokeMaxAge();
	}

	/** Type=TargetingPod only - see WeaponStats's own doc. */
	public java.util.Set<String> targetingPodTargets() {
		return WeaponStatsLoader.get(weaponName).targetingPodTargets();
	}

	/** Type=TargetingPod only - see WeaponStats's own doc. */
	public float targetingPodLength() {
		return WeaponStatsLoader.get(weaponName).targetingPodLength();
	}

	/** Type=TargetingPod only - see WeaponStats's own doc. */
	public float targetingPodRadius() {
		return WeaponStatsLoader.get(weaponName).targetingPodRadius();
	}

	/** Type=TargetingPod only - see WeaponStats's own doc (SECONDS, not ticks). */
	public float targetingPodMarkTimeSeconds() {
		return WeaponStatsLoader.get(weaponName).targetingPodMarkTimeSeconds();
	}

	/** See WeaponStats's own doc - the explosion power actually used when detonating underwater instead of explosionPower(). */
	public float explosionPowerInWater() {
		return WeaponStatsLoader.get(weaponName).explosionPowerInWater();
	}

	/** See WeaponStats's own doc - ExplosionBlock's own independent block-destruction power (-1 if not set in the file at all). */
	public float explosionBlockPower() {
		return WeaponStatsLoader.get(weaponName).explosionBlockPower();
	}

	/** See WeaponStats's own doc - how many child submunitions this weapon scatters after use (0 = disabled). */
	public int bombletCount() {
		return WeaponStatsLoader.get(weaponName).bombletCount();
	}

	/** See WeaponStats's own doc - ticks after launch before the submunitions actually deploy. */
	public int bombletDeployTicks() {
		return WeaponStatsLoader.get(weaponName).bombletDeployTicks();
	}

	/** See WeaponStats's own doc - the submunitions' own spread rate. */
	public float bombletSpreadRate() {
		return WeaponStatsLoader.get(weaponName).bombletSpreadRate();
	}

	/** See WeaponStats's own doc - the submunitions' own model, if different from the parent projectile's own. */
	public Optional<Identifier> bombletModel() {
		return WeaponStatsLoader.get(weaponName).bombletModel();
	}

	/** See WeaponStats's own doc - the submunitions' own texture, if different from the parent projectile's own. */
	public Optional<Identifier> bombletTexture() {
		return WeaponStatsLoader.get(weaponName).bombletTexture();
	}

	/** See WeaponStats's own doc - true if this weapon's own Bomblet directive is actually configured. */
	public boolean hasBomblets() {
		return WeaponStatsLoader.get(weaponName).hasBomblets();
	}

	public static final Codec<WeaponDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("seat_index").forGetter(WeaponDefinition::seatIndex),
			WeaponOffset.CODEC.listOf().optionalFieldOf("offsets", List.of(new WeaponOffset(0.0, 0.0, 0.0, 0.0, 0.0, Optional.empty())))
					.forGetter(WeaponDefinition::offsets),
			WeaponAimRange.CODEC.optionalFieldOf("aim_range").forGetter(WeaponDefinition::aimRange),
			Identifier.CODEC.fieldOf("projectile_item").forGetter(WeaponDefinition::projectileItem),
			Codec.STRING.fieldOf("weapon_name").forGetter(WeaponDefinition::weaponName),
			Codec.BOOL.optionalFieldOf("pilot_usable", false).forGetter(WeaponDefinition::pilotUsable),
			Codec.FLOAT.optionalFieldOf("turret_rotation_speed").forGetter(WeaponDefinition::turretRotationSpeed)
	).apply(instance, WeaponDefinition::new));
}
