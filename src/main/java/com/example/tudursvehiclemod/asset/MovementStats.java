package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Groups VehicleDefinition's 13 movement/sound-related fields (max_speed, acceleration, turn_speed, step_height, gravity, on_ground_pitch, reverse_throttle, dive_max_speed, engine_sound_volume, pivot_turn_throttle, throttle_up_down, weight_type, throttle_switch_hold_ticks) into one nested field purely.. */
public record MovementStats(
		float maxSpeed, float acceleration, float turnSpeed, float stepHeight, float gravity, float onGroundPitch,
		// reverse_throttle - see AbstractVehicleEntity's own doc. mcheli_convert.py fills this with a vehicle-type-specific default if the source file doesn't set its own ReverseThrottle - this codec-level default (0) is only ever actually used for a hand-authored/edited JSON that omits the field entirely.
		float reverseThrottle,
		// dive_max_speed - SubmarineEntity-specific: this hull's own max speed while DIVING, separate from maxSpeed above (which, for a submarine, is used for SURFACED mode only - see SubmarineEntity's own doc). Empty (the default, since MC Heli's own format has no equivalent setting) means "use maxSpeed / 3 instead" - see SubmarineEntity's own tudursvehiclemod$getDiveMaxSpeed() doc.
		Optional<Float> diveMaxSpeed,
		// Engine_sound_volume - follows MC Heli's own documented SoundVolume convention for weapons (see WeaponStats's own soundVolume doc) applied to this vehicle's own engine_sound instead: 1.0 is Minecraft's own "normal max" per that same convention, above 1.0 makes the engine sound audible from further away (at the same, still-capped-at-1.0 peak loudness) rather than exceeding max loudness, and below 1.0 shrinks both loudness and range together. Not an MC Heli-native directive (MC Heli has no per-vehicle engine sound volume control at all) - this project's own new addition. Defaults to 3.0 (audible from 3x the normal range) when a vehicle's own config doesn't specify this field at all. See client.sound.VanillaStyleSoundAttenuation's own doc for exactly how this gets applied.
		float engineSoundVolume,
		// pivot_turn_throttle - CarEntity-specific (see Readme_Aircraft.txt's own PivotTurnThrottle doc): the MINIMUM speed (as a fraction of max_speed, 0-1) this vehicle needs before it's actually allowed to turn at all - 0 (the default) means it can pivot in place with no speed at all ("超信地旋回"/zero-radius turn), same as every ground vehicle here always could before this field existed. A value above 0 means a real tank-style "信地旋回" (turns only once moving at least this fast) - see CarEntity's own tudursvehiclemod$updateSteering() doc for exactly how this gets enforced (including automatically throttling UP toward this minimum whenever the player tries to turn without it).
		float pivotTurnThrottle,
		// wheel_rotation_speed (PartWheelRot) - see WheelPart's own doc / AbstractVehicleEntity's own tudursvehiclemod$getWheelPartSpinPhase() doc for exactly how this gets applied. Per Readme_Aircraft.txt's own doc: "タイヤの回転スピード、大きいほど速い" (tire rotation speed, bigger = faster) - a plain scaling factor applied to the vehicle's own current speed, same "direct value, no conversion formula" convention as RotorSpeed/TrackRollerRot.
		float wheelRotationSpeed,
		// Throttle_up_down (ThrottleUpDown) - MC Heli's own directive controlling throttle sensitivity while holding W/S - see AbstractVehicleEntity's own updateThrottle() doc for exactly how the "step" parameter it takes is used. treated as a MULTIPLIER on each vehicle type's own existing default step (0.03/0.02/0.015 for Car/Ship/Aircraft respectively, and Submarine's own THROTTLE_STEP), NOT an absolute step value directly - MC Heli's own scale for this directive differs substantially from this project's own, and small absolute values (e.g. 0.02) are awkward to reason about directly. 1.0 (used whenever the source file doesn't set its own ThrottleUpDown) means "unchanged from that vehicle type's own existing default"; 2.0 ramps twice as fast, 0.5 half as fast, and so on.
		Optional<Float> throttleUpDown,
		// weight_type (WeightType) - see WeightType's own doc for the full ported directive text and semantics.
		WeightType weightType,
		// Throttle_switch_hold_ticks - configurable duration (in ticks) of the "hold at exactly 0%" pause when throttle crosses from positive to negative or back (see AbstractVehicleEntity's own THROTTLE_SWITCH_HOLD_TICKS/updateThrottle()/tudursvehiclemod$applyThrottleSwitchHold() doc for exactly how this is used). Empty (the default, when the source file doesn't set its own value) preserves the current 40-tick default every vehicle type here has always used.
		Optional<Integer> throttleSwitchHoldTicks
) {
	public static final MapCodec<MovementStats> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.FLOAT.optionalFieldOf("max_speed", 1.0f).forGetter(MovementStats::maxSpeed),
			Codec.FLOAT.optionalFieldOf("acceleration", 0.05f).forGetter(MovementStats::acceleration),
			Codec.FLOAT.optionalFieldOf("turn_speed", 3.0f).forGetter(MovementStats::turnSpeed),
			Codec.FLOAT.optionalFieldOf("step_height", 1.0f).forGetter(MovementStats::stepHeight),
			Codec.FLOAT.optionalFieldOf("gravity", -0.04f).forGetter(MovementStats::gravity),
			// Pitch angle (degrees) this vehicle should ease towards while grounded.
			Codec.FLOAT.optionalFieldOf("on_ground_pitch", 0.0f).forGetter(MovementStats::onGroundPitch),
			Codec.FLOAT.optionalFieldOf("reverse_throttle", 0.0f).forGetter(MovementStats::reverseThrottle),
			Codec.FLOAT.optionalFieldOf("dive_max_speed").forGetter(MovementStats::diveMaxSpeed),
			Codec.FLOAT.optionalFieldOf("engine_sound_volume", 3.0f).forGetter(MovementStats::engineSoundVolume),
			Codec.FLOAT.optionalFieldOf("pivot_turn_throttle", 0.0f).forGetter(MovementStats::pivotTurnThrottle),
			Codec.FLOAT.optionalFieldOf("wheel_rotation_speed", 40.0f).forGetter(MovementStats::wheelRotationSpeed),
			Codec.FLOAT.optionalFieldOf("throttle_up_down").forGetter(MovementStats::throttleUpDown),
			WeightType.CODEC.optionalFieldOf("weight_type", WeightType.UNKNOWN).forGetter(MovementStats::weightType),
			Codec.INT.optionalFieldOf("throttle_switch_hold_ticks").forGetter(MovementStats::throttleSwitchHoldTicks)
	).apply(instance, MovementStats::new));
}
