package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/** Server -> client: "open your own dummy pilot settings screen for the block at (x,y,z)" - the reply to DummyPilotConfigRequestPayload.
 *
 * Carries availableSkinIds (rather than letting the client scan for itself) for the reason given in that request payload's own doc: the addon folders that supply extra skins are read server-side, so on a dedicated server the authoritative list is the server's. The client uses this list verbatim to build the selection UI - see client.screen.DummyPilotConfigScreen.
 *
 * nameVisible/name carry the requested nametag settings (visibility toggle + arbitrary custom name) alongside the original enabled/skin fields.
 *
 * This screen's own combat settings (weapon selection, attack altitudes, search range) moved to a dedicated sub-screen (see network.DummyPilotWeaponConfigOpenPayload) reached via its own "Weapon Settings" button, since this screen's own layout was already at its practical display limit - this payload itself went back to carrying only the original look/name fields it had before that combat feature was ever added here. */
public record DummyPilotConfigOpenPayload(
		int x, int y, int z, boolean enabled, String skinId, List<String> availableSkinIds,
		boolean nameVisible, String name
) implements CustomPayload {

	public static final CustomPayload.Id<DummyPilotConfigOpenPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "dummy_pilot_config_open"));

	public static final PacketCodec<RegistryByteBuf, DummyPilotConfigOpenPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DummyPilotConfigOpenPayload::x,
			PacketCodecs.VAR_INT, DummyPilotConfigOpenPayload::y,
			PacketCodecs.VAR_INT, DummyPilotConfigOpenPayload::z,
			PacketCodecs.BOOLEAN, DummyPilotConfigOpenPayload::enabled,
			PacketCodecs.STRING, DummyPilotConfigOpenPayload::skinId,
			PacketCodecs.STRING.collect(PacketCodecs.toList()), DummyPilotConfigOpenPayload::availableSkinIds,
			PacketCodecs.BOOLEAN, DummyPilotConfigOpenPayload::nameVisible,
			PacketCodecs.STRING, DummyPilotConfigOpenPayload::name,
			DummyPilotConfigOpenPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
