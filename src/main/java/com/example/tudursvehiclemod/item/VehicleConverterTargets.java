package com.example.tudursvehiclemod.item;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every vehicle kind the converter block can produce a spawner item for.
 *
 * The built-in VehicleCategory values are registered automatically at mod init. An ADDON MOD
 * registers its own with register() - deliberately opt-in rather than automatic, so an addon that
 * wants a different (e.g. deliberately costlier) way of obtaining its own vehicles can simply not
 * register and keep the converter out of it entirely.
 *
 * ORDERING is fixed: the built-in categories first, in their own enum order, then addon targets in
 * registration order. The converter stores its own current page as an INDEX into this list, so the
 * order has to be stable for as long as a world is loaded - which it is, since registration happens
 * during mod init and nothing is ever removed. Registering from anywhere other than a mod
 * initializer would break that and is not supported. */
public final class VehicleConverterTargets {

	private static final List<VehicleConverterTarget> TARGETS = new ArrayList<>();

	private VehicleConverterTargets() {
	}

	/** Called once by this mod's own init, before any addon gets a chance to register. */
	public static void registerBuiltIn() {
		for (VehicleCategory category : VehicleCategory.values()) {
			register(category);
		}
	}

	/** Adds one target to the converter. Call from an addon's own ModInitializer.
	 *
	 * Ignores a duplicate id rather than throwing, so a mod loaded twice (or registering defensively)
	 * can't break the ordering every converter block's own stored page index depends on. */
	public static void register(VehicleConverterTarget target) {
		if (target == null) {
			return;
		}
		for (VehicleConverterTarget existing : TARGETS) {
			if (existing.id().equals(target.id())) {
				return;
			}
		}
		TARGETS.add(target);
	}

	/** Every registered target, in the fixed order described in this class's own doc. */
	public static List<VehicleConverterTarget> all() {
		return Collections.unmodifiableList(TARGETS);
	}

	public static int count() {
		return TARGETS.size();
	}

	/** The target at one converter page index, or null if the index is somehow out of range (a
	 * world saved while a different set of mods was installed, for instance). */
	public static VehicleConverterTarget byIndex(int index) {
		return index >= 0 && index < TARGETS.size() ? TARGETS.get(index) : null;
	}

	/** Reverse lookup by a vehicle's own entity type - used when packing a vehicle back up into an
	 * item, to find which spawner item to hand back. Empty for a vehicle whose own type was never
	 * registered as a converter target at all (which is a perfectly valid addon choice - that
	 * vehicle simply isn't packable into a spawner item). */
	public static java.util.Optional<VehicleConverterTarget> byEntityTypeId(Identifier entityTypeId) {
		for (VehicleConverterTarget target : TARGETS) {
			if (target.entityTypeId().equals(entityTypeId)) {
				return java.util.Optional.of(target);
			}
		}
		return java.util.Optional.empty();
	}
}
