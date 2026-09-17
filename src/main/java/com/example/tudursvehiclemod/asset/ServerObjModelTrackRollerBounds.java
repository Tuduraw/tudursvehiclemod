package com.example.tudursvehiclemod.asset;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Computes, per named "$track_roller{N}"-style OBJ group, how many full
 * rotations that specific roller needs to make per block of belt travel
 * for it to spin in perfect lock-step with the crawler track underneath
 * it (see TrackRollerPart's own doc for the overall feature this serves).
 *
 * The roller's own rotation axis is always this vehicle's own fixed X
 * axis (see AddTrackRoller's own doc - no separate axis of its own), so
 * its own effective diameter is measured across the Y/Z cross-section
 * perpendicular to that axis - the larger of the group's own Y extent
 * and Z extent (a real roller should be close to circular in that plane;
 * taking the larger of the two is a safe, conservative choice against
 * minor mesh asymmetry either way). rotationsPerBlock = 1 / (π ×
 * diameter), i.e. one full rotation exactly every circumference's worth
 * of belt travel.
 *
 * Same caching convention as ServerObjModelHitboxBounds (own cache file
 * next to the model, own addon/built-in resolution, generated once per
 * model per launch or reused from a cache file across launches) - see
 * that class's own doc for the full rationale on why this is computed
 * ahead of time and recorded rather than redone from scratch every tick
 * or every render frame. */
public final class ServerObjModelTrackRollerBounds {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/ServerObjModelTrackRollerBounds");
	private static final Map<Identifier, Map<String, Float>> CACHE = new ConcurrentHashMap<>();

	private static final int CACHE_FORMAT_VERSION = 1;

	private ServerObjModelTrackRollerBounds() {
	}

	/** Empty map if the model couldn't be found/read/parsed, or if it simply has no "$track_roller"-named groups at all - callers should treat a missing entry for any one specific part name as "no computed value available" (e.g. fall back to not spinning that roller, or to a hand-specified rotationsPerBlock on its own TrackRollerPart, if any). */
	public static Map<String, Float> getRotationsPerBlock(Identifier modelId) {
		return CACHE.computeIfAbsent(modelId, ServerObjModelTrackRollerBounds::tudursvehiclemod$loadOrCompute);
	}

	/** Called once per model per game launch (see ServerObjModelBounds's own tudursvehiclemod$pregenerate() doc) - just a plain getRotationsPerBlock() call, since the cache above already makes repeat calls free. */
	public static void tudursvehiclemod$pregenerate(Identifier modelId) {
		getRotationsPerBlock(modelId);
	}

	private static Map<String, Float> tudursvehiclemod$loadOrCompute(Identifier modelId) {
		Path cacheFile = tudursvehiclemod$resolveCachePath(modelId);
		if (cacheFile != null && Files.isRegularFile(cacheFile)) {
			Optional<Map<String, Float>> cached = tudursvehiclemod$readCache(cacheFile);
			if (cached.isPresent()) {
				return cached.get();
			}
			LOGGER.warn("Cached track-roller rotations-per-block at {} were unreadable - regenerating", cacheFile);
		}

		Path modelFile = tudursvehiclemod$resolveModelPath(modelId);
		if (modelFile == null) {
			LOGGER.warn("Could not locate model file for {} - none of its own track rollers will have a computed rotations-per-block value unless hand-specified on their own TrackRollerPart", modelId);
			return Map.of();
		}

		Map<String, Float> computed = tudursvehiclemod$computeRotationsPerBlock(modelFile);
		if (cacheFile != null) {
			tudursvehiclemod$writeCache(cacheFile, computed);
		}
		return computed;
	}

	private record Vertex(float x, float y, float z) {
	}

	/** Same plain OBJ parse (v/o/g/f lines only, fan-triangulation, negative-index resolution) as ServerObjModelHitboxBounds's own tudursvehiclemod$computeBounds() - see that method's own doc. */
	private static Map<String, Float> tudursvehiclemod$computeRotationsPerBlock(Path modelFile) {
		Map<String, Float> result = new HashMap<>();
		try {
			java.util.List<float[]> allVertices = new java.util.ArrayList<>();
			Map<String, float[]> extentsByGroup = new HashMap<>();
			String currentGroup = "";
			for (String rawLine : Files.readAllLines(modelFile)) {
				String line = rawLine.strip();
				if (line.startsWith("v ")) {
					String[] parts = line.split("\\s+");
					if (parts.length >= 4) {
						allVertices.add(new float[]{
								Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
					}
				} else if (line.startsWith("o ") || line.startsWith("g ")) {
					String[] parts = line.split("\\s+", 2);
					currentGroup = parts.length > 1 ? parts[1].strip() : "";
				} else if (line.startsWith("f ")) {
					if (!tudursvehiclemod$isTrackRollerGroup(currentGroup)) {
						continue;
					}
					String[] parts = line.split("\\s+");
					for (int i = 1; i < parts.length; i++) {
						String indexPart = parts[i].split("/")[0];
						if (indexPart.isEmpty()) {
							continue;
						}
						int index = Integer.parseInt(indexPart);
						int resolved = index > 0 ? index - 1 : allVertices.size() + index;
						if (resolved < 0 || resolved >= allVertices.size()) {
							continue;
						}
						float[] v = allVertices.get(resolved);
						float[] extent = extentsByGroup.computeIfAbsent(currentGroup,
								k -> new float[]{Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE});
						extent[0] = Math.min(extent[0], v[1]); // minY
						extent[1] = Math.max(extent[1], v[1]); // maxY
						extent[2] = Math.min(extent[2], v[2]); // minZ
						extent[3] = Math.max(extent[3], v[2]); // maxZ
					}
				}
			}
			for (Map.Entry<String, float[]> entry : extentsByGroup.entrySet()) {
				float[] extent = entry.getValue();
				float extentY = extent[1] - extent[0];
				float extentZ = extent[3] - extent[2];
				float diameter = Math.max(extentY, extentZ);
				if (diameter <= 0.001f) {
					continue;
				}
				float circumference = (float) (Math.PI * diameter);
				result.put(entry.getKey(), 1.0f / circumference);
			}
		} catch (IOException | NumberFormatException e) {
			LOGGER.warn("Failed to read/parse {} for its own track-roller rotations-per-block", modelFile, e);
		}
		return result;
	}

	/** Matches CrawlerTrackPart's own family of "$track_roller"-style group-name conventions - see mcheli_convert.py's own TRACK_ROLLER_WORDS for the full accepted set (track_roller/trackroller/roller), case-insensitively, prefixed with "$". */
	private static boolean tudursvehiclemod$isTrackRollerGroup(String groupName) {
		String lower = groupName.toLowerCase(java.util.Locale.ROOT);
		return lower.startsWith("$track_roller") || lower.startsWith("$trackroller") || lower.startsWith("$roller");
	}

	private static void tudursvehiclemod$writeCache(Path cacheFile, Map<String, Float> rotationsPerBlock) {
		try {
			Files.createDirectories(cacheFile.getParent());
			StringBuilder sb = new StringBuilder();
			sb.append(CACHE_FORMAT_VERSION).append('\n');
			for (Map.Entry<String, Float> entry : rotationsPerBlock.entrySet()) {
				sb.append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
			}
			Files.writeString(cacheFile, sb.toString());
		} catch (IOException e) {
			LOGGER.warn("Failed to write cached track-roller rotations-per-block to {} - will regenerate every launch until this succeeds", cacheFile, e);
		}
	}

	private static Optional<Map<String, Float>> tudursvehiclemod$readCache(Path cacheFile) {
		try {
			java.util.List<String> lines = Files.readAllLines(cacheFile);
			if (lines.isEmpty()) {
				return Optional.empty();
			}
			if (Integer.parseInt(lines.get(0).strip()) != CACHE_FORMAT_VERSION) {
				return Optional.empty();
			}
			Map<String, Float> result = new HashMap<>();
			for (int i = 1; i < lines.size(); i++) {
				String line = lines.get(i).strip();
				if (line.isEmpty()) {
					continue;
				}
				int lastSpace = line.lastIndexOf(' ');
				if (lastSpace < 0) {
					continue;
				}
				result.put(line.substring(0, lastSpace), Float.parseFloat(line.substring(lastSpace + 1)));
			}
			return Optional.of(result);
		} catch (IOException | NumberFormatException e) {
			return Optional.empty();
		}
	}

	/** Same resolution order as ServerObjModelHitboxBounds's own doc. */
	private static Path tudursvehiclemod$resolveModelPath(Identifier modelId) {
		String relativePath = "assets/" + modelId.getNamespace() + "/" + modelId.getPath();

		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path candidate = addonDir.resolve(relativePath);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}

		for (var mod : FabricLoader.getInstance().getAllMods()) {
			Optional<Path> found = mod.findPath(relativePath);
			if (found.isPresent() && Files.isRegularFile(found.get())) {
				return found.get();
			}
		}

		return null;
	}

	/** Same convention as ServerObjModelHitboxBounds's own resolveCachePath() - just a different cache-file suffix so the two never collide. */
	private static Path tudursvehiclemod$resolveCachePath(Identifier modelId) {
		String relativePath = "assets/" + modelId.getNamespace() + "/" + modelId.getPath();
		String cacheRelativePath = "tudursvehiclemod-generated/" + modelId.getNamespace() + "/" + modelId.getPath() + ".track-roller-bounds.txt";

		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			if (Files.isRegularFile(addonDir.resolve(relativePath))) {
				return addonDir.resolve(cacheRelativePath);
			}
		}

		return AddonPaths.getAddonsRoot().resolveSibling("tudursvehiclemod-generated-cache").resolve(cacheRelativePath);
	}
}
