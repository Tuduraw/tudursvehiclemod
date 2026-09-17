package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "fire the weapon mounted at this index on the vehicle I'm currently riding". */
public record FireWeaponPayload(int weaponIndex) implements CustomPayload {

	public static final CustomPayload.Id<FireWeaponPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "fire_weapon"));

	public static final PacketCodec<RegistryByteBuf, FireWeaponPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, FireWeaponPayload::weaponIndex,
			FireWeaponPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
