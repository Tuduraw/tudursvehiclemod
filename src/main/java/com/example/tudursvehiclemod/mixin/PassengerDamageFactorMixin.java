package com.example.tudursvehiclemod.mixin;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Implements MC Heli's own DamageFactor directive (see asset.VehicleExtras' own doc for what it means): multiplies incoming damage to a PASSENGER currently.. */
@Mixin(LivingEntity.class)
public abstract class PassengerDamageFactorMixin {

	@ModifyVariable(
			method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float tudursvehiclemod$applyPassengerDamageFactor(float amount) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getVehicle() instanceof AbstractVehicleEntity vehicle) {
			return amount * vehicle.getDefinition().damageFactor();
		}
		return amount;
	}
}
