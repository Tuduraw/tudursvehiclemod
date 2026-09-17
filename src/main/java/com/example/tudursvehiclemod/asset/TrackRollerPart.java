package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** MC Heli's own AddTrackRoller - see Readme_Aircraft.txt's own doc:
 * "AddTrackRoller = X座標, Y座標, Z座標" ("Add track roller: X/Y/Z
 * position") - a small idler/roller wheel that visually rides along a
 * crawler track's own belt, always spinning around this vehicle's own
 * fixed X axis (no steering, unlike AddPartWheel).
 *
 * Rather than spinning at a flat, unrelated
 * constant rate (MC Heli's own TrackRollerRot directive, which this
 * project used to just copy over as a plain spinning_part), this now
 * spins in genuine lock-step with whichever CrawlerTrackPart it actually
 * sits on (matched by X sign - negative = right, positive = left, same
 * convention as CrawlerTrackPart's own x - see AbstractVehicleEntity's
 * own tudursvehiclemod$getTrackRollerSpinRotation() doc for exactly how
 * that match and the resulting rotation are computed) - a roller only
 * ever turns as fast as the belt actually underneath it is moving, at
 * whatever rate its own physical size demands, including correctly
 * following the belt back down again during braking/reversing.
 *
 * rotationsPerBlock is this roller's own "how many full rotations per
 * block of belt travel" - i.e. 1 / (this roller's own circumference,
 * π × diameter). Deliberately NOT computed inline every tick (or even
 * every render frame) here - see ServerObjModelTrackRollerBounds's own
 * doc for why this is instead derived ONCE from the model's own geometry
 * (same "record it like hit-detection data" caching convention already
 * used for attack-hitbox bounds) and cached to disk, only ever
 * recomputed if that cache is missing/stale. Present here as an
 * Optional purely to allow a manual override in the vehicle definition
 * JSON itself (skipping the geometry lookup for this one roller entirely)
 * - empty (the default) means "look up the cached/computed value for
 * this exact part name instead". */
public record TrackRollerPart(
		String part,
		double pivotX, double pivotY, double pivotZ,
		Optional<Float> rotationsPerBlock
) {
	public static final Codec<TrackRollerPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("part").forGetter(TrackRollerPart::part),
			Codec.DOUBLE.fieldOf("pivot_x").forGetter(TrackRollerPart::pivotX),
			Codec.DOUBLE.fieldOf("pivot_y").forGetter(TrackRollerPart::pivotY),
			Codec.DOUBLE.fieldOf("pivot_z").forGetter(TrackRollerPart::pivotZ),
			Codec.FLOAT.optionalFieldOf("rotations_per_block").forGetter(TrackRollerPart::rotationsPerBlock)
	).apply(instance, TrackRollerPart::new));
}
