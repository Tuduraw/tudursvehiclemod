package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A fixed point of light, mounted on the vehicle, with no aim/steering concept at all and no cone shape - just a position (relative to the vehicle's own body, same convention as SearchLightPart's own pivot) and a colour.
 *
 * WHY THIS IS SEPARATE FROM SearchLightPart RATHER THAN REUSING IT: a search light's own record carries an entire cone's worth of fields (length, endRadius, follow mode, steering) that a nav light has no use for at all - forcing a nav light through that shape would mean either populating meaningless dummy values for all of them or complicating SearchLightPart's own doc with a "some fields don't apply" caveat. A plain position+colour record is a truer fit for what MC Heli's own AddNavLight-equivalent purpose actually needs, and keeps each record's own doc honest about what it represents.
 *
 * Lights unconditionally whenever the vehicle exists and is loaded - unlike a search light, there is no separate "on/off" switch of its own here, matching how a real aircraft's nav lights or a ship's running lights are typically always lit rather than player-toggled. colorArgb's own alpha drives brightness exactly the way SearchLightPart's own startColorArgb does (see SearchLightIllumination's own doc on that convention), so 0 alpha is the way to author a light that exists in the model but currently contributes no illumination. */
public record NavLightPart(
		String part,
		double pivotX, double pivotY, double pivotZ,
		int colorArgb
) {
	public static final Codec<NavLightPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("part").forGetter(NavLightPart::part),
			Codec.DOUBLE.fieldOf("pivot_x").forGetter(NavLightPart::pivotX),
			Codec.DOUBLE.fieldOf("pivot_y").forGetter(NavLightPart::pivotY),
			Codec.DOUBLE.fieldOf("pivot_z").forGetter(NavLightPart::pivotZ),
			Codec.INT.fieldOf("color_argb").forGetter(NavLightPart::colorArgb)
	).apply(instance, NavLightPart::new));
}
