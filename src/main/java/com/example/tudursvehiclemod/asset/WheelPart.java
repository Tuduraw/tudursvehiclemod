package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** MC Heli's own AddPartWheel - see Readme_Aircraft.txt's own doc:
 * "AddPartWheel = X座標, Y座標, Z座標, 舵角 [, 回転軸X,Y,Z, 回転位置X,Y,Z]"
 * ("Add tire: X/Y/Z position, steer angle [, steering axis X/Y/Z, steering
 * pivot position X/Y/Z]").
 *
 * Combines TWO separate rotations, composed together (spin applied first,
 * in the wheel's own local frame, with steering applied second/outermost -
 * see AbstractVehicleEntity's own tudursvehiclemod$getWheelPartRotation()
 * doc for exactly why that order):
 * - A continuous SPIN around this wheel's own local X axis (matching
 * AddTrackRoller's own convention - not separately configurable here,
 * since MC Heli's own doc never gives AddPartWheel an explicit spin axis
 * at all), driven by the vehicle's own current speed and this weapon's
 * (sic - this VEHICLE's) own PartWheelRot stat.
 * - A STEERING rotation around a separate axis/pivot (X,Y,Z default -
 * (0,1,0)/this same wheel's own pivotX/Y/Z, per MC Heli's own doc:
 * "回転軸を省略すると(0,1,0)が使用される" - a vertical king-pin axis,
 * the ordinary case for a steered wheel), up to steerAngle degrees in
 * either direction, proportional to the vehicle's own current steering
 * input. */
public record WheelPart(
		String part,
		double pivotX, double pivotY, double pivotZ,
		float steerAngle,
		double steerAxisX, double steerAxisY, double steerAxisZ,
		double steerPivotX, double steerPivotY, double steerPivotZ
) {
	public static final Codec<WheelPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("part").forGetter(WheelPart::part),
			Codec.DOUBLE.fieldOf("pivot_x").forGetter(WheelPart::pivotX),
			Codec.DOUBLE.fieldOf("pivot_y").forGetter(WheelPart::pivotY),
			Codec.DOUBLE.fieldOf("pivot_z").forGetter(WheelPart::pivotZ),
			Codec.FLOAT.optionalFieldOf("steer_angle", 0.0f).forGetter(WheelPart::steerAngle),
			Codec.DOUBLE.optionalFieldOf("steer_axis_x", 0.0).forGetter(WheelPart::steerAxisX),
			Codec.DOUBLE.optionalFieldOf("steer_axis_y", 1.0).forGetter(WheelPart::steerAxisY),
			Codec.DOUBLE.optionalFieldOf("steer_axis_z", 0.0).forGetter(WheelPart::steerAxisZ),
			Codec.DOUBLE.optionalFieldOf("steer_pivot_x", 0.0).forGetter(WheelPart::steerPivotX),
			Codec.DOUBLE.optionalFieldOf("steer_pivot_y", 0.0).forGetter(WheelPart::steerPivotY),
			Codec.DOUBLE.optionalFieldOf("steer_pivot_z", 0.0).forGetter(WheelPart::steerPivotZ)
	).apply(instance, WheelPart::new));
}
