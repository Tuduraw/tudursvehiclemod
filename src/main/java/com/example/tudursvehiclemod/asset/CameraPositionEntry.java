package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One entry of MC Heli's own CameraPosition directive - see Readme_Aircraft.txt's
 * own doc:
 * "CameraPosition = X, Y, Z [, forceCamera, fixedYaw, fixedPitch]"
 * "複数設定すると、Hキーでそれぞれ視点を変えられる" (setting several lets you
 * cycle between each viewpoint with a key - see AbstractVehicleEntity's own
 * tudursvehiclemod$cycleCameraPosition() doc for exactly how this project
 * does that, since the 'H' key itself is already taken by hatch/canopy
 * toggle here).
 *
 * forceCamera/fixedYaw/fixedPitch are parsed and stored here for a future
 * pass (see README's own doc) - only x/y/z (the actual eye position) is
 * currently wired up to anything. */
public record CameraPositionEntry(
		double x, double y, double z,
		// 4th param - per MC Heli's own doc: "常にカメラからの視点になる" (always uses the camera viewpoint, i.e. never toggles to the passenger's own first-person view) when true.
		boolean forceCamera,
		// 5th/6th params - per MC Heli's own doc: a fixed horizontal/vertical camera angle, independent of the passenger's own mouse look, when present.
		java.util.Optional<Double> fixedYaw,
		java.util.Optional<Double> fixedPitch
) {
	public static final Codec<CameraPositionEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("x").forGetter(CameraPositionEntry::x),
			Codec.DOUBLE.fieldOf("y").forGetter(CameraPositionEntry::y),
			Codec.DOUBLE.fieldOf("z").forGetter(CameraPositionEntry::z),
			Codec.BOOL.optionalFieldOf("force_camera", false).forGetter(CameraPositionEntry::forceCamera),
			Codec.DOUBLE.optionalFieldOf("fixed_yaw").forGetter(CameraPositionEntry::fixedYaw),
			Codec.DOUBLE.optionalFieldOf("fixed_pitch").forGetter(CameraPositionEntry::fixedPitch)
	).apply(instance, CameraPositionEntry::new));
}
