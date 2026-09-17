package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "update the Drone Center at (x,y,z) to this formation config" - sent whenever the player adjusts a value in client.screen.DroneCenterFormationConfigScreen. those controls moved to their own dedicated screen, which has no access to that other screen's own speed/orbitAltitude/radiusMultiplier/active/redstoneControlEnabled values at all - a separate, formation-only payload avoids needing to thread all of that unrelated state through just to keep reusing DroneCenterConfigUpdatePayload.
 *
 * formationConfig - same "formationSize,formationType,formationSpacing,formationElementSpacing" compact format DroneCenterConfigOpenPayload/UpdatePayload's own identical field already uses. */
public record DroneCenterFormationConfigUpdatePayload(int x, int y, int z, String formationConfig) implements CustomPayload {

	public static final CustomPayload.Id<DroneCenterFormationConfigUpdatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_center_formation_config_update"));

	public static final PacketCodec<RegistryByteBuf, DroneCenterFormationConfigUpdatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DroneCenterFormationConfigUpdatePayload::x,
			PacketCodecs.VAR_INT, DroneCenterFormationConfigUpdatePayload::y,
			PacketCodecs.VAR_INT, DroneCenterFormationConfigUpdatePayload::z,
			PacketCodecs.STRING, DroneCenterFormationConfigUpdatePayload::formationConfig,
			DroneCenterFormationConfigUpdatePayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
