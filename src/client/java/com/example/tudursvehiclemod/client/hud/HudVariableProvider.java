package com.example.tudursvehiclemod.client.hud;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Map;

/**
 * Lets an addon mod add its own variables to HUD scripts.
 *
 * <p>{@link HudVariables#build} fires {@link #EVENT}'s {@link #provideNumeric} after it has put every built-in
 * numeric variable into its map, and {@link HudVariables#buildStringVariables} fires {@link #provideString}
 * after the built-in string variables. A listener puts its own entries into the map it is handed; every HUD
 * script then sees them like any built-in variable (numeric ones in expressions, string ones through
 * {@code %s} in DrawString). Both run every frame the HUD is drawn, on the client, so keep them cheap.
 *
 * <p>Register from your client entrypoint:
 * <pre>{@code
 * HudVariableProvider.EVENT.register(new HudVariableProvider() {
 *     @Override
 *     public void provideNumeric(MinecraftClient client, PlayerEntity player, AbstractVehicleEntity vehicle, Map<String, Double> vars) {
 *         if (vehicle instanceof MyRobotEntity robot) {
 *             vars.put("myaddon_arm_angle", (double) robot.getArmAngle());
 *         }
 *     }
 * });
 * }</pre>
 *
 * <p>Both methods default to doing nothing, so implement only the one you need.
 *
 * <p><b>Names</b>: a listener may overwrite a built-in variable (speed, hp, ...), which is allowed on purpose
 * so an addon can adjust one, but doing it by accident breaks every HUD that reads it. Prefix your own names
 * (myaddon_xxx). HUD script variable names are matched in lower case, so put lower-case names in.
 *
 * <p><b>Failures</b>: an exception thrown by a listener is caught and logged once per listener; the HUD still
 * draws, with whatever that listener managed to put in before it failed, and every later listener still runs.
 */
public interface HudVariableProvider {

	/** Adds numeric variables. vars already holds every built-in numeric variable. */
	default void provideNumeric(MinecraftClient client, PlayerEntity player, AbstractVehicleEntity vehicle, Map<String, Double> vars) {
	}

	/** Adds string variables (used through %s in DrawString). vars already holds every built-in string variable. */
	default void provideString(MinecraftClient client, PlayerEntity player, AbstractVehicleEntity vehicle, Map<String, String> vars) {
	}

	/**
	 * The event addon mods register with. Each callback is invoked on every registered listener in turn,
	 * each one guarded so a failing listener neither hides the HUD nor stops the listeners after it.
	 */
	Event<HudVariableProvider> EVENT = EventFactory.createArrayBacked(HudVariableProvider.class,
			listeners -> new HudVariableProvider() {
				@Override
				public void provideNumeric(MinecraftClient client, PlayerEntity player, AbstractVehicleEntity vehicle, Map<String, Double> vars) {
					for (HudVariableProvider listener : listeners) {
						try {
							listener.provideNumeric(client, player, vehicle, vars);
						} catch (RuntimeException e) {
							Failures.report(listener, "provideNumeric", e);
						}
					}
				}

				@Override
				public void provideString(MinecraftClient client, PlayerEntity player, AbstractVehicleEntity vehicle, Map<String, String> vars) {
					for (HudVariableProvider listener : listeners) {
						try {
							listener.provideString(client, player, vehicle, vars);
						} catch (RuntimeException e) {
							Failures.report(listener, "provideString", e);
						}
					}
				}
			});

	/** Logs a listener's failure once (per listener and callback): these run every frame, so logging every time would flood the log. */
	final class Failures {
		private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("VehicleMod/HudVariableProvider");
		private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

		private Failures() {
		}

		static void report(HudVariableProvider listener, String callback, RuntimeException e) {
			String key = listener.getClass().getName() + "#" + callback;
			if (REPORTED.add(key)) {
				LOGGER.error("[HudVariableProvider] {} threw in {} - its variables may be missing from the HUD. Further failures from it are not logged.",
						listener.getClass().getName(), callback, e);
			}
		}
	}
}
