package com.example.tudursvehiclemod.asset;

/** MC Heli's own weapon "SetCartridge" directive (see Readme_Weapon.txt's own doc):
 * SetCartridge = modelName, acceleration, yaw, pitch, modelScale, gravity, bound
 * Ejects a small, purely cosmetic (non-damaging) spent-casing model each time this weapon fires. */
public record CartridgeConfig(
		String modelName,
		float acceleration,
		float yawDegrees,
		float pitchDegrees,
		float modelScale,
		float gravity,
		float bounce
) {
}
