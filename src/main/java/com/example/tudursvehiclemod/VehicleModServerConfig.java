package com.example.tudursvehiclemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-side config, separate from client.VehicleModConfig (which is
 * client-only - camera distance, render culling, and so on, none of which
 * exist server-side at all). Currently just the hit-detection threading
 * options below. Written to its own file so the two never collide. */
public final class VehicleModServerConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("tudursvehiclemod-server.json");

	/** When true, this mod's own custom hit
	 * detection (see AbstractVehicleEntity's own
	 * tudursvehiclemod$updateCustomHitDetection() doc) is batched across
	 * EVERY vehicle in the world and run as ONE parallel pass at the end
	 * of each server tick, rather than each vehicle independently doing
	 * its own (already-parallel, but only across its OWN few candidate
	 * projectiles) check inline during its own tick - see
	 * entity.HitDetectionCoordinator's own doc for why that distinction
	 * actually matters for real-world throughput.
	 *
	 * Defaults to FALSE - the existing per-vehicle
	 * approach stays the default, and this is strictly opt-in, so a
	 * server that's running fine today can't be destabilized by a
	 * behavioral change nobody asked for. Turn this on for a world with
	 * many high-poly vehicles where hit detection is measurably the
	 * bottleneck. */
	public boolean parallelHitDetectionAcrossVehicles = false;

	/** How many vehicles must actually need a hit-detection check in a
	 * given tick before the parallel path above is worth using at all -
	 * below this, the coordinator just runs them inline on the calling
	 * thread instead, since the fixed overhead of dispatching to a thread
	 * pool and joining back genuinely outweighs the work itself for one
	 * or two vehicles. Only meaningful when
	 * parallelHitDetectionAcrossVehicles is true. */
	public int parallelHitDetectionMinimumVehicles = 3;

	/** A vehicle that's been destroyed (see
	 * AbstractVehicleEntity's own tudursvehiclemod$isDestroyed() doc)
	 * despawns on its own after destroyedVehicleDespawnSeconds, rather
	 * than sitting in the world as wreckage forever until someone
	 * manually removes it. Deliberately only ever affects a vehicle
	 * that's actually confirmed destroyed - an intact, still-driveable
	 * vehicle never despawns from this, no matter how long it sits
	 * around unused. Defaults to true. */
	public boolean destroyedVehicleDespawnEnabled = true;

	/** How many seconds a destroyed vehicle (see
	 * destroyedVehicleDespawnEnabled's own doc) sits in the world before
	 * despawning on its own. Defaults to 300 (5 minutes). Only
	 * meaningful when destroyedVehicleDespawnEnabled is true. */
	public int destroyedVehicleDespawnSeconds = 300;

	/** A CarrierRunwayPlatformEntity's own dimensions are fixed once, at entity TYPE registration - which happens at mod init, before any vehicle.json (and so any RunwayDefinition's own actual width) has even been parsed yet. This is read at that same early point instead, standing in for "the runway width to size tiles for" - set this to match your own actual runway width(s) if they differ from the default (30.0). Defaults to 30.0. */
	public double carrierRunwayExpectedWidth = 30.0;

	/** Caps the TOTAL number of chunks force-loaded by flying projectiles (see entity.projectile.ProjectileChunkLoadTracker's own doc) across the whole server at once - deliberately separate from every OTHER always-loaded feature (CAS/Carrier aircraft, Drone Center, etc.), which a mapper can already tune indirectly via the relevant weapon/vehicle file's own settings and stay entirely unaffected by this. When exceeded, the OLDEST-spawned projectile(s) still holding a claim have theirs released first (oldest-spawn-order eviction, not oldest-updated) until back within the limit - a released projectile isn't discarded outright, it simply stops being force-loaded, so it may go on to freeze (stop ticking) if it then flies outside every player's own normal render/simulation distance. 0 disables projectile chunk-forcing entirely - no projectile ever force-loads anything, the exact pre-this-feature behavior. Defaults to 300 (a generous but bounded budget - a MachineGun-heavy server firing many projectiles at once won't spiral into an unbounded number of force-loaded chunks). */
	public int projectileForcedChunkLimit = 300;

	/** "/tvm clean -d" (command.TvmCommand - complete removal of every one of this mod's own vehicles, including ones sitting in currently-unloaded chunks, achieved by force-loading and checking every chunk the world has ever actually saved to disk) be speed-limited, since force-loading a chunk that would otherwise sit untouched is real, avoidable server work: the maximum number of chunk candidates command.WorldDataCleanupTask checks per server tick. -1 (the default) means no count-based cap at all - this does NOT mean "the entire scan finishes in one single, uninterrupted tick" any more (that was an earlier bug - see WorldDataCleanupTask's own MAX_TICK_DURATION_NANOS doc for why a wall-clock time budget is ALSO always enforced regardless of this setting, so the game's own tick loop - and this task's own periodic progress reports - keeps advancing throughout a long scan either way). Set to a positive number (e.g. 50) to additionally cap by chunk count on top of that same time budget, trading a longer total run for a gentler per-tick load - progress is reported periodically regardless of this setting (see WorldDataCleanupTask's own doc). */
	public int worldDataCleanupChunksPerTick = -1;

	/** Exposed so VehicleMod's own startup log can state exactly where this file actually lives - the resolved path isn't necessarily obvious. */
	public static Path getConfigPath() {
		return CONFIG_PATH;
	}

	private static VehicleModServerConfig instance;

	/** Loaded once, lazily, on first use - and deliberately NOT reloaded afterwards, so a mid-tick config change can't change threading behavior halfway through a tick's own batch. */
	public static VehicleModServerConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static VehicleModServerConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				VehicleModServerConfig loaded = GSON.fromJson(reader, VehicleModServerConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException | RuntimeException ignored) {
				// Malformed or unreadable - falls through to defaults below.
			}
		}
		VehicleModServerConfig defaults = new VehicleModServerConfig();
		defaults.save();
		return defaults;
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException ignored) {
			// Not being able to write the config is not worth crashing over.
		}
	}
}
