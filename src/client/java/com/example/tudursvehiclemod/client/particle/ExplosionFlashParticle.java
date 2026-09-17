package com.example.tudursvehiclemod.client.particle;

import com.example.tudursvehiclemod.particle.ExplosionFlashEffect;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.random.Random;

/** A big, custom-textured "fireball flash" - grows
 * quickly to its own peak size, then shrinks and fades out over its own
 * lifespan, using a hand-made radial-gradient texture (white-hot center,
 * through yellow/orange, fading to transparent red at the edge - see
 * textures/particle/explosion_flash.png) rather than any vanilla particle
 * sprite at all. Meant to be layered together with vanilla's own
 * EXPLOSION_EMITTER/CLOUD particles (see
 * com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity's
 * own tudursvehiclemod$spawnBigExplosionParticles() doc), not replace them -
 * this specifically gives the actual moment of detonation a distinct,
 * substantial, mod-authored look.
 *
 * Extends BillboardParticle (this MC version's own renamed replacement for
 * the older "SpriteBillboardParticle") rather than the plain Particle base
 * class, since that's what actually provides sprite-quad rendering (scale,
 * color, alpha, a texture) at all - Particle itself is purely physics/
 * lifecycle (position, velocity, age), with no rendering of its own. */
public class ExplosionFlashParticle extends BillboardParticle {

	/** How many ticks this particle grows for (its first phase) before beginning to shrink/fade (its second phase). */
	private static final int GROW_TICKS = 4;
	/** Total lifespan in ticks - deliberately short (a "flash", not a lingering cloud - vanilla's own CLOUD particles, already layered in alongside this, handle the "lingering" part of the effect instead). */
	private static final int TOTAL_TICKS = 14;
	/** Standard "always fully lit regardless of ambient light level" lightmap coordinate (sky=15, block=15) - a detonation should read as genuinely bright even at night/underground/underwater. */
	private static final int FULL_BRIGHT = 0xF000F0;

	private final float peakScale;

	protected ExplosionFlashParticle(ClientWorld world, double x, double y, double z, float peakScale, Sprite sprite) {
		super(world, x, y, z, sprite);
		this.peakScale = peakScale;
		this.maxAge = TOTAL_TICKS;
		this.gravityStrength = 0f;
		this.collidesWithWorld = false;
		this.velocityX = 0.0;
		this.velocityY = 0.0;
		this.velocityZ = 0.0;
		this.scale = 0.1f;
		this.setColor(1.0f, 1.0f, 1.0f);
	}

	@Override
	public void tick() {
		super.tick();
		float progress = (float) this.age / (float) this.maxAge;
		if (this.age <= GROW_TICKS) {
			// Fast grow-in.
			float growProgress = (float) this.age / (float) GROW_TICKS;
			this.scale = this.peakScale * growProgress;
			this.alpha = 1.0f;
		} else {
			// Slower shrink/fade-out for the remainder of this particle's own life.
			float shrinkProgress = (float) (this.age - GROW_TICKS) / (float) Math.max(1, this.maxAge - GROW_TICKS);
			this.scale = this.peakScale * (1.0f - 0.4f * shrinkProgress);
			this.alpha = Math.max(0.0f, 1.0f - shrinkProgress);
		}
		// Slight color drift from white-hot towards orange as it ages, on top of the texture's own baked-in gradient.
		float warmth = Math.min(1.0f, progress * 1.5f);
		this.setColor(1.0f, 1.0f - warmth * 0.35f, 1.0f - warmth * 0.75f);
	}

	@Override
	protected int getBrightness(float tint) {
		return FULL_BRIGHT;
	}

	@Override
	protected RenderType getRenderType() {
		return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
	}

	public static class Factory implements ParticleFactory<ExplosionFlashEffect> {
		private final SpriteProvider spriteProvider;

		public Factory(SpriteProvider spriteProvider) {
			this.spriteProvider = spriteProvider;
		}

		@Override
		public Particle createParticle(ExplosionFlashEffect effect, ClientWorld world,
				double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
			// peakScale now comes directly from the properly codec-deserialized effect object itself - see ExplosionFlashEffect's own doc for why this replaced the earlier (unreliable) velocityX-smuggling approach.
			Sprite sprite = this.spriteProvider.getSprite(random);
			return new ExplosionFlashParticle(world, x, y, z, effect.peakScale(), sprite);
		}
	}
}
