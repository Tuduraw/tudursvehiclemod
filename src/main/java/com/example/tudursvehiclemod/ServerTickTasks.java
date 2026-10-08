package com.example.tudursvehiclemod;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Short-lived work that has to run over several server ticks (an explosion's water column/ripple/smoke waves, a smoke weapon's cloud, a TargetingPod mark timing out). Each one used to register its own ServerTickEvents.END_SERVER_TICK listener - but a Fabric event has no way to unregister, so every finished effect stayed in the listener list for good, still called (as a no-op) every tick and still holding its ServerWorld: the server got a little slower with every explosion, and a world that had been left stayed in memory. Instead, they all go into one list here, run from the single listener VehicleMod registers, and are dropped the moment they finish.
 *
 * Server thread only. A task scheduled while the list is being run (from inside another task) starts on the next tick; one scheduled during the tick (an entity's own tick, say) runs at the end of that same tick, exactly as the old per-effect listener did. Cleared when the server stops. */
public final class ServerTickTasks {
	/** One tick of a scheduled task's work. Returns true once it's finished, which removes it. */
	@FunctionalInterface
	public interface Task {
		boolean tick(MinecraftServer server);
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static final List<Task> ADDED = new ArrayList<>();

	private ServerTickTasks() {
	}

	public static void schedule(Task task) {
		ADDED.add(task);
	}

	/** Called once per server tick, from VehicleMod's END_SERVER_TICK listener. */
	public static void onEndServerTick(MinecraftServer server) {
		if (!ADDED.isEmpty()) {
			TASKS.addAll(ADDED);
			ADDED.clear();
		}
		if (TASKS.isEmpty()) {
			return;
		}
		TASKS.removeIf(task -> {
			try {
				return task.tick(server);
			} catch (RuntimeException e) {
				VehicleMod.LOGGER.error("A scheduled tick task failed and was dropped", e);
				return true;
			}
		});
	}

	public static void clear() {
		TASKS.clear();
		ADDED.clear();
	}
}
