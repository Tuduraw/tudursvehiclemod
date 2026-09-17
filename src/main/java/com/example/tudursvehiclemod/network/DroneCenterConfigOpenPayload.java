package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: "open your own Drone Center config screen for the block at (x,y,z), currently configured with these values" - sent when a player right-clicks a bound Drone Center. See client.screen.DroneCenterConfigScreen's own doc for the actual UI this populates.
 *
 * FormationConfig - "formationSize,formationType,formationSpacing,formationElementSpacing" (comma-separated) rather than 4 separate fields - avoids risking PacketCodec.tuple's own 12-field arity limit (8 existing fields + 4 new would land exactly at that boundary). Parsed/built via client.screen.DroneCenterConfigScreen's own tudursvehiclemod$encodeFormationConfig()/-decodeFormationConfig() helpers, shared with DroneCenterConfigUpdatePayload's own identical field. */
public record DroneCenterConfigOpenPayload(
		int x, int y, int z, float speedFraction, float orbitAltitude, float radiusMultiplier, boolean active,
		boolean redstoneControlEnabled, String formationConfig
) implements CustomPayload {

	public static final CustomPayload.Id<DroneCenterConfigOpenPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_center_config_open"));

	public static final PacketCodec<RegistryByteBuf, DroneCenterConfigOpenPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DroneCenterConfigOpenPayload::x,
			PacketCodecs.VAR_INT, DroneCenterConfigOpenPayload::y,
			PacketCodecs.VAR_INT, DroneCenterConfigOpenPayload::z,
			PacketCodecs.FLOAT, DroneCenterConfigOpenPayload::speedFraction,
			PacketCodecs.FLOAT, DroneCenterConfigOpenPayload::orbitAltitude,
			PacketCodecs.FLOAT, DroneCenterConfigOpenPayload::radiusMultiplier,
			PacketCodecs.BOOLEAN, DroneCenterConfigOpenPayload::active,
			PacketCodecs.BOOLEAN, DroneCenterConfigOpenPayload::redstoneControlEnabled,
			PacketCodecs.STRING, DroneCenterConfigOpenPayload::formationConfig,
			DroneCenterConfigOpenPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
