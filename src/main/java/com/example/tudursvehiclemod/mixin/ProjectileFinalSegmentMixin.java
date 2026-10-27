package com.example.tudursvehiclemod.mixin;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.entity.ProjectileDeflection;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Before a projectile's block or entity hit is handled, its last move (from where it was at the start of this tick to the point struck) is checked against nearby vehicles' meshes: a shell that passed through a hull and struck the ground or a wall behind it in the same tick used to go unregistered - see AbstractVehicleEntity.tudursvehiclemod$tryHitOnFinalSegment(). Every projectile type (this mod's and vanilla's arrows, tridents and so on), since vehicles are hit only through their meshes (see ProjectileVehicleHitMixin). Server side only. */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileFinalSegmentMixin {

	@Inject(method = "hitOrDeflect", at = @At("HEAD"), cancellable = true)
	private void tudursvehiclemod$checkVehiclesOnFinalSegment(HitResult hitResult, CallbackInfoReturnable<ProjectileDeflection> cir) {
		// An entity hit too: a shell that went through a hull and on into someone standing behind it, or a crew member inside it, struck the vehicle first.
		if (hitResult.getType() == HitResult.Type.MISS) {
			return;
		}
		ProjectileEntity self = (ProjectileEntity) (Object) this;
		if (!(self.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		// Where the projectile was when this tick began - see AbstractVehicleEntity.tudursvehiclemod$lastMove().
		Vec3d from = AbstractVehicleEntity.tudursvehiclemod$lastMove(self)[0];
		boolean vehicleHit = AbstractVehicleEntity.tudursvehiclemod$tryHitOnFinalSegment(world, self, from, hitResult.getPos());
		if (vehicleHit) {
			cir.setReturnValue(ProjectileDeflection.NONE);
		}
	}
}
