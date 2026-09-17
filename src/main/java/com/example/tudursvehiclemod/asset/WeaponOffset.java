package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** One firing position (relative to the vehicle) for a weapon mount. */
public record WeaponOffset(
		double x, double y, double z, double mountYaw, double mountPitch,
		// linked_part - present only when the source file's own declaration order let this SPECIFIC muzzle be linked to a SPECIFIC visual part (see mcheli_convert.py's own compute_addweapon_part_ownership() doc) - the exact WeaponPart.part() name to use for tudursvehiclemod$applyWeaponPartRotation(), taking priority over just matching by weapon name (which alone can't tell apart several parts sharing one weapon, e.g. a dual/quad mount split across more than one AddPartWeapon).
		Optional<String> linkedPart
) {
	public static final Codec<WeaponOffset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(WeaponOffset::x),
			Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(WeaponOffset::y),
			Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(WeaponOffset::z),
			Codec.DOUBLE.optionalFieldOf("mount_yaw", 0.0).forGetter(WeaponOffset::mountYaw),
			Codec.DOUBLE.optionalFieldOf("mount_pitch", 0.0).forGetter(WeaponOffset::mountPitch),
			Codec.STRING.optionalFieldOf("linked_part").forGetter(WeaponOffset::linkedPart)
	).apply(instance, WeaponOffset::new));
}
