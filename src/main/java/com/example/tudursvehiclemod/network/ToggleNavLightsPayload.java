package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "toggle this vehicle's own nav light(s) on/off" - see AbstractVehicleEntity's own tudursvehiclemod$areNavLightsOn() doc. Mirrors ToggleSearchLightPayload's own shape exactly (an empty marker payload; the vehicle being acted on is resolved server-side from the sending player's own current vehicle, same as every other per-vehicle toggle here). */
public record ToggleNavLightsPayload() implements CustomPayload {

	public static final CustomPayload.Id<ToggleNavLightsPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "toggle_nav_lights"));

	public static final PacketCodec<RegistryByteBuf, ToggleNavLightsPayload> CODEC =
			PacketCodec.unit(new ToggleNavLightsPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
