package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.asset.DummyPilotSkinRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Client-side half of the dummy pilot skin system (the shared half - which ids exist at all - is DummyPilotSkinRegistry): turns a skin id into an Identifier that can actually be bound for rendering.
 *
 * Two cases, because the two kinds of skin reach the game by genuinely different routes:
 * <ul>
 * <li>A BUILT-IN skin is an ordinary mod resource, so its Identifier resolves through the resource pack system with no work needed here at all.</li>
 * <li>An ADDON skin is a loose file outside any resource pack, so nothing will resolve its Identifier unless this class first reads that file and registers it with the texture manager - the same fundamental problem (and the same solution) as AddonTextureLoader's own doc describes for vehicle textures.</li>
 * </ul>
 *
 * Addon skins are registered lazily, on first use, and then remembered: a player who never selects a given skin never pays to decode it. Unlike vehicle textures these are tiny (64x64) and never dithered/upscaled, so no background decoding machinery is warranted - a direct synchronous read on first use is imperceptible. */
public final class DummyPilotSkins {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/DummyPilotSkins");

	/** Fallback for an addon skin whose file turns out to be unreadable - the built-in default, which is always present. */
	private static final Identifier FALLBACK_TEXTURE =
			DummyPilotSkinRegistry.tudursvehiclemod$builtInTexture(DummyPilotSkinRegistry.DEFAULT_SKIN_ID);

	/** skin id -> the Identifier registered for it. Addon skins only; built-ins never need an entry. */
	private static final Map<String, Identifier> REGISTERED_ADDON_TEXTURES = new HashMap<>();
	/** Ids already tried and found unreadable, so a broken file isn't re-read (and re-logged) every single frame. */
	private static final Set<String> FAILED_ADDON_SKINS = new HashSet<>();

	private DummyPilotSkins() {
	}

	/** Forgets every lazily-registered addon texture, so a resource reload genuinely re-reads files that may have changed. Built-ins need nothing (the resource pack system reloads those itself). */
	public static void clearCache() {
		REGISTERED_ADDON_TEXTURES.clear();
		FAILED_ADDON_SKINS.clear();
	}

	/** The bindable texture for this skin id - registering an addon file on first use if needed. Never returns null: an unknown, missing, or unreadable skin resolves to the built-in default. */
	public static Identifier tudursvehiclemod$getTexture(String skinId) {
		String resolved = DummyPilotSkinRegistry.tudursvehiclemod$resolveOrDefault(skinId);
		Path addonFile = DummyPilotSkinRegistry.tudursvehiclemod$getAddonSkinFile(resolved);
		if (addonFile == null) {
			// Built-in - resolves through the ordinary resource pack path with no work here.
			return DummyPilotSkinRegistry.tudursvehiclemod$builtInTexture(resolved);
		}
		Identifier alreadyRegistered = REGISTERED_ADDON_TEXTURES.get(resolved);
		if (alreadyRegistered != null) {
			return alreadyRegistered;
		}
		if (FAILED_ADDON_SKINS.contains(resolved)) {
			return FALLBACK_TEXTURE;
		}
		Identifier registered = tudursvehiclemod$registerAddonSkin(resolved, addonFile);
		if (registered == null) {
			FAILED_ADDON_SKINS.add(resolved);
			return FALLBACK_TEXTURE;
		}
		REGISTERED_ADDON_TEXTURES.put(resolved, registered);
		return registered;
	}

	/** Reads a loose addon PNG and hands it to the texture manager under its own Identifier. Returns null (rather than throwing) on any failure - see the caller for how that degrades. */
	private static Identifier tudursvehiclemod$registerAddonSkin(String skinId, Path file) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return null;
		}
		try (InputStream stream = Files.newInputStream(file)) {
			NativeImage image = NativeImage.read(stream);
			Identifier id = Identifier.of("tudursvehiclemod", "dummy_pilot_addon/" + skinId);
			client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> id.toString(), image));
			return id;
		} catch (Exception e) {
			LOGGER.warn("Failed to read addon dummy pilot skin '{}' from {}", skinId, file, e);
			return null;
		}
	}
}
