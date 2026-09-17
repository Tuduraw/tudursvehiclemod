package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** A named OBJ group/object (see ObjModel's "o"/"g" group support) that spins continuously around its own pivot point and axis, independent of the rest of the.. */
public record PartAnimation(
		String part,
		float pivotX, float pivotY, float pivotZ,
		float axisX, float axisY, float axisZ,
		float degreesPerTick,
		// In MC Heli's own source format, an
		// AddBlade entry immediately following an AddPartRotor is a CHILD
		// of that specific nacelle - see mcheli_convert.py's own
		// build_vtol_rotor_parts() doc. When present, this is that
		// parent VtolRotorPart's own resolved part name (e.g.
		// "$vtol_rotor0") - this spinning blade's own continuous spin
		// then ADDITIONALLY inherits that parent's own current tilt (see
		// VtolRotorPart's own resolveAngleDegrees() doc), rather than
		// staying frozen at a fixed angle while the rest of the nacelle
		// tilts out from under it. Empty/absent for every other spinning
		// part (a helicopter's own plain main/tail rotor included).
		Optional<String> vtolRotorParent
) {
	public static final Codec<PartAnimation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("part").forGetter(PartAnimation::part),
			Codec.FLOAT.optionalFieldOf("pivot_x", 0f).forGetter(PartAnimation::pivotX),
			Codec.FLOAT.optionalFieldOf("pivot_y", 0f).forGetter(PartAnimation::pivotY),
			Codec.FLOAT.optionalFieldOf("pivot_z", 0f).forGetter(PartAnimation::pivotZ),
			Codec.FLOAT.optionalFieldOf("axis_x", 0f).forGetter(PartAnimation::axisX),
			Codec.FLOAT.optionalFieldOf("axis_y", 1f).forGetter(PartAnimation::axisY),
			Codec.FLOAT.optionalFieldOf("axis_z", 0f).forGetter(PartAnimation::axisZ),
			Codec.FLOAT.optionalFieldOf("speed", 15f).forGetter(PartAnimation::degreesPerTick),
			Codec.STRING.optionalFieldOf("vtol_rotor_parent").forGetter(PartAnimation::vtolRotorParent)
	).apply(instance, PartAnimation::new));
}
