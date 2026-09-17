package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Only meaningful for a TogglePart whose trigger is "wing_fold" (see
 * AddPartWing/VariableSweepWing/SweepWingSpeed) - variableSweepWing=false (the
 * default) means the wing can only fold/unfold while stationary, and the
 * vehicle can't move at all while folded (a storage fold). variableSweepWing=true
 * means it can be swept in flight (a dedicated key, not gated on speed), and
 * while folded/folding, sweepWingSpeed REPLACES this vehicle's own max_speed
 * as its upper speed limit (matching a real variable-sweep aircraft: swept
 * back = faster). */
public record WingSweepConfig(
		boolean variableSweepWing,
		float sweepWingSpeed
) {
	public static final Codec<WingSweepConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("variable_sweep_wing", false).forGetter(WingSweepConfig::variableSweepWing),
			Codec.FLOAT.optionalFieldOf("sweep_wing_speed", 0f).forGetter(WingSweepConfig::sweepWingSpeed)
	).apply(instance, WingSweepConfig::new));
}
