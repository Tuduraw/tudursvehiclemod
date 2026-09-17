package com.example.tudursvehiclemod.asset;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Scans data/<namespace>/vehicles/*.json on every reload (including /reload on a running server) and rebuilds VehicleRegistry. */
public class VehicleDefinitionReloadListener implements SimpleSynchronousResourceReloadListener {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/Definitions");
	private static final String DIRECTORY = "vehicles";
	private static final String SUFFIX = ".json";

	@Override
	public Identifier getFabricId() {
		return Identifier.of("tudursvehiclemod", "vehicle_definitions");
	}

	@Override
	public void reload(ResourceManager manager) {
		Map<Identifier, VehicleDefinition> loaded = new HashMap<>();

		for (Map.Entry<Identifier, Resource> entry :
				manager.findResources(DIRECTORY, id -> id.getPath().endsWith(SUFFIX)).entrySet()) {

			Identifier fileId = entry.getKey();
			String path = fileId.getPath();
			Identifier vehicleId = Identifier.of(
					fileId.getNamespace(),
					path.substring(DIRECTORY.length() + 1, path.length() - SUFFIX.length())
			);

			try (Reader reader = entry.getValue().getReader()) {
				var json = JsonParser.parseReader(reader);
				VehicleDefinition.CODEC.parse(JsonOps.INSTANCE, json)
						.resultOrPartial(error -> LOGGER.error("Failed to parse vehicle '{}': {}", vehicleId, error))
						.ifPresent(def -> loaded.put(vehicleId, def));
			} catch (Exception e) {
				LOGGER.error("Failed to read vehicle definition {}", vehicleId, e);
			}
		}

		int fromResourcePacks = loaded.size();
		int addonPackCount = AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot()).size();
		loadFromAddonsFolder(loaded);
		int fromAddonsFolder = loaded.size() - fromResourcePacks;

		VehicleRegistry.setAll(loaded);
		LOGGER.info("Loaded {} vehicle definition(s) ({} from tudursvehiclemod-addons/, {} addon pack(s) found)",
				loaded.size(), fromAddonsFolder, addonPackCount);
	}

	/** See AddonPaths for the expected folder layout. Never throws. */
	private void loadFromAddonsFolder(Map<Identifier, VehicleDefinition> loaded) {
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path dataDir = addonDir.resolve("data");
			for (Path namespaceDir : AddonPaths.listSubdirectories(dataDir)) {
				String namespace = namespaceDir.getFileName().toString();
				Path vehiclesDir = namespaceDir.resolve(DIRECTORY);
				if (!Files.isDirectory(vehiclesDir)) {
					continue;
				}
				try (var files = Files.walk(vehiclesDir)) {
					for (Path jsonFile : (Iterable<Path>) files.filter(p -> p.toString().endsWith(SUFFIX))::iterator) {
						String relative = vehiclesDir.relativize(jsonFile).toString().replace('\\', '/');
						String path = relative.substring(0, relative.length() - SUFFIX.length());
						Identifier vehicleId = Identifier.of(namespace, path);

						try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
							var json = JsonParser.parseReader(reader);
							VehicleDefinition.CODEC.parse(JsonOps.INSTANCE, json)
									.resultOrPartial(error -> LOGGER.error(
											"Failed to parse addon vehicle '{}' ({}): {}", vehicleId, jsonFile, error))
									.ifPresent(def -> loaded.put(vehicleId, def));
						} catch (Exception e) {
							LOGGER.error("Failed to read addon vehicle definition {}", jsonFile, e);
						}
					}
				} catch (Exception e) {
					LOGGER.error("Failed to scan {}", vehiclesDir, e);
				}
			}
		}
	}
}
