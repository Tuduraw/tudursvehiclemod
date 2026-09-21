package com.example.tudursvehiclemod.asset;

import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registry an addon uses to give its own {@link WeaponType#CUSTOM} weapons real behaviour - see
 * {@link WeaponType#CUSTOM}'s own doc for how a weapon file opts into one ({@code Type = <id>}, any
 * value containing ':') and {@link CustomWeaponBehavior}'s own doc for what a registered behaviour
 * actually gets to decide.
 *
 * <p>Call {@link #register} once, typically from the addon's own mod initializer (so it has run
 * before any weapon file referencing the id is actually fired) - re-registering the same id simply
 * replaces the previous behaviour, so re-initialization (a dev-environment hot reload, say) doesn't
 * need any special handling. Thread-safe: registration and lookup can happen from either side. */
public final class CustomWeaponTypes {

	private CustomWeaponTypes() {
	}

	private static final Map<Identifier, CustomWeaponBehavior> BEHAVIORS = new ConcurrentHashMap<>();

	public static void register(Identifier id, CustomWeaponBehavior behavior) {
		BEHAVIORS.put(id, behavior);
	}

	/** Never null - an unregistered id (the owning addon isn't installed, or simply hasn't
	 * registered yet) resolves to {@link CustomWeaponBehavior#DEFAULT} rather than throwing, so a
	 * missing registration degrades to ordinary unguided fire instead of crashing the weapon. */
	public static CustomWeaponBehavior get(Identifier id) {
		return BEHAVIORS.getOrDefault(id, CustomWeaponBehavior.DEFAULT);
	}
}
