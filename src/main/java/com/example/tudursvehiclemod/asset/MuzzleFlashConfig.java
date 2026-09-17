package com.example.tudursvehiclemod.asset;

/** MC Heli's own weapon "AddMuzzleFlash" directive (see Readme_Weapon.txt's own doc):
 * AddMuzzleFlash = distanceFromMuzzle, size, displayTicks, A, R, G, B
 * A brief, bright flash spawned at the muzzle each time this weapon fires. */
public record MuzzleFlashConfig(
		float distanceFromMuzzle,
		float size,
		int displayTicks,
		int argbColor
) {
}
