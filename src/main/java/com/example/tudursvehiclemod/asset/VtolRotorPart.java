package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** AddPartRotor - a named OBJ group/object (typically an engine nacelle)
 * that tilts through a limited angle between VtolEntity's own two flight
 * modes (helicopter/aircraft), driven by that vehicle's own mode-switch
 * transition progress (see VtolEntity's own tudursvehiclemod$getVtolTiltProgress()
 * doc: 0 = fully helicopter-mode orientation, 1 = fully aircraft-mode
 * orientation) rather than spinning continuously like PartAnimation, or
 * easing open/closed between two arbitrary states like TogglePart.
 *
 * Deliberately a single, fixed TILT_MAX_ANGLE_DEGREES (see that constant's
 * own doc for the actual value and reasoning) rather than a per-part
 * configurable angle like TogglePart's own maxAngle - every VTOL nacelle
 * tilts through the exact same helicopter-vs-aircraft range by
 * definition, so there's nothing for a per-part angle to actually
 * customize here. */
public record VtolRotorPart(
		String part,
		float pivotX, float pivotY, float pivotZ,
		float axisX, float axisY, float axisZ
) {
	/** The full tilt range this part rotates through between this
	 * vehicle's own two flight modes. Shared between
	 * client.render.VehicleEntityRenderer's own render() and
	 * AbstractVehicleEntity's own hit-detection math, so both use the
	 * exact same tilt angle for any given tudursvehiclemod$getVtolTiltProgress()
	 * - see resolveAngleDegrees()'s own doc for how the two actually
	 * combine. A real tilt-rotor's own nacelle rotates through
	 * (approximately) this same range between vertical lift and forward
	 * flight, and there's no per-vehicle config for this since every VTOL
	 * here shares the same two-mode design by definition. */
	public static final float TILT_MAX_ANGLE_DEGREES = 90f;

	public static final Codec<VtolRotorPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("part").forGetter(VtolRotorPart::part),
			Codec.FLOAT.optionalFieldOf("pivot_x", 0f).forGetter(VtolRotorPart::pivotX),
			Codec.FLOAT.optionalFieldOf("pivot_y", 0f).forGetter(VtolRotorPart::pivotY),
			Codec.FLOAT.optionalFieldOf("pivot_z", 0f).forGetter(VtolRotorPart::pivotZ),
			// The tilt direction came out
			// backwards compared to the original MC Heli vehicle: this
			// used to be collapsed into a single "x"/"y"/"z" letter via a
			// magnitude-only comparison on the converter's own side
			// (mcheli_convert.py's own build_vtol_rotor_parts()),
			// discarding sign entirely (and even mis-selecting the axis
			// outright whenever a negative component was actually the
			// largest-magnitude one, since a plain max() comparison
			// doesn't account for sign at all). Now a full signed vector,
			// matching PartAnimation/TogglePart's own established
			// convention, so the source data's own sign - and therefore
			// this part's own actual rotation direction - survives the
			// conversion intact.
			Codec.FLOAT.optionalFieldOf("axis_x", 1f).forGetter(VtolRotorPart::axisX),
			Codec.FLOAT.optionalFieldOf("axis_y", 0f).forGetter(VtolRotorPart::axisY),
			Codec.FLOAT.optionalFieldOf("axis_z", 0f).forGetter(VtolRotorPart::axisZ)
	).apply(instance, VtolRotorPart::new));

	/** Resolves VtolEntity's own tudursvehiclemod$getVtolTiltProgress()
	 * (0 = helicopter mode, 1 = aircraft mode) into the actual rotation
	 * angle (degrees) this part's own tilt applies, around its own axisX/
	 * AxisY/axisZ. MC Heli's own source vehicle
	 * displays its own AIRCRAFT-mode orientation as this part's own
	 * as-modeled rest pose (i.e. no rotation applied at all) - the
	 * opposite of what an earlier version of this method assumed
	 * (helicopter mode = rest pose instead) - so this is (1 - progress) *
	 * TILT_MAX_ANGLE_DEGREES, not progress * TILT_MAX_ANGLE_DEGREES:
	 * fully rotated at progress=0 (helicopter mode), all the way back to
	 * 0 degrees (this part's own rest pose) at progress=1 (aircraft
	 * mode). */
	public static float resolveAngleDegrees(float tiltProgress) {
		return (1f - tiltProgress) * TILT_MAX_ANGLE_DEGREES;
	}
}
