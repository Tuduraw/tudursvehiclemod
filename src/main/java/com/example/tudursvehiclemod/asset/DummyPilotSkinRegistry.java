package com.example.tudursvehiclemod.asset;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The catalogue of every dummy pilot skin currently available to choose from.
 *
 * Each skin is identified by a plain lowercase string id (the texture file's own name without its extension), which is what actually gets persisted in a Drone Center's own NBT and synced to clients - NOT an Identifier or a file path, so a save referencing a skin whose addon is currently absent degrades gracefully to the built-in default rather than failing to load.
 *
 * Scanned from two places, in this order (so a built-in id always wins a name collision, and an addon can't silently replace the guaranteed-present default):
 * <ul>
 * <li>BUILT_IN_SKIN_IDS below - shipped in this mod's own resources at assets/tudursvehiclemod/textures/entity/dummy_pilot/&lt;id&gt;.png.</li>
 * <li>Every addon folder's own textures/dummy_pilot/&lt;id&gt;.png (see AddonPaths for where addon folders live). so an author can simply drop in any existing player skin.</li>
 * </ul>
 *
 * Deliberately lives in the SHARED (non-client) source set even though only the client ever draws these: the server needs the same id list to validate an incoming selection (see DummyPilotConfigUpdatePayload's own handler), and a dedicated server has the addon folders too. Only the actual texture BINDING is client-side (see the client's own DummyPilotSkins). */
public final class DummyPilotSkinRegistry {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/DummyPilotSkins");

	/** Skins shipped with the mod itself. The first entry is also the fallback for any unknown/missing id - see DEFAULT_SKIN_ID. */
	private static final List<String> BUILT_IN_SKIN_IDS = List.of("default", "driver", "soldier", "ww2_pilot");

	/** Used whenever a Drone Center has no explicit selection yet, or names a skin that isn't currently available (its addon was removed, say). Guaranteed to always be present in getAvailableSkinIds(). */
	public static final String DEFAULT_SKIN_ID = "default";

	/** id -> absolute file path, for addon-supplied skins only. Built-in skins aren't in here at all (they resolve through the ordinary resource pack system instead) - see the client's own DummyPilotSkins for how the two are told apart at bind time. */
	private static final Map<String, Path> ADDON_SKIN_FILES = new LinkedHashMap<>();

	/** Every currently-known id, built-ins first then addons, each appearing exactly once. Rebuilt by reload(). */
	private static final List<String> AVAILABLE_SKIN_IDS = new ArrayList<>();

	private DummyPilotSkinRegistry() {
	}

	/** Re-scans the addon folders. Called on world load and on resource reload, so an author dropping in a new skin file doesn't need a full game restart. Safe to call repeatedly. */
	public static synchronized void reload() {
		ADDON_SKIN_FILES.clear();
		AVAILABLE_SKIN_IDS.clear();
		AVAILABLE_SKIN_IDS.addAll(BUILT_IN_SKIN_IDS);

		for (Path addonFolder : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path skinFolder = addonFolder.resolve("textures").resolve("dummy_pilot");
			if (!Files.isDirectory(skinFolder)) {
				continue;
			}
			try (var stream = Files.list(skinFolder)) {
				for (Path file : stream.filter(Files::isRegularFile).toList()) {
					String fileName = file.getFileName().toString();
					if (!fileName.toLowerCase(Locale.ROOT).endsWith(".png")) {
						continue;
					}
					String id = fileName.substring(0, fileName.length() - ".png".length()).toLowerCase(Locale.ROOT);
					if (!tudursvehiclemod$isValidSkinId(id)) {
						LOGGER.warn("Skipping dummy pilot skin with unusable name: {}", file);
						continue;
					}
					if (AVAILABLE_SKIN_IDS.contains(id)) {
						// Built-ins (and whichever addon got here first) win - see this class's own doc.
						continue;
					}
					ADDON_SKIN_FILES.put(id, file);
					AVAILABLE_SKIN_IDS.add(id);
				}
			} catch (IOException e) {
				LOGGER.warn("Failed to list dummy pilot skins in {}", skinFolder, e);
			}
		}
		LOGGER.info("Dummy pilot skins available: {} ({} from addons)", AVAILABLE_SKIN_IDS.size(), ADDON_SKIN_FILES.size());
	}

	/** Every selectable skin id, in display order (built-ins first). Never empty - the built-ins are always present even if no reload has run yet. */
	public static synchronized List<String> tudursvehiclemod$getAvailableSkinIds() {
		if (AVAILABLE_SKIN_IDS.isEmpty()) {
			AVAILABLE_SKIN_IDS.addAll(BUILT_IN_SKIN_IDS);
		}
		return List.copyOf(AVAILABLE_SKIN_IDS);
	}

	/** Whether this id is currently available to select. Used server-side to reject a selection that names something unknown, rather than persisting an id that would just render as the fallback forever. */
	public static synchronized boolean tudursvehiclemod$isAvailable(String skinId) {
		return skinId != null && tudursvehiclemod$getAvailableSkinIds().contains(skinId.toLowerCase(Locale.ROOT));
	}

	/** This id if it's genuinely available, else DEFAULT_SKIN_ID - the single place "unknown skin degrades to the default" is decided, so a save whose addon has since been removed still loads and renders. */
	public static String tudursvehiclemod$resolveOrDefault(String skinId) {
		return tudursvehiclemod$isAvailable(skinId) ? skinId.toLowerCase(Locale.ROOT) : DEFAULT_SKIN_ID;
	}

	/** The addon file backing this id, or null if it's a built-in (or unknown). Client-only in practice - see this class's own doc. */
	public static synchronized Path tudursvehiclemod$getAddonSkinFile(String skinId) {
		return skinId == null ? null : ADDON_SKIN_FILES.get(skinId.toLowerCase(Locale.ROOT));
	}

	/** The resource-pack Identifier a BUILT-IN skin resolves through. Meaningless for addon skins (those come from a loose file instead - see tudursvehiclemod$getAddonSkinFile()). */
	public static Identifier tudursvehiclemod$builtInTexture(String skinId) {
		if (DEFAULT_SKIN_ID.equals(skinId)) {
			// Resolves directly to vanilla's own actual shipped Steve texture rather than a copy this mod ships itself.
			return Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
		}
		return Identifier.of("tudursvehiclemod", "textures/entity/dummy_pilot/" + skinId + ".png");
	}

	/** Identifier paths only accept a restricted character set, and this id becomes part of one for addon skins too (see the client's own DummyPilotSkins) - anything else is rejected at scan time rather than blowing up later at bind time. */
	private static boolean tudursvehiclemod$isValidSkinId(String id) {
		if (id.isEmpty()) {
			return false;
		}
		for (int i = 0; i < id.length(); i++) {
			char c = id.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
			if (!ok) {
				return false;
			}
		}
		return true;
	}
}
