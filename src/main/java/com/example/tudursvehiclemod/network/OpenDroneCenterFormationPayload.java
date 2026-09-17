package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "open the formation wingman-slot UI for the Drone Center at this position" - sent from a button inside client.screen.DroneCenterConfigScreen (see that class's own doc), for the Drone Center formation-flying feature. Separate from OpenBlockSlotsPayload (which always opens DroneCenterBlockEntity's own default 2-slot menu, since that block entity IS its own NamedScreenHandlerFactory) - this instead opens screen.DroneCenterFormationScreenHandler specifically, via a dedicated wrapper factory constructed at the handler site. */
public record OpenDroneCenterFormationPayload(int x, int y, int z) implements CustomPayload {

	public static final CustomPayload.Id<OpenDroneCenterFormationPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "open_drone_center_formation"));

	public static final PacketCodec<RegistryByteBuf, OpenDroneCenterFormationPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, OpenDroneCenterFormationPayload::x,
			PacketCodecs.INTEGER, OpenDroneCenterFormationPayload::y,
			PacketCodecs.INTEGER, OpenDroneCenterFormationPayload::z,
			OpenDroneCenterFormationPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
