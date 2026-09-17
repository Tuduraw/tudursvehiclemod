package com.example.tudursvehiclemod.asset;

/** MC Heli's own weapon "Sight" directive (see Readme_Weapon.txt's own doc) - which on-screen reticle a weapon shows while selected. */
public enum SightType {
	/** Moves with this vehicle's own current aim direction - the ordinary case for most weapon types. */
	MOVE_SIGHT,
	/** No reticle shown at all. */
	NONE,
	/** A lock-on-style reticle - mandatory for AAMissile/ATMissile per Readme_Weapon.txt's own doc. */
	MISSILE_SIGHT
}
