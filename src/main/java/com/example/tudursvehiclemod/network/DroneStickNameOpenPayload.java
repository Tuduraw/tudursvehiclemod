package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "open your own drone control stick naming screen for this vehicle" - sent to the interacting player specifically, the instant they right-click a vehicle while holding item.DroneControlStickItem (see entity.AbstractVehicleEntity's own interact() doc for where that dispatch happens). Kept as a server round-trip (rather than the client opening the screen directly within its own interact() call) so this common-code entity class never needs to reference the client-only screen class at all - the client-side receiver that actually opens client.screen.DroneStickNameScreen lives entirely in client.VehicleModClient, same convention as this project's other drone-config "open" payloads (DroneWaypointsOpenPayload, DroneRouteBookOpenPayload). vehicleId is sent as its String form, same convention as DroneStickRegisterPayload's own doc explains. */
public record DroneStickNameOpenPayload(boolean mainHand, String vehicleId, String suggestedName) implements CustomPayload {

	public static final CustomPayload.Id<DroneStickNameOpenPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_stick_name_open"));

	public static final PacketCodec<RegistryByteBuf, DroneStickNameOpenPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, DroneStickNameOpenPayload::mainHand,
			PacketCodecs.STRING, DroneStickNameOpenPayload::vehicleId,
			PacketCodecs.STRING, DroneStickNameOpenPayload::suggestedName,
			DroneStickNameOpenPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
