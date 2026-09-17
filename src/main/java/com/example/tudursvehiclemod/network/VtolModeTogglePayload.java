package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "toggle my VTOL between helicopter and aircraft mode now". */
public record VtolModeTogglePayload() implements CustomPayload {

	public static final CustomPayload.Id<VtolModeTogglePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "vtol_mode_toggle"));

	public static final PacketCodec<RegistryByteBuf, VtolModeTogglePayload> CODEC =
			PacketCodec.unit(new VtolModeTogglePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
