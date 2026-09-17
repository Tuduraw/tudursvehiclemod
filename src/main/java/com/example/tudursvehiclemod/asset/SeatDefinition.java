package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** A single passenger position relative to the vehicle's origin.
 *
 * dismountOffsetX/Y/Z each independently override where THIS seat's own occupant ends up standing
 * after dismounting, in the same vehicle-local (pre-rotation) coordinate space as offsetX/Y/Z
 * themselves - the rotation that already applies to the seat's own offset applies here too. Any
 * axis left absent falls back to its own default: X and Z from this seat's own offsetX/offsetZ
 * (exactly the pre-existing behavior), Y from the ground surface directly beneath the vehicle at
 * dismount time (see AbstractVehicleEntity's own removePassenger() doc) - neither default is a
 * fixed number bakeable into this record itself, since both depend on the vehicle's own position
 * and rotation at the moment of dismounting. */
public record SeatDefinition(String name, double offsetX, double offsetY, double offsetZ, boolean driver,
							  boolean enableParachuting, List<CameraPositionEntry> cameraPositions,
							  java.util.Optional<Double> dismountOffsetX, java.util.Optional<Double> dismountOffsetY,
							  java.util.Optional<Double> dismountOffsetZ) {

	public static final Codec<SeatDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(SeatDefinition::name),
			Codec.DOUBLE.fieldOf("offset_x").forGetter(SeatDefinition::offsetX),
			Codec.DOUBLE.fieldOf("offset_y").forGetter(SeatDefinition::offsetY),
			Codec.DOUBLE.fieldOf("offset_z").forGetter(SeatDefinition::offsetZ),
			Codec.BOOL.optionalFieldOf("driver", false).forGetter(SeatDefinition::driver),
			// Absent (the default) for any seat authored before this feature existed, preserving existing behavior exactly (no seat could parachute-jump before this).
			Codec.BOOL.optionalFieldOf("enable_parachuting", false).forGetter(SeatDefinition::enableParachuting),
			// camera_positions - MC Heli's own doc notes that setting several lets you cycle between each viewpoint with a key - a plain list rather than a single optional offset (this record's own earlier shape) so multiple CameraPosition entries can actually be represented at all. Empty (the default) means this seat has no explicit camera position configured, falling back to the passenger's own standing eye height the same way an empty list always has here.
			Codec.list(CameraPositionEntry.CODEC).optionalFieldOf("camera_positions", List.of()).forGetter(SeatDefinition::cameraPositions),
			// dismount_x/y/z - see this record's own doc. Absent (the default) for every seat authored before this feature existed, so an existing vehicle keeps behaving exactly as this session's own ground-surface fix now specifies rather than picking up some arbitrary baked-in coordinate.
			Codec.DOUBLE.optionalFieldOf("dismount_x").forGetter(SeatDefinition::dismountOffsetX),
			Codec.DOUBLE.optionalFieldOf("dismount_y").forGetter(SeatDefinition::dismountOffsetY),
			Codec.DOUBLE.optionalFieldOf("dismount_z").forGetter(SeatDefinition::dismountOffsetZ)
	).apply(instance, SeatDefinition::new));
}
