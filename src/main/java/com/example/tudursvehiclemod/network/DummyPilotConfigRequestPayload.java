package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "open the dummy pilot settings for the Drone Center at (x,y,z)" - sent when the player presses that button on the Drone Center config screen. The server replies with DummyPilotConfigOpenPayload.
 *
 * A round trip rather than the client just opening the screen directly (the way a purely client-side sub-screen could) because the available skin list is a SERVER-side scan (see DummyPilotSkinRegistry's own doc - addon folders live on the server too, and on a dedicated server the client may not have the same ones), so the client can't populate the selection UI on its own. */
public record DummyPilotConfigRequestPayload(int x, int y, int z) implements CustomPayload {

	public static final CustomPayload.Id<DummyPilotConfigRequestPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "dummy_pilot_config_request"));

	public static final PacketCodec<RegistryByteBuf, DummyPilotConfigRequestPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DummyPilotConfigRequestPayload::x,
			PacketCodecs.VAR_INT, DummyPilotConfigRequestPayload::y,
			PacketCodecs.VAR_INT, DummyPilotConfigRequestPayload::z,
			DummyPilotConfigRequestPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
