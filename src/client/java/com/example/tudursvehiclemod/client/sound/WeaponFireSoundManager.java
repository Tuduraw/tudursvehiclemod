package com.example.tudursvehiclemod.client.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.openal.AL10;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Plays one-shot, positioned weapon-fire sounds (see WeaponDefinition's own sound/soundVolume/soundPitch/soundPitchRandom fields) via short-lived OpenAL sources.. */
public final class WeaponFireSoundManager {

	private static final Random RANDOM = new Random();
	private static final List<Integer> activeSources = new ArrayList<>();
	/** "Un-scaled" (volume=1.0) reference/max distances - see VanillaStyleSoundAttenuation's own doc for how soundVolume actually stretches/shrinks these. */
	private static final float BASE_REFERENCE_DISTANCE = 4.0f;
	private static final float BASE_MAX_DISTANCE = 128.0f;

	private WeaponFireSoundManager() {
	}

	/** Plays soundName once at pos. volume follows MC Heli's own documented convention (1.0 = normal max, above 1.0 = audible from further away - see VanillaStyleSoundAttenuation's own doc for exactly how that's implemented here). */
	public static void play(String soundName, Vec3d pos, float volume, float pitch, float pitchRandom) {
		Integer bufferId = AddonSoundLoader.getBuffer(soundName);
		if (bufferId == null) {
			return;
		}
		int source = AL10.alGenSources();
		AL10.alSourcei(source, AL10.AL_BUFFER, bufferId);
		AL10.alSource3f(source, AL10.AL_POSITION, (float) pos.x, (float) pos.y, (float) pos.z);
		// Disables OpenAL's own built-in distance attenuation for this one-shot source (per-source setting, doesn't affect any other sound) - see VanillaStyleSoundAttenuation's own doc for why gain is instead computed by hand below.
		AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);
		Vec3d listenerPos = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
		double distance = pos.distanceTo(listenerPos);
		float gain = VanillaStyleSoundAttenuation.computeGain(distance, volume, BASE_REFERENCE_DISTANCE, BASE_MAX_DISTANCE);
		AL10.alSourcef(source, AL10.AL_GAIN, gain);
		float randomOffset = pitchRandom > 0f ? (RANDOM.nextFloat() * 2f - 1f) * pitchRandom : 0f;
		AL10.alSourcef(source, AL10.AL_PITCH, Math.max(0.1f, pitch + randomOffset));
		AL10.alSourcePlay(source);
		activeSources.add(source);
	}

	/** Cleans up any source that's finished playing. */
	public static void tick() {
		activeSources.removeIf(source -> {
			int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
			if (state != AL10.AL_PLAYING) {
				AL10.alDeleteSources(source);
				return true;
			}
			return false;
		});
	}

	/** Stops and releases every currently-playing fire sound. */
	public static void stopAll() {
		for (int source : activeSources) {
			AL10.alSourceStop(source);
			AL10.alDeleteSources(source);
		}
		activeSources.clear();
	}
}
