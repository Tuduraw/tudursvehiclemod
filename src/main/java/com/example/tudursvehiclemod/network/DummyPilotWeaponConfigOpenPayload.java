package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/** Server -> client: "open your own dummy pilot WEAPON settings screen for the block at (x,y,z)" - the reply to DummyPilotWeaponConfigRequestPayload.
 *
 * WeaponDisplayNames carries the currently-bound vehicle's own weapon list (each entry's own DisplayName, or empty for a vehicle with no weapons at all/none currently bound - see block.DroneCenterBlockEntity's own tudursvehiclemod$getDummyPilotWeaponDisplayName() doc for why this has to be server-resolved: the vehicle's own definition, and so its own weapon list, is server-side data the client has no independent way to look up). weaponIndex is the currently-selected slot (-1 if none/invalid). attackStartAltitude/attackStopAltitude carry the Drone-Center-configured attack-altitude thresholds (default 200/40, matching CAS/Carrier's own weapon-file defaults). searchRange carries how far (blocks) the dummy pilot looks for a hostile mob to engage (default 64.0, matching entity.DummyPilotEntity's own previous hardcoded value). */
public record DummyPilotWeaponConfigOpenPayload(
		int x, int y, int z, List<String> weaponDisplayNames, int weaponIndex,
		float attackStartAltitude, float attackStopAltitude, float searchRange, float diveTargetYOffset
) implements CustomPayload {

	public static final CustomPayload.Id<DummyPilotWeaponConfigOpenPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "dummy_pilot_weapon_config_open"));

	public static final PacketCodec<RegistryByteBuf, DummyPilotWeaponConfigOpenPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DummyPilotWeaponConfigOpenPayload::x,
			PacketCodecs.VAR_INT, DummyPilotWeaponConfigOpenPayload::y,
			PacketCodecs.VAR_INT, DummyPilotWeaponConfigOpenPayload::z,
			PacketCodecs.STRING.collect(PacketCodecs.toList()), DummyPilotWeaponConfigOpenPayload::weaponDisplayNames,
			PacketCodecs.VAR_INT, DummyPilotWeaponConfigOpenPayload::weaponIndex,
			PacketCodecs.FLOAT, DummyPilotWeaponConfigOpenPayload::attackStartAltitude,
			PacketCodecs.FLOAT, DummyPilotWeaponConfigOpenPayload::attackStopAltitude,
			PacketCodecs.FLOAT, DummyPilotWeaponConfigOpenPayload::searchRange,
			PacketCodecs.FLOAT, DummyPilotWeaponConfigOpenPayload::diveTargetYOffset,
			DummyPilotWeaponConfigOpenPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
