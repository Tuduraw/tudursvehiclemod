package com.example.tudursvehiclemod.client.mixin;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The seated riding pose does not trigger while mounted on a ParachuteEntity specifically - and a further report that the previous session's own attempt at this (directly overwriting hasVehicle/pose from two separate updateRenderState() injections, in mixin.LivingEntityRendererMixin and the now-removed mixin.BipedEntityRendererMixin) simply didn't work at all: this targets setAngles(BipedEntityRenderState) directly instead - confirmed directly from BipedEntityModel's own bytecode to be the actual method that reads hasVehicle to decide whether to bend the legs into the seated pose. Injecting at this method's own HEAD, forcing hasVehicle/pose back to normal immediately before its own vanilla logic runs, sidesteps every ordering question entirely: setAngles() is unconditionally called after updateRenderState() has already finished populating the state for this frame, every single frame, regardless of how many separate updateRenderState() injections exist or what order they run in. This is now the ONLY place in the whole mechanism that actually overwrites hasVehicle/pose - client.ParachuteSittingSuppressRenderState's own doc covers the flag this reads, and where it gets set. */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {

	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("HEAD"))
	private void tudursvehiclemod$suppressParachuteSittingPose(BipedEntityRenderState state, CallbackInfo ci) {
		if ((Object) state instanceof com.example.tudursvehiclemod.client.ParachuteSittingSuppressRenderState suppressState
				&& suppressState.tudursvehiclemod$isSittingPoseSuppressed()) {
			state.hasVehicle = false;
			state.pose = net.minecraft.entity.EntityPose.STANDING;
		}
	}
}
