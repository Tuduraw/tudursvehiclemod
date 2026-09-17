package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.client.VehicleModClient;
import com.example.tudursvehiclemod.client.VehicleModConfig;
import net.minecraft.client.MinecraftClient;

/** Shared by VehicleEntityRenderer/VehicleProjectileRenderer's own shouldRender overrides. */
final class RenderDistanceHelper {

	private RenderDistanceHelper() {
	}

	/** Returns Boolean.TRUE/FALSE if config decides the outcome outright (disableEntityRenderCulling, or matchChunkViewDistanceForEntityRender's own distance check).. */
	static Boolean tudursvehiclemod$overrideShouldRender(double entityX, double entityY, double entityZ,
			double cameraX, double cameraY, double cameraZ) {
		VehicleModConfig config = VehicleModClient.getConfig();
		if (config == null) {
			return null; // config not loaded yet (shouldn't normally happen) - fall back to vanilla
		}
		if (config.disableEntityRenderCulling) {
			// Unconditional - skips frustum culling too, not just distance: culling-related optimizations are disabled entirely rather than just extending..
			return Boolean.TRUE;
		}
		if (config.matchChunkViewDistanceForEntityRender) {
			double viewDistanceBlocks = MinecraftClient.getInstance().options.getViewDistance().getValue() * 16.0;
			double dx = entityX - cameraX;
			double dy = entityY - cameraY;
			double dz = entityZ - cameraZ;
			double distanceSquared = dx * dx + dy * dy + dz * dz;
			// Deliberately skips frustum culling too (not just extending the distance threshold).
			return distanceSquared < viewDistanceBlocks * viewDistanceBlocks;
		}
		return null;
	}
}
