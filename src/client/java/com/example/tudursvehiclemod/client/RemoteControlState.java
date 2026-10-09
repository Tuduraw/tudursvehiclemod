package com.example.tudursvehiclemod.client;

/** Tracks whether (and which entity) the LOCAL player is currently remote-controlling a UAV vehicle through a bound block.StationBlockEntity - set by network.RemoteControlStartPayload's own client handler, cleared by RemoteControlEndPayload's own (and on disconnect/world change - see VehicleModClient.tudursvehiclemod$resetSessionState()). The player rides the vehicle for real while controlling it, so the vehicle itself is always a loaded client-side entity; see client.mixin.CameraMixin's own doc for where controlledEntityId drives the camera. */
public final class RemoteControlState {

	/** Entity ID of the vehicle currently being remote-controlled, or null if not controlling anything right now. */
	public static Integer controlledEntityId;

	private RemoteControlState() {
	}
}
