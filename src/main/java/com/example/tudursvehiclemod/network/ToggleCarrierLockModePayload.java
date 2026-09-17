package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "toggle lock mode" for a Carrier formation's own lead aircraft - see entity.AbstractVehicleEntity's own CARRIER_LOCK_MODE_ACTIVE doc for what this actually does. Sent when the player presses the dedicated toggle key WITHOUT Alt held (see client.VehicleModClient's own carrierLockToggleKey doc - Alt-held instead sends ReleaseAllCarrierLocksPayload). No payload data needed - the server flips whichever vehicle the player is CURRENTLY riding. */
public record ToggleCarrierLockModePayload() implements CustomPayload {

	public static final CustomPayload.Id<ToggleCarrierLockModePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "toggle_carrier_lock_mode"));

	public static final PacketCodec<RegistryByteBuf, ToggleCarrierLockModePayload> CODEC =
			PacketCodec.unit(new ToggleCarrierLockModePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
