package com.example.tudursvehiclemod.client.mixin;

import com.example.tudursvehiclemod.client.AircraftOrientationInputState;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Two independent things, both only while riding a vehicle: 1. */
@Mixin(Entity.class)
public abstract class PlayerLookRateMixin {

	/** Approximate; vanilla's own cursorDelta-to-degrees conversion is a more complex, non-linear function of the mouse sensitivity slider that this doesn't attempt.. */
	@Unique
	private static final float DEGREES_PER_RAW_UNIT = 0.06f;

	@Unique
	private float tudursvehiclemod$yawBefore;
	@Unique
	private float tudursvehiclemod$pitchBefore;

	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "changeLookDirection(DD)V", at = @At("HEAD"), argsOnly = true, index = 1)
	private double tudursvehiclemod$applyWeaponCameraPitchSetting(double cursorDeltaY) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof PlayerEntity player)) {
			return cursorDeltaY;
		}
		if (!(com.example.tudursvehiclemod.client.VehicleModClient.tudursvehiclemod$getClientEffectiveVehicle(player) instanceof AbstractVehicleEntity vehicle)) {
			return cursorDeltaY;
		}
		int selectedWeaponIndex = com.example.tudursvehiclemod.client.VehicleModClient.getSelectedWeaponIndex();
		java.util.List<com.example.tudursvehiclemod.asset.WeaponDefinition> weapons = vehicle.getDefinition().weapons();
		if (selectedWeaponIndex < 0 || selectedWeaponIndex >= weapons.size()
				|| !com.example.tudursvehiclemod.client.VehicleModClient.tudursvehiclemod$ownSeatWeaponIndices(player).contains(selectedWeaponIndex)) {
			return cursorDeltaY;
		}
		com.example.tudursvehiclemod.asset.WeaponDefinition selectedWeapon = weapons.get(selectedWeaponIndex);
		// FixCameraPitch: locks view pitch to 0 entirely while this weapon is selected - checked before the speed multiplier, since a locked pitch has no meaningful "speed" to scale.
		if (selectedWeapon.fixCameraPitch()) {
			return 0.0;
		}
		// CameraRotationSpeedPitch: a plain multiplier on the raw pitch delta - 1.0 (unchanged) if this weapon doesn't configure it.
		return cursorDeltaY * selectedWeapon.cameraRotationSpeedPitch();
	}

	@Inject(method = "changeLookDirection(DD)V", at = @At("HEAD"), cancellable = true)
	private void tudursvehiclemod$captureBeforeLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;

		// Per Readme_Weapon.txt's own TVMissile doc: while the LOCAL
		// player is directly steering a TV-guided missile (see
		// client.TvMissileControlState's own doc), ALL mouse input goes
		// exclusively to the missile instead - this player's own
		// yaw/pitch is left completely untouched (the underlying vanilla
		// method is cancelled outright, not just left unused) for the
		// whole duration, so there's zero risk of any stray rotation
		// having crept in by the time control eventually returns to
		// whatever vehicle/view they had before. Checked FIRST, ahead of
		// every other case below, since this overrides any vehicle the
		// player might also currently be riding entirely.
		if (self instanceof PlayerEntity && com.example.tudursvehiclemod.client.TvMissileControlState.controlledEntityId != null) {
			com.example.tudursvehiclemod.client.TvMissileControlState.accumulatedYawDelta += (float) (cursorDeltaX * DEGREES_PER_RAW_UNIT);
			com.example.tudursvehiclemod.client.TvMissileControlState.accumulatedPitchDelta += (float) (cursorDeltaY * DEGREES_PER_RAW_UNIT);
			ci.cancel();
			return;
		}

		this.tudursvehiclemod$yawBefore = self.getYaw();
		this.tudursvehiclemod$pitchBefore = self.getPitch();

		if (self instanceof PlayerEntity player) {
			if (com.example.tudursvehiclemod.client.VehicleModClient.tudursvehiclemod$getClientEffectiveVehicle(player) instanceof AbstractVehicleEntity vehicle) {
				if (AbstractVehicleEntity.tudursvehiclemod$usesAircraftStyleOrientation(vehicle)
						&& !vehicle.tudursvehiclemod$isEffectiveFreeLook(player)) {
					AircraftOrientationInputState.accumulatedYawDelta += (float) (cursorDeltaX * DEGREES_PER_RAW_UNIT);
					AircraftOrientationInputState.accumulatedPitchDelta += (float) (cursorDeltaY * DEGREES_PER_RAW_UNIT);
				}
			} else if (com.example.tudursvehiclemod.client.RemoteControlState.controlledEntityId != null
					&& com.example.tudursvehiclemod.client.RemoteControlState.usesAircraftStyleOrientation) {
				// The vehicle being remote-controlled
				// (see client.RemoteControlState's own doc) very often
				// can't actually be resolved as a real, loaded client-side
				// Entity at all - getClientEffectiveVehicle() above then
				// returns null, so this input was previously silently
				// dropped entirely instead of ever reaching the vehicle.
				// Free-look isn't supported for remote control yet (no
				// real vehicle object to check tudursvehiclemod$isEffectiveFreeLook()
				// against at all) - always accumulates as if not in it.
				AircraftOrientationInputState.accumulatedYawDelta += (float) (cursorDeltaX * DEGREES_PER_RAW_UNIT);
				AircraftOrientationInputState.accumulatedPitchDelta += (float) (cursorDeltaY * DEGREES_PER_RAW_UNIT);
			}
		}
	}

	@Inject(method = "changeLookDirection(DD)V", at = @At("TAIL"))
	private void tudursvehiclemod$adjustAfterLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof PlayerEntity player)) {
			return;
		}
		if (!(com.example.tudursvehiclemod.client.VehicleModClient.tudursvehiclemod$getClientEffectiveVehicle(player) instanceof AbstractVehicleEntity vehicle) || vehicle.tudursvehiclemod$isEffectiveFreeLook(player)) {
			return;
		}
		if (AbstractVehicleEntity.tudursvehiclemod$usesAircraftStyleOrientation(vehicle)
				|| (vehicle instanceof com.example.tudursvehiclemod.entity.HelicopterEntity helicopter
						&& helicopter.tudursvehiclemod$isEffectivelyGrounded())) {
			// The CAMERA's own rotation (see
			// client.mixin.CameraMixin's own doc) is completely
			// overridden to directly match this vehicle's own current
			// yaw/pitch/roll while not in free-look - but nothing kept
			// this PLAYER ENTITY's own stored yaw/pitch (the value
			// compass/map/F3 all actually read) in sync with that at
			// all, since vanilla's own changeLookDirection() (not
			// cancelled above) already let it freely follow the raw
			// mouse instead, completely unclamped. The two would then
			// silently drift apart - what's actually shown on screen
			// (the vehicle's own, rate-limited orientation) versus what
			// compass/map/F3 report (the pilot's own raw, unclamped
			// mouse-following angle) - with nothing to tie them
			// together. Syncing this PLAYER'S OWN yaw/pitch to match the
			// vehicle's own CURRENT orientation here instead keeps
			// compass/map/F3 consistent with what's actually on screen.
			self.setYaw(vehicle.getYaw());
			self.setPitch(vehicle.getPitch());
			return;
		}

		float rawYawDelta = MathHelper.wrapDegrees(self.getYaw() - this.tudursvehiclemod$yawBefore);
		float rawPitchDelta = self.getPitch() - this.tudursvehiclemod$pitchBefore;

		// Rotate the (yaw, pitch) delta by the vehicle's current roll.
		float rollRad = (float) Math.toRadians(vehicle.getCurrentRoll());
		float cos = (float) Math.cos(rollRad);
		float sin = (float) Math.sin(rollRad);
		float yawDelta = rawYawDelta * cos - rawPitchDelta * sin;
		float pitchDelta = rawYawDelta * sin + rawPitchDelta * cos;

		float maxYawRate = vehicle.getYawFollowRateDegrees();
		if (maxYawRate >= 0f) {
			yawDelta = MathHelper.clamp(yawDelta, -maxYawRate, maxYawRate);
		}
		float maxPitchRate = vehicle.getPitchFollowRateDegrees();
		if (maxPitchRate >= 0f) {
			pitchDelta = MathHelper.clamp(pitchDelta, -maxPitchRate, maxPitchRate);
		}

		self.setYaw(this.tudursvehiclemod$yawBefore + yawDelta);
		self.setPitch(this.tudursvehiclemod$pitchBefore + pitchDelta);
	}
}
