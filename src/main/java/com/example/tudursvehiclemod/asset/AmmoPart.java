package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A named OBJ group/object that's hidden once weaponName's own remaining ammo
 * drops to slotIndex or below - see AddPartWeaponMissile's own doc (used for
 * externally-visible ordnance like missiles/bombs that should visually
 * disappear one at a time as they're expended). Declaring several of these
 * for the same weaponName with slotIndex 0, 1, 2,.. makes them disappear
 * one at a time as ammo depletes (slotIndex N stays visible while more than
 * N rounds remain). Purely visual - has no effect on firing/ammo logic
 * itself (see AbstractVehicleEntity's own ammo tracking for that), EXCEPT for
 * displayPenalty below.
 *
 * DisplayPenalty - an optional, per-part speed/
 * maneuverability penalty applied while THIS specific part is currently
 * visible (e.g. external ordnance still attached increasing drag) -
 * "speedPenaltyPercent, maneuverabilityPenaltyPercent" (each a percentage,
 * stacked additively across every currently-visible AmmoPart that configures
 * this - see AbstractVehicleEntity's own tudursvehiclemod$getAmmoPartSpeedMultiplier()/
 * -ManeuverabilityMultiplier() doc). Deliberately per-part (not a single
 * vehicle-wide value) so different weapon types/mount positions can each
 * configure their own effect. Omitted (the default) means no effect at all
 * for this part. */
public record AmmoPart(
		String part,
		String weaponName,
		int slotIndex,
		java.util.Optional<String> displayPenalty
) {
	public static final Codec<AmmoPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("part").forGetter(AmmoPart::part),
			Codec.STRING.fieldOf("weapon_name").forGetter(AmmoPart::weaponName),
			Codec.INT.optionalFieldOf("slot_index", 0).forGetter(AmmoPart::slotIndex),
			Codec.STRING.optionalFieldOf("display_penalty").forGetter(AmmoPart::displayPenalty)
	).apply(instance, AmmoPart::new));

	/** See displayPenalty's own doc. 0 (no effect) if displayPenalty is absent or malformed. */
	public float speedPenaltyPercent() {
		return tudursvehiclemod$parsePenaltyComponent(0);
	}

	/** See displayPenalty's own doc. 0 (no effect) if displayPenalty is absent or malformed. */
	public float maneuverabilityPenaltyPercent() {
		return tudursvehiclemod$parsePenaltyComponent(1);
	}

	private float tudursvehiclemod$parsePenaltyComponent(int index) {
		if (this.displayPenalty.isEmpty()) {
			return 0f;
		}
		String[] parts = this.displayPenalty.get().split(",");
		if (index >= parts.length) {
			return 0f;
		}
		try {
			return Float.parseFloat(parts[index].trim());
		} catch (NumberFormatException e) {
			return 0f;
		}
	}
}
