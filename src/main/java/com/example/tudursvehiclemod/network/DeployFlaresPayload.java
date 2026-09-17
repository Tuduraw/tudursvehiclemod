package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "deploy this vehicle's own flares/countermeasures now" - disables the guidance of every missile currently homing on this vehicle. Mirrors ToggleSearchLightPayload's own shape exactly (an empty marker payload; the vehicle being acted on is resolved server-side from the sending player's own current vehicle). */
public record DeployFlaresPayload() implements CustomPayload {

	public static final CustomPayload.Id<DeployFlaresPayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "deploy_flares"));

	public static final PacketCodec<RegistryByteBuf, DeployFlaresPayload> CODEC =
			PacketCodec.unit(new DeployFlaresPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
