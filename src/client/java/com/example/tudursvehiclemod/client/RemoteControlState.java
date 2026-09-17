package com.example.tudursvehiclemod.client;

/** Tracks whether (and which entity)
 * the LOCAL player is currently remote-controlling a UAV vehicle through
 * a bound block.StationBlockEntity - set by
 * network.RemoteControlStartPayload's own client handler, cleared by
 * RemoteControlEndPayload's own (see client.mixin.CameraMixin's own doc
 * for where controlledEntityId actually drives the camera override).
 * Mirrors TvMissileControlState's own exact shape/reasoning. */
public final class RemoteControlState {

	/** Entity ID of the vehicle currently being remote-controlled, or null if not controlling anything right now. */
	public static Integer controlledEntityId;

	/** Input redirection (pitch/yaw/roll from
	 * mouse look and A/D) wasn't reaching the vehicle either - that
	 * redirection logic (client.mixin.PlayerLookRateMixin) needs to know
	 * whether the controlled vehicle actually uses aircraft-style
	 * orientation input at all, but couldn't resolve it (the same
	 * "vehicle isn't actually a real, loaded client-side Entity at all"
	 * problem transformX's own doc describes) - synced directly here
	 * instead, once, at the same time controlledEntityId itself is set. */
	public static boolean usesAircraftStyleOrientation;

	/** The controlled vehicle very often isn't
	 * actually loaded/trackable as a real client-side Entity at all
	 * (it's very possibly far away from wherever the controlling
	 * player's own client actually has chunks loaded, well outside
	 * vanilla's own normal entity-tracking range) - this vehicle's own
	 * current transform, synced directly every tick via
	 * network.RemoteControlVehicleTransformPayload instead of relying on
	 * a real, client-tracked Entity object's own fields at all. NaN
	 * (transformX specifically) means no sync has actually arrived yet -
	 * client.mixin.CameraMixin's own doc has more on how this actually
	 * gets used. */
	public static double transformX = Double.NaN;
	public static double transformY;
	public static double transformZ;
	public static float transformYaw;
	public static float transformPitch;
	public static float transformRoll;

	private RemoteControlState() {
	}
}
