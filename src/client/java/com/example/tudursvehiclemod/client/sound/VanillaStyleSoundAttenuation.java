package com.example.tudursvehiclemod.client.sound;

/** Implements MC Heli's own documented SoundVolume
 * convention (see Readme_Weapon.txt's own doc for SoundVolume - "1.0 or
 * above becomes max volume per Minecraft's own spec; above 1.0 makes it
 * audible from further away"). Both weapon-fire sounds and vehicle engine
 * sounds here are played via raw OpenAL sources (bypassing vanilla's own
 * SoundEvent volume/attenuation handling entirely - see
 * AddonSoundLoader's own doc for why), so this replicates that same
 * vanilla behavior by hand: actual loudness (gain) never exceeds 1.0
 * regardless of how high volume is configured, but the DISTANCE at which
 * the sound remains audible scales proportionally with volume instead -
 * a volume of 2.0 is audible from twice as far as a volume of 1.0, at
 * the same (capped-at-1.0) peak loudness. A volume below 1.0 does the
 * same thing in reverse, shrinking both peak loudness AND range together
 * (matching the same doc's own "音量を下げる場合は 1.0 未満にする" - "to
 * lower the volume, use below 1.0"). */
public final class VanillaStyleSoundAttenuation {

	private VanillaStyleSoundAttenuation() {
	}

	/** Computes the final gain (0-1, ready to hand straight to AL_GAIN) for
	 * a sound currently `distance` blocks from the listener.
	 * baseReferenceDistance/baseMaxDistance are this specific sound's own
	 * "un-scaled" (volume=1.0) full-loudness/silent-beyond distances -
	 * both actually get multiplied by volume here, so a louder-than-1.0
	 * volume stretches the whole falloff curve outward, and a
	 * quieter-than-1.0 volume shrinks it inward. Callers should disable
	 * OpenAL's own built-in distance attenuation entirely for the source
	 * this gain gets applied to (AL_ROLLOFF_FACTOR = 0) - see
	 * VehicleEngineSoundManager's own createLoopingSource() doc for why
	 * OpenAL's own default distance model can't be relied on for this at
	 * all (it doesn't even reference AL_MAX_DISTANCE). */
	public static float computeGain(double distance, float volume, float baseReferenceDistance, float baseMaxDistance) {
		float safeVolume = Math.max(0f, volume);
		float gainCap = Math.min(1f, safeVolume);
		float referenceDistance = baseReferenceDistance * safeVolume;
		float maxDistance = baseMaxDistance * safeVolume;
		if (maxDistance <= referenceDistance) {
			// Degenerate case (e.g. safeVolume is 0, or the base distances themselves were misconfigured) - just an on/off cutoff right at referenceDistance, rather than dividing by a zero/negative range below.
			return distance <= referenceDistance ? gainCap : 0f;
		}
		float distanceAttenuation;
		if (distance <= referenceDistance) {
			distanceAttenuation = 1f;
		} else if (distance >= maxDistance) {
			distanceAttenuation = 0f;
		} else {
			distanceAttenuation = 1f - (float) ((distance - referenceDistance) / (maxDistance - referenceDistance));
		}
		return gainCap * distanceAttenuation;
	}
}
