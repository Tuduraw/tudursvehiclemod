package com.example.tudursvehiclemod.registry;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.particle.ExplosionFlashEffect;
import com.example.tudursvehiclemod.particle.WaterSplashEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** This mod's own custom explosion/smoke/water-splash
 * visual effects, separate from vanilla's own built-in particles - see
 * client.particle.ExplosionFlashParticle/SmokePuffParticle/WaterSplashParticle
 * for the actual rendering of each.
 *
 * EXPLOSION_FLASH/WATER_SPLASH use FabricParticleTypes.complex() (a real
 * ParticleType<T> with its own Codec/PacketCodec - see those records' own
 * doc) since they each need to carry their own per-spawn scale data
 * reliably - smuggling that through spawnParticles()'s own velocity
 * parameters instead was tried first and proved unreliable (see
 * ExplosionFlashEffect's own doc). SMOKE_PUFF has no such data to carry, so
 * it stays a plain FabricParticleTypes.simple().
 *
 * A fourth custom type (WakeTrailEffect/WakeTrailParticle)
 * was added and then fully removed again after it consistently failed to
 * spawn at all client-side ("Could not spawn particle effect", every single
 * attempt) - see AbstractVehicleEntity's own tudursvehiclemod$updateWakeTrail()
 * doc for what that feature now uses instead (vanilla's own ParticleTypes.SPLASH). */
public class ModParticleTypes {

	public static ParticleType<ExplosionFlashEffect> EXPLOSION_FLASH;
	public static SimpleParticleType SMOKE_PUFF;
	public static ParticleType<WaterSplashEffect> WATER_SPLASH;

	public static void register() {
		EXPLOSION_FLASH = Registry.register(Registries.PARTICLE_TYPE,
				Identifier.of(VehicleMod.MOD_ID, "explosion_flash"),
				net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.complex(
						true, ExplosionFlashEffect.CODEC, ExplosionFlashEffect.PACKET_CODEC));
		SMOKE_PUFF = Registry.register(Registries.PARTICLE_TYPE,
				Identifier.of(VehicleMod.MOD_ID, "smoke_puff"),
				net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));
		WATER_SPLASH = Registry.register(Registries.PARTICLE_TYPE,
				Identifier.of(VehicleMod.MOD_ID, "water_splash"),
				net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.complex(
						true, WaterSplashEffect.CODEC, WaterSplashEffect.PACKET_CODEC));
	}
}
