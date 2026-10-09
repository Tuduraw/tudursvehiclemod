package com.example.tudursvehiclemod.mixin;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ends a player's remote control before they're saved on logout. PlayerManager.remove() saves the player first and dismounts afterwards, so a remote controller was saved still riding the drone (it travels with the player's save as their vehicle) - logging back in put them on the drone, wherever it was, with no remote control running. Ending it here puts them back at their station first, as ending control normally does. (A server stop saves players through saveAllPlayerData() before it disconnects anyone - VehicleMod's SERVER_STOPPING listener covers that.) */
@Mixin(PlayerManager.class)
public abstract class RemoteControlLogoutMixin {

	@Inject(method = "remove", at = @At("HEAD"))
	private void tudursvehiclemod$endRemoteControlBeforeSave(ServerPlayerEntity player, CallbackInfo ci) {
		AbstractVehicleEntity.tudursvehiclemod$endRemoteControlOf(player);
	}
}
