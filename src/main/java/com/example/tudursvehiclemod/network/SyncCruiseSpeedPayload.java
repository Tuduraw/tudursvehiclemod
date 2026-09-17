package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: forces the client's own local copy of entityId's own cruiseSpeed/throttle to match these values immediately. cruiseSpeed is a plain field, never automatically synced by vanilla's own entity tracking the way position/velocity are - a Carrier aircraft's own client-side copy never ran its own autonomous-flight tick at all while flying unmanned out of that player's own render distance, so it stayed stale at 0 until normal piloted physics gradually caught it up on its own. Sent once, right after a Carrier seat switch (entity.AbstractVehicleEntity's own tudursvehiclemod$toggleCarrierSeat()) successfully mounts a player into an aircraft, with the server's own already-correct values. */
public record SyncCruiseSpeedPayload(int entityId, float cruiseSpeed, float throttle) implements CustomPayload {

	public static final CustomPayload.Id<SyncCruiseSpeedPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "sync_cruise_speed"));

	public static final PacketCodec<RegistryByteBuf, SyncCruiseSpeedPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SyncCruiseSpeedPayload::entityId,
			PacketCodecs.FLOAT, SyncCruiseSpeedPayload::cruiseSpeed,
			PacketCodecs.FLOAT, SyncCruiseSpeedPayload::throttle,
			SyncCruiseSpeedPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
