package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * MC Heli's own AddWeapon "DefaultYaw, MinYaw, MaxYaw, MinPitch, MaxPitch" trailing parameters,
 * plus this mod's own optional pitch_zones (see {@link WeaponPitchZone}): yaw sectors in which the
 * pitch limits differ from the ordinary min_pitch / max_pitch - e.g. so a turret can't depress
 * into its own hull while it points across it. Empty (the default) means the ordinary limits
 * apply at every yaw, exactly as before this field existed.
 */
public record WeaponAimRange(double defaultYaw, double minYaw, double maxYaw, double minPitch, double maxPitch,
		List<WeaponPitchZone> pitchZones) {
	public static final Codec<WeaponAimRange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.optionalFieldOf("default_yaw", 0.0).forGetter(WeaponAimRange::defaultYaw),
			Codec.DOUBLE.optionalFieldOf("min_yaw", -180.0).forGetter(WeaponAimRange::minYaw),
			Codec.DOUBLE.optionalFieldOf("max_yaw", 180.0).forGetter(WeaponAimRange::maxYaw),
			Codec.DOUBLE.optionalFieldOf("min_pitch", -90.0).forGetter(WeaponAimRange::minPitch),
			Codec.DOUBLE.optionalFieldOf("max_pitch", 90.0).forGetter(WeaponAimRange::maxPitch),
			WeaponPitchZone.CODEC.listOf().optionalFieldOf("pitch_zones", List.of()).forGetter(WeaponAimRange::pitchZones)
	).apply(instance, WeaponAimRange::new));

	/**
	 * The lower pitch bound in force at this yaw offset (degrees from default_yaw). The ordinary
	 * min_pitch / max_pitch are normalised first (a weapon whose data has them inverted still
	 * works, same as before). If several zones cover the yaw, the FIRST one listed wins, and a
	 * zone that leaves one of its own bounds out takes that bound from the ordinary limits.
	 *
	 * <p>A zone can only NARROW the ordinary range, never widen it: its bounds are held inside
	 * min_pitch / max_pitch. Zones exist to keep a gun out of its own hull, and a bound outside
	 * the ordinary range would defeat that (a mistyped sign, say) while silently being accepted.
	 * A zone whose bounds end up crossed - say a min_pitch above the ordinary max_pitch - collapses
	 * to that single pitch instead of producing an inverted range.
	 */
	public double effectiveMinPitch(double yawOffset) {
		return limits(yawOffset)[0];
	}

	/** Upper counterpart of {@link #effectiveMinPitch(double)}. */
	public double effectiveMaxPitch(double yawOffset) {
		return limits(yawOffset)[1];
	}

	private double[] limits(double yawOffset) {
		double ordinaryLower = Math.min(minPitch, maxPitch);
		double ordinaryUpper = Math.max(minPitch, maxPitch);
		WeaponPitchZone zone = zoneAt(yawOffset);
		if (zone == null) {
			return new double[]{ordinaryLower, ordinaryUpper};
		}
		double lower = Double.isNaN(zone.minPitch()) ? ordinaryLower : Math.max(zone.minPitch(), ordinaryLower);
		double upper = Double.isNaN(zone.maxPitch()) ? ordinaryUpper : Math.min(zone.maxPitch(), ordinaryUpper);
		lower = Math.min(lower, ordinaryUpper);
		upper = Math.max(upper, ordinaryLower);
		if (lower > upper) {
			// crossed bounds: hold the pitch at the bound that was closest to the ordinary range
			double pinned = Double.isNaN(zone.minPitch()) ? upper : lower;
			return new double[]{pinned, pinned};
		}
		return new double[]{lower, upper};
	}

	private WeaponPitchZone zoneAt(double yawOffset) {
		for (WeaponPitchZone zone : pitchZones) {
			if (zone.contains(yawOffset)) {
				return zone;
			}
		}
		return null;
	}
}
