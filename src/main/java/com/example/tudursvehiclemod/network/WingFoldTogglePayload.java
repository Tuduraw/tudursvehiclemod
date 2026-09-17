package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "try to toggle my vehicle's wing fold now" - see
 * AbstractVehicleEntity#tryToggleWingFold()'s own doc for why this can be
 * rejected (a non-variable-sweep wing only folds/unfolds while stationary). */
public record WingFoldTogglePayload() implements CustomPayload {

	public static final CustomPayload.Id<WingFoldTogglePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "wing_fold_toggle"));

	public static final PacketCodec<RegistryByteBuf, WingFoldTogglePayload> CODEC =
			PacketCodec.unit(new WingFoldTogglePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
