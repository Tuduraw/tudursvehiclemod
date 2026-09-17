package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "update the Drone Center at (x,y,z)'s own patrol route, Home Point, and return via-point to this" - sent whenever the player adds/removes/edits/records a waypoint, the Home Point, or the return via-point in the detailed settings screen. Always sends everything (not just whichever one changed), same "resend everything" reasoning as DroneCenterConfigUpdatePayload's own doc. encodedViaPoint may be empty (meaning "no via-point configured"), unlike encodedHomePoint which is always non-empty. */
public record DroneWaypointsUpdatePayload(int x, int y, int z, String encodedWaypoints, String encodedHomePoint, String encodedViaPoint) implements CustomPayload {

	public static final CustomPayload.Id<DroneWaypointsUpdatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_waypoints_update"));

	public static final PacketCodec<RegistryByteBuf, DroneWaypointsUpdatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DroneWaypointsUpdatePayload::x,
			PacketCodecs.VAR_INT, DroneWaypointsUpdatePayload::y,
			PacketCodecs.VAR_INT, DroneWaypointsUpdatePayload::z,
			PacketCodecs.STRING, DroneWaypointsUpdatePayload::encodedWaypoints,
			PacketCodecs.STRING, DroneWaypointsUpdatePayload::encodedHomePoint,
			PacketCodecs.STRING, DroneWaypointsUpdatePayload::encodedViaPoint,
			DroneWaypointsUpdatePayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
