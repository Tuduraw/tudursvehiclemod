package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "deploy parachute" - sent when the player presses Space while a parachute is equipped in the chest slot and this player is currently in a "deployable" state (falling, not already deployed, not gliding via elytra - see client.VehicleModClient's own tudursvehiclemod$isParachuteDeployable() doc for the exact client-side check, mirroring elytra's own "can this even be used right now" conditions per that direct request). No payload data needed - the server re-checks the same conditions itself (never trusts the client alone) and, if still valid, deploys THIS player's own parachute; the server is also solely responsible for auto-retracting on landing, so this payload only ever needs to request DEPLOYMENT, never retraction. */
public record DeployParachutePayload() implements CustomPayload {

	public static final CustomPayload.Id<DeployParachutePayload> ID =
			new CustomPayload.Id<>(Identifier.of(VehicleMod.MOD_ID, "deploy_parachute"));

	public static final PacketCodec<RegistryByteBuf, DeployParachutePayload> CODEC =
			PacketCodec.unit(new DeployParachutePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
