package com.example.tudursvehiclemod.client.mixin;

import com.example.tudursvehiclemod.client.AircraftRidingRenderState;
import com.example.tudursvehiclemod.entity.FreeCameraVehicle;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.entity.model.EntityModel;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes a player (or any other living entity) riding an aircraft behave like a rigid PART of the aircraft, the same way its seat position already does, rather than swinging freely with vanilla's own physics. Also locks the head's yaw and pitch to face forward while not in free-look. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void tudursvehiclemod$suppressParachuteSittingPose(T entity, S state, float tickProgress, CallbackInfo ci) {
		if ((Object) state instanceof com.example.tudursvehiclemod.client.ParachuteSittingSuppressRenderState suppressState) {
			suppressState.tudursvehiclemod$setSittingPoseSuppressed(
					entity.getVehicle() instanceof com.example.tudursvehiclemod.entity.ParachuteEntity);
		}
	}

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void tudursvehiclemod$captureAircraftTilt(T entity, S state, float tickProgress, CallbackInfo ci) {
		if (!((Object) state instanceof AircraftRidingRenderState arState)) {
			return;
		}
		// This used to require FreeCameraVehicle, which only AircraftEntity and SubmarineEntity implement - so a Car's passengers stayed bolt upright even though the Car itself was already tilting to match the terrain (see CarEntity's own ground-sampling pitch/roll). Widened to every AbstractVehicleEntity, since "ride the body's own attitude" is correct for all of them; the free-look head-locking below still checks FreeCameraVehicle specifically, because THAT genuinely only applies to vehicles with a free-look camera of their own.
		if (entity.getVehicle() instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity aircraft) {
			arState.tudursvehiclemod$setRidingAircraft(true);
			arState.tudursvehiclemod$setAircraftYaw(aircraft.getYaw(tickProgress));
			arState.tudursvehiclemod$setAircraftPitch(aircraft.getPitch(tickProgress));
			arState.tudursvehiclemod$setAircraftRoll(aircraft.getRoll(tickProgress));
			// Head yaw AND pitch (both independent-of-body head rotation
			// fields, unlike the earlier reverted attempt which apparently
			// modified something during setupTransforms/updateRenderState's
			// own vanilla computation rather than a plain field set at TAIL
			// like this) - locks the head to face straight ahead (matching
			// the body) while not in free-look.
			if (aircraft instanceof FreeCameraVehicle && !aircraft.tudursvehiclemod$isEffectiveFreeLook(entity)) {
				state.relativeHeadYaw = 0f;
				state.pitch = 0f;
			}
		} else {
			arState.tudursvehiclemod$setRidingAircraft(false);
		}

		// MC Heli's own HideEntity/EntityWidth/EntityHeight - applies to
		// ANY vehicle type (not just aircraft/FreeCameraVehicle), unlike
		// the tilt-locking above.
		if (entity.getVehicle() instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
			var def = vehicle.getDefinition();
			// A remote-control pilot (see
			// that vehicle's own tudursvehiclemod$isRemoteControlHiddenPilot()
			// doc) is forced hidden regardless of this vehicle's own
			// HideEntity setting - they're a REAL passenger now (for
			// every other system's sake), but should never actually be
			// rendered at all, matching this whole feature's own "no
			// actual boarding shown, at least visually" design
			// requirement.
			arState.tudursvehiclemod$setRiddenEntityHidden(def.hideEntity() || vehicle.tudursvehiclemod$isRemoteControlHiddenPilot(entity));
			arState.tudursvehiclemod$setRiddenEntityScaleWidth(def.entityWidth());
			arState.tudursvehiclemod$setRiddenEntityScaleHeight(def.entityHeight());
		} else {
			arState.tudursvehiclemod$setRiddenEntityHidden(false);
			arState.tudursvehiclemod$setRiddenEntityScaleWidth(1f);
			arState.tudursvehiclemod$setRiddenEntityScaleHeight(1f);
		}
	}

	/** MC Heli's own HideEntity - skips rendering entirely (this passenger stays fully present/interactive, just invisible) rather than only shrinking them to nothing via the scale below, which would still cost a (pointless) draw call. */
	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
			at = @At("HEAD"), cancellable = true)
	private void tudursvehiclemod$hideIfConfigured(S state, MatrixStack matrices,
			net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
			net.minecraft.client.render.state.CameraRenderState cameraState, CallbackInfo ci) {
		if ((Object) state instanceof AircraftRidingRenderState arState && arState.tudursvehiclemod$isRiddenEntityHidden()) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = "setupTransforms(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V",
			at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private float tudursvehiclemod$overrideBodyYaw(float bodyYaw, S state, MatrixStack matrices, float baseHeight) {
		if ((Object) state instanceof AircraftRidingRenderState arState && arState.tudursvehiclemod$isRidingAircraft()) {
			return arState.tudursvehiclemod$getAircraftYaw();
		}
		return bodyYaw;
	}

	@Inject(method = "setupTransforms(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V", at = @At("TAIL"))
	private void tudursvehiclemod$applyAircraftTilt(S state, MatrixStack matrices, float bodyYaw, float baseHeight,
											   CallbackInfo ci) {
		if ((Object) state instanceof AircraftRidingRenderState arState && arState.tudursvehiclemod$isRidingAircraft()) {
			// Both pitch and roll are negated here relative to AircraftEntity's own getPitch()/getRoll().
			matrices.multiply(new Quaternionf()
					.rotateX((float) Math.toRadians(-arState.tudursvehiclemod$getAircraftPitch()))
					.rotateZ((float) Math.toRadians(-arState.tudursvehiclemod$getAircraftRoll())));
		}

		// MC Heli's own EntityWidth/EntityHeight - scales this passenger's
		// own rendered size while mounted on ANY vehicle type configured
		// with this (not just aircraft). Width applies to both the X and Z
		// axes (a rendered entity's own horizontal footprint has no
		// separate "depth" the way a vehicle's own model does), height to
		// Y only - matching Readme_Aircraft.txt's own "幅と高さ" (width and
		// height) framing.
		if ((Object) state instanceof AircraftRidingRenderState arState) {
			float scaleWidth = arState.tudursvehiclemod$getRiddenEntityScaleWidth();
			float scaleHeight = arState.tudursvehiclemod$getRiddenEntityScaleHeight();
			if (scaleWidth != 1f || scaleHeight != 1f) {
				matrices.scale(scaleWidth, scaleHeight, scaleWidth);
			}
		}
	}
}
