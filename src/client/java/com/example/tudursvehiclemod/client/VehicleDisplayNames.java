package com.example.tudursvehiclemod.client;

import com.example.tudursvehiclemod.asset.VehicleDefinition;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;

/** A genuine Minecraft translation key is checked FIRST, before falling back to whatever VehicleDefinition.displayName(Identifier) itself would otherwise return (an addon-authored raw string, or the id-derived default).
 *
 * WHY A KEY-BASED APPROACH RATHER THAN REPRODUCING MC HELI'S OWN MECHANISM: MC Heli's AddDisplayName worked by APPENDING an entry to a lang file at addon-conversion time - a workaround for MC Heli itself having no real i18n system of its own. This mod is built on Minecraft's OWN translation system already (see the many "gui.tudursvehiclemod.*" keys throughout this project's own lang files), so the equivalent, idiomatic mechanism is simply: an addon ships ITS OWN lang file(s) (assets/<addon namespace>/lang/<locale>.json) with an entry at the conventional key below, exactly the same way any other Minecraft content provides localized names - no special conversion step, no custom file format, and it automatically benefits from resource-pack layering, language fallback, and every other feature Minecraft's own translation system already has.
 *
 * THE CONVENTION: "vehicle.<namespace>.<path>", where namespace/path come from the vehicle's own Identifier - e.g. an addon "someaddon" defining a vehicle at "someaddon:ah6" would provide "vehicle.someaddon.ah6" in its own lang files. This deliberately does NOT collide with the small, fixed set of "vehicle.tudursvehiclemod.<category>" keys already in this mod's own lang files (car/helicopter/ship/submarine/aircraft) - those are built-in CATEGORY labels, not per-vehicle names, and no addon is expected to define a vehicle in the tudursvehiclemod namespace itself.
 *
 * Client-only (I18n is CLIENT environment-only, per its own annotation) - this is why the check lives here rather than inside VehicleDefinition itself, which is common code reachable from the dedicated server too. An addon that provides no translation for a given locale simply falls through to the existing behaviour unchanged - nothing about this is required for a vehicle to work at all. */
public final class VehicleDisplayNames {

	private VehicleDisplayNames() {
	}

	/** Per this class's own doc: resolves the localized name for the given vehicle if its own addon provided one for the current locale, otherwise falls back to VehicleDefinition.displayName(Identifier) exactly as before.
	 *
	 * AddonLangLoader is checked FIRST, since that is the only place a lang file placed under the addons folder (see AddonPaths' own doc) can ever actually be found - genuine I18n has no visibility into that folder at all, for the same reason none of this project's other addon asset types (models, sounds, vehicle definitions themselves) go through Minecraft's own resource system either. I18n.hasTranslation() is kept as a secondary check afterwards, for the much rarer case where a vehicle's own namespace happens to be served through an ACTUAL registered resource pack instead. */
	public static String resolve(Identifier vehicleId, VehicleDefinition def) {
		String key = "vehicle." + vehicleId.getNamespace() + "." + vehicleId.getPath();
		String addonTranslation = AddonLangLoader.translate(key);
		if (addonTranslation != null) {
			return addonTranslation;
		}
		if (I18n.hasTranslation(key)) {
			return I18n.translate(key);
		}
		return def.displayName(vehicleId);
	}
}
