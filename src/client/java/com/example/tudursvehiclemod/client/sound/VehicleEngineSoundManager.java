package com.example.tudursvehiclemod.client.sound;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.openal.AL10;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One looping OpenAL source per vehicle entity that has an engine_sound configured (see VehicleDefinition.engineSound()) and is currently loaded in the client.. */
public final class VehicleEngineSoundManager {

	/** Gain at zero throttle. */
	private static final float IDLE_GAIN = 0.0f;
	private static final float FULL_GAIN = 1.0f;
	private static final float IDLE_PITCH = 0.8f;
	private static final float FULL_PITCH = 1.3f;
	/** How quickly the AUDIBLE gain/pitch follow throttle changes, per tick. */
	private static final float SOUND_SMOOTHING = 0.2f;

	private static final Map<Integer, Integer> engineSources = new HashMap<>();
	private static final Map<Integer, Float> smoothedThrottle = new HashMap<>();

	private VehicleEngineSoundManager() {
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			stopAll();
			return;
		}

		Set<Integer> stillPresent = new HashSet<>();
		for (Entity entity : client.world.getEntities()) {
			if (!(entity instanceof AbstractVehicleEntity vehicle)) {
				continue;
			}
			var engineSoundName = vehicle.getDefinition().engineSound();
			if (engineSoundName.isEmpty()) {
				continue;
			}
			Integer bufferId = AddonSoundLoader.getBuffer(engineSoundName.get());
			if (bufferId == null) {
				continue;
			}

			int entityId = vehicle.getId();
			stillPresent.add(entityId);
			int source = engineSources.computeIfAbsent(entityId, id -> createLoopingSource(bufferId));
			updateSource(source, vehicle, entityId);
		}

		// Stop/clean up sources for any vehicle no longer present (unloaded, removed, or its engine_sound/buffer went away on a resource reload).
		engineSources.entrySet().removeIf(entry -> {
			if (!stillPresent.contains(entry.getKey())) {
				AL10.alSourceStop(entry.getValue());
				AL10.alDeleteSources(entry.getValue());
				smoothedThrottle.remove(entry.getKey());
				return true;
			}
			return false;
		});
	}

	private static int createLoopingSource(int bufferId) {
		int source = AL10.alGenSources();
		AL10.alSourcei(source, AL10.AL_BUFFER, bufferId);
		AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
		// Engine sound remained faintly audible
		// from far beyond where it should have fully faded out: OpenAL's
		// own DEFAULT distance model is AL_INVERSE_DISTANCE (NOT the
		// _CLAMPED variant), whose own gain formula asymptotically
		// approaches (but mathematically never actually reaches) zero as
		// distance grows - and, critically, that formula doesn't even
		// reference AL_MAX_DISTANCE at all (only the _CLAMPED distance
		// models actually use it to cut attenuation off at that point).
		// Setting AL_ROLLOFF_FACTOR to 0 here disables OpenAL's own
		// built-in distance attenuation for this source ENTIRELY (this is
		// a per-source property, so this can't affect any other sound in
		// the game) - distance-based gain is instead computed by hand in
		// updateSource() below (see VanillaStyleSoundAttenuation's own
		// doc), independent of whatever the game's own global AL distance
		// model happens to be set to.
		AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);
		AL10.alSourcePlay(source);
		return source;
	}

	/** "Un-scaled" (engine_sound_volume=1.0) reference/max distances - doubled from their original values (4.0/64.0) so the audible range at the default engine_sound_volume=1.0 is twice as far. See VanillaStyleSoundAttenuation's own doc for how that volume actually stretches/shrinks these further. */
	private static final float BASE_REFERENCE_DISTANCE = 8.0f;
	private static final float BASE_MAX_DISTANCE = 128.0f;

	private static void updateSource(int source, AbstractVehicleEntity vehicle, int entityId) {
		Vec3d pos = vehicle.getEntityPos();
		AL10.alSource3f(source, AL10.AL_POSITION, (float) pos.x, (float) pos.y, (float) pos.z);
		Vec3d velocity = vehicle.getVelocity();
		AL10.alSource3f(source, AL10.AL_VELOCITY, (float) velocity.x, (float) velocity.y, (float) velocity.z);

		boolean rotorAlwaysSpinning = vehicle instanceof com.example.tudursvehiclemod.entity.HelicopterEntity
				|| (vehicle instanceof com.example.tudursvehiclemod.entity.VtolEntity vtol && vtol.isHelicopterMode());
		float rawThrottle;
		if (vehicle.tudursvehiclemod$isUsingRampedAutopilotThrottle()) {
			rawThrottle = MathHelper.clamp(vehicle.tudursvehiclemod$getCasWaypointSpeedFraction(), 0f, 1f);
		} else if (vehicle.tudursvehiclemod$isDroneActive()) {
			rawThrottle = MathHelper.clamp(vehicle.tudursvehiclemod$getDroneSpeedFraction(), 0f, 1f);
		} else if (rotorAlwaysSpinning) {
			float rotorFraction = MathHelper.clamp(vehicle.tudursvehiclemod$getSpinningPartSpeedMultiplier(), 0f, 1f);
			if (vehicle.getControllingPassenger() instanceof net.minecraft.entity.player.PlayerEntity) {
				// Helicopter's own getThrottle() is -1.1 (0=hover), so (x+1)/2 maps it into the 0.1 fraction this calculation wants. VtolEntity's own hover-mode getThrottle() is ALREADY 0.1 (0.5=hover) - using it directly, with no transform at all, is what's actually correct for that convention.
				float throttleFraction = vehicle instanceof com.example.tudursvehiclemod.entity.HelicopterEntity
						? (MathHelper.clamp(vehicle.getThrottle(), -1f, 1f) + 1f) / 2f
						: MathHelper.clamp(vehicle.getThrottle(), 0f, 1f);
				// Averaged with the exact same value already driving this vehicle's own visible rotor-blade spin speed, rather than the throttle fraction alone - regardless of piloted state: excluding it while piloted produced a worse, stranger bug (rotor visibly spinning while sound went completely silent). Keeping sound consistent with the visible rotor animation at all times is what's actually correct here.
				rawThrottle = (throttleFraction + rotorFraction) / 2f;
			} else {
				// Stationary helicopter/VTOL(-in-helicopter-mode) still plays engine sound at what sounds like a minimum volume floor, confirmed independent of both mob-drop and the recent yaw work: getThrottle() is never reset to any special "truly silent" sentinel on dismount - it just sits at whatever the last real flown value was (commonly 0, i.e. neutral/hover), which the (x+1)/2 transform above would otherwise keep reading as "still actively holding 50% power to hover" forever, even though rotorFraction (the OTHER half of that average) is already correctly reporting 0 for this same unmanned state. Averaging a correct 0 with an incorrectly-still-0.5 throttle fraction produced exactly the persistent ~25% floor this report describes. Using rotorFraction alone while unmanned - which by itself already correctly reaches exactly 0 once genuinely landed and stopped - is what actually reaches true silence.
				rawThrottle = rotorFraction;
			}
		} else {
			rawThrottle = MathHelper.clamp(Math.abs(vehicle.getThrottle()), 0f, 1f);
		}
		float previous = smoothedThrottle.getOrDefault(entityId, rawThrottle);
		float smoothed = previous + (rawThrottle - previous) * SOUND_SMOOTHING;
		smoothedThrottle.put(entityId, smoothed);

		float throttleGain = MathHelper.lerp(smoothed, IDLE_GAIN, FULL_GAIN);
		float pitch = MathHelper.lerp(smoothed, IDLE_PITCH, FULL_PITCH);

		// Follows MC Heli's own documented
		// SoundVolume convention (see VanillaStyleSoundAttenuation's own
		// doc), applied to this vehicle's own configured
		// engine_sound_volume (3.0 default) - using the actual
		// camera/listener position (not necessarily the same as the
		// player entity's own feet position - e.g. a spectator or a
		// detached free-look camera).
		float engineSoundVolume = vehicle.getDefinition().engineSoundVolume();
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d listenerPos = client.gameRenderer.getCamera().getCameraPos();
		double distance = pos.distanceTo(listenerPos);
		float distanceGain = VanillaStyleSoundAttenuation.computeGain(distance, engineSoundVolume, BASE_REFERENCE_DISTANCE, BASE_MAX_DISTANCE);

		AL10.alSourcef(source, AL10.AL_GAIN, throttleGain * distanceGain);
		AL10.alSourcef(source, AL10.AL_PITCH, pitch);
	}

	/** Stops and releases every active engine sound source. */
	public static void stopAll() {
		for (int source : engineSources.values()) {
			AL10.alSourceStop(source);
			AL10.alDeleteSources(source);
		}
		engineSources.clear();
		smoothedThrottle.clear();
	}
}
