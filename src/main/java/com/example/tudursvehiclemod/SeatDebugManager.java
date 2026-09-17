package com.example.tudursvehiclemod;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Toggled per-player via the "/vehiclemod debugseats" command. While enabled, every SEAT_DEBUG_INTERVAL_TICKS, spawns a colored dust particle at every seat's own current world position (tudursvehiclemod$getSeatWorldPos()) for every vehicle within SEAT_DEBUG_SEARCH_RADIUS of that player - green for an empty seat, red for an occupied one, so both a seat's own exact position AND its current occupancy are visible at a glance. Purely a visual aid; changes nothing about actual seat behavior. */
public final class SeatDebugManager {

	private SeatDebugManager() {
	}

	private static final int SEAT_DEBUG_INTERVAL_TICKS = 10;
	private static final double SEAT_DEBUG_SEARCH_RADIUS = 32.0;
	private static final DustParticleEffect EMPTY_SEAT_COLOR = new DustParticleEffect(0x00FF00, 1.0f);
	private static final DustParticleEffect OCCUPIED_SEAT_COLOR = new DustParticleEffect(0xFF0000, 1.0f);

	/** Presence means "debug display currently enabled" - keyed by player UUID (not the ServerPlayerEntity instance itself, which doesn't survive a respawn/dimension change the same way a UUID does), same convention as DroneRecordingManager's own ticksUntilNextRecord. */
	private static final Set<UUID> enabledPlayers = new HashSet<>();

	/** Called by the "/vehiclemod debugseats" command - toggles the display on/off for the executing player, with a chat confirmation either way. */
	public static void toggle(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		if (enabledPlayers.remove(id)) {
			player.sendMessage(Text.translatable("message.tudursvehiclemod.debug_seats.disabled"), false);
			return;
		}
		enabledPlayers.add(id);
		player.sendMessage(Text.translatable("message.tudursvehiclemod.debug_seats.enabled"), false);
	}

	/** Registered once against ServerTickEvents.END_SERVER_TICK (see VehicleMod's own onInitialize() doc) - a no-op (immediate return) whenever nobody currently has this enabled, so this costs nothing for a server where the feature is never used at all. */
	public static void onServerTick(MinecraftServer server) {
		if (enabledPlayers.isEmpty()) {
			return;
		}
		if (server.getTicks() % SEAT_DEBUG_INTERVAL_TICKS != 0) {
			return;
		}
		for (UUID id : enabledPlayers) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null) {
				continue;
			}
			if (!(player.getEntityWorld() instanceof ServerWorld serverWorld)) {
				continue;
			}
			Box searchBox = player.getBoundingBox().expand(SEAT_DEBUG_SEARCH_RADIUS);
			for (AbstractVehicleEntity vehicle : serverWorld.getEntitiesByClass(AbstractVehicleEntity.class, searchBox, e -> true)) {
				VehicleDefinition def = vehicle.getDefinition();
				for (int i = 0; i < def.seats().size(); i++) {
					Vec3d seatWorldPos = vehicle.tudursvehiclemod$getSeatWorldPos(i);
					DustParticleEffect color = vehicle.tudursvehiclemod$getSeatOccupant(i) != null ? OCCUPIED_SEAT_COLOR : EMPTY_SEAT_COLOR;
					serverWorld.spawnParticles(player, color, true, false,
							seatWorldPos.x, seatWorldPos.y, seatWorldPos.z, 2, 0.02, 0.02, 0.02, 0.0);
				}
			}
		}
	}
}
