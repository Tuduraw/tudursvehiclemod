package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "open your own station menu screen for the station at this position" - sent whenever a player right-clicks a block.StationBlock (see that class's own doc). and to consolidate the remote-control toggle and the item slots into one reachable screen (client.screen.StationMenuScreen) rather than the remote-control toggle firing immediately on every right-click as before. */
public record StationMenuOpenPayload(int x, int y, int z) implements CustomPayload {

	public static final CustomPayload.Id<StationMenuOpenPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "station_menu_open"));

	public static final PacketCodec<RegistryByteBuf, StationMenuOpenPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, StationMenuOpenPayload::x,
			PacketCodecs.INTEGER, StationMenuOpenPayload::y,
			PacketCodecs.INTEGER, StationMenuOpenPayload::z,
			StationMenuOpenPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
