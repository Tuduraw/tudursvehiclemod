package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One yaw sector of a weapon's aim range where the pitch limits differ from the
 * weapon's ordinary min_pitch/max_pitch (see {@link WeaponAimRange#pitchZones()}).
 *
 * <p>Intended for e.g. a tank turret or a warship's gun that must not depress into its
 * own hull or superstructure while it points across it: a sector covering the
 * directions that face the hull gets a higher min_pitch, everything else keeps the
 * ordinary limits.
 *
 * <p>from_yaw / to_yaw are measured the same way as min_yaw / max_yaw - degrees
 * relative to the weapon's own default_yaw (0 = default_yaw itself, positive to one
 * side, negative to the other) - so a zone means the same thing whatever
 * default_yaw the weapon uses. The sector runs from from_yaw to to_yaw in the
 * direction of INCREASING yaw offset, and may wrap around +-180 (from_yaw 150,
 * to_yaw -150 covers the 60 degrees directly behind default_yaw). from_yaw ==
 * to_yaw is an empty sector, never a full circle; leave the zone out to apply the
 * ordinary limits everywhere.
 */
public record WeaponPitchZone(double fromYaw, double toYaw, double minPitch, double maxPitch) {
	public static final Codec<WeaponPitchZone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("from_yaw").forGetter(WeaponPitchZone::fromYaw),
			Codec.DOUBLE.fieldOf("to_yaw").forGetter(WeaponPitchZone::toYaw),
			// Either pitch bound may be left out: it then falls back to the weapon's own
			// ordinary min_pitch / max_pitch (resolved in WeaponAimRange, which knows them).
			Codec.DOUBLE.optionalFieldOf("min_pitch", Double.NaN).forGetter(WeaponPitchZone::minPitch),
			Codec.DOUBLE.optionalFieldOf("max_pitch", Double.NaN).forGetter(WeaponPitchZone::maxPitch)
	).apply(instance, WeaponPitchZone::new));

	/** Whether the yaw offset (degrees from default_yaw, any range) lies inside this sector. */
	public boolean contains(double yawOffset) {
		double from = wrap(fromYaw);
		double to = wrap(toYaw);
		double y = wrap(yawOffset);
		if (from == to) {
			return false;
		}
		if (from < to) {
			return y >= from && y <= to;
		}
		return y >= from || y <= to;   // sector wraps around +-180
	}

	private static double wrap(double degrees) {
		double d = degrees % 360.0;
		if (d >= 180.0) {
			d -= 360.0;
		} else if (d < -180.0) {
			d += 360.0;
		}
		return d;
	}
}
