package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "toggle drone route recording mode" - sent when the player presses the dedicated recording-toggle key (see client.VehicleModClient's own droneRecordingToggleKey doc). recording mode is only actually usable while the player has a DroneRouteBookItem in their own OFFHAND (validated server-side, not here) - while active, every 100 ticks (5 seconds) this records the player's own currently-ridden vehicle's own position/speed/roll into that same offhand book (see server.DroneRecordingManager's own doc for the full tick-by-tick logic), automatically stopping if the key is pressed again OR the book leaves the offhand. No payload data needed at all - this is purely a toggle request, all the actual state (who's recording, when the next record is due) lives server-side. */
public record ToggleDroneRecordingPayload() implements CustomPayload {

	public static final CustomPayload.Id<ToggleDroneRecordingPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "toggle_drone_recording"));

	public static final PacketCodec<RegistryByteBuf, ToggleDroneRecordingPayload> CODEC =
			PacketCodec.unit(new ToggleDroneRecordingPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
