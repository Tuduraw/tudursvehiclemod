package com.example.tudursvehiclemod.command;

import com.example.tudursvehiclemod.VehicleModServerConfig;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The presence/absence of "--all" now purely controls how each found vehicle is handled (Mode.RESET vs Mode.DISCARD below), not which chunks get scanned at all (an earlier design had "-d" alone scan only loaded chunks, which this request explicitly corrected): the multi-tick task that discovers every chunk this world's own current dimension has EVER actually saved to disk (by listing its own region files directly, rather than force-loading the entire theoretical world border) and force-loads/checks each one in turn, spread across as many server ticks as VehicleModServerConfig's own worldDataCleanupChunksPerTick requires.
 *
 * <p>Each region file covers a fixed 32x32 grid of chunks, so every region found contributes up to 1024 candidate chunk positions - not every one of those 1024 necessarily holds actual saved data (a region can be sparse). An earlier "verify existence via getChunk(x, z, ChunkStatus.FULL, false) before force-loading" pre-check silently skipped every genuinely-unloaded candidate (that call is understood to only recognize a chunk already resident in memory, not saved data on disk), this pre-check was removed entirely - every candidate is force-loaded directly via the simple getChunk(x, z) overload instead, accepting the small, bounded risk of triggering terrain generation for a genuinely-sparse position (each candidate already comes from a REAL, existing region file on disk rather than the theoretical world border, so this risk only ever applies to the comparatively rare sparse-within-region case).
 *
 * <p>processes up to VehicleModServerConfig's own worldDataCleanupChunksPerTick candidates per server tick (its own default, -1, means no count-based cap at all) - but this alone let "unlimited" swallow the entire remaining queue in one single, uninterrupted tick invocation with zero chance for the periodic progress check below to ever run before the whole scan had already finished, MAX_TICK_DURATION_NANOS's own wall-clock time budget is ALSO always enforced regardless of that config, so a tick invocation still yields back to the game's own tick loop periodically even when "unlimited" - see that field's own doc for the reasoning. For progress visibility during a long-running scan, reports "chunks investigated" (the total candidate count) and "chunks processed so far, as a percentage of that total" every 20 ticks (1 second) for as long as the scan is still running - now genuinely visible throughout a long scan regardless of worldDataCleanupChunksPerTick's own setting, rather than only once a slower, spread-out speed happened to be configured.
 *
 * <p>Only one scan may run at a time (a second "/tvm clean -d" invocation while one is already in progress is rejected with a message rather than starting a competing scan) - this project has no use case for running two of these simultaneously, and doing so would only make both slower while producing confusingly interleaved progress reports. */
public final class WorldDataCleanupTask {

	/** Per the class-level doc above: the only thing "--all" actually changes. RESET (no "--all", the default) replaces each found vehicle with a freshly-spawned one of the exact same type at the exact same position/rotation - every persisted field (throttle, attitude, part/toggle state, everything) resets to its own default, but the vehicle itself, as a presence in the world, is preserved. DISCARD ("--all") removes each found vehicle outright, nothing spawned in its place. Both skip a vehicle currently carrying a passenger entirely, for the same reason either way - forcibly ejecting whoever's riding/piloting it is too disruptive for a bulk admin command to do without an explicit warning of its own. */
	private enum Mode {
		RESET, DISCARD
	}

	/** How often (in ticks) progress is reported while a scan is still running - 20 ticks = 1 second, per the direct request. */
	private static final int PROGRESS_REPORT_INTERVAL_TICKS = 20;

	private static WorldDataCleanupTask activeTask;

	private final ServerCommandSource source;
	private final ServerWorld world;
	private final Mode mode;
	private final List<ChunkPos> candidates;
	private int nextIndex = 0;
	private int chunksProcessed = 0;
	private int vehiclesHandled = 0;
	private int vehiclesSkippedOccupied = 0;
	private int ticksSinceLastReport = 0;

	private WorldDataCleanupTask(ServerCommandSource source, ServerWorld world, Mode mode, List<ChunkPos> candidates) {
		this.source = source;
		this.world = world;
		this.mode = mode;
		this.candidates = candidates;
	}

	/** Called from command.TvmCommand's own "-d" handler (resetMode=true) - resets every found vehicle to a freshly-spawned replacement rather than removing it outright. Starts a new scan for the command executor's own current world, or does nothing but report if one is already running. */
	public static void startReset(ServerCommandSource source) {
		tudursvehiclemod$start(source, Mode.RESET);
	}

	/** Called from command.TvmCommand's own "-d --all" handler - removes every found vehicle outright, nothing spawned in its place. Starts a new scan for the command executor's own current world, or does nothing but report if one is already running. */
	public static void startDiscard(ServerCommandSource source) {
		tudursvehiclemod$start(source, Mode.DISCARD);
	}

	private static void tudursvehiclemod$start(ServerCommandSource source, Mode mode) {
		if (activeTask != null) {
			source.sendFeedback(() -> Text.literal("[tvm] A world data cleanup scan is already in progress - wait for it to finish before starting another."), false);
			return;
		}
		ServerWorld world = source.getWorld();
		List<ChunkPos> candidates = tudursvehiclemod$discoverCandidateChunks(world);
		if (candidates.isEmpty()) {
			source.sendFeedback(() -> Text.literal("[tvm] No saved region files were found for this dimension - nothing to scan."), true);
			return;
		}
		WorldDataCleanupTask task = new WorldDataCleanupTask(source, world, mode, candidates);
		activeTask = task;
		int candidateCount = candidates.size();
		source.sendFeedback(() -> Text.literal("[tvm] Starting world data cleanup scan: " + candidateCount + " chunk(s) under investigation."), true);
	}

	/** Registered on VehicleMod's own END_SERVER_TICK - a no-op whenever no scan is currently active. */
	public static void onServerTick(MinecraftServer server) {
		if (activeTask != null) {
			activeTask.tudursvehiclemod$tick();
		}
	}

	/** The maximum wall-clock time (milliseconds) tudursvehiclemod$tick() spends processing candidates in a single call, regardless of worldDataCleanupChunksPerTick's own count-based cap - see that method's own doc for why a purely count-based limit alone (particularly its own "-1/unlimited" case) could process every remaining candidate in one single, uninterrupted call, never actually returning control to the game's own tick loop (and so never letting the periodic progress check below run) until the entire scan had already finished. 50ms - deliberately generous relative to a normal tick's own ~50ms budget (this command already accepts real, admin-approved server stalling/slowdown while it runs), but still bounded, so the game's own tick loop - and this task's own periodic progress reporting - keeps actually advancing throughout a long scan rather than freezing solid for its entire duration. */
	private static final long MAX_TICK_DURATION_NANOS = 50_000_000L;

	private void tudursvehiclemod$tick() {
		int perTick = VehicleModServerConfig.get().worldDataCleanupChunksPerTick;
		int countLimit = perTick < 0 ? Integer.MAX_VALUE : perTick;
		long tickStartNanos = System.nanoTime();
		int processedThisTick = 0;
		while (this.nextIndex < this.candidates.size() && processedThisTick < countLimit
				&& (System.nanoTime() - tickStartNanos) < MAX_TICK_DURATION_NANOS) {
			this.tudursvehiclemod$processChunk(this.candidates.get(this.nextIndex));
			this.nextIndex++;
			this.chunksProcessed++;
			processedThisTick++;
		}
		boolean finished = this.nextIndex >= this.candidates.size();
		this.ticksSinceLastReport++;
		if (!finished && this.ticksSinceLastReport >= PROGRESS_REPORT_INTERVAL_TICKS) {
			this.ticksSinceLastReport = 0;
			this.tudursvehiclemod$reportProgress();
		}
		if (finished) {
			this.tudursvehiclemod$reportFinished();
			activeTask = null;
		}
	}

	/** getChunk(x, z, ChunkStatus.FULL, false) (this method's own earlier pre-check) is understood to only return non-null for a chunk ALREADY resident in memory, rather than actually reading saved data from disk for a currently-unloaded position - silently skipping every genuinely-unloaded candidate as if it had never been saved at all. Removed that pre-check entirely; every candidate is now force-loaded directly (accepting the small, bounded risk of triggering terrain generation for a genuinely-sparse position within an otherwise-populated region file - each candidate already comes from a REAL, existing region file on disk rather than the theoretical world border, so this only ever applies to the comparatively rare sparse-within-region case). Per the class-level Mode doc: handles each found vehicle according to this.mode - RESET replaces it with a fresh instance of itself, DISCARD removes it outright. */
	private void tudursvehiclemod$processChunk(ChunkPos pos) {
		this.world.getChunk(pos.x, pos.z); // Force-loads (generating only in the rare sparse-within-region case - see this method's own doc).
		Box chunkBox = new Box(pos.x * 16.0, -2048.0, pos.z * 16.0, pos.x * 16.0 + 16.0, 2048.0, pos.z * 16.0 + 16.0);
		for (AbstractVehicleEntity vehicle : this.world.getEntitiesByClass(AbstractVehicleEntity.class, chunkBox, e -> true)) {
			if (!vehicle.getPassengerList().isEmpty()) {
				this.vehiclesSkippedOccupied++;
				continue;
			}
			if (this.mode == Mode.DISCARD) {
				vehicle.discard();
				this.vehiclesHandled++;
				continue;
			}
			net.minecraft.util.Identifier definitionId = vehicle.getVehicleDefinitionId();
			net.minecraft.entity.Entity fresh = vehicle.getType().create(this.world, net.minecraft.entity.SpawnReason.COMMAND);
			if (fresh == null) {
				continue;
			}
			fresh.setPosition(vehicle.getX(), vehicle.getY(), vehicle.getZ());
			fresh.setYaw(vehicle.getYaw());
			fresh.setPitch(vehicle.getPitch());
			if (fresh instanceof AbstractVehicleEntity freshVehicle && definitionId != null) {
				freshVehicle.setVehicleDefinitionId(definitionId);
			}
			vehicle.discard();
			this.world.spawnEntity(fresh);
			this.vehiclesHandled++;
		}
	}

	private void tudursvehiclemod$reportProgress() {
		int processed = this.chunksProcessed;
		int total = this.candidates.size();
		double percent = total == 0 ? 100.0 : (processed * 100.0 / total);
		this.source.sendFeedback(() -> Text.literal(String.format(
				"[tvm] World data cleanup: %d chunk(s) under investigation, %d processed (%.1f%%).",
				total, processed, percent)), true);
	}

	private void tudursvehiclemod$reportFinished() {
		int handled = this.vehiclesHandled;
		int skipped = this.vehiclesSkippedOccupied;
		int total = this.candidates.size();
		String verb = this.mode == Mode.DISCARD ? "removed" : "reset to freshly-spawned defaults";
		this.source.sendFeedback(() -> Text.literal("[tvm] World data cleanup finished: " + total + " chunk(s) investigated, "
				+ handled + " vehicle(s) " + verb
				+ (skipped > 0 ? " (" + skipped + " skipped - currently occupied)." : ".")), true);
	}

	/** Per this class's own doc: lists every "r.{x}.{z}.mca" region file actually present under this world's own current dimension save folder, and expands each one into its own 32x32 grid of candidate chunk positions. Returns an empty list (rather than throwing) if the region folder can't be listed at all (e.g. an unusual/misconfigured save layout) - a failed discovery simply means nothing gets scanned, not a crash. */
	private static List<ChunkPos> tudursvehiclemod$discoverCandidateChunks(ServerWorld world) {
		List<ChunkPos> candidates = new ArrayList<>();
		Path regionDir = tudursvehiclemod$resolveRegionDirectory(world);
		if (regionDir == null || !Files.isDirectory(regionDir)) {
			return candidates;
		}
		Pattern regionFileName = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
		try (var stream = Files.list(regionDir)) {
			for (Path file : stream.toList()) {
				Matcher matcher = regionFileName.matcher(file.getFileName().toString());
				if (!matcher.matches()) {
					continue;
				}
				int regionX = Integer.parseInt(matcher.group(1));
				int regionZ = Integer.parseInt(matcher.group(2));
				for (int localX = 0; localX < 32; localX++) {
					for (int localZ = 0; localZ < 32; localZ++) {
						candidates.add(new ChunkPos(regionX * 32 + localX, regionZ * 32 + localZ));
					}
				}
			}
		} catch (IOException ignored) {
			return new ArrayList<>();
		}
		return candidates;
	}

	/** Replicates vanilla's own long-standing, stable per-dimension folder convention by hand instead - the overworld's own region files sit directly under the world's own root save folder, the nether's under "DIM-1", the end's under "DIM1", and any other (modded/datapack) dimension's own under "dimensions/{namespace}/{path}". */
	private static Path tudursvehiclemod$resolveRegionDirectory(ServerWorld world) {
		Path root = world.getServer().getSavePath(WorldSavePath.ROOT);
		var dimensionKey = world.getRegistryKey();
		if (dimensionKey.equals(World.OVERWORLD)) {
			return root.resolve("region");
		} else if (dimensionKey.equals(World.NETHER)) {
			return root.resolve("DIM-1").resolve("region");
		} else if (dimensionKey.equals(World.END)) {
			return root.resolve("DIM1").resolve("region");
		} else {
			var id = dimensionKey.getValue();
			return root.resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath()).resolve("region");
		}
	}
}
