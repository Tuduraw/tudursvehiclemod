package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "release every wingman's own current target lock" for a Carrier formation's own lead aircraft, returning all of them to ordinary formation-follow immediately - see entity.AbstractVehicleEntity's own tudursvehiclemod$releaseAllCarrierLocks() doc for the full server-side logic. Sent when the player presses the dedicated toggle key WITH Alt held (see client.VehicleModClient's own carrierLockToggleKey doc - held alone instead sends ToggleCarrierLockModePayload). No payload data needed - the server releases every wingman currently following whichever vehicle the player is CURRENTLY riding. */
public record ReleaseAllCarrierLocksPayload() implements CustomPayload {

	public static final CustomPayload.Id<ReleaseAllCarrierLocksPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "release_all_carrier_locks"));

	public static final PacketCodec<RegistryByteBuf, ReleaseAllCarrierLocksPayload> CODEC =
			PacketCodec.unit(new ReleaseAllCarrierLocksPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
