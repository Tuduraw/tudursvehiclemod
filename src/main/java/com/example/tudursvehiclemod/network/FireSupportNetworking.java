package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.block.FireSupportCenterBlockEntity;
import com.example.tudursvehiclemod.screen.FireSupportScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Fire Support Center networking: the screen's actions in, its state out (see the two payloads' docs). Registered from VehicleMod. */
public final class FireSupportNetworking {

	/** How often (ticks) an open screen is sent the center's state again. */
	private static final int STATE_INTERVAL_TICKS = 10;

	private FireSupportNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(FireSupportActionPayload.ID, FireSupportActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FireSupportStatePayload.ID, FireSupportStatePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(FireSupportActionPayload.ID, (payload, context) -> context.server().execute(() -> {
			ServerPlayerEntity player = context.player();
			if (!ModNetworking.tudursvehiclemod$canReachBlock(player, payload.pos())
					|| !(player.getEntityWorld() instanceof ServerWorld world)
					|| !(world.getBlockEntity(payload.pos()) instanceof FireSupportCenterBlockEntity center)) {
				return;
			}
			center.tudursvehiclemod$handleAction(world, player, payload.action(), payload.a(), payload.b(), payload.c());
			tudursvehiclemod$sendState(player, world, center);
		}));

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(FireSupportNetworking::tudursvehiclemod$pushOpenScreens);
	}

	public static void tudursvehiclemod$sendState(ServerPlayerEntity player, ServerWorld world, FireSupportCenterBlockEntity center) {
		ServerPlayNetworking.send(player, new FireSupportStatePayload(center.getPos(), center.tudursvehiclemod$encodeState(world)));
	}

	private static void tudursvehiclemod$pushOpenScreens(MinecraftServer server) {
		if (server.getTicks() % STATE_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.currentScreenHandler instanceof FireSupportScreenHandler handler && handler.tudursvehiclemod$getBlockPos() != null
					&& player.getEntityWorld() instanceof ServerWorld world
					&& world.getBlockEntity(handler.tudursvehiclemod$getBlockPos()) instanceof FireSupportCenterBlockEntity center) {
				tudursvehiclemod$sendState(player, world, center);
			}
		}
	}
}
