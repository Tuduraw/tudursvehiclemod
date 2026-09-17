package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** MC Heli's own AddWeapon "DefaultYaw, MinYaw, MaxYaw, MinPitch, MaxPitch" trailing parameters. */
public record WeaponAimRange(double defaultYaw, double minYaw, double maxYaw, double minPitch, double maxPitch) {
	public static final Codec<WeaponAimRange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.optionalFieldOf("default_yaw", 0.0).forGetter(WeaponAimRange::defaultYaw),
			Codec.DOUBLE.optionalFieldOf("min_yaw", -180.0).forGetter(WeaponAimRange::minYaw),
			Codec.DOUBLE.optionalFieldOf("max_yaw", 180.0).forGetter(WeaponAimRange::maxYaw),
			Codec.DOUBLE.optionalFieldOf("min_pitch", -90.0).forGetter(WeaponAimRange::minPitch),
			Codec.DOUBLE.optionalFieldOf("max_pitch", 90.0).forGetter(WeaponAimRange::maxPitch)
	).apply(instance, WeaponAimRange::new));
}
