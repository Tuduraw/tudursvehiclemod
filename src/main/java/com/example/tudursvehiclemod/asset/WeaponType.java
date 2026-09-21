package com.example.tudursvehiclemod.asset;

/** MC Heli's own weapon "Type" directive (see Readme_Weapon.txt's own documented list), governing POST-LAUNCH projectile behavior and fire-eligibility rules. */
public enum WeaponType {
	/** MachineGun1 (fixed direction) and MachineGun2 (tracks player view) merged into one. */
	MACHINE_GUN,
	/** Straight-line, unguided (no homing/curving after launch). */
	ROCKET,
	/** Only fireable while the vehicle's own attitude is near level (see AbstractVehicleEntity.BOMB_MAX_TILT_DEGREES's own call site) - explodes on contact with the water surface too (not just solid blocks/entities), unlike DEPTH below. */
	BOMB,
	/** Behaves exactly like BOMB (see that entry's own doc for the level-attitude firing restriction), EXCEPT it does NOT explode on contact with the water surface - it sinks through/past it instead, only exploding on an actual solid block/entity hit (or whatever OTHER trigger it's otherwise configured with), matching what BOMB itself used to do before that behavior changed. */
	DEPTH,
	/** Only fireable while the vehicle's own attitude is near level AND at low altitude (see AbstractVehicleEntity's own altitude/tilt checks). */
	TORPEDO,
	/** Homes toward a fixed ground point. */
	AS_MISSILE,
	/** Homes toward a locked target entity. */
	AA_MISSILE,
	/** See AA_MISSILE's own doc. */
	AT_MISSILE,
	/** A new, project-specific type (not from MC Heli's own format, same "not part of the original spec" precedent as DEPTH's own doc) - a lock-on homing missile that doesn't distinguish ground vs airborne targets at all, unlike AA_MISSILE/AT_MISSILE's own documented (but not actually currently implemented as distinct - see tudursvehiclemod$findLockOnTarget()'s own doc) air/ground split. Shares the exact same lock-on/homing/proximity-fuse logic as AA_MISSILE/AT_MISSILE (see AbstractVehicleEntity's own tryFireWeapon() MISSILE case and tudursvehiclemod$updateMissileLockOnIndicators() doc) - kept as its own distinct enum entry, rather than aliasing to one of the other two, so a weapon's own configured Type is preserved/inspectable as written (same reasoning as MK_ROCKET's own doc). */
	MISSILE,
	/** Per Readme_Weapon.txt's own doc: fires like an ordinary guided missile, but hands control of it directly to the shooter after launch - their own mouse steers the missile itself (see AbstractVehicleEntity's own tudursvehiclemod$tryFireWeapon() TV_MISSILE case, client.TvMissileControlState's own doc, and client.mixin.CameraMixin's own doc for the camera override) rather than homing towards any pre-selected target on its own. */
	TV_MISSILE,
	/** Behaves exactly like AS_MISSILE (homes toward a fixed ground point marked under the shooter's own crosshair at fire time) - MC Heli documents these as two separate Type names, but the actual targeting/guidance behavior is identical, so this project's own implementation just reuses AS_MISSILE's own logic wherever it matters (see AbstractVehicleEntity's own tryFireWeapon doc). Kept as its own enum entry (rather than literally aliasing to AS_MISSILE in the parser) purely so a weapon's own configured Type is preserved/inspectable as written. */
	MK_ROCKET,
	/** Homes toward a fixed target point exactly like AS_MISSILE (crosshair-raycast at fire time, no entity lock/LockTime at all - deliberately NOT an AA_MISSILE/AT_MISSILE-style locked-entity homing weapon, to keep this simple, since a real anti-submarine weapon's own warhead-separation/multi-stage behavior is out of scope here), but flies level ABOVE the water surface until within the weapon's own configured DiveDistance (WeaponStats' own new diveDistance field) of that target point, at which point it dives and transitions into the exact same underwater-cruise behavior TORPEDO already has (reusing that same AccelerationInWater/VelocityInWater/TargetDepth configuration - see VehicleProjectileEntity's own tudursvehiclemod$updateAntiSubmarineDive() doc for exactly how the two phases connect). */
	AS_WEAPON,
	/** No projectile/bullet model at all - "着弾地点に対して設定したMinecraftのアイテムを使用する" (uses a configured Minecraft item AT the impact point, as if a player had used that item on the block there - e.g. flint_and_steel ignites it, a hoe tills farmland,..; water_bucket is special-cased to extinguish nearby fire/lava instead of placing a water source). Visually, particles fall along the ballistic trajectory and spread out according to DispenseRange on impact - see WeaponStats's own dispenseItem/dispenseRange doc. */
	DISPENSER,
	/** No projectile/bullet model at all - firing just spawns a burst of colored particles at the weapon's own current muzzle position (see WeaponStats's own smokeColor/smokeSize/smokeMaxAge doc) - the same idea as the existing damage-triggered smoke effect, but manually triggerable on demand at a configured color/size/duration instead. */
	SMOKE,
	/** An entirely empty weapon slot - selectable in the weapon list (e.g. alongside a WeaponBay for purely cosmetic/display tuning) but firing it does nothing at all - no projectile, no sound, no cooldown, nothing. */
	DUMMY,
	/** No projectile/bullet model at all - using it highlights (spots) whatever's currently within its own configured cone (see WeaponStats's own targetingPodTarget/Length/Radius/MarkTime doc) for a duration, rather than firing anything. */
	TARGETING_POD,
	/** A support-aircraft "call in an airstrike" weapon - the player marks a target point via ballistic trajectory (matching a bullet's own impact point, obstacles not considered - see AbstractVehicleEntity's own tudursvehiclemod$computeBallisticTargetPoint() doc); actual damage comes from a separate support aircraft flying a level bombing run over that marked point and dropping real BOMB-type ordnance, an entirely different spawn position/behavior from every other weapon type here. Bullet model IS still configured/used for that ordnance itself, same as an ordinary bomb. */
	CAS,
	/** A "launch a carrier aircraft" weapon, combining UAV-style player-controllable piloting (stage 2, not yet implemented) with CAS-style autonomous waypoint flight - unlike CAS, spawns at THIS weapon's own AddWeapon mount position (i.e. launches from the firing vehicle itself, same "no real projectile" no-position-computation-needed convention as SMOKE/TARGETING_POD), not at a distant marked point. The marked target point (same ballistic trajectory idea as CAS - see AbstractVehicleEntity's own tudursvehiclemod$computeBallisticTargetPoint() doc) still serves as the route's own relative-coordinate center, exactly like CAS's own target point does. */
	CARRIER,
	/** Everything else MC Heli documents that this project doesn't give distinct behavior to. */
	OTHER,
	/** A project-specific "drop tank" (external fuel tank) weapon - temporarily raises this vehicle's own max fuel by fuelPerAmmo() (WeaponStats' own new field) times this weapon's own CURRENT remaining ammo (see AbstractVehicleEntity's own getMaxFuel() doc for exactly how this is summed in). Firing it (dropping one tank) reduces remaining ammo by 1 like any other weapon, which reduces this bonus accordingly - any current fuel now above the new, lower max is clamped down (never refunded/converted to anything). If a bullet/projectile item is configured, the dropped tank itself falls exactly like BOMB (inherits this vehicle's own current velocity, no propulsion of its own, droppable at any attitude) - its own impact behavior otherwise depends on this weapon's own configuration, same as any other weapon. */
	DROP_TANK,
	/** An addon-registered custom weapon type - set exactly when a weapon file's own {@code Type}
	 * value contains ':' (an {@code Identifier}, e.g. {@code humanoidrobotaddon:multi_missile}),
	 * rather than one of the built-in names above. The identifier itself lives on WeaponStats' own
	 * customTypeId field (this enum entry alone doesn't carry it - a plain enum constant can't hold
	 * per-weapon data). Its actual firing/targeting behavior comes from whatever
	 * com.example.tudursvehiclemod.asset.CustomWeaponTypes.get(id) returns - see that registry's own
	 * doc for how an addon registers one and exactly which decisions it gets to make (lock-on
	 * target, per-shot guidance target, salvo size); everything else (ammo, cooldown, reload, heat,
	 * projectile spawn/flight/explosion) is the same shared machinery every other type already uses.
	 * An unregistered id (the addon that owns it isn't installed, or hasn't registered yet) falls
	 * back to CustomWeaponTypes' own default - ordinary unguided fire, no lock-on - rather than
	 * crashing or silently doing nothing. */
	CUSTOM
}
