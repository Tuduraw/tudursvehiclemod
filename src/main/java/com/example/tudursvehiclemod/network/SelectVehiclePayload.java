package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "I picked this vehicle from the selection screen". Which hand is included since the player could be holding a valid tiered spawner in either. */
public record SelectVehiclePayload(Identifier vehicleId, boolean mainHand) implements CustomPayload {

	public static final CustomPayload.Id<SelectVehiclePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "select_vehicle"));

	public static final PacketCodec<RegistryByteBuf, SelectVehiclePayload> CODEC = PacketCodec.tuple(
			Identifier.PACKET_CODEC, SelectVehiclePayload::vehicleId,
			PacketCodecs.BOOLEAN, SelectVehiclePayload::mainHand,
			SelectVehiclePayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
