package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "please send me the current GROUND route for the Drone Center at (x,y,z) so I can open its detailed settings screen" - the surface-vehicle counterpart to DroneWaypointsRequestPayload (see that payload's own doc for the same round-trip reasoning). which of the two request payloads the simple config screen actually sends is decided server-side, from the bound vehicle's own type (see block.DroneCenterBlockEntity's own tudursvehiclemod$usesGroundRoute() doc). */
public record GroundWaypointsRequestPayload(int x, int y, int z) implements CustomPayload {

	public static final CustomPayload.Id<GroundWaypointsRequestPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "ground_waypoints_request"));

	public static final PacketCodec<RegistryByteBuf, GroundWaypointsRequestPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, GroundWaypointsRequestPayload::x,
			PacketCodecs.VAR_INT, GroundWaypointsRequestPayload::y,
			PacketCodecs.VAR_INT, GroundWaypointsRequestPayload::z,
			GroundWaypointsRequestPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
