package com.example.tudursvehiclemod.client.particle;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;

/** This mod's own custom damage-smoke effect,
 * replacing vanilla's own LARGE_SMOKE (see AbstractVehicleEntity's own
 * tudursvehiclemod$updateDamageSmoke() doc) with a hand-made, soft,
 * irregular grey puff (see textures/particle/smoke_puff.png). Per a
 * further direct request, this rises noticeably (accelerating upward over
 * its own life, like a real rising heat plume) rather than just hovering
 * in place while it fades - it should read as genuinely floating up and
 * away into the sky, not as a static puff that happens to lose opacity.
 * No custom per-spawn data needed (unlike
 * ExplosionFlashParticle/WaterSplashParticle), so this uses a plain
 * SimpleParticleType rather than a custom ParticleEffect+Codec. */
public class SmokePuffParticle extends BillboardParticle {

	private static final int MIN_AGE = 40;
	private static final int MAX_AGE_EXTRA = 20;
	/** How much velocityY grows every tick - a real rising plume speeds up as it goes, rather than drifting at one constant rate the whole time. */
	private static final double UPWARD_ACCELERATION = 0.0035;

	protected SmokePuffParticle(ClientWorld world, double x, double y, double z, Sprite sprite, Random random) {
		super(world, x, y, z, sprite);
		this.maxAge = MIN_AGE + random.nextInt(MAX_AGE_EXTRA);
		this.gravityStrength = 0f;
		this.collidesWithWorld = false;
		// Gentle upward drift with a little random horizontal wander - a damaged part's own smoke should look like it's genuinely rising away, not just sitting in place. Accelerates over its own life (see tick()'s own doc/UPWARD_ACCELERATION's own doc).
		this.velocityX = (random.nextDouble() - 0.5) * 0.02;
		this.velocityY = 0.015 + random.nextDouble() * 0.01;
		this.velocityZ = (random.nextDouble() - 0.5) * 0.02;
		this.scale = 0.6f + random.nextFloat() * 0.4f;
		this.setColor(1.0f, 1.0f, 1.0f);
		this.alpha = 0.0f;
	}

	@Override
	public void tick() {
		super.tick();
		float progress = (float) this.age / (float) this.maxAge;
		// Fades in quickly, holds at full visibility through most of its own
		// rise, then fades out only over the FINAL stretch of its own life -
		// This should read as "drifts up and away,
		// THEN disappears" rather than gradually fading the whole time it's
		// still rising.
		if (progress < 0.1f) {
			this.alpha = progress / 0.1f;
		} else if (progress > 0.75f) {
			this.alpha = Math.max(0.0f, 1.0f - (progress - 0.75f) / 0.25f);
		} else {
			this.alpha = 1.0f;
		}
		this.scale += 0.012f;
		// Rises faster and faster, like a real heat plume - see UPWARD_ACCELERATION's own doc.
		this.velocityY += UPWARD_ACCELERATION;
		// Horizontal drift slows down over time, like real smoke losing momentum (vertical rise is the opposite - see above - since buoyancy keeps pulling it up rather than momentum carrying it sideways).
		this.velocityX *= 0.98;
		this.velocityZ *= 0.98;
	}

	@Override
	protected RenderType getRenderType() {
		return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
	}

	public static class Factory implements ParticleFactory<SimpleParticleType> {
		private final SpriteProvider spriteProvider;

		public Factory(SpriteProvider spriteProvider) {
			this.spriteProvider = spriteProvider;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientWorld world,
				double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
			Sprite sprite = this.spriteProvider.getSprite(random);
			return new SmokePuffParticle(world, x, y, z, sprite, random);
		}
	}
}
