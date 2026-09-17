package com.example.tudursvehiclemod.asset;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Reads just the Z-axis extent and the minimum Y value (min/max "v x y z"
 * line values) of an OBJ model file, entirely server-safe (i.e. without
 * going through the vanilla ResourceManager, which doesn't expose
 * "assets/" namespace content on the server side at all - see
 * ObjModelLoader's own doc for the normal, client-only loading path). Used
 * by SubmarineEntity's own hull-length based surface-broach trigger and
 * seabed-detection reference point, both of which need real physical
 * geometry and run as part of server-authoritative movement. Looks in
 * loose addon folders first (see AddonPaths), then falls back to scanning
 * every loaded mod's own bundled resources. Results are cached per model
 * Identifier - model files don't change at runtime. */
public final class ServerObjModelZExtent {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/ServerObjModelZExtent");
	private static final Map<Identifier, Optional<Float>> CACHE = new ConcurrentHashMap<>();
	private static final Map<Identifier, Optional<Float>> MIN_Y_CACHE = new ConcurrentHashMap<>();

	private ServerObjModelZExtent() {
	}

	/** Returns the model's own (maxZ - minZ), in model-space units (the
	 * caller is responsible for applying the vehicle's own "scale" field on
	 * top) - empty if the file couldn't be found or had no vertices at all. */
	public static Optional<Float> getLengthZ(Identifier modelId) {
		return CACHE.computeIfAbsent(modelId, ServerObjModelZExtent::tudursvehiclemod$computeLengthZ);
	}

	/** Returns the model's own actual lowest Y vertex, in model-space units
	 * (the caller is responsible for applying the vehicle's own "scale"
	 * Field on top) - this is a real reading of the
	 * model's own actual geometry rather than a bounding-box-derived
	 * estimate (height/2), which can be significantly off for a hull whose
	 * own visual center isn't exactly midway between its own top and
	 * bottom (e.g. a submarine with a conning tower/sail well above the
	 * main hull, inflating the OVERALL height far beyond the hull's own
	 * actual draft) - empty if the file couldn't be found or had no
	 * vertices at all. */
	public static Optional<Float> getMinY(Identifier modelId) {
		return MIN_Y_CACHE.computeIfAbsent(modelId, ServerObjModelZExtent::tudursvehiclemod$computeMinY);
	}

	private static Optional<Float> tudursvehiclemod$computeLengthZ(Identifier modelId) {
		Path resolved = tudursvehiclemod$resolveModelPath(modelId);
		if (resolved == null) {
			LOGGER.warn("Could not locate model file for {} - hull-length-based checks will fall back to a simpler heuristic", modelId);
			return Optional.empty();
		}
		try {
			float minZ = Float.MAX_VALUE;
			float maxZ = -Float.MAX_VALUE;
			boolean any = false;
			for (String line : Files.readAllLines(resolved)) {
				String trimmed = line.strip();
				if (!trimmed.startsWith("v ")) {
					continue;
				}
				String[] parts = trimmed.split("\\s+");
				if (parts.length < 4) {
					continue;
				}
				float z = Float.parseFloat(parts[3]);
				minZ = Math.min(minZ, z);
				maxZ = Math.max(maxZ, z);
				any = true;
			}
			if (!any) {
				return Optional.empty();
			}
			return Optional.of(maxZ - minZ);
		} catch (IOException | NumberFormatException e) {
			LOGGER.warn("Failed to read/parse {} for its own Z extent", resolved, e);
			return Optional.empty();
		}
	}

	private static Optional<Float> tudursvehiclemod$computeMinY(Identifier modelId) {
		Path resolved = tudursvehiclemod$resolveModelPath(modelId);
		if (resolved == null) {
			LOGGER.warn("Could not locate model file for {} - seabed-detection will fall back to a simpler heuristic", modelId);
			return Optional.empty();
		}
		try {
			float minY = Float.MAX_VALUE;
			boolean any = false;
			for (String line : Files.readAllLines(resolved)) {
				String trimmed = line.strip();
				if (!trimmed.startsWith("v ")) {
					continue;
				}
				String[] parts = trimmed.split("\\s+");
				if (parts.length < 4) {
					continue;
				}
				float y = Float.parseFloat(parts[2]);
				minY = Math.min(minY, y);
				any = true;
			}
			if (!any) {
				return Optional.empty();
			}
			return Optional.of(minY);
		} catch (IOException | NumberFormatException e) {
			LOGGER.warn("Failed to read/parse {} for its own minimum Y", resolved, e);
			return Optional.empty();
		}
	}

	/** Loose addon folders (see AddonPaths) first, then every loaded mod's own bundled resources. */
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
}
