package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "update the Drone Center at (x,y,z) to these basic (speed/altitude/radius) settings" - sent whenever the player adjusts a value in client.screen.DroneCenterBasicConfigScreen. the cruise speed/orbit altitude/turn radius rows moved to their own dedicated screen too, mirroring network.DroneCenterFormationConfigUpdatePayload's own separation reasoning - that new screen has no access to active/redstoneControlEnabled/formationConfig at all, so a dedicated, basic-settings-only payload avoids needing to thread all of that unrelated state through just to keep reusing DroneCenterConfigUpdatePayload. */
public record DroneCenterBasicConfigUpdatePayload(int x, int y, int z, float speedFraction, float orbitAltitude, float radiusMultiplier) implements CustomPayload {

	public static final CustomPayload.Id<DroneCenterBasicConfigUpdatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_center_basic_config_update"));

	public static final PacketCodec<RegistryByteBuf, DroneCenterBasicConfigUpdatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DroneCenterBasicConfigUpdatePayload::x,
			PacketCodecs.VAR_INT, DroneCenterBasicConfigUpdatePayload::y,
			PacketCodecs.VAR_INT, DroneCenterBasicConfigUpdatePayload::z,
			PacketCodecs.FLOAT, DroneCenterBasicConfigUpdatePayload::speedFraction,
			PacketCodecs.FLOAT, DroneCenterBasicConfigUpdatePayload::orbitAltitude,
			PacketCodecs.FLOAT, DroneCenterBasicConfigUpdatePayload::radiusMultiplier,
			DroneCenterBasicConfigUpdatePayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
