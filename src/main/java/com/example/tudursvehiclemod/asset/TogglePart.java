package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** A named OBJ group/object (see ObjModel's "o"/"g" group support) that eases between two fixed states ("closed"/0 and "open"/1). */
public record TogglePart(
		String part,
		float pivotX, float pivotY, float pivotZ,
		/** "rotate" (uses axisX/Y/Z + maxAngle) or "slide" (uses offsetX/Y/Z). */
		String mode,
		float axisX, float axisY, float axisZ,
		float maxAngle,
		float offsetX, float offsetY, float offsetZ,
		/** "key" (hatch/canopy key), "landing_gear" (follows AbstractVehicleEntity#landingGearProgress), "weapon_bay" (open while weaponName is selected - see AddPartWeaponBay), or "wing_fold" (dedicated wing-fold key - see AddPartWing/WingSweepConfig). */
		String trigger,
		/** How fast this part eases towards its current target, per tick. */
		float speed,
		/** Only meaningful for trigger="weapon_bay" - which weapon (WeaponDefinition#weaponName()) this bay opens for. */
		Optional<String> weaponName,
		/** Only meaningful for trigger="wing_fold" - see WingSweepConfig's own doc. */
		Optional<WingSweepConfig> wingSweep
) {
	public static final Codec<TogglePart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("part").forGetter(TogglePart::part),
			Codec.FLOAT.optionalFieldOf("pivot_x", 0f).forGetter(TogglePart::pivotX),
			Codec.FLOAT.optionalFieldOf("pivot_y", 0f).forGetter(TogglePart::pivotY),
			Codec.FLOAT.optionalFieldOf("pivot_z", 0f).forGetter(TogglePart::pivotZ),
			Codec.STRING.optionalFieldOf("mode", "rotate").forGetter(TogglePart::mode),
			Codec.FLOAT.optionalFieldOf("axis_x", 0f).forGetter(TogglePart::axisX),
			Codec.FLOAT.optionalFieldOf("axis_y", 1f).forGetter(TogglePart::axisY),
			Codec.FLOAT.optionalFieldOf("axis_z", 0f).forGetter(TogglePart::axisZ),
			Codec.FLOAT.optionalFieldOf("max_angle", 90f).forGetter(TogglePart::maxAngle),
			Codec.FLOAT.optionalFieldOf("offset_x", 0f).forGetter(TogglePart::offsetX),
			Codec.FLOAT.optionalFieldOf("offset_y", 0f).forGetter(TogglePart::offsetY),
			Codec.FLOAT.optionalFieldOf("offset_z", 0f).forGetter(TogglePart::offsetZ),
			Codec.STRING.optionalFieldOf("trigger", "key").forGetter(TogglePart::trigger),
			Codec.FLOAT.optionalFieldOf("speed", 6f).forGetter(TogglePart::speed),
			Codec.STRING.optionalFieldOf("weapon_name").forGetter(TogglePart::weaponName),
			WingSweepConfig.CODEC.optionalFieldOf("wing_sweep").forGetter(TogglePart::wingSweep)
	).apply(instance, TogglePart::new));
}
