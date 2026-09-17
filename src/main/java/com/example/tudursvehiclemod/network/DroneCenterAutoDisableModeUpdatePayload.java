package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "set this Drone Center's own auto-disable resume mode" - sent when the player presses the toggle button on client.screen.DummyPilotConfigScreen. See DroneCenterAutoDisableModeOpenPayload's own doc for why this is a separate payload rather than an extra field on an existing one. */
public record DroneCenterAutoDisableModeUpdatePayload(int x, int y, int z, boolean autoResume) implements CustomPayload {

	public static final CustomPayload.Id<DroneCenterAutoDisableModeUpdatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_center_auto_disable_mode_update"));

	public static final PacketCodec<RegistryByteBuf, DroneCenterAutoDisableModeUpdatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DroneCenterAutoDisableModeUpdatePayload::x,
			PacketCodecs.VAR_INT, DroneCenterAutoDisableModeUpdatePayload::y,
			PacketCodecs.VAR_INT, DroneCenterAutoDisableModeUpdatePayload::z,
			PacketCodecs.BOOLEAN, DroneCenterAutoDisableModeUpdatePayload::autoResume,
			DroneCenterAutoDisableModeUpdatePayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
