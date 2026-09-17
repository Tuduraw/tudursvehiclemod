package com.example.tudursvehiclemod.asset;

/** CAS/Carrier's own target-point calculation method is selectable per weapon (not a project-wide constant) - see AbstractVehicleEntity's own tudursvehiclemod$computeCasCarrierTargetPoint() doc for how each mode is actually computed. */
public enum CasTargetMode {
	/** Default - this project's own current, obstacle-ignoring ballistic trajectory calculation (a pure mathematical "same-height-return" point, purely from the weapon's own velocity/gravity and the shooter's own aim - see tudursvehiclemod$computeBallisticTargetPoint()'s own doc). */
	BALLISTIC,
	/** The method CAS/Carrier originally used before BALLISTIC replaced it - a straight crosshair raycast to the first block hit (or a fixed max-range point if nothing's hit at all) - see tudursvehiclemod$computeCasCarrierRaycastPoint()'s own doc. */
	RAYCAST,
	/** The same trajectory physics BALLISTIC itself uses, but the target is determined SOLELY by an actual block collision along the simulated arc (mirroring client.hud.MortarMarkerRenderer's own Bomb/Rocket marker, which already does exactly this for its own HUD display) - the simulation keeps running (well past the same-height point if needed, e.g. firing from a height down into a valley) until it actually hits something, rather than ever substituting the obstacle-ignoring BALLISTIC point in its place. */
	COLLISION
}
