package com.example.tudursvehiclemod.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

/** This mod's own custom water-splash/column visual
 * effect (see client.particle.WaterSplashParticle for the actual
 * rendering), used for both the large water column on an explosion at/in
 * water, and the small splash on a plain projectile's own first contact
 * with the water surface - scale carries how big a given spawn should be
 * (see AbstractVehicleEntity/VehicleProjectileEntity's own call sites for
 * how that's actually computed in each of those two cases). Same
 * Codec/PacketCodec-based approach as ExplosionFlashEffect (see that
 * record's own doc for why this is necessary rather than smuggling scale
 * through spawnParticles()'s own velocity parameters instead). */
public record WaterSplashEffect(float scale) implements ParticleEffect {

	public static final MapCodec<WaterSplashEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.FLOAT.fieldOf("scale").forGetter(WaterSplashEffect::scale)
	).apply(instance, WaterSplashEffect::new));

	public static final PacketCodec<RegistryByteBuf, WaterSplashEffect> PACKET_CODEC =
			PacketCodecs.FLOAT.xmap(WaterSplashEffect::new, WaterSplashEffect::scale).cast();

	@Override
	public ParticleType<WaterSplashEffect> getType() {
		return com.example.tudursvehiclemod.registry.ModParticleTypes.WATER_SPLASH;
	}
}
