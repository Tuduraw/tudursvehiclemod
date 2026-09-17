package com.example.tudursvehiclemod.client.mixin;

import com.example.tudursvehiclemod.client.render.SearchLightIllumination;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes terrain inside a search light's own cone actually render brighter.
 *
 * WHY HERE: WorldRenderer.getLightmapCoordinates() is what the chunk renderer asks for every block face's own light, and its return value is the packed lightmap coordinate the vertex is then given. Injecting at TAIL lets this compare vanilla's own answer against what this mod's own lights say about the same position and hand back whichever is brighter - so an unlit area gets lit while an already-bright one is left completely alone.
 *
 * TAKING THE BRIGHTER rather than adding is deliberate: block light is a 0-15 level, and vanilla itself resolves overlapping light sources by maximum, not by sum. Adding would let a search light make daylight brighter than daylight.
 *
 * The SKY light component is passed through untouched. A search light is a block light source, exactly like a torch or a lantern, so it has no business claiming the sky is brighter than it is - that would wash out the day/night cycle in the lit area. */
@Mixin(WorldRenderer.class)
public class WorldRendererLightmapMixin {

	@Inject(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
			at = @At("TAIL"), cancellable = true)
	private static void tudursvehiclemod$brightenInsideSearchLight(BlockRenderView world, BlockPos pos,
			CallbackInfoReturnable<Integer> cir) {
		tudursvehiclemod$applySearchLight(pos, cir);
	}

	/** The 2-argument overload above is a convenience wrapper, and is NOT what the chunk/block renderer itself calls - block model rendering goes through this 4-argument form (the one taking a BrightnessGetter, which lives in net.minecraft.client.render.block). Injecting only into the short form therefore lit entities (which reach the lighting engine by their own separate path) while leaving terrain completely untouched, which is exactly the reported behaviour.
	 *
	 * Both overloads now funnel into the same tudursvehiclemod$applySearchLight(), so whichever one a given piece of geometry happens to use, it gets the identical treatment. */
	@Inject(method = "getLightmapCoordinates(Lnet/minecraft/client/render/WorldRenderer$BrightnessGetter;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I",
			at = @At("TAIL"), cancellable = true)
	private static void tudursvehiclemod$brightenInsideSearchLightWithState(WorldRenderer.BrightnessGetter brightnessGetter, BlockRenderView world,
			BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		tudursvehiclemod$applySearchLight(pos, cir);
	}

	/** The shared body both overloads above use - see the 4-argument one's own doc for why there are two entry points at all. */
	private static void tudursvehiclemod$applySearchLight(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		// TERRAIN lighting is "dynamic_light" only. This path takes effect only when a chunk is rebuilt, so leaving it on in "beam" mode is exactly what made that mode quietly chunk-rebuild-dependent - the thing it exists to avoid. Entity lighting is deliberately NOT gated this way (see EntityRendererLightMixin's own doc).
		var config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
		if (config == null || !"dynamic_light".equals(config.searchLightMode)) {
			return;
		}
		// getLightLevelAt() early-outs on an empty light list, so a world with no lit search light in it pays almost nothing for this being here at all - which matters, because this is called for a very large number of positions per frame.
		int searchLightLevel = SearchLightIllumination.getLightLevelAt(Vec3d.ofCenter(pos));
		if (searchLightLevel <= 0) {
			return;
		}
		int vanilla = cir.getReturnValueI();
		int vanillaBlockLight = LightmapTextureManager.getBlockLightCoordinates(vanilla);
		if (searchLightLevel <= vanillaBlockLight) {
			return;
		}
		// Rebuilds the packed coordinate with the brighter block light, keeping vanilla's own sky component exactly as it was - see this class's own doc for why sky is deliberately left alone.
		cir.setReturnValue(LightmapTextureManager.pack(searchLightLevel, LightmapTextureManager.getSkyLightCoordinates(vanilla)));
	}
}
