package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** MC Heli's own AddPartSteeringWheel - see Readme_Aircraft.txt's own doc:
 * "AddPartSteeringWheel = X座標, Y座標, Z座標, 回転軸X, 軸Y, 軸Z, 最大回転角度"
 * ("Add steering wheel: X/Y/Z position, rotation axis X/Y/Z, max rotation
 * angle"). Unlike WheelPart, this is JUST the visible steering-wheel model
 * itself turning in the driver's own hands - no separate spin, and no
 * separate steering-vs-spin pivot distinction (this part's own single
 * pivot/axis IS the rotation). Rotates proportionally to the vehicle's own
 * current steering input, up to maxAngle in either direction. */
public record SteeringWheelPart(
		String part,
		double pivotX, double pivotY, double pivotZ,
		double axisX, double axisY, double axisZ,
		float maxAngle
) {
	public static final Codec<SteeringWheelPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("part").forGetter(SteeringWheelPart::part),
			Codec.DOUBLE.fieldOf("pivot_x").forGetter(SteeringWheelPart::pivotX),
			Codec.DOUBLE.fieldOf("pivot_y").forGetter(SteeringWheelPart::pivotY),
			Codec.DOUBLE.fieldOf("pivot_z").forGetter(SteeringWheelPart::pivotZ),
			Codec.DOUBLE.optionalFieldOf("axis_x", 0.0).forGetter(SteeringWheelPart::axisX),
			Codec.DOUBLE.optionalFieldOf("axis_y", 0.0).forGetter(SteeringWheelPart::axisY),
			Codec.DOUBLE.optionalFieldOf("axis_z", 1.0).forGetter(SteeringWheelPart::axisZ),
			Codec.FLOAT.optionalFieldOf("max_angle", 130.0f).forGetter(SteeringWheelPart::maxAngle)
	).apply(instance, SteeringWheelPart::new));
}
