package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** MC Heli's own HideEntity/EntityWidth/EntityHeight, grouped into their own nested record purely to stay under RecordCodecBuilder's 16-field group() limit (VehicleExtras was already close to it) - see client.mixin.LivingEntityRendererMixin for where these are actually applied to a mounted passenger's own rendering. */
public record PassengerDisplay(
		// HideEntity - hides every mounted passenger's own rendering entirely while true (they're still fully present/interactive, just invisible) - false (MC Heli's own default) renders them normally.
		boolean hideEntity,
		// EntityWidth/EntityHeight - scales a mounted passenger's own rendered size (1.0 = unscaled, matching MC Heli's own "not set" default here - the readme's own listed default of 0.9 is that one example vehicle's own choice, not MC Heli's actual fallback when omitted entirely).
		float entityWidth,
		float entityHeight
) {
	public static final PassengerDisplay DEFAULT = new PassengerDisplay(false, 1.0f, 1.0f);

	public static final Codec<PassengerDisplay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("hide_entity", false).forGetter(PassengerDisplay::hideEntity),
			Codec.FLOAT.optionalFieldOf("entity_width", 1.0f).forGetter(PassengerDisplay::entityWidth),
			Codec.FLOAT.optionalFieldOf("entity_height", 1.0f).forGetter(PassengerDisplay::entityHeight)
	).apply(instance, PassengerDisplay::new));
}
