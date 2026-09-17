package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "open your own drone route book editor for whichever hand you just used this item in" - sent when the player right-clicks a held item.DroneRouteBookItem. Only the hand is sent, not the book's own contents - the client already has its own local copy of the held stack (standard item sync), so it reads the route directly from that rather than needing it duplicated over the network. */
public record DroneRouteBookOpenPayload(boolean mainHand) implements CustomPayload {

	public static final CustomPayload.Id<DroneRouteBookOpenPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_route_book_open"));

	public static final PacketCodec<RegistryByteBuf, DroneRouteBookOpenPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, DroneRouteBookOpenPayload::mainHand,
			DroneRouteBookOpenPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
