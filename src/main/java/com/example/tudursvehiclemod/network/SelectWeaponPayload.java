package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "I've selected this weapon index on the vehicle I'm riding" - sent whenever the
 * locally-cycled selection changes, not just on fire, so AddPartWeaponBay-style parts (see
 * TogglePart's own "weapon_bay" trigger) can open for everyone, not just the selecting player's
 * own client. */
public record SelectWeaponPayload(int weaponIndex) implements CustomPayload {

	public static final CustomPayload.Id<SelectWeaponPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "select_weapon"));

	public static final PacketCodec<RegistryByteBuf, SelectWeaponPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SelectWeaponPayload::weaponIndex,
			SelectWeaponPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
