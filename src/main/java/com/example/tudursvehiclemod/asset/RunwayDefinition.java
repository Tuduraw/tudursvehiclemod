package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A ship-local rectangular region defined on the VEHICLE itself (not tied to any one WeaponType.CARRIER weapon file), treated as a flat plane of solid blocks for landing gear/stall/collision - see entity.AircraftEntity's own tudursvehiclemod$updateCarrierRunwayGrounding() doc. All coordinates relative to this vehicle's own entity position, rotated with its current yaw. centerX/startZ/endZ/heightY define a rectangle: width blocks wide (centered on centerX), spanning startZ to endZ (either order), at height heightY. Parsed from a vehicle.json file's own "runway" object: {"width", "center_x", "height_y", "start_z", "end_z"} - absent means no runway.
 *
 * hatchGated/hatchOffsetX/Y/Z/hatchMoveSpeed: two hatch-driven variants, not bound to any named TogglePart. hatchGated (rotation-type hatch, e.g. a landing craft's bow ramp): runway exists only while isHatchOpen() is true. hatchOffsetX/Y/Z + hatchMoveSpeed (slide-type hatch, e.g. a carrier elevator): effective center/height/start/end ease toward the offset values while open, base values while closed - see AbstractVehicleEntity's own updateRunwayHatchProgress(). Offsets default to 0.0 (no movement); hatchMoveSpeed defaults to 1.0.
 */
public record RunwayDefinition(double width, double centerX, double heightY, double startZ, double endZ,
		boolean hatchGated, double hatchOffsetX, double hatchOffsetY, double hatchOffsetZ, double hatchMoveSpeed) {
	public static final Codec<RunwayDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("width").forGetter(RunwayDefinition::width),
			Codec.DOUBLE.optionalFieldOf("center_x", 0.0).forGetter(RunwayDefinition::centerX),
			Codec.DOUBLE.optionalFieldOf("height_y", 0.0).forGetter(RunwayDefinition::heightY),
			Codec.DOUBLE.fieldOf("start_z").forGetter(RunwayDefinition::startZ),
			Codec.DOUBLE.fieldOf("end_z").forGetter(RunwayDefinition::endZ),
			Codec.BOOL.optionalFieldOf("hatch_gated", false).forGetter(RunwayDefinition::hatchGated),
			Codec.DOUBLE.optionalFieldOf("hatch_offset_x", 0.0).forGetter(RunwayDefinition::hatchOffsetX),
			Codec.DOUBLE.optionalFieldOf("hatch_offset_y", 0.0).forGetter(RunwayDefinition::hatchOffsetY),
			Codec.DOUBLE.optionalFieldOf("hatch_offset_z", 0.0).forGetter(RunwayDefinition::hatchOffsetZ),
			Codec.DOUBLE.optionalFieldOf("hatch_move_speed", 1.0).forGetter(RunwayDefinition::hatchMoveSpeed)
	).apply(instance, RunwayDefinition::new));

	/** Per hatchOffsetX/Y/Z's own doc: returns a NEW RunwayDefinition with this tick's own hatch-progress offset already baked into centerX/heightY/startZ/endZ, so every existing piece of code that lays out tiles/checks grounding against a RunwayDefinition's own geometry (the server's own tudursvehiclemod$updateCarrierRunwayPlatform(), and AbstractVehicleEntity's own tudursvehiclemod$computeCarrierRunwayTileWorldPosClientSide()) can keep reading centerX()/heightY()/startZ()/endZ() completely unchanged, with zero awareness that a hatch offset is even involved. progress is expected in [0, 1] (0 = fully closed/base position, 1 = fully open/offset) - not clamped here, since updateRunwayHatchProgress() already guarantees that range. A no-op (returns this exact instance) whenever every offset is exactly 0, avoiding a pointless allocation for the (default, far more common) case of a runway with no hatch tracking at all. */
	public RunwayDefinition tudursvehiclemod$withHatchProgress(float progress) {
		if (this.hatchOffsetX == 0.0 && this.hatchOffsetY == 0.0 && this.hatchOffsetZ == 0.0) {
			return this;
		}
		return new RunwayDefinition(this.width, this.centerX + progress * this.hatchOffsetX, this.heightY + progress * this.hatchOffsetY,
				this.startZ + progress * this.hatchOffsetZ, this.endZ + progress * this.hatchOffsetZ,
				this.hatchGated, this.hatchOffsetX, this.hatchOffsetY, this.hatchOffsetZ, this.hatchMoveSpeed);
	}
}
