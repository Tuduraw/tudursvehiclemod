package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "toggle my vehicle's hatch/canopy now". */
public record HatchTogglePayload() implements CustomPayload {

	public static final CustomPayload.Id<HatchTogglePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "hatch_toggle"));

	public static final PacketCodec<RegistryByteBuf, HatchTogglePayload> CODEC =
			PacketCodec.unit(new HatchTogglePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
