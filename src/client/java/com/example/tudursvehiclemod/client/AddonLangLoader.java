package com.example.tudursvehiclemod.client;

import com.example.tudursvehiclemod.asset.AddonPaths;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** The addons folder is a plain runtime folder Minecraft's own resource system never registers, like every other addon asset type here (models/sounds/vehicle definitions) - so I18n.hasTranslation() can never see a lang file sitting in it, however correctly formatted. This class is the missing loader for that asset type, following the same pattern the others already use.
 *
 * Loads every assets/&lt;namespace&gt;/lang/&lt;locale&gt;.json under every addon folder into an in-memory map, keyed by locale then translation key - queried by VehicleDisplayNames.resolve() instead of (or alongside) genuine I18n. Re-scanned on every resource reload. */
public final class AddonLangLoader implements SimpleSynchronousResourceReloadListener {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/AddonLang");
	private static volatile Map<String, Map<String, String>> loadedByLocale = Map.of();

	public static void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new AddonLangLoader());
	}

	@Override
	public Identifier getFabricId() {
		return Identifier.of("tudursvehiclemod", "addon_lang_loader");
	}

	/** Per this class's own doc: looks up `key` for the CURRENTLY selected game language first (GameOptions.language, e.g. "ja_jp" - the exact same string this mod's own locale-keyed map is stored under, since both ultimately come from the same options.txt-style naming), falling back to "en_us" if that locale's own addon lang file didn't define this key (or no addon lang file exists for it at all) - the same fallback behaviour Minecraft's own translation system uses for any lang file. Returns null if neither has it, so callers can fall through to their own further fallback (see VehicleDisplayNames.resolve()'s own use of this). */
	public static String translate(String key) {
		Map<String, Map<String, String>> byLocale = loadedByLocale;
		String currentLocale = MinecraftClient.getInstance().options.language;
		if (currentLocale != null) {
			Map<String, String> current = byLocale.get(currentLocale.toLowerCase(Locale.ROOT));
			if (current != null && current.containsKey(key)) {
				return current.get(key);
			}
		}
		Map<String, String> fallback = byLocale.get("en_us");
		return fallback == null ? null : fallback.get(key);
	}

	@Override
	public void reload(ResourceManager manager) {
		Map<String, Map<String, String>> byLocale = new HashMap<>();
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path assetsDir = addonDir.resolve("assets");
			for (Path namespaceDir : AddonPaths.listSubdirectories(assetsDir)) {
				Path langDir = namespaceDir.resolve("lang");
				if (!Files.isDirectory(langDir)) {
					continue;
				}
				try (var files = Files.walk(langDir)) {
					for (Path langFile : (Iterable<Path>) files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".json"))::iterator) {
						String locale = langFile.getFileName().toString();
						locale = locale.substring(0, locale.length() - ".json".length()).toLowerCase(Locale.ROOT);
						try (Reader reader = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
						com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseReader(reader);
						if (parsed != null && parsed.isJsonObject()) {
							Map<String, String> entries = new HashMap<>();
							for (var jsonEntry : parsed.getAsJsonObject().entrySet()) {
								if (jsonEntry.getValue().isJsonPrimitive()) {
									entries.put(jsonEntry.getKey(), jsonEntry.getValue().getAsString());
								}
							}
							// Merged rather than replaced per locale: several addons can each ship their own lang/<locale>.json for the SAME locale, and every one of them should contribute (their own vehicle.<their namespace>.* keys don't collide with each other, since each addon uses its own namespace).
							byLocale.computeIfAbsent(locale, k -> new HashMap<>()).putAll(entries);
						}
					} catch (JsonSyntaxException | IOException e) {
						LOGGER.warn("Failed to read addon lang file {}", langFile, e);
					}
					}
				} catch (IOException e) {
					LOGGER.warn("Failed to scan {}", langDir, e);
				}
			}
		}
		loadedByLocale = Map.copyOf(byLocale);
	}
}
