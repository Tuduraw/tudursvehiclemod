package com.example.tudursvehiclemod.client.hud;

import com.example.tudursvehiclemod.asset.WeaponDefinition;
import com.example.tudursvehiclemod.asset.WeaponType;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.List;

/** DisplayMortarDistance for Bomb/Rocket - see WeaponStats's own doc. Unlike MachineGun/AS_MISSILE/CAS/MK_ROCKET (a plain HUD number, see HudVariables), these two get an in-world marker at the first block their own predicted trajectory would actually hit.
 * This instead draws a single stroked (outline-only) circle gizmo via MinecraftClient's own newGizmoScope()/GizmoDrawing.circle() - confirmed via direct bytecode inspection of the actual game jar (WorldRenderer.collectGizmos(), called unconditionally every frame as part of the normal world render pass, not gated behind any debug-mode toggle) that this is safe to use for an always-visible, non-debug gameplay marker. One circle outline (20 short line segments internally, per CircleGizmo's own implementation) is far cheaper than spawning particles continuously over a multi-block area, and can be shown every tick rather than throttled. */
public final class MortarMarkerRenderer {

	private MortarMarkerRenderer() {
	}

	/** This vehicle's own getVelocity() (used for the vehicle's own momentum contribution in tudursvehiclemod$computeMachineGunDistance()) is the raw, client-synced value - not smoothly interpolated the way position/rotation are for rendering - so it can occasionally report a brief, non-interpolated noise spike/dip between network updates even while nothing the player controls is actually changing. Eased towards the newly-computed value each frame instead of snapping to it outright, so a single noisy frame doesn't visibly flicker the display - still tracks a genuine, sustained change (actually re-aiming) within a few frames. Reset to -1 immediately (no easing INTO an invalid state) the moment the value stops being computable at all, rather than visibly decaying towards an increasingly wrong number while the player is no longer aiming upward. */
	private static double smoothedMachineGunMortarDistance = -1.0;
	private static final double MORTAR_DISTANCE_SMOOTHING = 0.3;

	/** Computes the same-height-return range for a MACHINE_GUN weapon (HudVariables' own "mortar_distance" number), moved here unchanged from that class's own former inline MACHINE_GUN case - see this whole class's own doc for why an in-world marker isn't used for this weapon type (a plain HUD number instead, since a machine-gun-style flat/direct shot has no single meaningful "impact point" the way a lobbed Bomb/Rocket does). */
	public static double tudursvehiclemod$computeMachineGunDistance(MinecraftClient client, PlayerEntity player,
			AbstractVehicleEntity vehicle, WeaponDefinition selectedWeapon) {
		if (!selectedWeapon.displayMortarDistance()) {
			return -1.0;
		}
		float muzzleVelocity = selectedWeapon.velocity();
		float gravity = selectedWeapon.gravity();
		// Uses this weapon's own clamped aim pitch (matching the actual fired bullet's own pitch limit - see AbstractVehicleEntity's own getWeaponAimPitch() doc) rather than the player's raw view pitch, so the displayed range correctly stops changing past the weapon's own pitch cap, same as the real fired shot does. Yaw doesn't need clamping here (only pitch affects the horizontal/vertical split below), so the player's own raw yaw is still used to build the full 3D direction the vehicle's own velocity gets added to.
		// pilotUsable() (see WeaponDefinition's own doc - lets the pilot fire a gunner-seat weapon when that seat is empty) must be passed here too, not a hardcoded false - otherwise, when the pilot is the one actually using this weapon, tudursvehiclemod$resolveWeaponTrackingOccupant() looks for an occupant in the weapon's own (empty) gunner seat, finds none, and falls back to a stale/default pitch instead of the pilot's own real aim, breaking the calculation below (always reading as "not aiming upward").
		float aimPitch = vehicle.getWeaponAimPitch(selectedWeapon.seatIndex(), selectedWeapon.pilotUsable(),
				selectedWeapon.aimRange().orElse(null), 1.0f);
		Vec3d aimDir = Vec3d.fromPolar(aimPitch, player.getYaw(1.0f));
		// A tilted mount (default_pitch / default_roll) reports its pitch in its own tilted frame, which can't be combined with the player's vehicle-frame yaw as above. Such a weapon fires along its effective aim (the turret's actual direction, turned back into the vehicle's frame by the same path firing uses), so that direction - still in the vehicle's frame, like the view it replaces - is used instead. An untilted weapon keeps the direction above exactly as before.
		if (selectedWeapon.aimRange().isPresent() && selectedWeapon.aimRange().get().tilt().isTilted()) {
			double[] effective = vehicle.tudursvehiclemod$getEffectiveWeaponAim(selectedWeapon);
			double[] localDir = selectedWeapon.aimRange().get().tilt().directionInVehicleFrame(effective[1], effective[0]);
			org.joml.Vector3f tiltedWorldDir = new org.joml.Vector3f((float) localDir[0], (float) localDir[1], (float) localDir[2]);
			vehicle.tudursvehiclemod$getBodyOrientation().transform(tiltedWorldDir);
			aimDir = new Vec3d(tiltedWorldDir.x, tiltedWorldDir.y, tiltedWorldDir.z);
		}
		// Matches the real tryFireWeapon() formula (aim * weapon.velocity() + the firing vehicle's own current velocity) - horizontal/vertical components are then derived from the actual resulting velocity vector, not the raw aim pitch alone, so a moving vehicle's own momentum is correctly accounted for.
		Vec3d launchVelocity = aimDir.multiply(muzzleVelocity).add(vehicle.getVelocity());
		// See tudursvehiclemod$computeCollisionDistance()'s own doc. Checked here, using the SAME launch origin/velocity just resolved above for MachineGun's own default calculation, rather than a separate/potentially-inconsistent resolution.
		if (selectedWeapon.casTargetMode() == com.example.tudursvehiclemod.asset.CasTargetMode.COLLISION) {
			Vec3d cameraPos = player.getCameraPosVec(1.0f);
			Vec3d origin = new Vec3d(cameraPos.x, vehicle.getY(), cameraPos.z);
			double collisionDistance = tudursvehiclemod$computeCollisionDistance(client, player, origin, launchVelocity, gravity);
			if (collisionDistance < 0.0) {
				smoothedMachineGunMortarDistance = -1.0;
			} else if (smoothedMachineGunMortarDistance < 0.0) {
				smoothedMachineGunMortarDistance = collisionDistance;
			} else {
				smoothedMachineGunMortarDistance += (collisionDistance - smoothedMachineGunMortarDistance) * MORTAR_DISTANCE_SMOOTHING;
			}
			return smoothedMachineGunMortarDistance;
		}
		double horizontalVelocity = Math.sqrt(launchVelocity.x * launchVelocity.x + launchVelocity.z * launchVelocity.z);
		double verticalVelocity = launchVelocity.y;
		double rawDistance = com.example.tudursvehiclemod.entity.projectile.Ballistics.levelReturnDistance(horizontalVelocity, verticalVelocity, gravity,
				com.example.tudursvehiclemod.entity.projectile.Ballistics.levelReturnTickCap(verticalVelocity, gravity));
		// Smoothing - see smoothedMachineGunMortarDistance's own doc for why this exists.
		if (rawDistance < 0.0) {
			smoothedMachineGunMortarDistance = -1.0;
		} else if (smoothedMachineGunMortarDistance < 0.0) {
			smoothedMachineGunMortarDistance = rawDistance;
		} else {
			smoothedMachineGunMortarDistance += (rawDistance - smoothedMachineGunMortarDistance) * MORTAR_DISTANCE_SMOOTHING;
		}
		return smoothedMachineGunMortarDistance;
	}

	/** Marker color: opaque orange (0xAARRGGBB - DrawStyle's own color fields go through ColorHelper.scaleAlpha(), confirming ARGB with a real alpha channel, not the bare 0xRRGGBB some other color constants in this project use), distinct from vanilla's own debug-renderer colors. */
	private static final int MARKER_COLOR = 0xFFFFA500;
	/** The marker now covers a fixed ~5 block radius area (matching the actual blast/impact zone at a glance), rather than a single point. */
	private static final float MARKER_RADIUS = 5.0f;
	/** Hard cap on simulated distance, so an unobstructed shot (fired at the sky, or Gravity=0) doesn't simulate forever. */
	private static final double MAX_SIMULATION_DISTANCE = 300.0;
	/** The raycast-stepping simulation itself (the actual per-block cost) still only re-runs every Nth tick - several times a second, indistinguishable to the eye for a marker that isn't meant to track split-second changes - but the gizmo itself (a cheap, already-computed circle) is re-added every tick in between so the marker never visibly flickers or goes stale-looking. */
	private static final int SIMULATION_INTERVAL_TICKS = 4;
	private static int tudursvehiclemod$tickCounter = 0;
	private static Vec3d tudursvehiclemod$cachedImpactPos = null;

	public static void tick(MinecraftClient client) {
		tudursvehiclemod$tickCounter++;
		if (client.player == null || client.world == null) {
			tudursvehiclemod$cachedImpactPos = null;
			return;
		}
		PlayerEntity player = client.player;
		if (!(com.example.tudursvehiclemod.client.VehicleModClient.tudursvehiclemod$getClientEffectiveVehicle(player)
				instanceof AbstractVehicleEntity vehicle)) {
			tudursvehiclemod$cachedImpactPos = null;
			return;
		}
		int selectedWeaponIndex = com.example.tudursvehiclemod.client.VehicleModClient.getSelectedWeaponIndex();
		List<WeaponDefinition> weapons = vehicle.getDefinition().weapons();
		if (selectedWeaponIndex < 0 || selectedWeaponIndex >= weapons.size()
				|| !com.example.tudursvehiclemod.client.VehicleModClient.tudursvehiclemod$ownSeatWeaponIndices(player).contains(selectedWeaponIndex)) {
			tudursvehiclemod$cachedImpactPos = null;
			return;
		}
		WeaponDefinition selectedWeapon = weapons.get(selectedWeaponIndex);
		if (!selectedWeapon.displayMortarDistance()
				|| (selectedWeapon.weaponType() != WeaponType.BOMB && selectedWeapon.weaponType() != WeaponType.ROCKET)) {
			tudursvehiclemod$cachedImpactPos = null;
			return;
		}

		if (tudursvehiclemod$tickCounter % SIMULATION_INTERVAL_TICKS == 0) {
			tudursvehiclemod$cachedImpactPos = tudursvehiclemod$simulateImpactPos(client, player, vehicle, selectedWeapon);
		}
		if (tudursvehiclemod$cachedImpactPos != null) {
			try (var scope = client.newGizmoScope()) {
				GizmoDrawing.circle(tudursvehiclemod$cachedImpactPos, MARKER_RADIUS, DrawStyle.stroked(MARKER_COLOR));
			}
		}
	}

	/** Where the selected weapon's shell would land: its real flight (entity.projectile.Ballistics - gravity and air drag, tick by tick, as the projectile itself moves), with a block raycast along each tick, out to MAX_SIMULATION_DISTANCE. */
	private static Vec3d tudursvehiclemod$simulateImpactPos(MinecraftClient client, PlayerEntity player,
			AbstractVehicleEntity vehicle, WeaponDefinition selectedWeapon) {
		// Uses the vehicle's own Y (hitbox height), not the player's own (often notably higher, while seated) eye position, as the simulation's own starting height - the actual projectile launches from roughly hull height, not headroom height.
		Vec3d cameraPos = player.getCameraPosVec(1.0f);
		Vec3d muzzlePos = new Vec3d(cameraPos.x, vehicle.getY(), cameraPos.z);
		Vec3d viewDir;
		if (selectedWeapon.aimRange().isPresent()) {
			// Matches tryFireWeapon()'s own "effectivelyAiming" branch: follows the player's own current view.
			viewDir = player.getRotationVec(1.0f);
		} else {
			// Matches tryFireWeapon()'s own "no aim range at all" branch: a FIXED direction (this weapon's own mount_yaw/mount_pitch, combined with the vehicle's own current body orientation) - completely independent of wherever the player happens to be looking, including free-look.
			com.example.tudursvehiclemod.asset.WeaponOffset mountOffset = selectedWeapon.offsets().isEmpty()
					? new com.example.tudursvehiclemod.asset.WeaponOffset(0, 0, 0, 0, 0, java.util.Optional.empty())
					: selectedWeapon.offsets().get(0);
			Vec3d localDir = Vec3d.fromPolar((float) mountOffset.mountPitch(), (float) mountOffset.mountYaw());
			org.joml.Vector3f worldDir = new org.joml.Vector3f((float) localDir.x, (float) localDir.y, (float) localDir.z);
			vehicle.tudursvehiclemod$getBodyOrientation().transform(worldDir);
			viewDir = new Vec3d(worldDir.x, worldDir.y, worldDir.z);
		}
		float muzzleVelocity = selectedWeapon.velocity();
		float gravity = selectedWeapon.gravity();
		// Matches the real tryFireWeapon() formula (aim * weapon.velocity() + the firing vehicle's own current velocity) - a Bomb typically has velocity()==0 and relies entirely on the vehicle's own momentum at release, which this simulation would otherwise completely miss.
		Vec3d velocity = viewDir.multiply(muzzleVelocity).add(vehicle.getVelocity());
		if (velocity.length() < 0.01) {
			// A bomb dropped with no meaningful initial velocity (stationary vehicle, weapon velocity()==0) still falls under gravity - seeds a small downward nudge so the simulation below can build up realistic falling speed, rather than reading as "stalled out" and bailing before gravity ever gets a chance to act.
			velocity = new Vec3d(0.0, -0.01, 0.0);
		}

		// The same tick-by-tick flight as the real projectile (Ballistics) - this used to step a fixed distance at a time with no drag at all, so the marker landed further out than the shell did.
		com.example.tudursvehiclemod.entity.projectile.Ballistics.Impact impact = com.example.tudursvehiclemod.entity.projectile.Ballistics.flyUntilBlocked(muzzlePos, velocity, gravity, client.world.getBottomY() - 8,
				MAX_SIMULATION_DISTANCE, MARKER_SIMULATION_MAX_TICKS, (from, to) -> tudursvehiclemod$raycastBlock(client, from, to, player));
		return impact.hitBlock() ? impact.pos() : null; // Nothing hit within MAX_SIMULATION_DISTANCE - nothing to mark.
	}

	/** Tick cap for the impact marker's flight - far more than a shell needs to come down within MAX_SIMULATION_DISTANCE. */
	private static final int MARKER_SIMULATION_MAX_TICKS = 1200;

	/** One tick of flight's block raycast, as Ballistics.flyUntilBlocked() wants it: the hit point, or null. */
	private static Vec3d tudursvehiclemod$raycastBlock(MinecraftClient client, Vec3d from, Vec3d to, net.minecraft.entity.Entity exclude) {
		BlockHitResult hit = client.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, exclude));
		return hit.getType() == HitResult.Type.MISS ? null : hit.getPos();
	}

	/** Other weapon types (MachineGun/AS_MISSILE/MK_ROCKET/Bomb/Rocket) also support CasTargetMode.COLLISION for their own DisplayMortarDistance - see this method's own doc for the full reasoning. Generous enough ticks (matching AbstractVehicleEntity's own identical CAS_CARRIER_COLLISION_MAX_TICKS) for the simulated trajectory to keep falling well below launch height until it actually hits terrain, rather than an arbitrary early cutoff - the SAME semantics as CasTargetMode.COLLISION's own CAS/Carrier implementation, just parameterized here so every weapon type can share this one calculation. */
	private static final int GENERIC_COLLISION_MAX_TICKS = 3000;

	/** Generic COLLISION-mode distance calculation, shared across every weapon type that supports it - see GENERIC_COLLISION_MAX_TICKS's own doc. origin/launchVelocity are already resolved per the CALLER's own type-specific aim convention (see each call site's own comment for how). Returns -1.0 if client.world is unavailable at all (shouldn't normally happen mid-render, but defensive regardless) - never returns a negative distance otherwise, even for a degenerate (near-zero) launch velocity, since a stationary "shot" still has SOME distance to wherever gravity eventually carries it (or 0.0 if it's already resting exactly at its own origin). */
	public static double tudursvehiclemod$computeCollisionDistance(MinecraftClient client, PlayerEntity excludeEntity, Vec3d origin, Vec3d launchVelocity, float gravity) {
		if (client.world == null) {
			return -1.0;
		}
		if (gravity <= 0f) {
			// No meaningful arc at all - straight-line raycast along the launch direction instead, matching every other mode's own fallback for this same degenerate case.
			Vec3d direction = launchVelocity.length() > 1.0e-6 ? launchVelocity.normalize() : new Vec3d(0.0, -1.0, 0.0);
			Vec3d fallbackEnd = origin.add(direction.multiply(CAS_CARRIER_DEFAULT_VELOCITY * 20.0));
			RaycastContext fallbackContext = new RaycastContext(origin, fallbackEnd,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, excludeEntity);
			BlockHitResult fallbackHit = client.world.raycast(fallbackContext);
			Vec3d fallbackPos = fallbackHit.getType() == HitResult.Type.MISS ? fallbackEnd : fallbackHit.getPos();
			return origin.distanceTo(fallbackPos);
		}
		Vec3d impact = com.example.tudursvehiclemod.entity.projectile.Ballistics.flyUntilBlocked(origin, launchVelocity, gravity, client.world.getBottomY() - 8,
				Double.MAX_VALUE, GENERIC_COLLISION_MAX_TICKS, (from, to) -> tudursvehiclemod$raycastBlock(client, from, to, excludeEntity)).pos();
		return origin.distanceTo(impact);
	}

	/** Bomb/Rocket (which previously showed only the in-world marker, no HUD distance number at all - see this whole class's own doc) also show a distance number specifically when CasTargetMode.COLLISION is selected: reuses tudursvehiclemod$simulateImpactPos()'s own existing result (the SAME point the marker itself is drawn at, not a separate/potentially-inconsistent calculation), so the displayed number always matches precisely where the marker is shown. Returns -1.0 (this whole feature's own existing "nothing meaningful to show" sentinel) whenever COLLISION isn't selected at all (this weapon's own default marker-only behavior is left completely unchanged - "現在のデフォルト値に加え", per the direct request this whole feature came from) or the underlying simulation itself found nothing to hit. */
	public static double tudursvehiclemod$computeBombRocketCollisionDistance(MinecraftClient client, PlayerEntity player,
			AbstractVehicleEntity vehicle, WeaponDefinition selectedWeapon) {
		if (selectedWeapon.casTargetMode() != com.example.tudursvehiclemod.asset.CasTargetMode.COLLISION) {
			return -1.0;
		}
		Vec3d impactPos = tudursvehiclemod$simulateImpactPos(client, player, vehicle, selectedWeapon);
		if (impactPos == null) {
			return -1.0;
		}
		// Matches tudursvehiclemod$simulateImpactPos()'s own identical muzzlePos computation exactly, so the returned distance is measured from the SAME origin the simulation itself actually used.
		Vec3d cameraPos = player.getCameraPosVec(1.0f);
		Vec3d muzzlePos = new Vec3d(cameraPos.x, vehicle.getY(), cameraPos.z);
		return muzzlePos.distanceTo(impactPos);
	}


	/** CAS/Carrier displays as a plain HUD range number (like MachineGun's own mortar_distance), not an in-world marker: the SAME ballistic trajectory calculation a bullet's own impact point uses, obstacles NOT considered at all (CasTargetMode.BALLISTIC) - mirrors AbstractVehicleEntity's own tudursvehiclemod$computeBallisticTargetPoint() exactly (same default velocity/gravity, same air drag, same "same-height-return" semantics), so the displayed number always matches precisely where the server-side targeting will actually land. */
	private static final float CAS_CARRIER_DEFAULT_VELOCITY = 197f;
	private static final float CAS_CARRIER_DEFAULT_GRAVITY = 32f;
	/** Matches AbstractVehicleEntity's own identical CAS_CARRIER_RAYCAST_RANGE - see that field's own doc for why RAYCAST mode needs its own range distinct from AS_MISSILE's own much shorter MISSILE_TARGET_SEARCH_RANGE. */
	private static final double CAS_CARRIER_RAYCAST_RANGE = 1000.0;
	/** Matches AbstractVehicleEntity's own identical CAS_CARRIER_COLLISION_MAX_TICKS - see that field's own doc for why COLLISION mode's own simulation needs to keep running well past where BALLISTIC itself would already stop. */
	private static final int CAS_CARRIER_COLLISION_MAX_TICKS = 3000;

	/** Dispatches to whichever calculation this weapon's own mode selects, mirroring AbstractVehicleEntity's own tudursvehiclemod$computeCasCarrierTargetPoint() dispatcher exactly, so the displayed number always matches precisely where the server-side targeting will actually land regardless of mode. Returns -1.0 (this whole feature's own existing "nothing meaningful to show" sentinel - see HudVariables' own doc) if the underlying calculation itself has nothing meaningful to report. */
	public static double tudursvehiclemod$computeCasCarrierDistance(PlayerEntity player, WeaponDefinition selectedWeapon) {
		return switch (selectedWeapon.casTargetMode()) {
			case RAYCAST -> tudursvehiclemod$computeCasCarrierRaycastDistance(player);
			case COLLISION -> tudursvehiclemod$computeCasCarrierCollisionDistance(player, selectedWeapon);
			case BALLISTIC -> tudursvehiclemod$computeCasCarrierBallisticDistance(player, selectedWeapon);
		};
	}

	private static double tudursvehiclemod$computeCasCarrierBallisticDistance(PlayerEntity player, WeaponDefinition selectedWeapon) {
		float velocity = selectedWeapon.velocity() > 0f ? selectedWeapon.velocity() : CAS_CARRIER_DEFAULT_VELOCITY;
		float gravity = selectedWeapon.gravity() > 0f ? selectedWeapon.gravity() : CAS_CARRIER_DEFAULT_GRAVITY;
		Vec3d aimDir = player.getRotationVec(1.0f);
		double horizontalAimLength = Math.sqrt(aimDir.x * aimDir.x + aimDir.z * aimDir.z);
		double verticalSpeed = aimDir.y * velocity;
		double distance = com.example.tudursvehiclemod.entity.projectile.Ballistics.levelReturnDistance(horizontalAimLength * velocity, verticalSpeed, gravity,
				com.example.tudursvehiclemod.entity.projectile.Ballistics.levelReturnTickCap(verticalSpeed, gravity));
		if (distance >= 0.0) {
			return distance;
		}
		// Never rises above the launch height: the server takes the point where the line of sight meets the water or ground (AbstractVehicleEntity's tudursvehiclemod$computeBallisticTargetPoint()), or the range itself out at the horizon - shown the same way here.
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return -1.0;
		}
		Vec3d start = player.getEyePos();
		Vec3d sightEnd = start.add(aimDir.multiply(CAS_CARRIER_RAYCAST_RANGE));
		BlockHitResult sightHit = client.world.raycast(new RaycastContext(start, sightEnd,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.SOURCE_ONLY, player));
		if (sightHit.getType() == HitResult.Type.BLOCK) {
			return start.distanceTo(sightHit.getPos());
		}
		return CAS_CARRIER_RAYCAST_RANGE;
	}

	/** CasTargetMode.RAYCAST's own HUD distance - mirrors AbstractVehicleEntity's own tudursvehiclemod$computeCasCarrierRaycastPoint() exactly (same range, same "nothing hit -> the range itself" fallback). */
	private static double tudursvehiclemod$computeCasCarrierRaycastDistance(PlayerEntity player) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return -1.0;
		}
		Vec3d start = player.getEyePos();
		Vec3d viewDir = player.getRotationVec(1.0f);
		Vec3d end = start.add(viewDir.multiply(CAS_CARRIER_RAYCAST_RANGE));
		RaycastContext context = new RaycastContext(start, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
		BlockHitResult hit = client.world.raycast(context);
		Vec3d hitPos = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
		return start.distanceTo(hitPos);
	}

	/** CasTargetMode.COLLISION's own HUD distance - mirrors AbstractVehicleEntity's own tudursvehiclemod$computeCasCarrierCollisionPoint() exactly (same drag-aware trajectory physics, same per-step block-collision raycast, same generous tick cap allowing the simulation to keep running well past the same-height point BALLISTIC itself would already stop at). */
	private static double tudursvehiclemod$computeCasCarrierCollisionDistance(PlayerEntity player, WeaponDefinition selectedWeapon) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return -1.0;
		}
		float velocity = selectedWeapon.velocity() > 0f ? selectedWeapon.velocity() : CAS_CARRIER_DEFAULT_VELOCITY;
		float gravity = selectedWeapon.gravity() > 0f ? selectedWeapon.gravity() : CAS_CARRIER_DEFAULT_GRAVITY;
		Vec3d start = player.getEyePos();
		Vec3d aimDir = player.getRotationVec(1.0f);
		if (gravity <= 0f) {
			Vec3d fallbackEnd = start.add(aimDir.multiply(CAS_CARRIER_DEFAULT_VELOCITY * 20.0));
			RaycastContext fallbackContext = new RaycastContext(start, fallbackEnd,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
			BlockHitResult fallbackHit = client.world.raycast(fallbackContext);
			Vec3d fallbackPos = fallbackHit.getType() == HitResult.Type.MISS ? fallbackEnd : fallbackHit.getPos();
			return start.distanceTo(fallbackPos);
		}
		Vec3d impact = com.example.tudursvehiclemod.entity.projectile.Ballistics.flyUntilBlocked(start, aimDir.multiply(velocity), gravity, client.world.getBottomY() - 8,
				Double.MAX_VALUE, CAS_CARRIER_COLLISION_MAX_TICKS, (from, to) -> tudursvehiclemod$raycastBlock(client, from, to, player)).pos();
		return start.distanceTo(impact);
	}
}
