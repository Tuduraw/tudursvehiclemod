package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "update the Drone Center at (x,y,z)'s own GROUND route to this" - the surface-vehicle counterpart to DroneWaypointsUpdatePayload (see that payload's own doc). Always sends the full current route, same "resend everything" reasoning. */
public record GroundWaypointsUpdatePayload(int x, int y, int z, String encodedWaypoints) implements CustomPayload {

	public static final CustomPayload.Id<GroundWaypointsUpdatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "ground_waypoints_update"));

	public static final PacketCodec<RegistryByteBuf, GroundWaypointsUpdatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, GroundWaypointsUpdatePayload::x,
			PacketCodecs.VAR_INT, GroundWaypointsUpdatePayload::y,
			PacketCodecs.VAR_INT, GroundWaypointsUpdatePayload::z,
			PacketCodecs.STRING, GroundWaypointsUpdatePayload::encodedWaypoints,
			GroundWaypointsUpdatePayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
