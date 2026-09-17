package com.example.tudursvehiclemod.asset;

import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;

/** Holds whatever VehicleDefinitions are currently loaded (repopulated on every datapack / resource reload). */
public final class VehicleRegistry {

	private static Map<Identifier, VehicleDefinition> DEFINITIONS = Map.of();

	private VehicleRegistry() {}

	public static void setAll(Map<Identifier, VehicleDefinition> definitions) {
		DEFINITIONS = Map.copyOf(definitions);
		// Per a further direct request: every UNIQUE model (several
		// vehicles can share the same one -.distinct() below avoids
		// redundant work for those) is now pregenerated in PARALLEL rather
		// than one at a time - safe to do since each model's own
		// computation (parsing its own OBJ file, splitting into connected
		// components, filtering decorative parts, simplifying high-poly
		// meshes, building each one's own spatial grid index - see
		// ServerObjModelHitboxes' own doc) only ever reads that SAME
		// model's own file and writes that SAME model's own cache file,
		// with zero shared mutable state between different models at
		// all; the in-memory cache ServerObjModelHitboxes' own
		// tudursvehiclemod$pregenerate() call populates is already a
		// thread-safe ConcurrentHashMap whose own computeIfAbsent()
		// guarantees a given model is only ever actually computed once,
		// however many threads happen to request it at the same time -
		// so this can't duplicate work or corrupt any shared state
		// either. This can meaningfully cut down on-first-launch (before
		// any cache files exist yet) or post-reload load time for an
		// addon pack with many distinct high-poly models.
		DEFINITIONS.values().stream()
				.map(VehicleDefinition::model)
				.distinct()
				.parallel()
				.forEach(model -> {
					ServerObjModelHitboxes.tudursvehiclemod$pregenerate(model);
					ServerObjModelTrackRollerBounds.tudursvehiclemod$pregenerate(model);
				});
	}

	public static Optional<VehicleDefinition> get(Identifier id) {
		return Optional.ofNullable(DEFINITIONS.get(id));
	}

	/** Finds a loaded VehicleDefinition by its own file name alone (the last path segment, ignoring any subdirectories AND namespace - e.g. "f16" matches "tudursvehiclemod:aircraft/f16" just as readily as "tudursvehiclemod:f16"), case-insensitive. If more than one loaded vehicle happens to share that same file name (different subdirectories or namespaces), returns whichever one the underlying map happens to iterate first - genuinely ambiguous in that case, so callers relying on this for anything where that distinction matters should use the exact-Identifier get() overload instead. */
	public static Optional<VehicleDefinition> getByFileName(String fileName) {
		String target = fileName.toLowerCase(java.util.Locale.ROOT);
		for (Map.Entry<Identifier, VehicleDefinition> entry : DEFINITIONS.entrySet()) {
			String path = entry.getKey().getPath();
			int lastSlash = path.lastIndexOf('/');
			String lastSegment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
			if (lastSegment.equalsIgnoreCase(target)) {
				return Optional.of(entry.getValue());
			}
		}
		return Optional.empty();
	}

	/** Same lookup as getByFileName() but returns the matched Identifier itself (the actual registry key) rather than the VehicleDefinition - needed anywhere the caller has to record WHICH vehicle this is (e.g. AbstractVehicleEntity's own setVehicleDefinitionId()), not just read its current stats. */
	public static Optional<Identifier> getIdByFileName(String fileName) {
		String target = fileName.toLowerCase(java.util.Locale.ROOT);
		for (Identifier id : DEFINITIONS.keySet()) {
			String path = id.getPath();
			int lastSlash = path.lastIndexOf('/');
			String lastSegment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
			if (lastSegment.equalsIgnoreCase(target)) {
				return Optional.of(id);
			}
		}
		return Optional.empty();
	}

	public static Map<Identifier, VehicleDefinition> getAll() {
		return DEFINITIONS;
	}
}
