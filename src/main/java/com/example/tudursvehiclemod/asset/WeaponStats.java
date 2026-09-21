package com.example.tudursvehiclemod.asset;

import net.minecraft.util.Identifier;

import java.util.Optional;

/** Everything WeaponDefinition used to carry baked directly into a vehicle's own JSON (damage, velocity, cooldown, gravity, sound, bullet model). */
public record WeaponStats(
		float damage,
		float velocity,
		int cooldownTicks,
		float gravity,
		Optional<String> sound,
		float soundVolume,
		float soundPitch,
		float soundPitchRandom,
		int soundDelayTicks,
		Optional<Identifier> bulletModel,
		Optional<Identifier> bulletTexture,
		float bulletScale,
		// DisplayName - shown on HUD scripts referencing WPN_NAME.
		String displayName,
		// Round = <n> - magazine capacity; 0 means unlimited/no reload cycle at all (MC Heli's own documented meaning for 0 or omitted).
		int magazineSize,
		// ReloadTime = <ticks>.
		int reloadTicks,
		// MaxAmmo = <n> - the TOTAL ammo
		// reserve this weapon draws its magazine (Round) refills from -
		// a genuinely separate, independent pool from magazineSize
		// itself. 0 (or omitted) means unlimited reserve (this weapon's
		// automatic reload cycle just keeps refilling forever, the
		// original behavior before this field existed at all) - see
		// AbstractVehicleEntity's own updateWeaponReloads() doc for
		// where a limited reserve actually running out stops further
		// automatic reloading (true "out of ammo", rather than just an
		// empty magazine that refills again a moment later).
		int maxAmmo,
		// Type = MachineGun1/MachineGun2/Rocket/Bomb/..
		WeaponType weaponType,
		// Explosion = <power> (0 = no explosion, 1 = ghast fireball power).
		float explosionPower,
		// ExplosionBlock = <power> (0 = doesn't break blocks).
		boolean explosionDestroysBlocks,
		// Flaming - sets the struck entity on fire on impact; per Readme_Weapon.txt's own note, only meaningful when explosionPower > 0.
		boolean flaming,
		// HeatCount - how much heat a single shot adds for a "barrel heat" style weapon (see AbstractVehicleEntity's own weaponHeat tracking).
		float heatPerShot,
		// MaxHeatCount - once accumulated heat reaches this, firing is blocked until it cools back down..
		float maxHeat,
		// SuppliedNum - how much ammo one resupply action (see item.VehicleMenuScreenHandler's own resupply button) adds, up to magazineSize - 0..
		int suppliedNum,
		// Item = <count>, <iron_ingot|gunpowder|redstone> (up to 3, one per resource.
		int resupplyIronIngotCost,
		int resupplyGunpowderCost,
		int resupplyRedstoneCost,
		// AccelerationInWater - a torpedo's own target underwater cruise speed (max 4.0 per Readme_Weapon.txt), reached gradually after it first settles in the water (see VehicleProjectileEntity's own underwater-cruise doc).
		float accelerationInWater,
		// VelocityInWater - per Readme_Weapon.txt's own doc, "multiplied into velocity every tick" underwater; reused here as the per-tick response rate for BOTH losing speed on water entry and ramping up toward accelerationInWater afterward.
		float velocityInWater,
		// ExplosionInWater - the explosion power actually used instead of the plain Explosion value when detonating while touching water (see VehicleProjectileEntity's own explodeIfConfigured doc).
		float explosionPowerInWater,
		// ExplosionBlock - per Readme_Weapon.txt's own doc, this is its OWN independent block-destruction power (separate from explosionPower/explosionPowerInWater's own entity-damage power), used directly for the actual block-destruction radius when set. -1 is a sentinel meaning "not present in the file at all" - see WeaponStatsLoader's own parsing doc for how that's resolved.
		float explosionBlockPower,
		// Bomblet - per Readme_Weapon.txt's own doc, how many child submunitions this weapon scatters after use (0 = disabled, the ordinary case) - see VehicleProjectileEntity's own tudursvehiclemod$updateBombletDeployment() doc.
		int bombletCount,
		// BombletSTime - ticks after launch before the submunitions actually deploy.
		int bombletDeployTicks,
		// BombletDiff - the submunitions' own spread rate (a multiplier on how far their own velocity diverges from the parent's own velocity direction).
		float bombletSpreadRate,
		// ModelBomblet - same "models/obj/bullet_<name>.obj"/"textures/vehicle/bullet_<name>.png" convention as ModelBullet (see bulletModel()'s own doc), just for the SUBMUNITIONS specifically rather than the parent projectile itself. Empty falls back to using the SAME model/texture as the parent (or the plain item-based projectile look, if the parent itself had none either).
		Optional<Identifier> bombletModel,
		Optional<Identifier> bombletTexture,
		// TargetDepth - a torpedo's own target cruise depth, expressed as how far BELOW the water surface it aims for (2.0 default - "水面Y-2"). Not an MC Heli-native directive; this project's own new addition, read from the same weapon.txt file as everything else here. See VehicleProjectileEntity's own tudursvehiclemod$updateUnderwaterCruise() doc for how this is actually used.
		float targetDepthOffset,
		// DiveDistance - Type=ASWeapon only (see WeaponType.AS_WEAPON's own doc). Not an MC Heli-native directive; this project's own new addition. How close (blocks) this weapon's own projectile must get to its own target point before it dives underwater and transitions into the same cruise behavior TORPEDO uses (driven by accelerationInWater/velocityInWater/targetDepthOffset above, reused as-is). 10.0 default whenever this key is absent but Type=ASWeapon is actually set.
		float diveDistance,
		// RotationSpeed - how fast an AddPartRotWeapon-linked part spins (rotations per SECOND, not per tick) while this weapon is actively being fired - MC Heli's own Readme doesn't actually document a concrete number for AddPartRotWeapon's own spin rate at all, so 15.0 is used when this key is absent (the original 30.0 default was mathematically correct but visually excessive - see WeaponStatsLoader's own doc), and this project's own new addition lets it be tuned per weapon file. See WeaponPart's own spinsWhileFiring doc for where this actually gets used.
		float rotationSpeedPerSecond,
		// DispenseItem - Type=Dispenser only. The Minecraft item ID "used" AT the impact point (as if a player had used it on the block there - e.g. flint_and_steel ignites it, a hoe tills farmland,..) - empty if this weapon isn't actually a Dispenser, or the key is missing. water_bucket is special-cased (see VehicleProjectileEntity's own doc) to extinguish nearby fire/lava instead of placing an actual water source.
		Optional<String> dispenseItem,
		// DispenseRange - Type=Dispenser only - how far (blocks) from the impact point DispenseItem's own effect (and the falling-particle visual) actually spreads.
		float dispenseRange,
		// SmokeColor = Alpha, Red, Green, Blue (0-255 each) - Type=Smoke only. Packed into a single ARGB int (0xAARRGGBB) for direct use with vanilla's own DustParticleEffect-style color handling.
		int smokeColor,
		// SmokeSize - Type=Smoke only.
		float smokeSize,
		// SmokeMaxAge - Type=Smoke only - how long (ticks, matching every other tick-based duration in this same file) the smoke particles actually persist for.
		int smokeMaxAge,
		// Target = monsters/others/.. (see WeaponStatsLoader's own parsing doc for the exact recognized set) - Type=TargetingPod only. A "block" entry on its own means block-marking mode instead of entity-spotting mode entirely (mutually exclusive with every other value, per MC Heli's own doc).
		java.util.Set<String> targetingPodTargets,
		// Length - Type=TargetingPod only - how far (blocks) the spotting cone actually reaches.
		float targetingPodLength,
		// Radius - Type=TargetingPod only - the spotting cone's own half-angle (degrees) from dead-center.
		float targetingPodRadius,
		// MarkTime - Type=TargetingPod only - per MC Heli's own doc, this is in SECONDS (not ticks, unlike almost every other duration in this file) - converted to ticks at the one call site that actually uses it (see AbstractVehicleEntity's own doc), not here, so this field stays a faithful, unconverted read of the raw config value.
		float targetingPodMarkTimeSeconds,
		// ExplosionAltitude - per Readme_Weapon.txt's own doc: this projectile detonates the instant its own height above whatever ground is directly below it drops to (or below) this value, regardless of whether it's actually hit anything yet - meant for a missile that should airburst above its target rather than needing a direct hit. 0 (or the key absent) disables this entirely (the ordinary case - only explodes on an actual hit/fuse). See VehicleProjectileEntity's own tudursvehiclemod$updateExplosionAltitude() doc.
		float explosionAltitude,
		// DelayFuse - per Readme_Weapon.txt's own doc: once this projectile has actually HIT something (a block or entity - see VehicleProjectileEntity's own onBlockHit()/onEntityHit() doc), it now waits this many ticks before actually disappearing (exploding first, if Explosion/ExplosionInWater is set) rather than doing so the very same tick it hit - together with Bound (below), this is what lets a projectile bounce/sit around for a moment after impact instead of vanishing instantly. -1 is a sentinel meaning "not present in the file at all" (falls back to the original instant-discard-on-hit behavior) - see WeaponStatsLoader's own parsing doc for how 0 (a real, explicit "no delay") is distinguished from that.
		int delayFuseTicks,
		// TimeFuse - per Readme_Weapon.txt's own doc: this projectile disappears (exploding first, if Explosion/ExplosionInWater is set) this many ticks after being FIRED, win or lose, regardless of whether it's hit anything at all yet - a simple total-lifetime countdown, independent of DelayFuse's own post-impact one. -1 is the same "not present at all" sentinel as delayFuseTicks.
		int timeFuseTicks,
		// Bound - per Readme_Weapon.txt's own doc: this projectile's own impact bounce strength (0 = no bounce, the ordinary "stops/embeds on the first hit" behavior) - see VehicleProjectileEntity's own tudursvehiclemod$tryBounce() doc. Per that same doc's own warning, using this without ALSO setting DelayFuse is close to pointless (it would just explode/discard the very same tick it bounces anyway).
		float bounceStrength,
		// GravityInWater - per Readme_Weapon.txt's own doc: this projectile's own fall speed while submerged, completely separate from its own plain Gravity (used everywhere else) - defaults to matching gravity itself (see WeaponStatsLoader's own parsing doc) when the key is absent, rather than 0, so an ordinary (non-torpedo) weapon that never actually specifies this keeps falling normally underwater instead of suddenly floating.
		float gravityInWater,
		// GuidedTorpedo - per Readme_Weapon.txt's own doc: Type=Torpedo only. true (the default - matches this project's own original, only-ever-guided torpedo behavior before this field existed at all) - homes towards its own target block once submerged. false - travels in a straight line once it enters the water instead, with no homing behavior at all (still gets AccelerationInWater/VelocityInWater's own underwater speed handling, just no course correction on top of it).
		boolean guidedTorpedo,
		// Piercing - per Readme_Weapon.txt's own doc: how many additional blocks in a row this projectile can punch straight through (destroying each - see VehicleProjectileEntity's own tudursvehiclemod$tryPierceBlock() doc) before finally stopping for real, rather than stopping dead at the very first block it touches. 0 (the default) is the original, unpierced behavior.
		int piercingCount,
		// Accuracy - per Readme_Weapon.txt's own doc: a small random angular error (degrees) applied once, at the moment this projectile is actually fired - larger values scatter shots further off the intended aim point. 0 (the default) fires with perfect accuracy, the original behavior before this field existed. Only meaningful for unguided weapon types (MachineGun1/MachineGun2/Rocket/MkRocket) - a truly GUIDED weapon (a homing missile) corrects its own course after launch regardless, so this wouldn't be noticeable there anyway.
		float accuracyDegrees,
		// FAE - per Readme_Weapon.txt's own doc: true makes this a fuel-air explosive - its own explosion (Explosion/ExplosionInWater) never destroys blocks at all, REGARDLESS of whatever ExplosionBlock is otherwise set to (a real fuel-air explosion is a wide-area, low-cratering blast, unlike a normal high-explosive one).
		boolean fuelAirExplosive,
		// BulletColor = Alpha, Red, Green, Blue (0-255 each) - per Readme_Weapon.txt's own doc: this projectile's own particle/trail tint while travelling through air. Packed into a single ARGB int, same convention as smokeColor. White (opaque) is the default - matches this project's own original, uncolored bullet trail before this field existed.
		int bulletColor,
		// BulletColorInWater = Alpha, Red, Green, Blue (0-255 each) - per Readme_Weapon.txt's own doc: this projectile's own particle/trail tint specifically while submerged, replacing bulletColor for as long as it's underwater.
		int bulletColorInWater,
		// Sight = MoveSight/None/MissileSight - per Readme_Weapon.txt's own doc: which on-screen reticle this weapon shows while selected. MoveSight (the default) moves with this vehicle's own aim direction; MissileSight is a lock-on-style reticle (AAMissile/ATMissile only - mandatory for those per Readme_Weapon.txt's own doc); None shows nothing at all.
		SightType sight,
		// LockTime - per Readme_Weapon.txt's own doc: AAMissile/ATMissile only. How many ticks of CONTINUOUS lock-on tracking (see AbstractVehicleEntity's own tudursvehiclemod$updateMissileLockOnIndicators() doc) are needed before firing actually launches a guided shot - firing before that instead launches unguided (unless RidableOnly and the shooter isn't riding at all - see that field's own doc, in which case it doesn't fire at all). 0 (the default) locks instantly, this project's own original behavior before this field existed. this is now specifically the BASE required time (at zero distance) - see lockTimePerBlock's own doc for how the actual required time scales with the target's own current distance.
		int lockTimeTicks,
		// LockRange - AAMissile/ATMissile/MISSILE only. Maximum distance (blocks) a target can be acquired/locked from at all - see AbstractVehicleEntity's own tudursvehiclemod$findLockOnTarget() doc. 0 (the default, absent from the file) means unlimited - every currently-loaded/visible entity is a lock candidate regardless of distance, per that same direct request.
		double lockRange,
		// LockTimePerBlock - AAMissile/ATMissile/MISSILE only. Additional lock-on ticks REQUIRED per block of the target's own current distance, on top of lockTimeTicks's own base value (actualRequiredTicks = lockTimeTicks + lockTimePerBlock * currentDistance) - a farther target takes proportionally longer to lock, per that same direct request. Defaults to LOCK_TIME_PER_BLOCK_DEFAULT (see AbstractVehicleEntity's own doc for why that specific value) whenever this key is absent from the file, rather than 0 - per that same direct request, distance-scaled lock time is the intended DEFAULT behavior, not an opt-in one; an explicit "LockTimePerBlock = 0" in the weapon file itself is how a mapper gets the old flat-lockTimeTicks-regardless-of-distance behavior back.
		double lockTimePerBlock,
		// TurnRate - AA_MISSILE/AT_MISSILE/MISSILE/AS_MISSILE/MK_ROCKET/AS_WEAPON only (every guided weapon type). Maximum degrees this shot's own velocity can rotate per tick while homing (see VehicleProjectileEntity's own tudursvehiclemod$steerTowards() doc) - a real measure of "how maneuverable/agile is this particular missile", now tunable per weapon rather than one fixed value shared identically by every guided weapon in the whole mod. 3.0 (degrees/tick) is the default whenever this key is absent, matching this project's own original hardcoded value exactly, so an existing weapon file that never configures this behaves identically to before this field existed.
		float turnRateDegreesPerTick,
		// CasTargetMode - CAS/CARRIER only. Selects which of AbstractVehicleEntity's own tudursvehiclemod$computeCasCarrierTargetPoint() algorithms determines the route's own relative-coordinate center - see that enum's own doc for what each of its three values actually does. Defaults to CasTargetMode.BALLISTIC whenever this key is absent, matching this project's own current behavior exactly, so an existing weapon file that never configures this behaves identically to before this field existed.
		com.example.tudursvehiclemod.asset.CasTargetMode casTargetMode,
		// CasAttackStartAltitude - CAS/CARRIER only. How high (blocks) above a surface/submerged locked target a Carrier wingman must be before resuming/beginning an actual attack run on it - see AircraftEntity's own carrierLockClimbingToSafeAltitude doc for the full two-threshold hysteresis this and CasAttackStopAltitude together drive. 200.0 is the default whenever this key is absent, matching this project's own current behavior.
		float casAttackStartAltitude,
		// CasAttackStopAltitude - CAS/CARRIER only. How high (blocks) above a surface/submerged locked target a Carrier wingman can drop to before it must stop attacking and climb back to CasAttackStartAltitude - see that field's own doc for the full reasoning (a genuine hysteresis band, not a single threshold). 40.0 is the default whenever this key is absent, matching this project's own current behavior.
		float casAttackStopAltitude,
		// RidableOnly - per Readme_Weapon.txt's own doc: AAMissile/ATMissile only. MC Heli's own documented meaning is "only lockable while riding a vehicle" (as opposed to holding this weapon as a portable, handheld item) - this project's own weapon system is exclusively vehicle-mounted in the first place (there's no portable/handheld weapon item at all), so every weapon here already satisfies this by construction regardless of what this field is actually set to. Parsed and exposed for completeness/inspectability (and in case a portable weapon system gets added later), but doesn't currently change any actual behavior.
		boolean ridableOnly,
		// ProximityFuseDist - per Readme_Weapon.txt's own doc: AAMissile/ATMissile only. Once a GUIDED shot (see lockTimeTicks's own doc) comes within this many blocks of its own locked target, it detonates right there even without an actual direct hit - a real proximity-fused missile doesn't need to physically touch its target at all. 0 (the default) disables this entirely - a guided missile only ever detonates on an actual direct hit, this project's own original behavior before this field existed.
		float proximityFuseDist,
		// RigidityTime - per Readme_Weapon.txt's own doc: AAMissile/ATMissile only. How many ticks after launch a GUIDED shot flies dead straight before its own course-correction/homing actually begins - real anti-air missiles need a moment of unguided flight to physically clear their own launch platform before starting to steer. 7 is Readme_Weapon.txt's own documented default for when this key is entirely absent from the file.
		int rigidityTimeTicks,
		// Group - per Readme_Weapon.txt's own doc: weapons sharing the
		// same (case-sensitive, exact-match) group name share their own
		// fire-rate cooldown (Delay) AND reload timer (ReloadTime) -
		// using ANY ONE of them applies that SAME cooldown/reload state
		// to every OTHER weapon in the group too, specifically to
		// prevent firing weapon A, immediately switching to weapon B (a
		// different ammo type/round for the same actual gun), and firing
		// again instantly - see AbstractVehicleEntity's own
		// tudursvehiclemod$applyGroupCooldownAndReload() doc for where
		// this actually gets applied. Each weapon's own AMMO count stays
		// completely independent either way - Group only ever links
		// cooldown/reload timing, nothing else. Empty (the default, key
		// absent) means this weapon isn't in any group at all.
		Optional<String> group,
		// ModeNum - per Readme_Weapon.txt's own doc: how many selectable
		// modes this weapon has (see AbstractVehicleEntity's own
		// tudursvehiclemod$tryToggleWeaponMode() doc for the actual
		// per-Type mode behaviors) - only 1 or 2 are actually valid
		// values (Readme_Weapon.txt's own documented range), 1 (the
		// default, key absent) meaning this weapon has no alternate mode
		// at all.
		int modeNum,
		// TrajectoryParticle - per Readme_Weapon.txt's own doc: which
		// particle effect trails behind this projectile during flight
		// (none/explode/flame/hugeexplosion/largeexplode/largesmoke/
		// smoke - MC Heli's own documented list) - raw lowercase string,
		// resolved to an actual ParticleEffect at render/spawn time (see
		// VehicleProjectileEntity's own tudursvehiclemod$updateTrajectoryParticle()
		// doc). Empty (the default, key absent) means no trail at all -
		// this project's own original behavior before this field
		// existed.
		Optional<String> trajectoryParticle,
		// TrajectoryParticleStartTick - per Readme_Weapon.txt's own doc:
		// how many ticks after launch trajectoryParticle actually starts
		// appearing (0, the default, starts immediately).
		int trajectoryParticleStartTick,
		// DisableSmoke - per Readme_Weapon.txt's own doc: MC Heli
		// documents this as disabling a weapon's own smoke effect
		// specifically DURING FLIGHT (as opposed to at the moment of
		// firing) - interpreted here as suppressing trajectoryParticle
		// specifically when it's one of the "smoke"-category values
		// (smoke/largesmoke/explode/largeexplode/hugeexplosion all
		// render as smoke-like puffs per that field's own doc), while
		// still allowing flame to render regardless - see
		// tudursvehiclemod$updateTrajectoryParticle()'s own doc.
		boolean disableSmoke,
		// AddMuzzleFlash - see MuzzleFlashConfig's own doc. Empty (the
		// default, key absent) means no muzzle flash at all.
		Optional<MuzzleFlashConfig> muzzleFlash,
		// AddMuzzleFlashSmoke - see MuzzleFlashSmokeConfig's own doc.
		// Empty (the default, key absent) means no muzzle smoke puff at
		// all.
		Optional<MuzzleFlashSmokeConfig> muzzleFlashSmoke,
		// SetCartridge - see CartridgeConfig's own doc. Empty (the
		// default, key absent) means no ejected cartridge at all.
		Optional<CartridgeConfig> cartridge,
		// Recoil - per Readme_Weapon.txt's own doc: the strength of this
		// vehicle's own camera shake each time this weapon actually
		// fires - see AbstractVehicleEntity's own tudursvehiclemod$updateRecoilShake()
		// doc for how this is actually applied. 0 (the default, key
		// absent) means no shake at all.
		float recoil,
		// RecoilBufCount = recoilDurationTicks, recessionRateMultiplier -
		// per Readme_Weapon.txt's own doc: recoilDurationTicks stretches
		// the WHOLE shake's own total duration (recede + recover
		// combined); recessionRateMultiplier speeds up ONLY the initial
		// recede (kick) phase specifically, without affecting the total
		// duration - see tudursvehiclemod$updateRecoilShake()'s own doc
		// for the exact two-phase curve this drives. 40/5 is Readme_Weapon.txt's
		// own documented example, used as this project's own default
		// whenever Recoil is configured but this key is entirely absent.
		int recoilDurationTicks,
		int recoilRecessionRateMultiplier,
		// Destruct - per Readme_Weapon.txt's own doc: Type=Bomb only, and
		// only actually has any effect if the firing vehicle is a UAV
		// helicopter (see AbstractVehicleEntity's own tudursvehiclemod$isUav()
		// doc) - using this weapon self-destructs that UAV outright. false
		// (the default, key absent) is this project's own original
		// behavior before this field existed - an ordinary bomb drop with
		// no self-destruct at all.
		boolean destruct,
		// CameraRotationSpeedPitch - per Readme_Weapon.txt's own doc: a multiplier on the player's own view pitch rotation speed while this weapon is currently selected (regardless of vehicle type) - smaller values allow finer aim adjustment. 1.0 (the default, key absent) is unchanged/normal speed.
		float cameraRotationSpeedPitch,
		// FixCameraPitch - per Readme_Weapon.txt's own doc: while this weapon is currently selected (regardless of vehicle type), locks the player's own view pitch to 0 (dead level) entirely. false (the default, key absent) leaves pitch unaffected.
		boolean fixCameraPitch,
		// DisplayMortarDistance - per Readme_Weapon.txt's own doc: displays a computed impact/target distance for this weapon while selected - see AbstractVehicleEntity's own tudursvehiclemod$computeMortarDistance() doc for the per-WeaponType calculation. false (the default, key absent) shows nothing.
		boolean displayMortarDistance,
		// Cas - Type=CAS only. See CasStrikeConfig's own doc. Empty (the default, key absent) means this weapon isn't actually a working CAS strike at all (firing it, if the file otherwise sets Type=CAS, does nothing - same "unconfigured" fallback every other Type-specific field here already has).
		java.util.Optional<CasStrikeConfig> casStrike,
		// Carrier - Type=CARRIER only. See CarrierAircraftConfig's own doc. Empty (the default, key absent) means this weapon isn't actually a working Carrier launch at all - same "unconfigured falls back to inert" convention as casStrike.
		java.util.Optional<CarrierAircraftConfig> carrierAircraft,
		// UsableWhileDiving - lets this weapon (of any Type) stay usable while a submarine hull is submerged/diving, on top of the Torpedo type's own existing hardcoded default (see SubmarineEntity's own tudursvehiclemod$canFireWeapons(Optional) doc) - a non-Torpedo weapon a submarine wants usable underwater no longer needs to actually BE a Torpedo. false (the default, key absent) means this weapon follows the original behavior (usable underwater only if it's a Torpedo). Has no effect at all on any vehicle type other than SubmarineEntity, which is the only consumer of this flag.
		boolean usableWhileDiving,
		// FuelPerAmmo - Type=DropTank only (see WeaponType.DROP_TANK's own doc): how much this vehicle's own max fuel is temporarily raised per 1 remaining round of this weapon's own ammo. 0.0 (the default, key absent) means no fuel bonus at all - a DropTank weapon with this unconfigured has no effect on max fuel whatsoever.
		float fuelPerAmmo,
		// Type=<namespace>:<path> (anything containing ':') - an addon-registered custom weapon type
		// (see WeaponType.CUSTOM's own doc and CustomWeaponTypes' own doc). Empty for every ordinary,
		// built-in Type. weaponType is CUSTOM exactly when this is present.
		java.util.Optional<net.minecraft.util.Identifier> customTypeId
	) {
	/** Used when a WeaponDefinition names a weapon that isn't currently loaded (missing file, failed to parse, or simply not reloaded yet). */
	public static final WeaponStats FALLBACK = new WeaponStats(
			4.0f, 3.0f, 10, 0.03f, Optional.empty(), 1.0f, 1.0f, 0.0f, 0,
			Optional.empty(), Optional.empty(), 1.0f, "Weapon", 0, 20, 0,
			WeaponType.OTHER, 0.0f, false, false, 0.0f, 0.0f, 0, 0, 0, 0, 4.0f, 0.5f, 0.0f, -1f,
			0, 0, 0.7f, Optional.empty(), Optional.empty(), 2.0f, 10.0f, 15.0f,
			Optional.empty(), 4.0f, 0xFFE6C814, 2.0f, 500, java.util.Set.of(), 100.0f, 45.0f, 10.0f,
			0.0f, -1, -1, 0.0f, 0.03f, true, 0, 0.0f, false, 0xFFFFFFFF, 0xFFFFFFFF,
			SightType.MOVE_SIGHT, 0, 0.0, 0.0, 3.0f, com.example.tudursvehiclemod.asset.CasTargetMode.BALLISTIC, 200.0f, 40.0f, true, 0.0f, 7, Optional.empty(), 1,
			Optional.empty(), 0, false, Optional.empty(), Optional.empty(), Optional.empty(),
			0.0f, 40, 5, false, 1.0f, false, false, Optional.empty(), Optional.empty(), false, 0.0f, Optional.empty());

	/** True if this weapon's own Bomblet directive is actually configured (count > 0) - see VehicleProjectileEntity's own tudursvehiclemod$updateBombletDeployment() doc. */
	public boolean hasBomblets() {
		return this.bombletCount > 0;
	}

	/** True if this weapon uses the barrel-heat system (HeatCount/ MaxHeatCount both set, maxHeat > 0) instead of the ordinary magazineSize/reloadTicks one. */
	public boolean isHeatBased() {
		return this.maxHeat > 0f;
	}

	/** True if this weapon can be resupplied via the vehicle menu's own resupply button (see item.VehicleMenuScreenHandler). */
	public boolean isResuppliable() {
		return this.magazineSize > 0 && this.suppliedNum > 0;
	}

	/** True if ExplosionAltitude is actually configured (see that field's own doc) - 0 (the default, key absent) means this feature is off entirely. */
	public boolean hasExplosionAltitude() {
		return this.explosionAltitude > 0f;
	}

	/** True if DelayFuse was actually present in the file at all (see that field's own -1 sentinel doc), regardless of the ticks value it was actually set to (0 is a real, valid "no delay" - still distinct from "not present"). */
	public boolean hasDelayFuse() {
		return this.delayFuseTicks >= 0;
	}

	/** True if TimeFuse was actually present in the file at all - see hasDelayFuse()'s own doc for the same -1-sentinel-vs-0 distinction. */
	public boolean hasTimeFuse() {
		return this.timeFuseTicks >= 0;
	}

	/** True if Bound is actually configured (see that field's own doc) - 0 (the default) means this projectile stops/embeds on its first hit, the original behavior. */
	public boolean hasBounce() {
		return this.bounceStrength > 0f;
	}

	/** True if Piercing is actually configured - 0 (the default) means this projectile stops at the very first block it hits, the original behavior. */
	public boolean hasPiercing() {
		return this.piercingCount > 0;
	}

	/** True if this weapon actually has a second selectable mode (ModeNum=2) - see modeNum's own doc. */
	public boolean hasModes() {
		return this.modeNum >= 2;
	}

	/** True if Recoil is actually configured (>0) - see that field's own doc. */
	public boolean hasRecoil() {
		return this.recoil > 0f;
	}
}
