package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "toggle remote control at the station at this position" - sent when the player presses the "Toggle Remote Control" button inside client.screen.StationMenuScreen (see that class's own doc). Runs the exact same logic block.StationBlock's own right-click used to run directly, before that was replaced with this menu screen. */
public record StationToggleRemoteControlPayload(int x, int y, int z) implements CustomPayload {

	public static final CustomPayload.Id<StationToggleRemoteControlPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "station_toggle_remote_control"));

	public static final PacketCodec<RegistryByteBuf, StationToggleRemoteControlPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, StationToggleRemoteControlPayload::x,
			PacketCodecs.INTEGER, StationToggleRemoteControlPayload::y,
			PacketCodecs.INTEGER, StationToggleRemoteControlPayload::z,
			StationToggleRemoteControlPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
