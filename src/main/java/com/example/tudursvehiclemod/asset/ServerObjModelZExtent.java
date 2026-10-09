package com.example.tudursvehiclemod.asset;

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
	/** Both values come from one read of the file (they used to be two separate reads, each with its own cache). */
	private record Extents(Optional<Float> lengthZ, Optional<Float> minY) {
		static final Extents NONE = new Extents(Optional.empty(), Optional.empty());
	}

	private static final Map<Identifier, Extents> CACHE = new ConcurrentHashMap<>();

	private ServerObjModelZExtent() {
	}

	/** Returns the model's own Z-axis length (max Z - min Z), in model-space units (the caller is responsible for applying the vehicle's own "scale" field on top) - empty if the file couldn't be found or had no vertices at all. */
	public static Optional<Float> getLengthZ(Identifier modelId) {
		return CACHE.computeIfAbsent(modelId, ServerObjModelZExtent::tudursvehiclemod$compute).lengthZ();
	}

	/** Returns the model's own actual lowest Y vertex, in model-space units (the caller is responsible for applying the vehicle's own "scale" field on top) - a real reading of the model's geometry rather than a bounding-box-derived estimate (height/2), which can be well off for a hull whose visual center isn't midway between its top and bottom (a submarine with a tall sail, say) - empty if the file couldn't be found or had no vertices at all. */
	public static Optional<Float> getMinY(Identifier modelId) {
		return CACHE.computeIfAbsent(modelId, ServerObjModelZExtent::tudursvehiclemod$compute).minY();
	}

	private static Extents tudursvehiclemod$compute(Identifier modelId) {
		Path resolved = ServerAssetFiles.find(modelId);
		if (resolved == null) {
			LOGGER.warn("Could not locate model file for {} - hull-length and seabed checks will fall back to a simpler heuristic", modelId);
			return Extents.NONE;
		}
		try {
			float minZ = Float.MAX_VALUE;
			float maxZ = -Float.MAX_VALUE;
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
				float z = Float.parseFloat(parts[3]);
				minY = Math.min(minY, y);
				minZ = Math.min(minZ, z);
				maxZ = Math.max(maxZ, z);
				any = true;
			}
			if (!any) {
				return Extents.NONE;
			}
			return new Extents(Optional.of(maxZ - minZ), Optional.of(minY));
		} catch (IOException | NumberFormatException e) {
			LOGGER.warn("Failed to read/parse {} for its own Z extent and minimum Y", resolved, e);
			return Extents.NONE;
		}
	}
}
