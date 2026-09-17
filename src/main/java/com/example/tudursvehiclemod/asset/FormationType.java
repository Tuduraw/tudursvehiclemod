package com.example.tudursvehiclemod.asset;

/** The shape a multi-aircraft CAS/Carrier strike flies in. Aircraft index 0 is always the lead - it always flies the exact route configured (zero offset); every other index gets a fixed lateral/longitudinal offset (see AbstractVehicleEntity's own tudursvehiclemod$computeFormationOffsets() doc) added to every one of its own route waypoints, so the whole formation flies the SAME route rigidly, just staggered. */
public enum FormationType {
	/** A single perpendicular row, level with the lead, evenly spaced. For an even count, the lead can't be exactly centered (it must stay on the route) - the extra aircraft goes on one side, so the row is left-right asymmetric in that case. */
	LINE_ABREAST,
	/** Single file directly behind the lead, each one spacing further back along the route. Never left-right ambiguous. */
	LINE_ASTERN,
	/** A V, point-first, with the lead at the front - aircraft alternate left/right behind it, each pair further back than the last. Same even-count asymmetry as LINE_ABREAST. */
	V_FORMATION,
	/** Groups of 4 (lead + 3 wingmen) each flying a small diamond (front/right/left/tail) - for a total count above 4, the diamond ELEMENTS themselves are arranged into a larger echelon (a "combat box") using the same alternating-side, stepped-back logic as V_FORMATION, one level up. */
	DIAMOND,
	/** Groups of 3 (lead + 2 wingmen) each flying a small vic/delta (front/right/left) - for a total count above 3, the delta ELEMENTS are arranged into the same larger echelon "combat box" as DIAMOND. */
	DELTA
}
