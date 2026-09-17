package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "move me to the PREVIOUS available seat on the vehicle I'm riding" - see SwitchSeatPayload's own doc for the (forward) counterpart, and AbstractVehicleEntity's own tudursvehiclemod$trySwitchSeat() doc for the shared implementation both go through. */
public record SwitchSeatPreviousPayload() implements CustomPayload {

	public static final CustomPayload.Id<SwitchSeatPreviousPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "switch_seat_previous"));

	public static final PacketCodec<RegistryByteBuf, SwitchSeatPreviousPayload> CODEC =
			PacketCodec.unit(new SwitchSeatPreviousPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
