package com.example.tudursvehiclemod;

import com.example.tudursvehiclemod.block.DroneRouteBookWaypoint;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.AircraftEntity;
import com.example.tudursvehiclemod.item.DroneRouteBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A dedicated "recording mode" for while riding a vehicle, toggled via a keybind (see client.VehicleModClient's own droneRecordingToggleKey and network.ToggleDroneRecordingPayload's own doc) - only actually usable while a DroneRouteBookItem is held in the player's own OFFHAND. While active, every 100 ticks (5 seconds) records the player's own currently-ridden vehicle's own position, speed (as a fraction of that vehicle's own max speed), and roll (0 for a non-AircraftEntity vehicle, which has no roll concept at all) as a new waypoint appended to that same offhand book. Automatically stops (with a chat notification) the instant either the offhand book is no longer present, or the player is no longer riding any vehicle at all - not just on a second key press. This lets a player's own actual flight be recorded hands-free and later reproduced by a drone, rather than needing to manually add/edit waypoints one at a time. */
public final class DroneRecordingManager {

	private DroneRecordingManager() {
	}

	/** How often (ticks) a new waypoint is recorded while active - 100 ticks = 5 seconds. */
	private static final int RECORD_INTERVAL_TICKS = 100;

	/** Presence in this map means "currently recording" - the value is how many ticks remain until the next waypoint gets recorded (counts down, resets to RECORD_INTERVAL_TICKS after each record). Keyed by player UUID (not the ServerPlayerEntity instance itself, which doesn't survive a respawn/dimension change the same way a UUID does). */
	private static final Map<UUID, Integer> ticksUntilNextRecord = new HashMap<>();

	/** Called by network.ModNetworking's own ToggleDroneRecordingPayload handler - starts recording if not already, or stops it (with a chat notification either way) if it was. starting requires a DroneRouteBookItem already in the player's own offhand at the moment of toggling - silently does nothing (no chat message, no state change) if that's not the case, since there's nothing meaningful to record into. */
	public static void toggle(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		if (ticksUntilNextRecord.containsKey(id)) {
			ticksUntilNextRecord.remove(id);
			player.sendMessage(Text.translatable("message.tudursvehiclemod.drone_recording.stopped"), false);
			return;
		}
		if (!(player.getOffHandStack().getItem() instanceof DroneRouteBookItem)) {
			return;
		}
		ticksUntilNextRecord.put(id, RECORD_INTERVAL_TICKS);
		player.sendMessage(Text.translatable("message.tudursvehiclemod.drone_recording.started"), false);
	}

	/** Registered once against ServerTickEvents.END_SERVER_TICK (see VehicleMod's own onInitialize() doc) - iterates every currently-recording player each tick, validating/decrementing/recording as needed. A no-op (immediate return) whenever nobody is actually recording, so this costs nothing for a server where the feature is never used at all. */
	public static void onServerTick(MinecraftServer server) {
		if (ticksUntilNextRecord.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Integer>> iterator = ticksUntilNextRecord.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				// Logged off (or otherwise no longer present) - nothing to notify, just stop tracking them.
				iterator.remove();
				continue;
			}
			ItemStack offhand = player.getOffHandStack();
			if (!(offhand.getItem() instanceof DroneRouteBookItem)) {
				iterator.remove();
				player.sendMessage(Text.translatable("message.tudursvehiclemod.drone_recording.stopped"), false);
				continue;
			}
			if (!(AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(player) instanceof AbstractVehicleEntity vehicle)) {
				iterator.remove();
				player.sendMessage(Text.translatable("message.tudursvehiclemod.drone_recording.stopped"), false);
				continue;
			}
			int remaining = entry.getValue() - 1;
			if (remaining <= 0) {
				tudursvehiclemod$recordWaypoint(offhand, vehicle);
				remaining = RECORD_INTERVAL_TICKS;
			}
			entry.setValue(remaining);
		}
	}

	/** Appends one new waypoint to stack's own recorded route, from vehicle's own current position/speed/roll - see this class's own doc for exactly what each field means. */
	private static void tudursvehiclemod$recordWaypoint(ItemStack stack, AbstractVehicleEntity vehicle) {
		int x = MathHelper.floor(vehicle.getX());
		int y = MathHelper.floor(vehicle.getY());
		int z = MathHelper.floor(vehicle.getZ());
		float maxSpeed = vehicle.getDefinition().maxSpeed();
		float speedFraction = maxSpeed > 1.0e-4f
				? MathHelper.clamp((float) vehicle.getVelocity().length() / maxSpeed, 0.05f, 1.0f)
				: 0.05f;
		// A non-AircraftEntity vehicle (car/ship/etc.) has no roll concept at all - recorded as level (0).
		float roll = vehicle instanceof AircraftEntity aircraft ? aircraft.getRoll() : 0f;
		List<DroneRouteBookWaypoint> waypoints = new java.util.ArrayList<>(DroneRouteBookItem.tudursvehiclemod$getWaypoints(stack));
		waypoints.add(new DroneRouteBookWaypoint(x, y, z, speedFraction, roll, 1.0f, 1.0f));
		DroneRouteBookItem.tudursvehiclemod$setWaypoints(stack, waypoints);
	}
}
