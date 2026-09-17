package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** RelX/relY/relZ mirror MC Heli's own "降下位置X, Y, Z" (relative to the vehicle's own origin, rotated by its current yaw the same way any other seat/muzzle offset already is), and intervalTicks mirrors MC Heli's own "降下間隔(1/20秒)" - already expressed in ticks (1/20 second = 1 tick), so no unit conversion is needed at all, unlike some of MC Heli's other time-like fields. Null (absent) on VehicleDefinition means this vehicle has no mob-drop capability at all - the assigned key simply does nothing. */
public record MobDropOption(double relX, double relY, double relZ, int intervalTicks) {

	public static final Codec<MobDropOption> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("rel_x").forGetter(MobDropOption::relX),
			Codec.DOUBLE.fieldOf("rel_y").forGetter(MobDropOption::relY),
			Codec.DOUBLE.fieldOf("rel_z").forGetter(MobDropOption::relZ),
			Codec.INT.fieldOf("interval_ticks").forGetter(MobDropOption::intervalTicks)
	).apply(instance, MobDropOption::new));
}
