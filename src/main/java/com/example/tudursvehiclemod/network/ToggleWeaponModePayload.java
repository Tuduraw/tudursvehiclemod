package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "toggle the currently-selected weapon's own mode (see Readme_Weapon.txt's own ModeNum doc / AbstractVehicleEntity's own tudursvehiclemod$tryToggleWeaponMode() doc)". */
public record ToggleWeaponModePayload() implements CustomPayload {

	public static final CustomPayload.Id<ToggleWeaponModePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "toggle_weapon_mode"));

	public static final PacketCodec<RegistryByteBuf, ToggleWeaponModePayload> CODEC =
			PacketCodec.unit(new ToggleWeaponModePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
