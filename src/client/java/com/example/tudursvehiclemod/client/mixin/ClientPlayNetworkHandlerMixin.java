package com.example.tudursvehiclemod.client.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Logs every EntitySpawnS2CPacket/EntityPassengersSetS2CPacket/EntityPositionSyncS2CPacket the client actually receives that's relevant to a MobEntity, confirming whether the server-side resync (see entity.AbstractVehicleEntity's own tick() logging) actually reaches the client at all, and with what content - separating "server never sent it"/"server sent it but client discarded/mishandled it" as distinctly diagnosable cases. */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

	@Inject(method = "onEntitySpawn", at = @At("HEAD"))
	private void tudursvehiclemod$logEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
		org.slf4j.LoggerFactory.getLogger("VehicleMod/Entity").info(
				"[ParachuteDebug][CLIENT] onEntitySpawn received: entityId={}, uuid={}, type={}, pos=({}, {}, {})",
				packet.getEntityId(), packet.getUuid(), packet.getEntityType(), packet.getX(), packet.getY(), packet.getZ());
	}

	@Inject(method = "onEntityPassengersSet", at = @At("HEAD"))
	private void tudursvehiclemod$logEntityPassengersSet(EntityPassengersSetS2CPacket packet, CallbackInfo ci) {
		org.slf4j.LoggerFactory.getLogger("VehicleMod/Entity").info(
				"[ParachuteDebug][CLIENT] onEntityPassengersSet received: vehicleEntityId={}, passengerIds={}",
				packet.getEntityId(), java.util.Arrays.toString(packet.getPassengerIds()));
	}
}
