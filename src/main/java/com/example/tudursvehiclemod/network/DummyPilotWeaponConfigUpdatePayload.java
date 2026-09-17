package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "apply these dummy pilot combat settings to the Drone Center at (x,y,z)" - sent from client.screen.DummyPilotWeaponConfigScreen whenever the player cycles the weapon or edits an attack altitude/the search range, so a change takes effect immediately (see DroneCenterBlockEntity's own tudursvehiclemod$setDummyPilotCombatConfig()) rather than only when the screen closes.
 *
 * weaponIndex is re-validated server-side against the actual bound vehicle's own current weapon list (a client's own idea of "how many weapons" could be stale) rather than trusted as-is. */
public record DummyPilotWeaponConfigUpdatePayload(
		int x, int y, int z, int weaponIndex, float attackStartAltitude, float attackStopAltitude, float searchRange, float diveTargetYOffset
) implements CustomPayload {

	public static final CustomPayload.Id<DummyPilotWeaponConfigUpdatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "dummy_pilot_weapon_config_update"));

	public static final PacketCodec<RegistryByteBuf, DummyPilotWeaponConfigUpdatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DummyPilotWeaponConfigUpdatePayload::x,
			PacketCodecs.VAR_INT, DummyPilotWeaponConfigUpdatePayload::y,
			PacketCodecs.VAR_INT, DummyPilotWeaponConfigUpdatePayload::z,
			PacketCodecs.VAR_INT, DummyPilotWeaponConfigUpdatePayload::weaponIndex,
			PacketCodecs.FLOAT, DummyPilotWeaponConfigUpdatePayload::attackStartAltitude,
			PacketCodecs.FLOAT, DummyPilotWeaponConfigUpdatePayload::attackStopAltitude,
			PacketCodecs.FLOAT, DummyPilotWeaponConfigUpdatePayload::searchRange,
			PacketCodecs.FLOAT, DummyPilotWeaponConfigUpdatePayload::diveTargetYOffset,
			DummyPilotWeaponConfigUpdatePayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
