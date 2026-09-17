package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "please send me the current route for the Drone Center at (x,y,z) so I can open its detailed settings screen" - sent when the player presses "Detailed Settings.." on the simple config screen. The server responds with DroneWaypointsOpenPayload (see that payload's own doc) once it has looked up the block's own current route - this round-trip exists because the simple config screen's own client-side state doesn't otherwise know the route (only the block entity itself does). */
public record DroneWaypointsRequestPayload(int x, int y, int z) implements CustomPayload {

	public static final CustomPayload.Id<DroneWaypointsRequestPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_waypoints_request"));

	public static final PacketCodec<RegistryByteBuf, DroneWaypointsRequestPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DroneWaypointsRequestPayload::x,
			PacketCodecs.VAR_INT, DroneWaypointsRequestPayload::y,
			PacketCodecs.VAR_INT, DroneWaypointsRequestPayload::z,
			DroneWaypointsRequestPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
