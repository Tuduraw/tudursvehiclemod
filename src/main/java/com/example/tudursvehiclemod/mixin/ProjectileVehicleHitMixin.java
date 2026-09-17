package com.example.tudursvehiclemod.mixin;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** This mod's own custom, per-tick, model-
 * surface-based hit detection (see AbstractVehicleEntity's own
 * tudursvehiclemod$updateCustomHitDetection() doc) is meant to be the
 * ONLY way a vehicle ever takes projectile damage. Vanilla's own
 * projectile-vs-entity collision, however, runs entirely independently,
 * within the PROJECTILE's own tick - and would otherwise still succeed
 * against this vehicle's own single, crude getDimensions()-derived box
 * regardless, since AbstractVehicleEntity's own canHit() has to stay true
 * for mounting/interaction to keep working at all (see that method's own
 * doc: an earlier attempt to just return false from canHit() broke
 * mounting entirely, since vanilla's own crosshair entity-targeting
 * raycast - used for BOTH combat AND general right-click interaction -
 * apparently relies on it too, not just combat specifically).
 *
 * This instead intercepts ProjectileEntity's own canHit(Entity) - the
 * PROJECTILE's own per-candidate "is this specific entity a valid target
 * for ME" check (distinct from the target's own no-arg canHit()) - and
 * forces it false whenever the candidate is any AbstractVehicleEntity,
 * regardless of that vehicle's own canHit() value. This only affects
 * PROJECTILE collision specifically; mounting/interaction is a completely
 * separate code path (interact()/interactAt()) untouched by this at all. */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileVehicleHitMixin {

	@Inject(method = "canHit(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
	private void tudursvehiclemod$excludeVehicles(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof AbstractVehicleEntity) {
			cir.setReturnValue(false);
		}
	}
}
