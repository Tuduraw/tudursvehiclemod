package com.example.tudursvehiclemod.client.render;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.util.Identifier;

/** 1.21.9+ split entity rendering into two steps: updateRenderState() copies whatever the renderer needs off the entity.. */
public class VehicleRenderState extends EntityRenderState {
	public Identifier model;
	public Identifier texture;
	public float scale = 1.0f;
	public float yaw;
	public float pitch;
	public int light;

	/** Purely cosmetic (rotor spin, wheel spin,..). */
	public float animationPhase;

	/** Purely cosmetic extra Y offset - see AbstractVehicleEntity's own getRenderYOffset() doc. Defaults to 0 (no effect) for every vehicle type except CarEntity. */
	public float renderYOffset;

	/** Purely cosmetic bank/tilt angle in degrees. Still populated and still used for wake-trail
	 * heading and any other purely-yaw-based cosmetic effect that doesn't drive the vehicle's own
	 * mesh transform any more - see bodyOrientation's own doc for what replaced it there. */
	public float roll;

	/** The vehicle's own body rotation, as a single quaternion - what render() actually multiplies
	 * onto the mesh transform now, replacing three separate RotationAxis multiplies built from
	 * yaw/pitch/roll individually. See AbstractVehicleEntity's own
	 * tudursvehiclemod$getBodyOrientation(float) doc for why: for AircraftEntity/VtolEntity, whose
	 * own true attitude already IS a quaternion, decomposing it into yaw/pitch/roll only to
	 * immediately recompose it here was a lossy round trip, and the source of a gimbal-lock-adjacent
	 * rendering jitter whenever pitch neared +-90. Every other vehicle type's own default
	 * implementation still composes this from yaw/pitch/roll, so this changes no rendered pixel for
	 * Car/Ship/Submarine/Helicopter. */
	public org.joml.Quaternionf bodyOrientation = new org.joml.Quaternionf();

	/** Per-OBJ-group model-space transforms from AbstractVehicleEntity's own
	 * tudursvehiclemod$getCustomPartTransforms() - see that method's own doc. Empty for every vehicle
	 * type that doesn't override it. */
	public java.util.Map<String, org.joml.Matrix4f> customPartTransforms = java.util.Map.of();

	/** Which named OBJ parts spin, and how. */
	public java.util.List<com.example.tudursvehiclemod.asset.PartAnimation> spinningParts = java.util.List.of();
	/** part name -> current accumulated spin phase in degrees, captured this frame. */
	public java.util.Map<String, Float> spinningPartsPhase = java.util.Map.of();

	/** Which named OBJ parts ease between open/closed, and how. */
	public java.util.List<com.example.tudursvehiclemod.asset.TogglePart> toggleParts = java.util.List.of();
	/** part name -> current progress (0=closed, 1=open), captured this frame. */
	public java.util.Map<String, Float> togglePartProgress = java.util.Map.of();

	/** Which named OBJ parts track an aimer's yaw/pitch, and how. */
	public java.util.List<com.example.tudursvehiclemod.asset.WeaponPart> weaponParts = java.util.List.of();
	/** part name -> this part's own stage rotation (see AbstractVehicleEntity's own tudursvehiclemod$getWeaponPartOwnRotation() doc), captured this frame. */
	public java.util.Map<String, org.joml.Quaternionf> weaponPartOwnRotation = java.util.Map.of();
	/** part name -> the PARENT's own stage rotation for a child part (identity for a non-child part), captured this frame. */
	public java.util.Map<String, org.joml.Quaternionf> weaponPartParentRotation = java.util.Map.of();
	/** part name -> this part's own current part_type=2 recoil offset (blocks, see AbstractVehicleEntity's own tudursvehiclemod$getWeaponPartRecoilOffset() doc), captured this frame. */
	public java.util.Map<String, Float> weaponPartRecoilOffset = java.util.Map.of();

	/** True once the vehicle's own health has reached 0. */
	public boolean destroyed;

	/** -1 means no override (ordinary destroyed/white tint logic in VehicleEntityRenderer's own render() applies as before) - set to entity.ParachuteEntity's own current tint color (see that class's own TINT_COLOR doc) whenever the entity being rendered actually is one. */
	public int tintColorOverride = -1;

	/** Which named OBJ parts are ammo-count-gated (AddPartWeaponMissile), and how. */
	public java.util.List<com.example.tudursvehiclemod.asset.AmmoPart> ammoParts = java.util.List.of();
	/** part name -> currently visible (true) or hidden (false), captured this frame. */
	public java.util.Map<String, Boolean> ammoPartVisibility = java.util.Map.of();

	/** AddPartWheel - see WheelPart's own doc. */
	public java.util.List<com.example.tudursvehiclemod.asset.WheelPart> wheelParts = java.util.List.of();
	/** part name -> this wheel's own combined spin+steer rotation, captured this frame. */
	public java.util.Map<String, org.joml.Quaternionf> wheelPartRotation = java.util.Map.of();

	/** AddTrackRoller - see TrackRollerPart's own doc. */
	public java.util.List<com.example.tudursvehiclemod.asset.TrackRollerPart> trackRollerParts = java.util.List.of();
	/** part name -> this roller's own current belt-synced spin rotation, captured this frame. */
	public java.util.Map<String, org.joml.Quaternionf> trackRollerPartRotation = java.util.Map.of();

	/** AddPartSteeringWheel - see SteeringWheelPart's own doc. */
	public java.util.List<com.example.tudursvehiclemod.asset.SteeringWheelPart> steeringWheelParts = java.util.List.of();
	/** part name -> this steering wheel's own current rotation, captured this frame. */
	public java.util.Map<String, org.joml.Quaternionf> steeringWheelPartRotation = java.util.Map.of();

	/** AddCrawlerTrack - see CrawlerTrackPart's own doc. */
	public java.util.List<com.example.tudursvehiclemod.asset.CrawlerTrackPart> crawlerTracks = java.util.List.of();
	/** part name -> this track's own current belt phase (blocks travelled), captured this frame. */
	public java.util.Map<String, Float> crawlerTrackPhase = java.util.Map.of();

	/** AddPartRotor - see VtolRotorPart's own doc. Empty for every vehicle except VtolEntity. */
	public java.util.List<com.example.tudursvehiclemod.asset.VtolRotorPart> vtolRotorParts = java.util.List.of();
	/** VtolEntity's own tudursvehiclemod$getVtolTiltProgress() (0 = helicopter-mode orientation, 1 = aircraft-mode orientation), captured this frame - 0 (a no-op tilt) for every vehicle except VtolEntity. */
	public float vtolTiltProgress;

	/** Reverted back to per-entity fields. This-frame snapshot of the entity's wake-trail history (see AbstractVehicleEntity.updateWakeTrail()). Copied so render() works from a stable list. */
	public java.util.List<com.example.tudursvehiclemod.entity.AbstractVehicleEntity.WakeHistoryPoint> wakeBowHistory = java.util.List.of();
	public java.util.List<com.example.tudursvehiclemod.entity.AbstractVehicleEntity.WakeHistoryPoint> wakeSternHistory = java.util.List.of();
	public java.util.List<com.example.tudursvehiclemod.entity.AbstractVehicleEntity.WakeHistoryPoint> wakeSideHistory = java.util.List.of();
	/** def.wakeTrailDurationTicks(): how long a wake point survives before aging out. */
	public int wakeTrailDurationTicks = 300;
	/** This vehicle's current world time; used to compute elapsed time since each wake tile's creation. */
	public long currentWorldTick;
	/** True while this vehicle is moving in reverse - used to swap which end (bow/stern) gets the diverging wake pattern. */
	public boolean wakeReversing;
	/** This vehicle's own current yaw (degrees). used to re-rotate a young stern-band point's own local offset while it's still within its own gap-delay window, so it tracks this vehicle's own current turn instead of assuming a straight path. */
	public float wakeCurrentYaw;
	/** The game-world tick wakeBowHistory/wakeSternHistory/wakeSideHistory were most recently actually re-copied for. -1 initially, guaranteeing the very first call always copies. */
	public long wakeHistoryCopiedForTick = -1L;

	/** One entry per SearchLightPart that's currently ON, with everything renderSearchLightBeams() needs already resolved this frame - see LightBeam's own doc for why the world-space direction (rather than the raw definition + a live re-derivation at render time) is what gets carried here. Empty whenever this vehicle's own search light is off, or it has none defined - a no-op for every vehicle without one, costing nothing beyond this now-unused empty list. */
	public java.util.List<LightBeam> activeSearchLightBeams = java.util.List.of();

	/** This vehicle's own current block overlays - see SearchLightIllumination.BlockOverlay's own doc. */
	public java.util.List<SearchLightIllumination.BlockOverlay> activeBlockOverlays = java.util.List.of();


	/** One search light's own resolved-for-this-frame render data - see activeSearchLightBeams' own doc. worldYaw/worldPitch are already the FINAL aim (baseYaw/basePitch plus whatever FollowMode-specific offset applies - see AbstractVehicleEntity's own tudursvehiclemod$getSearchLightWorldAim() doc), computed once in updateRenderState() rather than re-derived inside the render loop itself. */
	public record LightBeam(
			double pivotX, double pivotY, double pivotZ,
			float worldYaw, float worldPitch,
			int startColorArgb, int endColorArgb,
			float length, float endRadius
	) {}
}
