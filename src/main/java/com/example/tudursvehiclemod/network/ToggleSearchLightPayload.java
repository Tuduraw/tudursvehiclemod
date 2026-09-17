package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "toggle this vehicle's own search light(s) on/off" - see AbstractVehicleEntity's own tudursvehiclemod$isSearchLightOn() doc. Mirrors ToggleWeaponModePayload's own shape exactly (an empty marker payload; the vehicle being acted on is resolved server-side from the sending player's own current vehicle, same as every other per-vehicle toggle here). */
public record ToggleSearchLightPayload() implements CustomPayload {

	public static final CustomPayload.Id<ToggleSearchLightPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "toggle_search_light"));

	public static final PacketCodec<RegistryByteBuf, ToggleSearchLightPayload> CODEC =
			PacketCodec.unit(new ToggleSearchLightPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
