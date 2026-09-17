package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "open your own GROUND route settings screen for the block at (x,y,z), currently configured with this route" - the surface-vehicle counterpart to DroneWaypointsOpenPayload (see that payload's own doc for why the route travels as one encoded string). No Home Point field: that exists purely for the aircraft landing autopilot (see entity.AircraftEntity's own tudursvehiclemod$updateDroneLandingRouteAutopilot() doc), which a surface vehicle has no equivalent of - it simply stops. */
public record GroundWaypointsOpenPayload(int x, int y, int z, String encodedWaypoints) implements CustomPayload {

	public static final CustomPayload.Id<GroundWaypointsOpenPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "ground_waypoints_open"));

	public static final PacketCodec<RegistryByteBuf, GroundWaypointsOpenPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, GroundWaypointsOpenPayload::x,
			PacketCodecs.VAR_INT, GroundWaypointsOpenPayload::y,
			PacketCodecs.VAR_INT, GroundWaypointsOpenPayload::z,
			PacketCodecs.STRING, GroundWaypointsOpenPayload::encodedWaypoints,
			GroundWaypointsOpenPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
