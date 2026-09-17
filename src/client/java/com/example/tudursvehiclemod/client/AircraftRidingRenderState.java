package com.example.tudursvehiclemod.client;

/** Implemented by LivingEntityRenderStateMixin (added to every LivingEntityRenderState instance, covering players and any other living entity) so.. */
public interface AircraftRidingRenderState {
	boolean tudursvehiclemod$isRidingAircraft();

	void tudursvehiclemod$setRidingAircraft(boolean value);

	float tudursvehiclemod$getAircraftYaw();

	void tudursvehiclemod$setAircraftYaw(float value);

	float tudursvehiclemod$getAircraftPitch();

	void tudursvehiclemod$setAircraftPitch(float value);

	float tudursvehiclemod$getAircraftRoll();

	void tudursvehiclemod$setAircraftRoll(float value);

	/** MC Heli's own HideEntity - see VehicleExtras' own hideEntity doc. Applies to any vehicle type (not just aircraft), unlike the "Aircraft"-named fields above. */
	boolean tudursvehiclemod$isRiddenEntityHidden();

	void tudursvehiclemod$setRiddenEntityHidden(boolean value);

	/** MC Heli's own EntityWidth/EntityHeight - see VehicleExtras' own entityWidth/entityHeight doc. 1.0 = unscaled. */
	float tudursvehiclemod$getRiddenEntityScaleWidth();

	void tudursvehiclemod$setRiddenEntityScaleWidth(float value);

	float tudursvehiclemod$getRiddenEntityScaleHeight();

	void tudursvehiclemod$setRiddenEntityScaleHeight(float value);
}
