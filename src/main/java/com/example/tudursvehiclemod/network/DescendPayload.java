package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "my dedicated descend key is currently held/released". */
public record DescendPayload(boolean descending) implements CustomPayload {

	public static final CustomPayload.Id<DescendPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "descend"));

	public static final PacketCodec<RegistryByteBuf, DescendPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, DescendPayload::descending,
			DescendPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
