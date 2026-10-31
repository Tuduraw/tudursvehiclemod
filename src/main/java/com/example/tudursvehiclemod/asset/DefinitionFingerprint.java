package com.example.tudursvehiclemod.asset;

import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/** A short per-entry hash of every loaded vehicle definition and weapon config, so a client can check that its own copy of the
 * addon packs matches the server's without the definitions themselves being sent (see network.DefinitionFingerprintPayload and
 * client.ClientDefinitions).
 *
 * Both are hashed from the parsed values (VehicleDefinition / WeaponStats and the records inside them), not from the files, so
 * formatting, comments and key order don't matter - only the values the game actually uses. Sets and maps are hashed in sorted
 * order: their own iteration order (Set.of, HashSet) differs from one JVM to the next, so the client's and the server's copies of
 * identical data would otherwise hash differently. */
public final class DefinitionFingerprint {

	private DefinitionFingerprint() {
	}

	/** id -> hash for every vehicle in the map, sorted by id. */
	public static Map<String, Long> ofVehicles(Map<Identifier, VehicleDefinition> vehicles) {
		Map<String, Long> out = new TreeMap<>();
		vehicles.forEach((id, def) -> out.put(id.toString(), hash(canonical(def))));
		return out;
	}

	/** name -> hash for every weapon in the map, sorted by name. */
	public static Map<String, Long> ofWeapons(Map<String, WeaponStats> weapons) {
		Map<String, Long> out = new TreeMap<>();
		weapons.forEach((name, stats) -> out.put(name, hash(canonical(stats))));
		return out;
	}

	/** What is loaded on this side right now. */
	public static Map<String, Long> currentVehicles() {
		return ofVehicles(VehicleRegistry.getAll());
	}

	public static Map<String, Long> currentWeapons() {
		return ofWeapons(WeaponStatsLoader.getAll());
	}

	/** A text form of value that is the same on every JVM for equal data: records field by field, sets and maps sorted. */
	static String canonical(Object value) {
		StringBuilder out = new StringBuilder();
		appendCanonical(out, value);
		return out.toString();
	}

	private static void appendCanonical(StringBuilder out, Object value) {
		if (value == null) {
			out.append("null");
		} else if (value instanceof Record record) {
			out.append(record.getClass().getSimpleName()).append('{');
			for (var component : record.getClass().getRecordComponents()) {
				out.append(component.getName()).append('=');
				try {
					component.getAccessor().setAccessible(true);
					appendCanonical(out, component.getAccessor().invoke(record));
				} catch (ReflectiveOperationException e) {
					out.append('?');
				}
				out.append(';');
			}
			out.append('}');
		} else if (value instanceof java.util.Optional<?> optional) {
			out.append("opt(");
			appendCanonical(out, optional.orElse(null));
			out.append(')');
		} else if (value instanceof java.util.Map<?, ?> map) {
			java.util.List<String> entries = new java.util.ArrayList<>();
			map.forEach((k, v) -> entries.add(canonical(k) + "=" + canonical(v)));
			java.util.Collections.sort(entries);
			out.append("map").append(entries);
		} else if (value instanceof java.util.Set<?> set) {
			java.util.List<String> entries = new java.util.ArrayList<>();
			set.forEach(element -> entries.add(canonical(element)));
			java.util.Collections.sort(entries);
			out.append("set").append(entries);
		} else if (value instanceof Iterable<?> iterable) {
			out.append('[');
			for (Object element : iterable) {
				appendCanonical(out, element);
				out.append(',');
			}
			out.append(']');
		} else if (value.getClass().isArray()) {
			out.append('[');
			for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) {
				appendCanonical(out, java.lang.reflect.Array.get(value, i));
				out.append(',');
			}
			out.append(']');
		} else {
			// Strings, numbers, booleans, enums, Identifier - all have a value-based toString().
			out.append(value);
		}
	}

	private static long hash(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			long value = 0;
			for (int i = 0; i < 8; i++) {
				value = (value << 8) | (digest[i] & 0xFF);
			}
			return value;
		} catch (java.security.NoSuchAlgorithmException e) {
			return text.hashCode();
		}
	}
}
