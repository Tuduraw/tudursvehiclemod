package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "update the route book I'm holding in this hand to this route" - sent whenever the player adds/removes/edits/records a waypoint in the drone route book screen. Always sends the full current route, same "resend everything" reasoning as this project's other drone-config payloads. */
/** Client -> server: "update the route book I'm holding in this hand to this route (and this coordinate offset)" - sent whenever the player adds/removes/edits/records a waypoint, or edits the offset, in the drone route book screen. Always sends the full current route, same "resend everything" reasoning as this project's other drone-config payloads. encodedOffset is "x,y,z" (see item.DroneRouteBookItem's own tudursvehiclemod$getOffset()/setOffset() doc) - empty string means no offset (0,0,0). */
public record DroneRouteBookUpdatePayload(boolean mainHand, String encodedWaypoints, String encodedOffset) implements CustomPayload {

	public static final CustomPayload.Id<DroneRouteBookUpdatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_route_book_update"));

	public static final PacketCodec<RegistryByteBuf, DroneRouteBookUpdatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, DroneRouteBookUpdatePayload::mainHand,
			PacketCodecs.STRING, DroneRouteBookUpdatePayload::encodedWaypoints,
			PacketCodecs.STRING, DroneRouteBookUpdatePayload::encodedOffset,
			DroneRouteBookUpdatePayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
