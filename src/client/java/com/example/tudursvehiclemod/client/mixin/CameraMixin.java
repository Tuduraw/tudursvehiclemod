package com.example.tudursvehiclemod.client.mixin;

import com.example.tudursvehiclemod.client.AircraftCameraZoomState;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.FreeCameraVehicle;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Two things while riding an aircraft/helicopter/submarine (or really any vehicle with a live seat offset): 1. */
@Mixin(Camera.class)
public abstract class CameraMixin {

	/** Camera's own `pos` field has a companion `blockPos` field that vanilla's own setPos(Vec3d) method (this invoker) keeps in sync alongside it. An earlier version of this mixin used a raw @Accessor("pos") field write instead, which bypasses that entirely - leaving blockPos stale at wherever vanilla's own un-offset camera position was, diverging from the seat-offset-adjusted `pos` this mixin actually wants to show. Used everywhere in this file instead of that raw accessor, so both fields stay consistent. */
	@Invoker("setPos")
	public abstract void tudursvehiclemod$setPos(Vec3d pos);

	@Inject(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("TAIL"))
	private void tudursvehiclemod$applyVehicleRollAndPos(World area, Entity focusedEntity, boolean thirdPerson,
													boolean inverseView, float tickProgress, CallbackInfo ci) {
		if (!(focusedEntity instanceof PlayerEntity player)) {
			return;
		}

		Camera self = (Camera) (Object) this;

		// Per Readme_Weapon.txt's own TVMissile doc: while the LOCAL
		// player is directly steering a TV-guided missile (see
		// client.TvMissileControlState's own doc), the camera renders
		// from THAT missile's own current position/orientation instead
		// of anything vehicle-related at all - checked FIRST, ahead of
		// every vehicle-piloting case below, since this overrides
		// whatever vehicle the player might also still actually be
		// seated in. Deliberately only ever moves the CAMERA itself, not
		// this player's own entity position at all (unlike vanilla's own
		// Entity#setCameraEntity(), which would forcibly teleport the
		// player's own entity to match - see
		// entity.projectile.VehicleProjectileEntity's own
		// tudursvehiclemod$setTvControlled() doc for exactly why that's
		// avoided here), so a passenger who's still actually seated in
		// their own vehicle never has their own position disturbed by
		// any of this at all.
		Integer controlledId = com.example.tudursvehiclemod.client.TvMissileControlState.controlledEntityId;
		if (controlledId != null) {
			Entity missile = area.getEntityById(controlledId);
			if (missile instanceof com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity) {
				float missileYaw = missile.getYaw(tickProgress);
				float missilePitch = missile.getPitch(tickProgress);
				Quaternionf fresh = new Quaternionf();
				fresh.rotateY((float) Math.toRadians(180.0 - missileYaw));
				fresh.rotateX((float) Math.toRadians(-missilePitch));
				if (inverseView) {
					fresh.rotateY((float) Math.PI);
				}
				self.getRotation().set(fresh);
				this.tudursvehiclemod$setPos(missile.getLerpedPos(tickProgress));
				return;
			}
			// The controlled entity isn't actually loaded/visible client-side (yet, or anymore) this exact frame - falls through to whatever the camera would otherwise be showing rather than leaving it stuck on a stale position; network.TvMissileControlEndPayload should arrive and clear controlledEntityId properly shortly regardless.
		}

		AbstractVehicleEntity vehicle;
		if (player.getVehicle() instanceof AbstractVehicleEntity mounted) {
			vehicle = mounted;
		} else {
			// While the LOCAL player is
			// remote-controlling a UAV vehicle through a bound
			// block.StationBlockEntity (see client.RemoteControlState's
			// own doc) - as opposed to actually riding one, the ordinary
			// case just above - this vehicle's own PILOT view is shown
			// instead, exactly the same way it would be if they really
			// were aboard it (this whole method's own logic below is
			// entirely unaware of/unaffected by which of the two cases
			// actually got it here). Checked here specifically (client-
			// side state, not entity.AbstractVehicleEntity's own server-
			// only tudursvehiclemod$getEffectiveVehicle(), which reads a
			// map that's only ever actually populated server-side) since
			// this whole method only ever runs client-side anyway.
			Integer remoteControlledId = com.example.tudursvehiclemod.client.RemoteControlState.controlledEntityId;
			if (remoteControlledId != null && area.getEntityById(remoteControlledId) instanceof AbstractVehicleEntity remoteVehicle) {
				vehicle = remoteVehicle;
			} else if (remoteControlledId != null && !Double.isNaN(com.example.tudursvehiclemod.client.RemoteControlState.transformX)) {
				// The controlled vehicle very
				// often can't actually be resolved as a real client-side
				// Entity at all (see network.RemoteControlVehicleTransformPayload's
				// own doc for why) - falls back to this vehicle's own
				// synced transform directly instead of returning/showing
				// nothing at all. Doesn't reuse this whole method's own
				// seat-offset eye-position logic below at all (that needs
				// a real Entity/VehicleDefinition to read from) - just a
				// simple, fixed eye-height approximation instead, which
				// is close enough for aiming/orientation purposes even
				// if not pixel-perfect against the real seat position.
				double eyeX = com.example.tudursvehiclemod.client.RemoteControlState.transformX;
				double eyeY = com.example.tudursvehiclemod.client.RemoteControlState.transformY + 1.2;
				double eyeZ = com.example.tudursvehiclemod.client.RemoteControlState.transformZ;
				float syncedYaw = com.example.tudursvehiclemod.client.RemoteControlState.transformYaw;
				float syncedPitch = com.example.tudursvehiclemod.client.RemoteControlState.transformPitch;
				float syncedRoll = com.example.tudursvehiclemod.client.RemoteControlState.transformRoll;
				Quaternionf fresh = new Quaternionf();
				fresh.rotateY((float) Math.toRadians(180.0 - syncedYaw));
				fresh.rotateX((float) Math.toRadians(-syncedPitch));
				fresh.rotateZ((float) Math.toRadians(-syncedRoll));
				if (inverseView) {
					fresh.rotateY((float) Math.PI);
				}
				self.getRotation().set(fresh);
				this.tudursvehiclemod$setPos(new Vec3d(eyeX, eyeY, eyeZ));
				return;
			} else {
				return;
			}
		}

		if (vehicle instanceof FreeCameraVehicle aircraft) {
			Vec3d eyePos = vehicle.getRotatedEyePos(player, tickProgress);

			if (aircraft.tudursvehiclemod$isEffectiveFreeLook(player)) {
				// Free-look: the pilot's own yaw/pitch (vanilla's own camera
				// rotation) shows through completely normally, but roll is
				// still the vehicle's own - not something the player
				// controls via mouse, so their head should still tilt with
				// the aircraft's own bank regardless of where they're
				// looking (same as it does outside free-look).
				float rollDegrees = aircraft.getRoll(tickProgress);
				if (rollDegrees != 0f) {
					self.getRotation().rotateZ((float) -Math.toRadians(rollDegrees));
				}
				if (thirdPerson) {
					this.tudursvehiclemod$setPos(tudursvehiclemod$pullBackAlongCurrentRotation(
							self, area, player, eyePos));
				} else {
					this.tudursvehiclemod$setPos(eyePos);
				}
				return;
			}

			float yaw = aircraft.getYaw(tickProgress);
			float pitch = aircraft.getPitch(tickProgress);
			float roll = aircraft.getRoll(tickProgress);

			Quaternionf fresh = new Quaternionf();
			fresh.rotateY((float) Math.toRadians(180.0 - yaw));
			fresh.rotateX((float) Math.toRadians(-pitch));
			fresh.rotateZ((float) Math.toRadians(-roll));
			if (inverseView) {
				// Minecraft's own second third-person stage (F5 twice): camera in front, facing back towards the player.
				fresh.rotateY((float) Math.PI);
			}
			self.getRotation().set(fresh);

			// Applied in BOTH first and third person for aircraft (unlike the other vehicles below, which only get it in first person).
			if (thirdPerson) {
				Quaternionf modelRotation = new Quaternionf()
						.rotateY((float) Math.toRadians(-yaw))
						.rotateX((float) Math.toRadians(pitch))
						.rotateZ((float) Math.toRadians(roll));
				// inverseView pulls the camera out in FRONT of the nose instead of behind it, to match the flipped rotation above.
				Vector3f pullDirection = new Vector3f(0, 0, inverseView ? 1 : -1);
				modelRotation.transform(pullDirection);
				this.tudursvehiclemod$setPos(tudursvehiclemod$pullBack(area, player, eyePos, pullDirection));
			} else {
				this.tudursvehiclemod$setPos(eyePos);
			}
			return;
		}

		float rollDegrees = vehicle.getRoll(tickProgress);
		if (rollDegrees != 0f) {
			self.getRotation().rotateZ((float) -Math.toRadians(rollDegrees));
		}

		// Third person was left on vanilla's own
		// default entirely for every vehicle type that doesn't implement
		// FreeCameraVehicle (car/ship/helicopter) - meaning whenever a
		// seat's own camera position (CameraPosition/AddGunnerSeat's own
		// camera fields) differs from the seat's own physical position,
		// only first person actually centered on it; third person still
		// centered on the seat position instead. Reusing the same
		// pull-back-along-current-rotation approach already used for
		// aircraft free-look - it leaves the camera's OWN rotation exactly
		// as vanilla already set it (so scroll-wheel zoom/F5 double-tap
		// keep working normally), just re-centering on the mod's own
		// eyePos instead of the player's raw seat position.
		Vec3d eyePos = vehicle.getRotatedEyePos(player, tickProgress);
		if (thirdPerson) {
			this.tudursvehiclemod$setPos(tudursvehiclemod$pullBackAlongCurrentRotation(self, area, player, eyePos));
		} else {
			this.tudursvehiclemod$setPos(eyePos);
		}
	}

	/** Pulls the camera away from eyePos along pullDirection (a world-space unit vector) by AircraftCameraZoomState's current distance, clamped by a raycast so it.. */
	private static Vec3d tudursvehiclemod$pullBack(World area, PlayerEntity player, Vec3d eyePos, Vector3f pullDirection) {
		double desiredDistance = AircraftCameraZoomState.currentDistance;
		Vec3d desiredPos = new Vec3d(
				eyePos.x + pullDirection.x * desiredDistance,
				eyePos.y + pullDirection.y * desiredDistance,
				eyePos.z + pullDirection.z * desiredDistance);

		double actualDistance = desiredDistance;
		RaycastContext raycastContext = new RaycastContext(eyePos, desiredPos,
				RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, player);
		BlockHitResult hit = area.raycast(raycastContext);
		if (hit.getType() == HitResult.Type.BLOCK) {
			// Pull in slightly further than the exact hit point, so the camera doesn't sit right at (and risk still clipping into) the surface it hit.
			double hitDistance = eyePos.distanceTo(hit.getPos());
			actualDistance = Math.max(0.0, Math.min(desiredDistance, hitDistance - 0.25));
		}

		return new Vec3d(
				eyePos.x + pullDirection.x * actualDistance,
				eyePos.y + pullDirection.y * actualDistance,
				eyePos.z + pullDirection.z * actualDistance);
	}

	/** Free-look's own third-person pull-back: since the rotation is left as whatever vanilla's own camera update already set it to.. */
	private static Vec3d tudursvehiclemod$pullBackAlongCurrentRotation(Camera self, World area, PlayerEntity player,
																   Vec3d eyePos) {
		Vector3f pullDirection = new Vector3f(0, 0, 1);
		self.getRotation().transform(pullDirection);
		return tudursvehiclemod$pullBack(area, player, eyePos, pullDirection);
	}

}
