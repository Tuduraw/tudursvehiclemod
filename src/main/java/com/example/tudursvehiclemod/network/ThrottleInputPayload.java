package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "my forward/backward and left/right movement input are currently these values".. */
public record ThrottleInputPayload(float forwardInput, float sidewaysInput) implements CustomPayload {

	public static final CustomPayload.Id<ThrottleInputPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "throttle_input"));

	public static final PacketCodec<RegistryByteBuf, ThrottleInputPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, ThrottleInputPayload::forwardInput,
			PacketCodecs.FLOAT, ThrottleInputPayload::sidewaysInput,
			ThrottleInputPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
