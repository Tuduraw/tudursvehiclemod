package com.example.tudursvehiclemod.asset;

/** MC Heli's own weapon "AddMuzzleFlashSmoke" directive (see Readme_Weapon.txt's own doc):
 * AddMuzzleFlashSmoke = distanceFromMuzzle, count, size, spreadRange, displayTicks, A, R, G, B
 * A short-lived puff of smoke particles spawned at the muzzle each time this weapon fires. */
public record MuzzleFlashSmokeConfig(
		float distanceFromMuzzle,
		int count,
		float size,
		float spreadRange,
		int displayTicks,
		int argbColor
) {
}
