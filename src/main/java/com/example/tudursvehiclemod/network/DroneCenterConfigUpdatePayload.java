package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "update the Drone Center at (x,y,z) to these config values" - sent whenever the player adjusts a value or toggles active/inactive in client.screen.DroneCenterConfigScreen. Always sends the full current set of values (not just whichever one changed) - simpler than tracking individual deltas, and this screen's own values change rarely enough (button presses, not continuous dragging) that resending everything each time has no real cost.
 *
 * formationConfig - see DroneCenterConfigOpenPayload's own identical field doc. */
public record DroneCenterConfigUpdatePayload(
		int x, int y, int z, float speedFraction, float orbitAltitude, float radiusMultiplier, boolean active,
		boolean redstoneControlEnabled, String formationConfig
) implements CustomPayload {

	public static final CustomPayload.Id<DroneCenterConfigUpdatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "drone_center_config_update"));

	public static final PacketCodec<RegistryByteBuf, DroneCenterConfigUpdatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DroneCenterConfigUpdatePayload::x,
			PacketCodecs.VAR_INT, DroneCenterConfigUpdatePayload::y,
			PacketCodecs.VAR_INT, DroneCenterConfigUpdatePayload::z,
			PacketCodecs.FLOAT, DroneCenterConfigUpdatePayload::speedFraction,
			PacketCodecs.FLOAT, DroneCenterConfigUpdatePayload::orbitAltitude,
			PacketCodecs.FLOAT, DroneCenterConfigUpdatePayload::radiusMultiplier,
			PacketCodecs.BOOLEAN, DroneCenterConfigUpdatePayload::active,
			PacketCodecs.BOOLEAN, DroneCenterConfigUpdatePayload::redstoneControlEnabled,
			PacketCodecs.STRING, DroneCenterConfigUpdatePayload::formationConfig,
			DroneCenterConfigUpdatePayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
