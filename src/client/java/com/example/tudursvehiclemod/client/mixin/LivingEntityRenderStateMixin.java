package com.example.tudursvehiclemod.client.mixin;

import com.example.tudursvehiclemod.client.AircraftRidingRenderState;
import com.example.tudursvehiclemod.client.ParachuteSittingSuppressRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** See AircraftRidingRenderState's own doc for why this data lives here. */
@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements AircraftRidingRenderState, ParachuteSittingSuppressRenderState {

	@Unique
	private boolean tudursvehiclemod$sittingPoseSuppressed = false;

	@Override
	public boolean tudursvehiclemod$isSittingPoseSuppressed() {
		return this.tudursvehiclemod$sittingPoseSuppressed;
	}

	@Override
	public void tudursvehiclemod$setSittingPoseSuppressed(boolean value) {
		this.tudursvehiclemod$sittingPoseSuppressed = value;
	}

	@Unique
	private boolean tudursvehiclemod$ridingAircraft = false;
	@Unique
	private float tudursvehiclemod$aircraftYaw = 0f;
	@Unique
	private float tudursvehiclemod$aircraftPitch = 0f;
	@Unique
	private float tudursvehiclemod$aircraftRoll = 0f;
	@Unique
	private boolean tudursvehiclemod$riddenEntityHidden = false;
	@Unique
	private float tudursvehiclemod$riddenEntityScaleWidth = 1f;
	@Unique
	private float tudursvehiclemod$riddenEntityScaleHeight = 1f;

	@Override
	public boolean tudursvehiclemod$isRiddenEntityHidden() {
		return this.tudursvehiclemod$riddenEntityHidden;
	}

	@Override
	public void tudursvehiclemod$setRiddenEntityHidden(boolean value) {
		this.tudursvehiclemod$riddenEntityHidden = value;
	}

	@Override
	public float tudursvehiclemod$getRiddenEntityScaleWidth() {
		return this.tudursvehiclemod$riddenEntityScaleWidth;
	}

	@Override
	public void tudursvehiclemod$setRiddenEntityScaleWidth(float value) {
		this.tudursvehiclemod$riddenEntityScaleWidth = value;
	}

	@Override
	public float tudursvehiclemod$getRiddenEntityScaleHeight() {
		return this.tudursvehiclemod$riddenEntityScaleHeight;
	}

	@Override
	public void tudursvehiclemod$setRiddenEntityScaleHeight(float value) {
		this.tudursvehiclemod$riddenEntityScaleHeight = value;
	}

	@Override
	public boolean tudursvehiclemod$isRidingAircraft() {
		return this.tudursvehiclemod$ridingAircraft;
	}

	@Override
	public void tudursvehiclemod$setRidingAircraft(boolean value) {
		this.tudursvehiclemod$ridingAircraft = value;
	}

	@Override
	public float tudursvehiclemod$getAircraftYaw() {
		return this.tudursvehiclemod$aircraftYaw;
	}

	@Override
	public void tudursvehiclemod$setAircraftYaw(float value) {
		this.tudursvehiclemod$aircraftYaw = value;
	}

	@Override
	public float tudursvehiclemod$getAircraftPitch() {
		return this.tudursvehiclemod$aircraftPitch;
	}

	@Override
	public void tudursvehiclemod$setAircraftPitch(float value) {
		this.tudursvehiclemod$aircraftPitch = value;
	}

	@Override
	public float tudursvehiclemod$getAircraftRoll() {
		return this.tudursvehiclemod$aircraftRoll;
	}

	@Override
	public void tudursvehiclemod$setAircraftRoll(float value) {
		this.tudursvehiclemod$aircraftRoll = value;
	}
}
