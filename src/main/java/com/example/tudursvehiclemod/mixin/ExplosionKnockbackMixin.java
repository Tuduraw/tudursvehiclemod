package com.example.tudursvehiclemod.mixin;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.entity.Entity;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Revisited after an earlier attempt (overriding
 * AbstractVehicleEntity's own getVelocityMultiplier()) caused a severe,
 * unrelated regression (see that method's own removal doc): vanilla's own
 * Entity.move() applies getVelocityMultiplier() to EVERY call,
 * unconditionally, every single tick, regardless of whether an explosion
 * is even involved at all - so overriding it to suppress explosion
 * knockback specifically also zeroed this vehicle's own horizontal
 * velocity on every ordinary tick, nothing to do with explosions.
 *
 * ExplosionBehavior.getKnockbackModifier(Entity) is the correct, narrow
 * hook instead - a comparison against vanilla mobs that are
 * already immune to explosion knockback (the Ender Dragon, the Wither),
 * this method (not the KNOCKBACK_RESISTANCE attribute those two actually
 * use - a LivingEntity-only mechanism this mod's own Entity-based
 * vehicles don't have access to at all) is ONLY ever consulted from
 * within an explosion's own knockback-impulse computation (see
 * ExplosionImpl's own doc) - never as part of any regular per-tick
 * movement/physics loop. EntityExplosionBehavior (the subclass actually
 * used for most real explosions - creepers, TNT, and this mod's own
 * tudursvehiclemod$onDestroyed()) doesn't override this method at all,
 * so mixing into the base ExplosionBehavior class here covers all of
 * those uniformly. shouldDamage()/calculateDamage() are entirely
 * untouched - a vehicle still takes normal explosion damage, it just
 * doesn't go flying afterward. */
@Mixin(ExplosionBehavior.class)
public abstract class ExplosionKnockbackMixin {

	@Inject(method = "getKnockbackModifier", at = @At("HEAD"), cancellable = true)
	private void tudursvehiclemod$suppressVehicleKnockback(Entity entity, CallbackInfoReturnable<Float> cir) {
		if (entity instanceof AbstractVehicleEntity) {
			cir.setReturnValue(0f);
		}
	}
}
