package com.example.tudursvehiclemod.client;

import com.example.tudursvehiclemod.asset.DefinitionFingerprint;
import com.example.tudursvehiclemod.asset.DummyPilotSkinRegistry;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.VehicleDefinitionReloadListener;
import com.example.tudursvehiclemod.asset.VehicleRegistry;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.network.DefinitionFingerprintPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Vehicle definitions and weapon configs on a client connected to a remote server.
 *
 * <p>Both are loaded by the server's data reload. In singleplayer and on a LAN host the client shares them with the integrated
 * server, but a client connected to a dedicated server (or to someone else's LAN game) never ran that reload, so every vehicle
 * fell back to the invisible 1x1 placeholder and the spawn list was empty - unless a singleplayer world had been opened earlier in
 * the same game session, in which case that world's copy was used.
 *
 * <p>The server does not send its definitions. Instead, on joining a remote server the client reads its own copy of the packs
 * (data/&lt;ns&gt;/vehicles and assets/&lt;ns&gt;/weapons in every loaded mod and in tudursvehiclemod-addons/ - the same sources the
 * server uses, minus data packs), lets addons adjust it ({@link ClientDefinitionEvents#LOAD}) and puts it into use. The server then
 * sends a short hash of what it has loaded ({@link DefinitionFingerprintPayload}); if the two differ, {@link ClientDefinitionEvents#MISMATCH}
 * fires and, unless an addon handled it, a warning is shown in chat (details in the log). Playing with the same packs as the server
 * is the player's responsibility - with different ones, what this client shows and predicts can disagree with the server.
 */
public final class ClientDefinitions {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/ClientDefinitions");
	/** How many entry names the chat warning lists per kind; the log always has all of them. */
	private static final int CHAT_NAME_LIMIT = 3;

	@Nullable
	private static DefinitionFingerprintPayload serverFingerprint;
	private static boolean loadedForRemote;

	private ClientDefinitions() {
	}

	/** Called from VehicleModClient. */
	static void register() {
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			serverFingerprint = null;
			loadedForRemote = false;
		}));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(DefinitionFingerprintPayload.ID,
				(payload, context) -> context.client().execute(() -> onServerFingerprint(context.client(), payload)));
	}

	/** True when this client reads its own definitions (connected to a server that isn't this game's integrated one). */
	public static boolean isRemote(MinecraftClient client) {
		return client.getNetworkHandler() != null && !client.isIntegratedServerRunning();
	}

	/** The hash the server last sent, or null (not connected, singleplayer, or the server has no such payload). */
	@Nullable
	public static DefinitionFingerprintPayload getServerFingerprint() {
		return serverFingerprint;
	}

	/**
	 * Reads the packs again, puts them into use and re-checks them against the server - for an addon that has just changed the
	 * files (a pack download, say). Does nothing unless connected to a remote server. Client thread only.
	 */
	public static void reload() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!isRemote(client)) {
			return;
		}
		load(client);
		if (serverFingerprint != null) {
			check(client, serverFingerprint);
		}
	}

	private static void onJoin(MinecraftClient client) {
		if (!isRemote(client)) {
			return;
		}
		load(client);
		if (serverFingerprint != null) {
			// The server's hash got here first.
			check(client, serverFingerprint);
		}
	}

	private static void load(MinecraftClient client) {
		long start = System.nanoTime();
		Map<Identifier, VehicleDefinition> vehicles = new HashMap<>(VehicleDefinitionReloadListener.loadWithoutResourceManager());
		WeaponStatsLoader.reload();
		Map<String, WeaponStats> weapons = new HashMap<>(WeaponStatsLoader.getAll());
		ClientDefinitionEvents.LOAD.invoker().onLoad(new ClientDefinitionEvents.LoadContext(vehicles, weapons, client.getCurrentServerEntry()));
		WeaponStatsLoader.setAll(weapons);
		VehicleRegistry.setAll(vehicles);
		DummyPilotSkinRegistry.reload();
		// Any vehicle that already resolved a definition (the placeholder, or a previous world's) looks it up again.
		if (client.world != null) {
			for (Entity entity : client.world.getEntities()) {
				if (entity instanceof AbstractVehicleEntity vehicle) {
					vehicle.tudursvehiclemod$refreshDefinition();
				}
			}
		}
		loadedForRemote = true;
		LOGGER.info("Loaded {} vehicle definition(s) and {} weapon config(s) from this client's own packs for a remote server ({} ms)",
				vehicles.size(), weapons.size(), (System.nanoTime() - start) / 1_000_000);
	}

	private static void onServerFingerprint(MinecraftClient client, DefinitionFingerprintPayload payload) {
		serverFingerprint = payload;
		// Singleplayer / LAN host: the client uses the integrated server's own definitions, so there is nothing to compare.
		if (isRemote(client) && loadedForRemote) {
			check(client, payload);
		}
	}

	private static void check(MinecraftClient client, DefinitionFingerprintPayload server) {
		Map<String, Long> ourVehicles = DefinitionFingerprint.currentVehicles();
		Map<String, Long> ourWeapons = DefinitionFingerprint.currentWeapons();
		ClientDefinitionEvents.MismatchReport report = new ClientDefinitionEvents.MismatchReport(
				missing(server.vehicles(), ourVehicles), different(server.vehicles(), ourVehicles), missing(ourVehicles, server.vehicles()),
				missing(server.weapons(), ourWeapons), different(server.weapons(), ourWeapons), missing(ourWeapons, server.weapons()),
				client.getCurrentServerEntry());
		if (!report.vehiclesExtra().isEmpty() || !report.weaponsExtra().isEmpty()) {
			LOGGER.info("Only on this client (not used by the server): vehicles {}, weapons {}", report.vehiclesExtra(), report.weaponsExtra());
		}
		if (!report.affectsPlay()) {
			LOGGER.info("Vehicle definitions and weapon configs match the server ({} vehicles, {} weapons)",
					server.vehicles().size(), server.weapons().size());
			return;
		}
		LOGGER.warn("Vehicle definitions / weapon configs differ from the server. Vehicles missing here: {}, different: {}. Weapons missing here: {}, different: {}",
				report.vehiclesMissing(), report.vehiclesDifferent(), report.weaponsMissing(), report.weaponsDifferent());
		if (ClientDefinitionEvents.MISMATCH.invoker().onMismatch(report)) {
			return;
		}
		if (client.player != null) {
			client.player.sendMessage(Text.translatable("message.tudursvehiclemod.definition_mismatch",
					report.vehiclesMissing().size() + report.vehiclesDifferent().size(),
					report.weaponsMissing().size() + report.weaponsDifferent().size()).formatted(Formatting.YELLOW), false);
			Set<String> names = new TreeSet<>();
			names.addAll(report.vehiclesMissing());
			names.addAll(report.vehiclesDifferent());
			names.addAll(report.weaponsMissing());
			names.addAll(report.weaponsDifferent());
			String shown = names.stream().limit(CHAT_NAME_LIMIT).collect(Collectors.joining(", "))
					+ (names.size() > CHAT_NAME_LIMIT ? ", ..." : "");
			client.player.sendMessage(Text.translatable("message.tudursvehiclemod.definition_mismatch_detail", shown).formatted(Formatting.GRAY), false);
		}
	}

	/** Keys in a that b doesn't have. */
	private static Set<String> missing(Map<String, Long> a, Map<String, Long> b) {
		Set<String> out = new TreeSet<>(a.keySet());
		out.removeAll(b.keySet());
		return out;
	}

	/** Keys in both whose hashes differ. */
	private static Set<String> different(Map<String, Long> server, Map<String, Long> ours) {
		Set<String> out = new TreeSet<>();
		server.forEach((key, hash) -> {
			Long mine = ours.get(key);
			if (mine != null && !mine.equals(hash)) {
				out.add(key);
			}
		});
		return out;
	}
}
