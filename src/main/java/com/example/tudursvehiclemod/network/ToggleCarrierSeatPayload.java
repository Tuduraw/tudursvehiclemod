package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "switch seats" between a Carrier weapon's own mothership seat and whichever aircraft it most recently launched from that seat - see entity.AbstractVehicleEntity's own tudursvehiclemod$toggleCarrierSeat() doc for the full server-side logic. Sent when the player presses the dedicated Alt+Y combo (see client.VehicleModClient's own carrierSeatSwitchKey doc). No payload data needed - the server determines the correct direction (mothership->aircraft or aircraft->mothership) from whichever vehicle the player is CURRENTLY riding. */
public record ToggleCarrierSeatPayload() implements CustomPayload {

	public static final CustomPayload.Id<ToggleCarrierSeatPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "toggle_carrier_seat"));

	public static final PacketCodec<RegistryByteBuf, ToggleCarrierSeatPayload> CODEC =
			PacketCodec.unit(new ToggleCarrierSeatPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
