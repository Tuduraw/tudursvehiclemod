package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "move me to the next available seat on the vehicle I'm riding" - see AbstractVehicleEntity's own tudursvehiclemod$trySwitchSeat() doc. */
public record SwitchSeatPayload() implements CustomPayload {

	public static final CustomPayload.Id<SwitchSeatPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "switch_seat"));

	public static final PacketCodec<RegistryByteBuf, SwitchSeatPayload> CODEC =
			PacketCodec.unit(new SwitchSeatPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
