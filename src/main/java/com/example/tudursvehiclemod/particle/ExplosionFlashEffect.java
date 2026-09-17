package com.example.tudursvehiclemod.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

/** The earlier approach (a plain SimpleParticleType,
 * with the desired peak scale "smuggled" through spawnParticles()'s own
 * deltaX/velocityX parameter) turned out not to actually work reliably -
 * the server-side log showed the correct value (e.g. 10.4) but the
 * client-side factory received something else entirely (1.0, this
 * class's own fallback floor) - so that value clearly wasn't making it
 * across the network intact after all, despite matching what count=0's
 * own documented behavior should do. This replaces that entirely with a
 * proper, explicit ParticleEffect carrying peakScale as real, codec-
 * serialized data - the same mechanism vanilla itself uses for particles
 * that need to carry their own extra data (e.g. DustParticleEffect's own
 * color/scale), rather than relying on repurposing unrelated fields. */
public record ExplosionFlashEffect(float peakScale) implements ParticleEffect {

	public static final MapCodec<ExplosionFlashEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.FLOAT.fieldOf("peak_scale").forGetter(ExplosionFlashEffect::peakScale)
	).apply(instance, ExplosionFlashEffect::new));

	public static final PacketCodec<RegistryByteBuf, ExplosionFlashEffect> PACKET_CODEC =
			PacketCodecs.FLOAT.xmap(ExplosionFlashEffect::new, ExplosionFlashEffect::peakScale).cast();

	@Override
	public ParticleType<ExplosionFlashEffect> getType() {
		return com.example.tudursvehiclemod.registry.ModParticleTypes.EXPLOSION_FLASH;
	}
}
