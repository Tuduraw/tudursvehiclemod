package com.example.tudursvehiclemod.asset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Normalizes older vehicle JSON layouts into the one {@link VehicleDefinition#CODEC} reads,
 * applied to the raw JSON right before decoding (see VehicleDefinitionReloadListener).
 *
 * <p>hide_entity / entity_width / entity_height belong inside the {@code passenger_display}
 * object (see {@link PassengerDisplay}). Earlier documentation and the MC Heli conversion
 * tool wrote them at the TOP LEVEL instead, where the codec silently ignored them - so
 * every converted vehicle lost its HideEntity / EntityWidth / EntityHeight settings. Rather
 * than making every existing pack be regenerated, top-level occurrences are moved into
 * {@code passenger_display} here. A value already present inside {@code passenger_display}
 * always wins over a top-level one, so a file that (for whatever reason) has both keeps
 * behaving exactly as the codec alone would have read it.
 */
public final class VehicleJsonCompat {
	private static final String PASSENGER_DISPLAY = "passenger_display";
	private static final String[] PASSENGER_DISPLAY_KEYS = {"hide_entity", "entity_width", "entity_height"};

	private VehicleJsonCompat() {
	}

	/** Returns the (possibly modified in place) element; non-objects are returned untouched. */
	public static JsonElement upgrade(JsonElement json) {
		if (json == null || !json.isJsonObject()) {
			return json;
		}
		JsonObject root = json.getAsJsonObject();
		JsonObject display = null;
		for (String key : PASSENGER_DISPLAY_KEYS) {
			if (!root.has(key)) {
				continue;
			}
			JsonElement value = root.remove(key);
			if (display == null) {
				JsonElement existing = root.get(PASSENGER_DISPLAY);
				if (existing != null && !existing.isJsonObject()) {
					// Malformed passenger_display: leave it for the codec to report as-is.
					return json;
				}
				display = existing != null ? existing.getAsJsonObject() : new JsonObject();
				root.add(PASSENGER_DISPLAY, display);
			}
			if (!display.has(key)) {
				display.add(key, value);
			}
		}
		return json;
	}
}
