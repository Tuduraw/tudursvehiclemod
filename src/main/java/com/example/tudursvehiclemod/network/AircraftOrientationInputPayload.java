package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: raw (pre-vanilla-clamp) mouse yaw/pitch deltas accumulated since the last tick, while piloting an aircraft. */
public record AircraftOrientationInputPayload(float yawDelta, float pitchDelta) implements CustomPayload {

	public static final CustomPayload.Id<AircraftOrientationInputPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "aircraft_orientation_input"));

	public static final PacketCodec<RegistryByteBuf, AircraftOrientationInputPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, AircraftOrientationInputPayload::yawDelta,
			PacketCodecs.FLOAT, AircraftOrientationInputPayload::pitchDelta,
			AircraftOrientationInputPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
