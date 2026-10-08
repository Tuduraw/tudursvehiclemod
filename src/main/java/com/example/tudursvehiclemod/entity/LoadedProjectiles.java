package com.example.tudursvehiclemod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;

/** How many projectiles - any ProjectileEntity, vanilla arrows and tridents as well as this mod's own - each world currently has loaded. Kept from Fabric's ServerEntityEvents.ENTITY_LOAD/ENTITY_UNLOAD (registered in VehicleMod), which fire as an entity starts/stops being tracked, so they always come in pairs.
 *
 * Lets AbstractVehicleEntity's per-vehicle, per-tick projectile hit query be skipped outright while a world has none at all (see its tudursvehiclemod$updateCustomHitDetection()). That query used to be skipped on VehicleProjectileEntity's own instance count instead, which missed vanilla projectiles - mixin.ProjectileVehicleHitMixin stops those from hitting a vehicle any other way, so they went straight through whenever none of this mod's own were in flight.
 *
 * Server thread only; cleared when the server stops. */
public final class LoadedProjectiles {
	private static final Map<ServerWorld, int[]> COUNTS = new HashMap<>();

	private LoadedProjectiles() {
	}

	public static void onLoad(Entity entity, ServerWorld world) {
		if (entity instanceof ProjectileEntity) {
			COUNTS.computeIfAbsent(world, w -> new int[1])[0]++;
		}
	}

	public static void onUnload(Entity entity, ServerWorld world) {
		if (entity instanceof ProjectileEntity) {
			int[] count = COUNTS.get(world);
			if (count != null && count[0] > 0) {
				count[0]--;
			}
		}
	}

	/** True if world has at least one projectile loaded. */
	public static boolean any(ServerWorld world) {
		int[] count = COUNTS.get(world);
		return count != null && count[0] > 0;
	}

	public static void clear() {
		COUNTS.clear();
	}
}
