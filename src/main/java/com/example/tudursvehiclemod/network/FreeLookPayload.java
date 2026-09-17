package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "my free-look key is currently held/released". Sent once on change (not every tick). */
public record FreeLookPayload(boolean freeLook) implements CustomPayload {

	public static final CustomPayload.Id<FreeLookPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "free_look"));

	public static final PacketCodec<RegistryByteBuf, FreeLookPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, FreeLookPayload::freeLook,
			FreeLookPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
