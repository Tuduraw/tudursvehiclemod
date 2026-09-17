package com.example.tudursvehiclemod.client.mixin;

import com.example.tudursvehiclemod.client.render.SearchLightIllumination;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity-side counterpart of WorldRendererLightmapMixin - see that class's own doc for the shared rationale (a search light must genuinely light things, which requires intercepting the lighting engine). Makes any entity standing inside a search light's own cone render brighter, exactly as terrain does.
 *
 * WHY getBlockLight RATHER THAN getLight: getLight() is final in 1.21.11, and it is also the wrong level to work at - it combines block and sky light into the packed lightmap coordinate, so intervening there would mean unpacking and repacking for no reason. getBlockLight() returns just the block-light level, which is precisely what a search light contributes, so this returns the brighter of it and vanilla's own and lets vanilla do its own packing afterwards.
 *
 * DELIBERATELY NOT GATED ON searchLightMode: entity lighting applies in both modes - unlike the terrain mixin, which is "dynamic_light" only. This asymmetry is intentional, not an oversight: terrain lighting has to be mode-gated because it only takes effect when a chunk is rebuilt, which is exactly what "beam" mode exists to stay free of. Entity lighting has no such constraint - entities are re-rendered every frame, so it follows a moving beam instantly at the cost of one cone test per entity. It was also already confirmed working and wanted as-is ("エンティティのライティング自体は現状でも問題ありません").
 *
 * Both sides still ask the SAME SearchLightIllumination.getLightLevelAt() question, so when terrain lighting is active the two agree exactly; in "beam" mode only entities light up, by design. */
@Mixin(EntityRenderer.class)
public class EntityRendererLightMixin {

	@Inject(method = "getBlockLight", at = @At("TAIL"), cancellable = true)
	private void tudursvehiclemod$brightenInsideSearchLight(Entity entity, BlockPos pos,
			CallbackInfoReturnable<Integer> cir) {
		// Queried at the entity's own actual position rather than the BlockPos it was given, so a beam's own cone edge cuts across entities at the same place it cuts across the world instead of snapping to block boundaries.
		int searchLightLevel = SearchLightIllumination.getLightLevelAt(entity.getEntityPos());
		if (searchLightLevel > cir.getReturnValueI()) {
			cir.setReturnValue(searchLightLevel);
		}
	}
}
