package com.example.tudursvehiclemod.client.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the player's own first-person arm/held
 * item entirely while riding any vehicle (inventory GUI/HUD are both
 * untouched - this only ever affects the in-world, first-person hand
 * rendering specifically, nothing else). */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"), cancellable = true)
	private void tudursvehiclemod$hideWhileRiding(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue,
												   ClientPlayerEntity player, int light, CallbackInfo ci) {
		if (player.getVehicle() instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity) {
			ci.cancel();
		}
	}
}
