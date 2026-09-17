package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "apply these dummy pilot settings to the Drone Center at (x,y,z)". Sent whenever the player toggles the option, picks a different skin, toggles the nametag, or edits its custom name, so a change takes effect immediately (see DroneCenterBlockEntity's own tudursvehiclemod$setDummyPilotConfig()) rather than only when the screen closes.
 *
 * skinId is validated server-side against the actual available list rather than trusted - see DummyPilotSkinRegistry's own tudursvehiclemod$resolveOrDefault(). name is likewise length-capped server-side rather than trusted as-is.
 *
 * This payload itself went back to carrying only the original look/name fields it had before that combat feature was ever added here. */
public record DummyPilotConfigUpdatePayload(
		int x, int y, int z, boolean enabled, String skinId, boolean nameVisible, String name
) implements CustomPayload {

	public static final CustomPayload.Id<DummyPilotConfigUpdatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "dummy_pilot_config_update"));

	public static final PacketCodec<RegistryByteBuf, DummyPilotConfigUpdatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DummyPilotConfigUpdatePayload::x,
			PacketCodecs.VAR_INT, DummyPilotConfigUpdatePayload::y,
			PacketCodecs.VAR_INT, DummyPilotConfigUpdatePayload::z,
			PacketCodecs.BOOLEAN, DummyPilotConfigUpdatePayload::enabled,
			PacketCodecs.STRING, DummyPilotConfigUpdatePayload::skinId,
			PacketCodecs.BOOLEAN, DummyPilotConfigUpdatePayload::nameVisible,
			PacketCodecs.STRING, DummyPilotConfigUpdatePayload::name,
			DummyPilotConfigUpdatePayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
