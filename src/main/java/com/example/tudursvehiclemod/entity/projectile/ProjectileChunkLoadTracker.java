package com.example.tudursvehiclemod.entity.projectile;

import com.example.tudursvehiclemod.ChunkForceTracker;
import com.example.tudursvehiclemod.VehicleModServerConfig;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Flying projectiles stay always-loaded until impact (like CAS/Carrier aircraft already do - see AircraftEntity's own casForcedChunks doc), but with a server-wide budget on the total number of chunks that feature is allowed to force-load at once, for performance: this class is the single, shared place every VehicleProjectileEntity's own chunk-forcing routes through, rather than each projectile independently calling ChunkForceTracker itself.
 *
 * <p>Tracks each currently-force-loading projectile's own held chunk set in a {@link LinkedHashMap}, keyed by the projectile entity itself, in SPAWN order - Java's own LinkedHashMap preserves insertion order by default (accessOrder=false), and {@link #update} deliberately never removes-then-reinserts an already-tracked projectile's own entry when its chunks change (only {@link Map#put} on the existing key, which does NOT move it), so a projectile's own position in this ordering is fixed at the moment it first starts force-loading anything and never changes afterward, regardless of how many times its own grid gets recentered as it flies. This is what makes "evict the oldest-SPAWNED projectile first" (the specific behavior requested) actually work, rather than an LRU-style "oldest-updated" eviction that would instead tend to evict a projectile that's stopped moving (e.g. embedded in a block, mid-explosion-delay) ahead of one still actively, repeatedly re-triggering updates.
 *
 * <p>{@link VehicleModServerConfig#projectileForcedChunkLimit} (0 = feature disabled entirely - see that field's own doc) is re-read on every {@link #update} call (not cached), so a config change takes effect on the next tick without a server restart. */
public final class ProjectileChunkLoadTracker {

	private ProjectileChunkLoadTracker() {
	}

	private static final Map<VehicleProjectileEntity, Set<ChunkPos>> ACTIVE = new LinkedHashMap<>();

	/** Called once per tick from a projectile's own tudursvehiclemod$updateProjectileForcedChunks() (and once at spawn, from tudursvehiclemod$forceLoadSpawnChunk()) with that projectile's own newly-computed chunk grid - reconciles the actual ChunkForceTracker claims (releasing chunks no longer in newChunks, requesting ones newly added) against whatever this projectile was PREVIOUSLY tracked as holding, then enforces projectileForcedChunkLimit across every currently-tracked projectile combined, evicting the oldest-spawned one(s) (see this whole class's own doc for why "oldest-spawned" specifically) until the total is back within budget. */
	public static void update(VehicleProjectileEntity projectile, ServerWorld world, Set<ChunkPos> newChunks) {
		int limit = VehicleModServerConfig.get().projectileForcedChunkLimit;
		if (limit <= 0) {
			// 0 disables this feature entirely - releases any claim this projectile might already be holding (e.g. the config was just changed mid-flight) and never tracks/requests anything further.
			remove(projectile, world);
			return;
		}
		Set<ChunkPos> previousChunks = ACTIVE.get(projectile);
		if (previousChunks != null) {
			for (ChunkPos chunk : previousChunks) {
				if (!newChunks.contains(chunk)) {
					ChunkForceTracker.release(world, chunk, projectile);
				}
			}
		}
		for (ChunkPos chunk : newChunks) {
			if (previousChunks == null || !previousChunks.contains(chunk)) {
				ChunkForceTracker.request(world, chunk, projectile);
			}
		}
		// Deliberately put() on the existing key rather than remove()+put() - see this class's own doc for why preserving this projectile's own original spawn-order position (not moving it to "most recently updated") matters here.
		ACTIVE.put(projectile, newChunks);
		tudursvehiclemod$enforceLimit(world, limit);
	}

	/** Evicts the oldest-spawned tracked projectile(s) - see this class's own doc - until the combined total chunk count across every remaining one is back within limit. */
	private static void tudursvehiclemod$enforceLimit(ServerWorld world, int limit) {
		int total = 0;
		for (Set<ChunkPos> chunks : ACTIVE.values()) {
			total += chunks.size();
		}
		java.util.Iterator<Map.Entry<VehicleProjectileEntity, Set<ChunkPos>>> iterator = ACTIVE.entrySet().iterator();
		while (total > limit && iterator.hasNext()) {
			Map.Entry<VehicleProjectileEntity, Set<ChunkPos>> oldest = iterator.next();
			for (ChunkPos chunk : oldest.getValue()) {
				ChunkForceTracker.release(world, chunk, oldest.getKey());
			}
			total -= oldest.getValue().size();
			iterator.remove();
		}
	}

	/** Called from VehicleProjectileEntity's own remove() (the single, guaranteed choke point every one of that class's own several discard() call sites ultimately funnels through) - releases every chunk this specific projectile was holding and stops tracking it entirely. Safe to call even if this projectile was never actually tracked at all (e.g. projectileForcedChunkLimit was 0 its whole lifetime). */
	public static void remove(VehicleProjectileEntity projectile, ServerWorld world) {
		Set<ChunkPos> chunks = ACTIVE.remove(projectile);
		if (chunks != null) {
			for (ChunkPos chunk : chunks) {
				ChunkForceTracker.release(world, chunk, projectile);
			}
		}
	}
}
