package com.example.tudursvehiclemod.asset;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Finds an asset file (an OBJ model, say) without the ResourceManager, which doesn't see assets/ on a server: loose addon folders (AddonPaths) first, then every loaded mod's own resources. Shared by ServerObjModelHitboxes, ServerObjModelZExtent and ServerObjModelTrackRollerBounds, which each used to carry their own copy of this search. */
public final class ServerAssetFiles {

	private ServerAssetFiles() {
	}

	/** The file for an asset Identifier (namespace + path under assets/<namespace>/), or null if it exists nowhere. */
	public static Path find(Identifier assetId) {
		return find("assets/" + assetId.getNamespace() + "/" + assetId.getPath());
	}

	/** The file at an asset-relative path ("assets/<namespace>/..."), or null if it exists nowhere. */
	public static Path find(String relativePath) {
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
