package com.example.tudursvehiclemod.client;

import com.example.tudursvehiclemod.network.FireSupportStatePayload;
import com.example.tudursvehiclemod.screen.FireSupportScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client side of the Fire Support Center: its screen, and the state updates for it (network.FireSupportStatePayload). Registered from VehicleModClient. */
public final class FireSupportClient {

	private FireSupportClient() {
	}

	public static void register() {
		net.minecraft.client.gui.screen.ingame.HandledScreens.register(
				com.example.tudursvehiclemod.registry.ModScreenHandlers.FIRE_SUPPORT_CENTER,
				com.example.tudursvehiclemod.client.screen.FireSupportScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(FireSupportStatePayload.ID, (payload, context) -> context.client().execute(() -> {
			if (context.client().player != null && context.client().player.currentScreenHandler instanceof FireSupportScreenHandler handler) {
				handler.tudursvehiclemod$applyState(payload.pos(), payload.state());
			}
		}));
	}
}
