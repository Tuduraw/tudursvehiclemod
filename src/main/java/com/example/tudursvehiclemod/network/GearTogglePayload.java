package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "manually flip my vehicle's landing gear now". */
public record GearTogglePayload() implements CustomPayload {

	public static final CustomPayload.Id<GearTogglePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "gear_toggle"));

	public static final PacketCodec<RegistryByteBuf, GearTogglePayload> CODEC =
			PacketCodec.unit(new GearTogglePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
