package com.example.tudursvehiclemod.client;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.WeaponStats;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Lets an addon mod take part in how the client gets its vehicle definitions and weapon configs when it is connected to a
 * remote server (a dedicated server, or someone else's LAN game). See {@link ClientDefinitions} for the whole flow.
 *
 * <p>The server never sends its definitions. On joining, the client reads its own copy of the addon packs (mod jars and
 * tudursvehiclemod-addons/), fires {@link #LOAD} so an addon can change the result, puts it into use, and then compares a
 * short hash of it against the one the server sends ({@link #MISMATCH} when they differ). Nothing here runs in singleplayer
 * or on a LAN host, where the client shares the integrated server's definitions.
 *
 * <p>A pack-sync addon would typically: download the server's files when {@link #MISMATCH} fires (returning true to suppress
 * the default chat warning), add their location as a source in {@link #LOAD}, then call {@link ClientDefinitions#reload()}
 * to read everything again and re-check against the server.
 *
 * <p>Register from your client entrypoint. Listeners run on the client thread; an exception thrown by one is caught and
 * logged, and the listeners after it still run.
 */
public final class ClientDefinitionEvents {

	private ClientDefinitionEvents() {
	}

	/** What the client is about to put into use. Both maps are mutable: add, replace or remove entries. */
	public record LoadContext(Map<Identifier, VehicleDefinition> vehicles, Map<String, WeaponStats> weapons, @Nullable ServerInfo server) {
	}

	/** Entries that differ between the server and this client. Weapon names are lower case. "Extra" entries exist only on the
	 * client and are harmless on their own (the server never spawns them); "missing" and "different" ones are what make a
	 * vehicle look or behave differently from the server. */
	public record MismatchReport(
			Set<String> vehiclesMissing, Set<String> vehiclesDifferent, Set<String> vehiclesExtra,
			Set<String> weaponsMissing, Set<String> weaponsDifferent, Set<String> weaponsExtra,
			@Nullable ServerInfo server) {

		/** True when something the server uses is missing or different here. */
		public boolean affectsPlay() {
			return !vehiclesMissing.isEmpty() || !vehiclesDifferent.isEmpty() || !weaponsMissing.isEmpty() || !weaponsDifferent.isEmpty();
		}
	}

	@FunctionalInterface
	public interface Load {
		void onLoad(LoadContext context);
	}

	@FunctionalInterface
	public interface Mismatch {
		/** Return true if this listener dealt with the mismatch itself: the default chat warning is then not shown. */
		boolean onMismatch(MismatchReport report);
	}

	/** After the client has read its own packs, before they are put into use. */
	public static final Event<Load> LOAD = EventFactory.createArrayBacked(Load.class, listeners -> context -> {
		for (Load listener : listeners) {
			try {
				listener.onLoad(context);
			} catch (Exception e) {
				VehicleMod.LOGGER.error("ClientDefinitionEvents.LOAD listener {} failed", listener.getClass().getName(), e);
			}
		}
	});

	/** When the server's definitions differ from the client's (only when {@link MismatchReport#affectsPlay()} is true). */
	public static final Event<Mismatch> MISMATCH = EventFactory.createArrayBacked(Mismatch.class, listeners -> report -> {
		boolean handled = false;
		for (Mismatch listener : listeners) {
			try {
				handled |= listener.onMismatch(report);
			} catch (Exception e) {
				VehicleMod.LOGGER.error("ClientDefinitionEvents.MISMATCH listener {} failed", listener.getClass().getName(), e);
			}
		}
		return handled;
	});
}
