package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** MC Heli's own AddSearchLight/AddFixedSearchLight/AddSteeringSearchLight - a directional cone of light mounted on the vehicle, primarily for night search/illumination. this mod previously had no equivalent at all.
 *
 * The three MC Heli directives differ only in what drives their own aim direction - see {@link #followMode()}'s own doc - and share every other field, so all three collapse into this one record with followMode as the distinguishing tag rather than three separate classes.
 *
 * Per Readme_Aircraft.txt's own documented syntax:
 * {@code AddSearchLight = posX, posY, posZ, startColor(ARGB hex), endColor(ARGB hex), length, endRadius, yaw, pitch[, steerAngle]} */
public record SearchLightPart(
		String part,
		double pivotX, double pivotY, double pivotZ,
		int startColorArgb, int endColorArgb,
		float length, float endRadius,
		float baseYaw, float basePitch,
		float steerAngle,
		FollowMode followMode
) {
	/** Which of MC Heli's three AddSearchLight variants this came from - see SearchLightPart's own doc. Each is a different rule for the LIVE yaw/pitch actually used each frame, always starting from baseYaw/basePitch as an offset. */
	public enum FollowMode {
		/** AddSearchLight - always aimed along whichever occupant is currently tracking it (the same "who's steering this part" resolution weapon parts already use), the same way a manned turret follows its own gunner's view. */
		PILOT_VIEW,
		/** AddFixedSearchLight - never moves on its own; baseYaw/basePitch (relative to the vehicle's own body) is the light's own permanent aim. */
		FIXED,
		/** AddSteeringSearchLight - follows the vehicle's own current steering angle (the same input WheelPart's own steer angle already reads), scaled by this part's own steerAngle as MC Heli's own maximum. */
		STEERING
	}

	public static final Codec<SearchLightPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("part").forGetter(SearchLightPart::part),
			Codec.DOUBLE.fieldOf("pivot_x").forGetter(SearchLightPart::pivotX),
			Codec.DOUBLE.fieldOf("pivot_y").forGetter(SearchLightPart::pivotY),
			Codec.DOUBLE.fieldOf("pivot_z").forGetter(SearchLightPart::pivotZ),
			Codec.INT.fieldOf("start_color_argb").forGetter(SearchLightPart::startColorArgb),
			Codec.INT.fieldOf("end_color_argb").forGetter(SearchLightPart::endColorArgb),
			Codec.FLOAT.fieldOf("length").forGetter(SearchLightPart::length),
			Codec.FLOAT.fieldOf("end_radius").forGetter(SearchLightPart::endRadius),
			Codec.FLOAT.optionalFieldOf("yaw", 0.0f).forGetter(SearchLightPart::baseYaw),
			Codec.FLOAT.optionalFieldOf("pitch", 0.0f).forGetter(SearchLightPart::basePitch),
			Codec.FLOAT.optionalFieldOf("steer_angle", 0.0f).forGetter(SearchLightPart::steerAngle),
			Codec.STRING.xmap(FollowMode::valueOf, FollowMode::name)
					.fieldOf("follow_mode").forGetter(SearchLightPart::followMode)
	).apply(instance, SearchLightPart::new));
}
