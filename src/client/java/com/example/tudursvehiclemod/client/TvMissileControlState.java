package com.example.tudursvehiclemod.client;

/** Per Readme_Weapon.txt's own TVMissile doc: tracks whether (and which
 * entity) the LOCAL player is currently steering a TV-guided missile
 * directly - set by network.TvMissileControlStartPayload's own client
 * handler, cleared by TvMissileControlEndPayload's own (see
 * client.mixin.CameraMixin's own doc for where controlledEntityId
 * actually drives the camera override, and
 * client.mixin.PlayerLookRateMixin's own doc for where mouse input gets
 * redirected into accumulatedYawDelta/accumulatedPitchDelta instead of
 * whatever vehicle the player happens to be riding). */
public final class TvMissileControlState {

	/** Entity ID of the missile currently being controlled, or null if not controlling anything right now. */
	public static Integer controlledEntityId;
	public static float accumulatedYawDelta;
	public static float accumulatedPitchDelta;

	private TvMissileControlState() {
	}
}
