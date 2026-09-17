package com.example.tudursvehiclemod.client.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla's own package-private RenderLayer.of(String, RenderSetup) factory method - used by client.render.DitherCutoutLayers to build this mod's own dithered-cutout entity render layer, since RenderLayers itself has no such variant and this factory method is the only way to construct a RenderLayer from a custom RenderSetup at all. */
@Mixin(RenderLayer.class)
public interface RenderLayerFactoryInvoker {
	@Invoker("of")
	static RenderLayer tudursvehiclemod$of(String name, RenderSetup renderSetup) {
		throw new AssertionError("Mixin invoker not applied");
	}
}
