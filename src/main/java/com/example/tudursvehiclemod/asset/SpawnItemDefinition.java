package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Describes how this vehicle is presented in the tiered spawner selection screen: its display name, and its "tier" (1-5). */
public record SpawnItemDefinition(
		Optional<String> displayName,
		int tier
) {

	public static final Codec<SpawnItemDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("display_name").forGetter(SpawnItemDefinition::displayName),
			Codec.INT.optionalFieldOf("tier", 1).forGetter(SpawnItemDefinition::tier)
	).apply(instance, SpawnItemDefinition::new));
}
