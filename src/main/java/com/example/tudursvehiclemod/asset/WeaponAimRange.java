package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * MC Heli's own AddWeapon "DefaultYaw, MinYaw, MaxYaw, MinPitch, MaxPitch" trailing parameters,
 * plus two optional additions of this mod's own:
 *
 * <ul>
 * <li>pitch_zones (see {@link WeaponPitchZone}): yaw sectors in which the pitch limits differ from
 * the ordinary min_pitch / max_pitch - e.g. so a turret can't depress into its own hull while it
 * points across it. Empty (the default) means the ordinary limits apply at every yaw.
 * <li>default_pitch / default_roll: the tilt of the mount's own reference frame, for a turret
 * placed tilted (see {@link WeaponMountTilt}). Both 0 (the default) means an ordinary, upright
 * mount, exactly as before these fields existed.
 * </ul>
 *
 * <p>Whenever a tilt is set, default_yaw, the yaw/pitch limits and the pitch zones are all angles in
 * the TILTED frame, not the vehicle's.
 */
public record WeaponAimRange(double defaultYaw, double minYaw, double maxYaw, double minPitch, double maxPitch,
		List<WeaponPitchZone> pitchZones, double defaultPitch, double defaultRoll) {
	public static final Codec<WeaponAimRange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.optionalFieldOf("default_yaw", 0.0).forGetter(WeaponAimRange::defaultYaw),
			Codec.DOUBLE.optionalFieldOf("min_yaw", -180.0).forGetter(WeaponAimRange::minYaw),
			Codec.DOUBLE.optionalFieldOf("max_yaw", 180.0).forGetter(WeaponAimRange::maxYaw),
			Codec.DOUBLE.optionalFieldOf("min_pitch", -90.0).forGetter(WeaponAimRange::minPitch),
			Codec.DOUBLE.optionalFieldOf("max_pitch", 90.0).forGetter(WeaponAimRange::maxPitch),
			WeaponPitchZone.CODEC.listOf().optionalFieldOf("pitch_zones", List.of()).forGetter(WeaponAimRange::pitchZones),
			Codec.DOUBLE.optionalFieldOf("default_pitch", 0.0).forGetter(WeaponAimRange::defaultPitch),
			Codec.DOUBLE.optionalFieldOf("default_roll", 0.0).forGetter(WeaponAimRange::defaultRoll)
	).apply(instance, WeaponAimRange::new));

	/**
	 * This mount's tilt - see {@link WeaponMountTilt}. It is asked for every frame by every tilted part,
	 * so a tilted mount's is built once and shared (a record can't hold extra instance fields, hence
	 * the small cache keyed by the two angles). The untilted case - every existing weapon - is the
	 * shared {@link WeaponMountTilt#NONE} and never touches the cache.
	 */
	public WeaponMountTilt tilt() {
		if (defaultPitch == 0.0 && defaultRoll == 0.0) {
			return WeaponMountTilt.NONE;
		}
		return TILT_CACHE.computeIfAbsent(new TiltKey(defaultPitch, defaultRoll), k -> new WeaponMountTilt(k.pitch(), k.roll()));
	}

	private record TiltKey(double pitch, double roll) {
	}

	private static final java.util.concurrent.ConcurrentHashMap<TiltKey, WeaponMountTilt> TILT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

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

	/**
	 * How far, in degrees, short of a zone's edge a turret that has to wait stops. The edges of a zone
	 * are INCLUSIVE (see WeaponPitchZone#contains), so stopping exactly on one would already be inside
	 * the zone; this keeps the waiting turret just outside it, still under the limits it was under.
	 */
	public static final double BOUNDARY_EPSILON = 1.0e-3;

	/** Slack, in degrees, when deciding whether a pitch is within a limit - float rounding must not make a pitch that is AT the limit look outside it. */
	public static final double PITCH_TOLERANCE = 1.0e-3;

	/**
	 * Result of {@link #gateYawMove}: the yaw offset the turret may actually move to this tick, and - when
	 * it had to stop short - the pitch limits of the sector it is waiting to enter ({@code waitingFor},
	 * {min, max}), which the pitch must be brought inside before the yaw is allowed past the edge.
	 */
	public record YawGate(double allowedYawOffset, double[] waitingFor) {
		public boolean blocked() {
			return waitingFor != null;
		}
	}

	/**
	 * Decides how far a yaw move may go given the CURRENT pitch, so that the barrel is never swung into a
	 * sector whose pitch limits it is outside of: a turret about to cross into such a sector stops short
	 * of the edge and waits (the pitch, moving at the turret's own speed, is brought inside the limits
	 * first - see {@link YawGate#waitingFor}), then carries on. A pitch already inside the limits of
	 * every sector the move crosses is not held up at all.
	 *
	 * <p>{@code fromOffset} and {@code toOffset} are yaw offsets from default_yaw, in degrees, and the move
	 * runs the SHORT way between them (|toOffset - fromOffset| is taken as at most one half turn, the
	 * way the turret itself steps), so the move may cross +-180. Only edges strictly inside the move, up to
	 * and including its far end, count: an edge the turret is already standing on is not crossed again.
	 */
	public YawGate gateYawMove(double fromOffset, double toOffset, double currentPitch) {
		if (pitchZones.isEmpty()) {
			return new YawGate(toOffset, null);
		}
		double delta = toOffset - fromOffset;
		delta -= 360.0 * Math.floor((delta + 180.0) / 360.0);   // shortest signed way, in [-180, 180)
		if (delta == 0.0) {
			return new YawGate(toOffset, null);
		}
		double dir = Math.signum(delta);
		// every zone edge that lies on the (unwrapped) path, nearest first. "On the path" is judged in the direction of travel: an edge counts when the move starts short of it and gets to it or past it - the edge the move ARRIVES at counts, the one it starts on does not. (Testing against a fixed (low, high] range instead counted the arrival edge only when moving toward increasing yaw, and let a turret reach an edge in the other direction unchecked.)
		java.util.List<Double> edges = new java.util.ArrayList<>();
		for (WeaponPitchZone zone : pitchZones) {
			for (double edge : new double[]{zone.fromYaw(), zone.toYaw()}) {
				for (int turn = -2; turn <= 2; turn++) {
					double e = edge + 360.0 * turn;
					double along = (e - fromOffset) * dir;          // distance to the edge in the direction of travel
					if (along > 0.0 && along <= delta * dir) {
						edges.add(e);
					}
				}
			}
		}
		edges.sort((a, b) -> Double.compare(Math.abs(a - fromOffset), Math.abs(b - fromOffset)));
		for (double edge : edges) {
			// the limits just past the edge, in the direction of travel
			double[] beyond = limits(edge + dir * BOUNDARY_EPSILON * 2.0);
			if (currentPitch < beyond[0] - PITCH_TOLERANCE || currentPitch > beyond[1] + PITCH_TOLERANCE) {
				return new YawGate(edge - dir * BOUNDARY_EPSILON, beyond);
			}
		}
		return new YawGate(toOffset, null);
	}

	/** The pitch limits at a yaw offset, as {min, max} - the same ones {@link #effectiveMinPitch} and {@link #effectiveMaxPitch} give. */
	public double[] pitchLimitsAt(double yawOffset) {
		return limits(yawOffset);
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
