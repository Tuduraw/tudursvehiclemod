package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Groups WeaponDefinition's 5 sound-related fields (sound, sound_volume, sound_pitch, sound_pitch_random, sound_delay_ticks) into one nested field purely for its.. */
public record WeaponSoundSettings(
		Optional<String> name, float volume, float pitch, float pitchRandom, int delayTicks
) {
	public static final MapCodec<WeaponSoundSettings> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.optionalFieldOf("sound").forGetter(WeaponSoundSettings::name),
			Codec.FLOAT.optionalFieldOf("sound_volume", 1.0f).forGetter(WeaponSoundSettings::volume),
			Codec.FLOAT.optionalFieldOf("sound_pitch", 1.0f).forGetter(WeaponSoundSettings::pitch),
			Codec.FLOAT.optionalFieldOf("sound_pitch_random", 0.0f).forGetter(WeaponSoundSettings::pitchRandom),
			Codec.INT.optionalFieldOf("sound_delay_ticks", 0).forGetter(WeaponSoundSettings::delayTicks)
	).apply(instance, WeaponSoundSettings::new));
}
