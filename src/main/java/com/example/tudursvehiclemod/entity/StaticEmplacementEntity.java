package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** MC Heli's own "Vehicles" category has always been
 * documented (by that mod itself, not this project's own interpretation)
 * as a distinct thing from its Helicopter/Plane/Tank categories: "Vehicle
 * is something which cannot be moved, such as the Utibi, or a turret."
 * (from that mod's own community documentation for authoring new
 * content). Converting that category as though it were another ground
 * vehicle (this project's own earlier behavior, mapping it straight onto
 * CarEntity) gave it throttle/steering/gravity physics it was never
 * actually meant to have at all, which is presumably a good part of why
 * compatibility with real "Vehicles"-category content felt poor.
 *
 * This entity has NO steering/throttle capability whatsoever - see
 * updateVehicleMovement()'s own doc for why it still falls under gravity
 * like anything else. It still supports everything movement-INDEPENDENT
 * that AbstractVehicleEntity already provides generically: seats (a
 * gunner can still ride it), WeaponPart-based turret tracking (a gunner
 * can still aim and fire - the turret barrel rotating is a PART-level
 * animation, entirely separate from the vehicle AS A WHOLE moving
 * anywhere), TogglePart-based hatches/covers, custom hit detection,
 * damage/destruction, and so on - it is fundamentally a fixed emplacement
 * with a rotating weapon on top, not a vehicle that happens to be
 * parked. */
public class StaticEmplacementEntity extends AbstractVehicleEntity {

	public StaticEmplacementEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected Identifier defaultDefinitionId() {
		return Identifier.of(VehicleMod.MOD_ID, "static_emplacement");
	}

	/** An earlier version of this set velocity to
	 * exactly zero every tick, unconditionally - which left this entity
	 * floating wherever it was first spawned and never moved from there
	 * again, since NOTHING ever pulled it down to the ground the way
	 * every other vehicle's own gravity naturally does. A newly-spawned
	 * vehicle's own initial position (see network.ModNetworking's own
	 * spawn-item handling) is placed a few blocks in front of the
	 * player's own EYE level along their look direction - not
	 * necessarily anywhere near actual ground level at all - so without
	 * gravity to settle it, it just stayed floating there indefinitely,
	 * reading as "spawns too high" even though nothing about the
	 * conversion's own Y-coordinate math had actually changed at all.
	 *
	 * This still falls under gravity and settles onto solid ground
	 * exactly like every other vehicle does (same isOnGround()-gated
	 * approach as CarEntity's own doc) - it just never responds to any
	 * throttle/steering input once there, matching MC Heli's own "cannot
	 * be MOVED" Vehicle category precisely: it can fall into place like
	 * any other object would, it just can't be DRIVEN anywhere
	 * afterwards. */
	@Override
	protected void updateVehicleMovement(VehicleDefinition def) {
		if (this.getControllingPassenger() == null && this.getEntityWorld().isClient()) {
			// Skips this method's own local physics recomputation for an unpiloted vehicle on the client, but still applies move() with the current (network-synced) velocity - matching AircraftEntity's own established pattern.
			this.move(MovementType.SELF, this.getVelocity());
			return;
		}
		if (this.isOnGround()) {
			this.setVelocity(0.0, 0.0, 0.0);
		} else if (def.isFloatCapable() && this.tudursvehiclemod$findWaterSurfaceY().isPresent()) {
			// A stationary emplacement has no independent means of movement/steering at all to avoid landing in water in the first place - if float-capable, settles at the water surface (via the shared spring, see AbstractVehicleEntity's own tudursvehiclemod$applySurfaceFloatSpring() doc) instead of sinking straight through it like solid ground would otherwise stop it.
			double staticSurfaceTargetY = this.tudursvehiclemod$findWaterSurfaceY().getAsDouble() + STATIC_SURFACE_FLOAT_DEPTH;
			double staticNewVelY = this.tudursvehiclemod$applySurfaceFloatSpring(staticSurfaceTargetY, this.getVelocity().y,
					SURFACE_SPRING_STRENGTH, SURFACE_VERTICAL_DAMPING, SURFACE_SETTLE_POSITION_THRESHOLD, SURFACE_SETTLE_VELOCITY_THRESHOLD);
			this.setVelocity(0.0, staticNewVelY, 0.0);
		} else {
			this.tudursvehiclemod$resetSurfaceFloatLock();
			this.setVelocity(0.0, this.getVelocity().y - 0.08, 0.0);
		}
		this.move(MovementType.SELF, this.getVelocity());
	}

	/** How far above the water surface a float-capable static emplacement settles while floating - see updateVehicleMovement()'s own new float branch. 0.0 (settles exactly at the water line) as a simple default; a genuinely new capability for this vehicle type. */
	protected static final double STATIC_SURFACE_FLOAT_DEPTH = 0.0;
}
