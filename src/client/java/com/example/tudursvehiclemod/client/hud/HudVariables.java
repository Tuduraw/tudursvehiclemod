package com.example.tudursvehiclemod.client.hud;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.AircraftEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;

/** Builds the variable set HUD script expressions read from, matching the naming convention documented in this feature's original spec (Readme_HUD.txt) so ported.. */
public final class HudVariables {

	/** Scales raw per-tick mouse input (pendingYawInput/pendingPitchInput) down to a roughly -1.1 "stick position" range for stick_x/stick_y. */
	private static final double STICK_SENSITIVITY = 0.04;
	/** Max range (blocks) for DisplayMortarDistance's own AS_MISSILE/MK_ROCKET ground-point raycast. */
	private static final double MORTAR_DISTANCE_RAYCAST_RANGE = 200.0;

	private HudVariables() {
	}

	public static Map<String, Double> build(MinecraftClient client, PlayerEntity player,
			AbstractVehicleEntity vehicle) {
		Map<String, Double> vars = new HashMap<>();

		int screenWidth = client.getWindow().getScaledWidth();
		int screenHeight = client.getWindow().getScaledHeight();
		vars.put("center_x", screenWidth / 2.0);
		vars.put("center_y", screenHeight / 2.0);
		vars.put("width", (double) screenWidth);
		vars.put("height", (double) screenHeight);

		vars.put("yaw", (double) vehicle.getYaw());
		vars.put("pitch", (double) vehicle.getPitch());
		vars.put("roll", (double) vehicle.getRoll());
		vars.put("plyr_yaw", (double) player.getYaw());
		vars.put("plyr_pitch", (double) player.getPitch());

		vars.put("altitude", altitudeAboveGround(vehicle));
		vars.put("sea_alt", Math.max(0.0, vehicle.getY() - vehicle.getEntityWorld().getSeaLevel()));

		// Real health values now that vehicles have an actual health pool (see entity.AbstractVehicleEntity's own damage()/getHealth()).
		double maxHealth = Math.max(1.0, vehicle.getMaxHealth());
		double health = MathHelper.clamp(vehicle.getHealth(), 0.0, maxHealth);
		double healthRatio = health / maxHealth;
		vars.put("hp", health);
		vars.put("max_hp", maxHealth);
		vars.put("hp_rto", healthRatio);
		// HP_PER - the current health scaled to a 0-100 range regardless of this vehicle's OWN actual max_hp, matching MC Heli's own HUD scripts (e.g.
		vars.put("hp_per", healthRatio * 100.0);

		// fuel/low_fuel - see Readme_HUD.txt's own doc (fuel is a 0.0-1.0 ratio; low_fuel blinks 0/1 once fuel drops low, for a flashing warning icon/text). A fuelless vehicle (AbstractVehicleEntity's own isFuelless()) always reads full and never warns.
		double maxFuel = Math.max(1.0, vehicle.getMaxFuel());
		double fuelRatio = vehicle.tudursvehiclemod$isFuelless()
				? 1.0
				: MathHelper.clamp(vehicle.getFuel(), 0.0, maxFuel) / maxFuel;
		vars.put("fuel", fuelRatio);
		boolean lowFuel = !vehicle.tudursvehiclemod$isFuelless() && fuelRatio <= 0.2;
		boolean blinkOn = (vehicle.getEntityWorld().getTime() / 10L) % 2L == 0L;
		vars.put("low_fuel", lowFuel && blinkOn ? 1.0 : 0.0);

		// CarEntity specifically displays throttle as
		// an ABSOLUTE value (reverse still shows as a positive percentage)
		// - every other vehicle type keeps the signed value as-is.
		double throttleValue = vehicle.getThrottle();
		if (vehicle instanceof com.example.tudursvehiclemod.entity.CarEntity) {
			throttleValue = Math.abs(throttleValue);
		}
		vars.put("throttle", throttleValue);
		vars.put("pos_x", vehicle.getX());
		vars.put("pos_y", vehicle.getY());
		vars.put("pos_z", vehicle.getZ());
		Vec3d velocity = vehicle.getVelocity();
		vars.put("motion_x", velocity.x);
		vars.put("motion_y", velocity.y);
		vars.put("motion_z", velocity.z);

		// stick_x/stick_y: for aircraft-style vehicles (see
		// AbstractVehicleEntity's own
		// tudursvehiclemod$usesAircraftStyleOrientation() doc - this used
		// to only ever check "instanceof AircraftEntity" alone, which
		// left VtolEntity's own aircraft mode falling through to the
		// stick_y=throttle branch below instead, showing W/S throttle
		// input where mouse-driven pitch was actually meant to appear),
		// BOTH axes are mouse-driven (yaw and pitch respectively).
		if (com.example.tudursvehiclemod.entity.AbstractVehicleEntity.tudursvehiclemod$usesAircraftStyleOrientation(vehicle)) {
			float pendingYawInput = vehicle instanceof AircraftEntity aircraft ? aircraft.pendingYawInput
					: vehicle instanceof com.example.tudursvehiclemod.entity.VtolEntity vtol ? vtol.pendingYawInput : 0f;
			float pendingPitchInput = vehicle instanceof AircraftEntity aircraft ? aircraft.pendingPitchInput
					: vehicle instanceof com.example.tudursvehiclemod.entity.VtolEntity vtol ? vtol.pendingPitchInput : 0f;
			double stickX = net.minecraft.util.math.MathHelper.clamp(
					pendingYawInput * STICK_SENSITIVITY, -1.0, 1.0);
			double stickY = net.minecraft.util.math.MathHelper.clamp(
					pendingPitchInput * STICK_SENSITIVITY, -1.0, 1.0);
			vars.put("stick_x", stickX);
			vars.put("stick_y", stickY);
		} else {
			vars.put("stick_x", (double) vehicle.getSyncedSidewaysInput());
			vars.put("stick_y", (double) vehicle.getSyncedThrottleInput());
		}

		// Weapon: currently CLIENT-selected weapon (see client.VehicleModClient's own weapon-switch key.
		var weapons = vehicle.getDefinition().weapons();
		int selectedWeaponIndex = com.example.tudursvehiclemod.client.VehicleModClient.getSelectedWeaponIndex();
		// A seat with no weapons of its own (or different
		// weapons than whatever happened to be globally selected) still
		// displayed weapon info regardless, since this only ever checked
		// "is this a valid index into the overall weapons list", not "is
		// this weapon actually mine". Also requiring it to be one of
		// tudursvehiclemod$ownSeatWeaponIndices() (the same check
		// VehicleModClient's own weapon-switch key logic already uses)
		// fixes that.
		boolean ownsSelectedWeapon = selectedWeaponIndex >= 0 && selectedWeaponIndex < weapons.size()
				&& com.example.tudursvehiclemod.client.VehicleModClient.tudursvehiclemod$ownSeatWeaponIndices(player).contains(selectedWeaponIndex);
		if (ownsSelectedWeapon) {
			var selectedWeapon = weapons.get(selectedWeaponIndex);
			// See AbstractVehicleEntity's own tudursvehiclemod$getEffectiveWeaponAim() doc. Already vehicle-relative (same convention DefaultYaw/getWeaponAimYaw() itself uses - tryFireWeapon() transforms this SAME value by the vehicle's own body orientation to get a world-space direction), so no further adjustment against the vehicle's own yaw is needed here.
			double[] effectiveAim = vehicle.tudursvehiclemod$getEffectiveWeaponAim(selectedWeapon);
			vars.put("gun_yaw", (double) MathHelper.wrapDegrees((float) effectiveAim[0]));
			vars.put("gun_pitch", effectiveAim[1]);
			int[] ammoState = vehicle.getWeaponAmmoState(selectedWeaponIndex);
			int ammoRemaining = ammoState[0];
			int reloadTicksRemaining = ammoState[1];
			int magazineSize = selectedWeapon.magazineSize();
			// "Reloading" (and reload_time) now
			// ALSO reflects this weapon's own fire-rate delay (MC Heli's
			// own "Delay" directive - this project's own cooldownTicks,
			// see AbstractVehicleEntity's own getWeaponCooldown() doc) -
			// not just an actual magazine reload - so a HUD script that
			// shows "RELOADING" (or similar) during reload_time > 0
			// shows the same thing while this weapon is simply still on
			// its own fire-rate cooldown between shots.
			int cooldownTicksRemaining = vehicle.getWeaponCooldown(selectedWeaponIndex);
			int displayedTicksRemaining = reloadTicksRemaining > 0 ? reloadTicksRemaining : cooldownTicksRemaining;
			vars.put("reloading", displayedTicksRemaining > 0 ? 1.0 : 0.0);
			vars.put("reload_time", (double) displayedTicksRemaining);
			if (magazineSize > 0 && ammoRemaining >= 0) {
				vars.put("wpn_ammo", (double) ammoRemaining);
				vars.put("wpn_rm_ammo", (double) magazineSize);
			} else {
				// Unlimited-ammo weapon (magazineSize == 0), or ammo state not yet synced from the server.
				vars.put("wpn_ammo", 0.0);
				vars.put("wpn_rm_ammo", 0.0);
			}
			// Per Readme_Weapon.txt's own Sight doc: exposes which reticle
			// this weapon is configured to show, as a simple numeric code
			// (0 = MoveSight, the default; 1 = None; 2 = MissileSight),
			// so a HUD script can conditionally show/hide its own reticle
			// elements based on it.
			vars.put("sight_type", switch (selectedWeapon.sight()) {
				case NONE -> 1.0;
				case MISSILE_SIGHT -> 2.0;
				case MOVE_SIGHT -> 0.0;
			});
			// Per Readme_Weapon.txt's own LockTime doc: lock_progress is a
			// 0.1 fraction of the way towards this weapon's own ACTUAL
			// required lock time (1.0 once fully locked - see
			// AbstractVehicleEntity's own tudursvehiclemod$missileLockRequiredTicks
			// doc for how that distance-aware value gets computed, and
			// tudursvehiclemod$missileLockProgressTicks's own doc for how
			// the underlying progress tick count actually gets built
			// up) - 0.0 for every weapon type that isn't actually
			// AAMissile/ATMissile, or a required time of 0 (locks
			// instantly, so "progress" isn't a meaningful concept there
			// at all - always reads as already fully locked instead).
			// locked is a plain 0/1 for a HUD script that just wants a
			// boolean rather than the raw fraction.
			int lockTimeTicks = selectedWeapon.lockTimeTicks();
			int lockRequiredTicks = vehicle.tudursvehiclemod$getMissileLockRequiredTicks(selectedWeaponIndex, lockTimeTicks);
			double lockProgressFraction = lockRequiredTicks <= 0 ? 1.0
					: MathHelper.clamp(vehicle.tudursvehiclemod$getMissileLockProgress(selectedWeaponIndex) / (double) lockRequiredTicks, 0.0, 1.0);
			vars.put("lock_progress", lockProgressFraction);
			vars.put("locked", lockProgressFraction >= 1.0 ? 1.0 : 0.0);
			// Per Readme_HUD.txt's own documented "lock" variable (missile lock-on state, 0.0-1.0): this project's own lock_progress above already computes the exact same value - lock itself was previously left hard-coded to 0.0 by a later, unconditional default assignment further down in this method (see that removed line's own history) that unintentionally clobbered it (and sight_type below) even when a weapon was actually owned/selected. Any HUD script written against the ORIGINAL documented spec (using "lock" by its official name, not this project's own lock_progress/locked additions) would otherwise always read 0 regardless of actual lock state.
			vars.put("lock", lockProgressFraction);
			// Per Readme_Weapon.txt's own ModeNum doc: wpn_mode is this
			// weapon's own currently-selected mode (0 or 1 - see
			// AbstractVehicleEntity's own tudursvehiclemod$tryToggleWeaponMode()
			// doc), has_modes is a plain 0/1 for whether this weapon
			// actually has a second mode to toggle into at all (always 0
			// for a weapon whose own ModeNum isn't 2), so a HUD script
			// can hide its own mode indicator entirely for a weapon that
			// doesn't use this at all.
			vars.put("wpn_mode", (double) vehicle.getWeaponMode(selectedWeaponIndex));
			vars.put("has_modes", selectedWeapon.hasModes() ? 1.0 : 0.0);
			// DisplayMortarDistance - see tudursvehiclemod$computeMortarDistance()'s own doc for the per-WeaponType calculation. Shared with buildStringVariables()'s own mortar_distance_str.
			double mortarDistance = tudursvehiclemod$computeMortarDistance(client, player, vehicle, selectedWeapon);
			vars.put("mortar_distance", mortarDistance);
			vars.put("has_mortar_distance", selectedWeapon.displayMortarDistance()
					&& selectedWeapon.weaponType() != com.example.tudursvehiclemod.asset.WeaponType.BOMB
					&& selectedWeapon.weaponType() != com.example.tudursvehiclemod.asset.WeaponType.ROCKET ? 1.0 : 0.0);
			// wpn_heat/is_heat_wpn.
			if (selectedWeapon.isHeatBased()) {
				double heatRatio = MathHelper.clamp(vehicle.getWeaponHeat(selectedWeaponIndex) / selectedWeapon.maxHeat(), 0.0, 1.0);
				vars.put("wpn_heat", heatRatio);
				vars.put("is_heat_wpn", 1.0);
			} else {
				vars.put("wpn_heat", 0.0);
				vars.put("is_heat_wpn", 0.0);
			}
		} else {
			vars.put("reloading", 0.0);
			vars.put("reload_time", 0.0);
			vars.put("wpn_ammo", 0.0);
			vars.put("wpn_rm_ammo", 0.0);
			vars.put("wpn_heat", 0.0);
			vars.put("is_heat_wpn", 0.0);
			vars.put("lock_progress", 0.0);
			vars.put("locked", 0.0);
			vars.put("lock", 0.0);
			vars.put("sight_type", 0.0);
		}
		vars.put("dsp_mt_dist", 0.0);
		vars.put("mt_dist", -1.0);
		vars.put("have_radar", 0.0);
		vars.put("radar_rot", 0.0);
		vars.put("vtol_stat", 0.0);

		vars.put("free_look", vehicle.isFreeLook() ? 1.0 : 0.0);
		vars.put("cam_mode", 0.0);
		vars.put("cam_zoom", 1.0);
		vars.put("auto_pilot", 0.0);
		vars.put("have_flare", 0.0);
		vars.put("can_flare", 0.0);
		vars.put("inventory", 0.0);
		vars.put("hovering", 0.0);
		vars.put("is_uav", 0.0);
		vars.put("uav_fs", 0.0);
		vars.put("gunner_mode", 0.0);
		vars.put("time", (double) (vehicle.getEntityWorld().getTimeOfDay() % 24000L));
		vars.put("test_mode", 0.0);

		// Extensions beyond the original spec, specific to this mod's own feature set.
		if (vehicle instanceof AircraftEntity aircraft) {
			vars.put("manual_mode", aircraft.isManualMode() ? 1.0 : 0.0);
			vars.put("stalling", aircraft.isStalling() ? 1.0 : 0.0);
		} else if (vehicle instanceof com.example.tudursvehiclemod.entity.VtolEntity vtol) {
			vars.put("manual_mode", vtol.isManualMode() ? 1.0 : 0.0);
			vars.put("stalling", vtol.isStalling() ? 1.0 : 0.0);
		} else {
			vars.put("manual_mode", 0.0);
			vars.put("stalling", 0.0);
		}
		vars.put("gear_deployed", vehicle.tudursvehiclemod$supportsLandingGearDisplay() && vehicle.isGearDeployed() ? 1.0 : 0.0);
		// speed: this vehicle's own tracked "airspeed" (getCruiseSpeed()), NOT raw getVelocity().length().
		double speed = vehicle.getCruiseSpeed();
		vars.put("speed", speed);
		vars.put("speed_kbh", speed * 72.0);

		return vars;
	}

	/** String-valued variables (see HudExecutionContext's own doc for why these are kept separate from the double-only map build() returns). */
	public static Map<String, String> buildStringVariables(MinecraftClient client, net.minecraft.entity.player.PlayerEntity player, AbstractVehicleEntity vehicle) {
		Map<String, String> vars = new HashMap<>();
		var weapons = vehicle.getDefinition().weapons();
		int selectedWeaponIndex = com.example.tudursvehiclemod.client.VehicleModClient.getSelectedWeaponIndex();
		boolean ownsSelectedWeapon = selectedWeaponIndex >= 0 && selectedWeaponIndex < weapons.size()
				&& com.example.tudursvehiclemod.client.VehicleModClient.tudursvehiclemod$ownSeatWeaponIndices(player).contains(selectedWeaponIndex);
		vars.put("wpn_name", ownsSelectedWeapon ? weapons.get(selectedWeaponIndex).displayName() : "");
		// DisplayMortarDistance - "---b" (rather than hiding the line entirely) whenever the value isn't currently computable (e.g. this weapon isn't currently aimed upward) - see tudursvehiclemod$computeMortarDistance()'s own doc for how this is computed. Usable directly via %s in a HUD script, instead of %.0f + mortar_distance, for a script that wants this exact fallback text.
		double mortarDistance = ownsSelectedWeapon
				? tudursvehiclemod$computeMortarDistance(client, player, vehicle, weapons.get(selectedWeaponIndex)) : -1.0;
		vars.put("mortar_distance_str", mortarDistance >= 0.0 ? String.format(java.util.Locale.ROOT, "%.0fb", mortarDistance) : "---b");
		return vars;
	}

	/** DisplayMortarDistance - per Readme_Weapon.txt's own doc, computed differently per WeaponType.
	 * MachineGun: a plain ballistic same-height range, from Acceleration/Gravity alone (no world raycast needed), using this weapon's own clamped aim pitch (not the player's raw view pitch) and including the firing vehicle's own current velocity - see client.hud.MortarMarkerRenderer's own tudursvehiclemod$computeMachineGunDistance() for this calculation itself, consolidated there alongside the rest of this feature's own logic.
	 * AS_MISSILE/MK_ROCKET: real-world distance to the ground point currently under the shooter's own crosshair (same raycast AS_MISSILE/MK_ROCKET's own guidance already uses at fire time).
	 * CAS/Carrier: a plain ballistic same-height range, same idea as MachineGun's own entry above - see client.hud.MortarMarkerRenderer's own tudursvehiclemod$computeCasCarrierDistance() (which AbstractVehicleEntity's own tudursvehiclemod$computeBallisticTargetPoint() mirrors exactly, so the displayed number always matches precisely where the actual strike will land).
	 * Bomb/Rocket instead get an in-world block marker (see client.hud.MortarMarkerRenderer), not a HUD number - always returns -1 for those.
	 * Returns -1 (an explicit sentinel, not merely "unset") whenever this weapon has the feature configured but the value isn't currently computable (e.g. not aiming upward) - see mortar_distance_str's own doc for the "---b" display this drives instead of hiding the line entirely. */
	private static double tudursvehiclemod$computeMortarDistance(MinecraftClient client, PlayerEntity player,
			AbstractVehicleEntity vehicle, com.example.tudursvehiclemod.asset.WeaponDefinition selectedWeapon) {
		if (!selectedWeapon.displayMortarDistance()) {
			return -1.0;
		}
		// Uses the vehicle's own Y (hitbox height), not the player's own (often notably higher, while seated) eye position, as the origin height - the actual projectile launches from roughly hull height, not headroom height, and the "returns to launch height" ballistic formula below needs that real reference point.
		double originY = vehicle.getY();
		return switch (selectedWeapon.weaponType()) {
			case MACHINE_GUN -> MortarMarkerRenderer.tudursvehiclemod$computeMachineGunDistance(client, player, vehicle, selectedWeapon);
			case CAS, CARRIER -> MortarMarkerRenderer.tudursvehiclemod$computeCasCarrierDistance(player, selectedWeapon);
			case AS_MISSILE, MK_ROCKET -> {
				Vec3d eyePos = player.getCameraPosVec(1.0f);
				Vec3d origin = new Vec3d(eyePos.x, originY, eyePos.z);
				Vec3d viewDir = player.getRotationVec(1.0f);
				// Uses this weapon's own actual launch velocity (aim * weapon.velocity()) and gravity for a genuine ballistic-collision trajectory instead, mirroring MortarMarkerRenderer's own identical CAS/Carrier COLLISION implementation.
				if (selectedWeapon.casTargetMode() == com.example.tudursvehiclemod.asset.CasTargetMode.COLLISION) {
					Vec3d launchVelocity = viewDir.multiply(selectedWeapon.velocity());
					yield MortarMarkerRenderer.tudursvehiclemod$computeCollisionDistance(client, player, origin, launchVelocity, selectedWeapon.gravity());
				}
				Vec3d rayEnd = origin.add(viewDir.multiply(MORTAR_DISTANCE_RAYCAST_RANGE));
				RaycastContext context = new RaycastContext(origin, rayEnd,
						RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
				BlockHitResult hit = client.world.raycast(context);
				Vec3d targetPoint = hit.getType() == HitResult.Type.MISS ? rayEnd : hit.getPos();
				yield origin.distanceTo(targetPoint);
			}
			// Bomb/Rocket (previously always -1.0 here - the in-world marker itself is handled entirely separately, by MortarMarkerRenderer's own tick()) also show a distance number specifically when CasTargetMode.COLLISION is selected - see that method's own doc (it returns -1.0 itself whenever COLLISION isn't actually selected, matching this whole switch's own previous default exactly for every OTHER case).
			case BOMB, ROCKET -> MortarMarkerRenderer.tudursvehiclemod$computeBombRocketCollisionDistance(client, player, vehicle, selectedWeapon);
			default -> -1.0;
		};
	}

	/** Distance straight down to the first solid block, matching this variable's own documented meaning ("機体から下方向のブロックまでの距離"). */
	private static double altitudeAboveGround(AbstractVehicleEntity vehicle) {
		Vec3d start = vehicle.getEntityPos();
		Vec3d end = start.add(0, -256, 0);
		HitResult hit = vehicle.getEntityWorld().raycast(new RaycastContext(start, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, vehicle));
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			return Math.max(0.0, start.y - blockHit.getPos().y);
		}
		return 256.0;
	}
}
