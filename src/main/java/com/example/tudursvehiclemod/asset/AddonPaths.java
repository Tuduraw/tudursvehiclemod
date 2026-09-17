package com.example.tudursvehiclemod.asset;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Vehicles, models, and textures can be dropped in as PLAIN (uncompressed) folders under <game directory>/tudursvehiclemod-addons/<addon name>/. */
public final class AddonPaths {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/Addons");

	private AddonPaths() {
	}

	/** <game directory>/tudursvehiclemod-addons/. */
	public static Path getAddonsRoot() {
		Path root = FabricLoader.getInstance().getGameDir().resolve("tudursvehiclemod-addons");
		try {
			Files.createDirectories(root);
		} catch (IOException e) {
			LOGGER.warn("Could not create {} - loose addon folders won't be found until it exists", root, e);
		}
		return root;
	}

	/** Lists the immediate subdirectories of a folder, or an empty list if it doesn't exist / can't be read. */
	public static java.util.List<Path> listSubdirectories(Path folder) {
		if (!Files.isDirectory(folder)) {
			return java.util.List.of();
		}
		try (var stream = Files.list(folder)) {
			return stream.filter(Files::isDirectory).toList();
		} catch (IOException e) {
			LOGGER.warn("Failed to list {}", folder, e);
			return java.util.List.of();
		}
	}
}
