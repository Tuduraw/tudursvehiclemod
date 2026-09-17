package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "my own (locally-computed, vehicle-following) position is currently this". well-reasoned diagnosis (confirmed by extensive position-trace logging: the CLIENT's own local passenger position correctly tracks a ridden Ship/Submarine the entire time, while the SERVER's own tracked position for that same player permanently freezes ~4 seconds after boarding, never resuming) that vanilla's own passenger-position-sync mechanism (which relies on the client sending VehicleMoveC2SPacket, and treats a player-ridden custom vehicle as "client-authoritative" for that purpose) isn't reliably reaching/being accepted by the server for a SLOW-moving vehicle specifically (Ship/Submarine here typically move far slower than this mod's own Car/Aircraft, which don't exhibit this bug at all) - this dedicated, explicit, always-sent payload sidesteps that entirely, rather than depending on however/whenever vanilla's own mechanism happens to actually fire. */
public record PassengerPositionSyncPayload(double x, double y, double z) implements CustomPayload {

	public static final CustomPayload.Id<PassengerPositionSyncPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "passenger_position_sync"));

	public static final PacketCodec<RegistryByteBuf, PassengerPositionSyncPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.DOUBLE, PassengerPositionSyncPayload::x,
			PacketCodecs.DOUBLE, PassengerPositionSyncPayload::y,
			PacketCodecs.DOUBLE, PassengerPositionSyncPayload::z,
			PassengerPositionSyncPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
