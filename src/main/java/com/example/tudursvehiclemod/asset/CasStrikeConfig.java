package com.example.tudursvehiclemod.asset;

import java.util.List;

/** Full configuration for a WeaponType.CAS weapon - a "call in an airstrike" weapon: firing it marks a ground target point, spawns aircraftFileName at the route's first waypoint, and flies it the configured route, auto-firing weaponIndex on attack=true waypoints (for the full field-by-field semantics - accuracy is the aircraft's own navigation imprecision, NOT weapon accuracy; timeoutTicks/stuckTimeoutTicks are two separate safety-net despawn timers; yawOffsetDegrees is a manual route-orientation correction). Parsed from a weapon.txt file's own "CasAircraft"/"CasWeaponIndex"/"CasAccuracy"/"CasTimeout"/"CasStuckTimeout"/"CasYawOffset"/repeated "CasWaypoint" lines.
 *
 * FormationSize/formationType/formationSpacing - spawns formationSize aircraft instead of just one, each flying the exact same configured route, rigidly offset by AbstractVehicleEntity's own tudursvehiclemod$computeFormationOffsets() (aircraft index 0 is always the lead - unmodified route, spawn position, and facing). formationSize 1 (the default, keys absent) means no formation at all - the original single-aircraft behavior, completely unchanged. Parsed from "CasFormationSize"/"CasFormationType"/"CasFormationSpacing".
 *
 * FormationElementSpacing - for FormationType.DIAMOND/DELTA only, the distance (blocks) between element CENTROIDS, independently configurable from formationSpacing (which, for these two types, instead governs spacing between aircraft WITHIN the same element). Empty (the default, key absent) falls back to AbstractVehicleEntity's own auto-derived value (elementSize*1.5x formationSpacing) - the original behavior from before this became independently configurable. Has no effect at all for the other three formation types. Parsed from "CasFormationElementSpacing". */
public record CasStrikeConfig(String aircraftFileName, int weaponIndex, float accuracy, int timeoutTicks, int stuckTimeoutTicks, float yawOffsetDegrees, List<CasWaypoint> waypoints,
		int formationSize, FormationType formationType, double formationSpacing, double elementSpacing) {
}
