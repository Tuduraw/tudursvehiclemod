package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "open the dummy pilot WEAPON settings for the Drone Center at (x,y,z)" - sent when the player presses the new "Weapon Settings" button on client.screen.DummyPilotConfigScreen. The server replies with DummyPilotWeaponConfigOpenPayload.
 *
 * A separate round trip, same reasoning as DummyPilotConfigRequestPayload's own doc - the bound vehicle's own weapon list is server-side data (its own definition), so the client can't populate the weapon selector on its own. */
public record DummyPilotWeaponConfigRequestPayload(int x, int y, int z) implements CustomPayload {

	public static final CustomPayload.Id<DummyPilotWeaponConfigRequestPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "dummy_pilot_weapon_config_request"));

	public static final PacketCodec<RegistryByteBuf, DummyPilotWeaponConfigRequestPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DummyPilotWeaponConfigRequestPayload::x,
			PacketCodecs.VAR_INT, DummyPilotWeaponConfigRequestPayload::y,
			PacketCodecs.VAR_INT, DummyPilotWeaponConfigRequestPayload::z,
			DummyPilotWeaponConfigRequestPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
