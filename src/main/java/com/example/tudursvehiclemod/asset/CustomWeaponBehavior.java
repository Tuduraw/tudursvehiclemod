package com.example.tudursvehiclemod.asset;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;

/** What an addon-registered CUSTOM weapon type (see WeaponType.CUSTOM's own doc) actually decides -
 * targeting and salvo count. Everything else a weapon needs (ammo, cooldown, reload, heat, the
 * projectile's own spawn/flight/explosion, mount tracking/turret rotation, recoil) is the same
 * shared machinery every built-in type already uses; a CUSTOM weapon's projectile is homing exactly
 * like MISSILE/AA_MISSILE/AT_MISSILE (guided toward whatever resolveGuidanceTarget() returns) -
 * there is currently no way to opt into a different (ballistic, beam, ...) flight model.
 *
 * <p>Register via {@link CustomWeaponTypes#register(net.minecraft.util.Identifier,
 * CustomWeaponBehavior)}, typically from the addon's own mod initializer. Every method has a
 * sensible default, so a minimal registration can override just the one thing that actually differs
 * (usesLockOn() and resolveLockTarget() for a lock-on weapon that picks its own target some other
 * way; getSalvoSize() and resolveGuidanceTarget() for a multi-target weapon) and inherit ordinary
 * single-target, no-lock-on behaviour for the rest. */
public interface CustomWeaponBehavior {

	/** The default behaviour for an unregistered id, and for anything a registered behaviour doesn't
	 * override: ordinary fire, no lock-on requirement, single target, ballistic guidance defaulting
	 * to whatever the shooter's own crosshair finds (AbstractVehicleEntity's own default lock-on
	 * search, reused so a registered behaviour's own resolveLockTarget() has a sensible super-call
	 * target). */
	CustomWeaponBehavior DEFAULT = new CustomWeaponBehavior() {
	};

	/** Whether this weapon needs a completed lock before it can fire, and shows the lock-on gauge /
	 * indicator highlight - the same gate AA_MISSILE/AT_MISSILE/MISSILE already have. False (the
	 * default) means this weapon fires immediately, no lock-on involved at all. */
	default boolean usesLockOn() {
		return false;
	}

	/** The target this weapon's own lock-on indicator and lock-time progress track - drives what the
	 * pilot sees (highlight, lock-on gauge) and, since firing waits for lock completion, whether the
	 * weapon may fire at all. Only consulted when {@link #usesLockOn()} is true. Default: the same
	 * crosshair-cone search every built-in lock-on weapon uses. */
	default Entity resolveLockTarget(AbstractVehicleEntity vehicle, ServerPlayerEntity shooter, WeaponDefinition weapon) {
		return vehicle.tudursvehiclemod$findLockOnTargetShared(shooter, weapon);
	}

	/** How many rounds one trigger pull of this weapon fires, back to back in the same game tick.
	 * Default 1 (ordinary single shot). Each extra round is a full, ordinary fire - same ammo,
	 * magazine, reload and mount-offset handling as consecutive shots - so a salvo naturally stops
	 * early if the magazine runs dry, and alternates between multiple muzzles the same way repeated
	 * shots already do. */
	default int getSalvoSize(AbstractVehicleEntity vehicle, WeaponDefinition weapon, int weaponIndex) {
		return 1;
	}

	/** The target ONE fired round homes on. salvoIndex is 0 for an ordinary shot and counts up
	 * through a salvo (see {@link #getSalvoSize}) - a behaviour spreading a salvo across several
	 * targets returns a different one per index. Default: {@link #resolveLockTarget}, i.e. every
	 * round in the salvo homes on the same single target. */
	default Entity resolveGuidanceTarget(AbstractVehicleEntity vehicle, ServerPlayerEntity shooter,
			WeaponDefinition weapon, int weaponIndex, int salvoIndex) {
		return this.resolveLockTarget(vehicle, shooter, weapon);
	}
}
