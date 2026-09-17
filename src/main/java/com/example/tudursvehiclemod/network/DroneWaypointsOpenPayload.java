package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "open your own Drone Center detailed (waypoint route) settings screen for the block at (x,y,z), currently configured with this route, this Home Point, and this return via-point" - sent when a player presses "Detailed Settings.." on the simple config screen. The route/Home Point/via-point are all sent as single encoded strings (see block.DroneWaypoint's own tudursvehiclemod$encode()/decode() doc) rather than as proper record fields, since this project's own network payloads elsewhere only use simple scalar codec types (int/float/boolean) - encoding each as one string reuses that same simple approach instead of introducing a new, unverified list-codec pattern. encodedViaPoint may be empty (meaning "no via-point configured"), unlike encodedHomePoint which is always non-empty. */
public record DroneWaypointsOpenPayload(int x, int y, int z, String encodedWaypoints, String encodedHomePoint, String encodedViaPoint) implements CustomPayload {

	public static final CustomPayload.Id<DroneWaypointsOpenPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_waypoints_open"));

	public static final PacketCodec<RegistryByteBuf, DroneWaypointsOpenPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DroneWaypointsOpenPayload::x,
			PacketCodecs.VAR_INT, DroneWaypointsOpenPayload::y,
			PacketCodecs.VAR_INT, DroneWaypointsOpenPayload::z,
			PacketCodecs.STRING, DroneWaypointsOpenPayload::encodedWaypoints,
			PacketCodecs.STRING, DroneWaypointsOpenPayload::encodedHomePoint,
			PacketCodecs.STRING, DroneWaypointsOpenPayload::encodedViaPoint,
			DroneWaypointsOpenPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
