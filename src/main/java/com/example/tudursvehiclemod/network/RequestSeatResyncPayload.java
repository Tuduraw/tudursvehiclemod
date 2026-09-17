package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: sent exactly once by AbstractVehicleEntity's own onSpawnPacket() override, the moment the client actually finishes loading that specific vehicle entity (covers both a fresh world join and an ordinary chunk reload identically, since both trigger a genuine client-side spawn) - asks the server to resend this vehicle's own passenger/seat-assignment data to just this one requesting player, replacing an earlier server-side polling approach that ran unconditionally every tick for every vehicle regardless of whether anyone actually needed it. */
public record RequestSeatResyncPayload(int vehicleEntityId) implements CustomPayload {

	public static final CustomPayload.Id<RequestSeatResyncPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "request_seat_resync"));

	public static final PacketCodec<RegistryByteBuf, RequestSeatResyncPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, RequestSeatResyncPayload::vehicleEntityId,
			RequestSeatResyncPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
