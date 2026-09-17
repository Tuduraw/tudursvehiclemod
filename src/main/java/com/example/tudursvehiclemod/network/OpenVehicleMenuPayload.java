package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "open the vehicle menu for whatever I'm currently riding". Sent once per key press (see VehicleModClient's own openVehicleMenuKey). */
public record OpenVehicleMenuPayload() implements CustomPayload {

	public static final CustomPayload.Id<OpenVehicleMenuPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "open_vehicle_menu"));

	public static final PacketCodec<RegistryByteBuf, OpenVehicleMenuPayload> CODEC =
			PacketCodec.unit(new OpenVehicleMenuPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
