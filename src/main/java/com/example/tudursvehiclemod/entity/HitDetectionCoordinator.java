package com.example.tudursvehiclemod.entity;

import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** An OPT-IN (see VehicleModServerConfig's own
 * parallelHitDetectionAcrossVehicles doc) alternative execution strategy
 * for this mod's own custom hit detection, batching EVERY vehicle's own
 * check into a single parallel pass at the end of each server tick rather
 * than each vehicle running its own check independently, inline, during
 * its own entity tick.
 *
 * Why this can help where the existing per-vehicle approach doesn't:
 * AbstractVehicleEntity's own tudursvehiclemod$updateCustomHitDetection()
 * already parallelizes across its OWN candidate projectiles - but in
 * practice a given vehicle usually has only one or two candidates near it
 * at a time, and dispatching that tiny amount of work to a thread pool
 * and joining back costs more than it saves. The expensive part isn't any
 * ONE vehicle's own check - it's that with many high-poly vehicles
 * loaded, ALL of their checks happen one after another, on the single
 * server thread, within the same tick. Batching them together turns that
 * into one parallel pass with enough total work in it to actually be
 * worth distributing.
 *
 * Thread safety: the parallel phase ONLY runs the pure, read-only
 * geometry math (each vehicle's own
 * tudursvehiclemod$checkProjectileHitForCoordinator() - see that method's
 * own doc), touching nothing but immutable, already-computed mesh data
 * and each entity's own current position/rotation/animation state - and
 * never the world: what the check would otherwise read from it (weapon
 * part rotations - see AbstractVehicleEntity.tudursvehiclemod$prepareParallelHitCheck())
 * is taken on the server thread right before the pass. A world read on a
 * worker waits for the server thread, which is itself waiting for the
 * workers: the server froze for good. Every
 * actual side effect (damage, discard) happens strictly sequentially
 * afterwards, on the server thread, only once the parallel phase has
 * fully joined. Running at END of tick also means every vehicle's own
 * per-tick animation state (spin phase, toggle progress, weapon aim -
 * all of which the geometry check reads) has already finished updating
 * for this tick and is stable for the whole parallel phase, rather than
 * potentially being mutated by some OTHER vehicle's own tick partway
 * through, which is a genuine (if unlikely) hazard the existing inline
 * approach technically has and this one doesn't. */
public final class HitDetectionCoordinator {

	private HitDetectionCoordinator() {
	}

	/** One vehicle's own pending hit-detection work for this tick - its own candidates were already gathered on the server thread (world entity queries are NOT safe to run off-thread), leaving only the pure geometry math for the parallel phase. */
	private record PendingCheck(AbstractVehicleEntity vehicle, ServerWorld world, List<AbstractVehicleEntity.HitCandidate> candidates) {
	}

	/** Concurrent purely as a defensive measure - submissions all come from the server thread during entity ticking in practice, but this costs essentially nothing and removes any doubt. */
	private static final Queue<PendingCheck> PENDING = new ConcurrentLinkedQueue<>();

	/** Called from AbstractVehicleEntity's own tudursvehiclemod$updateCustomHitDetection() (server thread, during that vehicle's own tick) INSTEAD of doing the check inline, whenever the parallel path is enabled - see this class's own doc. */
	static void tudursvehiclemod$submit(AbstractVehicleEntity vehicle, ServerWorld world, List<AbstractVehicleEntity.HitCandidate> candidates) {
		PENDING.add(new PendingCheck(vehicle, world, candidates));
	}

	/** One vehicle's own resolved hits, ready for sequential side effects. */
	private record ResolvedHits(PendingCheck check, List<AbstractVehicleEntity.ProjectileHit> hits) {
	}

	/** Called once per server tick, at END_SERVER_TICK (see VehicleMod's own registration) - drains everything submitted during this tick, runs all of it as ONE parallel pass, then applies every resulting side effect sequentially. A complete no-op when nothing was submitted (the overwhelmingly common case, and also always the case when the parallel path is disabled entirely). */
	public static void tudursvehiclemod$processPending() {
		if (PENDING.isEmpty()) {
			return;
		}
		List<PendingCheck> batch = new ArrayList<>(PENDING);
		PENDING.clear();

		int minimumVehicles = com.example.tudursvehiclemod.VehicleModServerConfig.get().parallelHitDetectionMinimumVehicles;
		boolean parallel = batch.size() >= minimumVehicles;
		java.util.stream.Stream<PendingCheck> stream = parallel
				? batch.parallelStream()
				: batch.stream();
		if (parallel) {
			// Everything the check reads from the world is taken now, on the server thread - see AbstractVehicleEntity.tudursvehiclemod$prepareParallelHitCheck()'s own doc for the freeze this prevents.
			for (PendingCheck check : batch) {
				check.vehicle().tudursvehiclemod$prepareParallelHitCheck();
			}
		}

		List<ResolvedHits> resolved;
		try {
			resolved = stream
				.map(check -> {
					List<AbstractVehicleEntity.ProjectileHit> hits = new ArrayList<>();
					// Each candidate is checked along the move it was gathered by (see AbstractVehicleEntity.HitCandidate's doc) - not wherever it has got to by now.
					for (AbstractVehicleEntity.HitCandidate candidate : check.candidates()) {
						if (candidate.projectile().isRemoved()) {
							continue;
						}
						AbstractVehicleEntity.ProjectileHit hit = check.vehicle().tudursvehiclemod$checkProjectileHitForCoordinator(candidate);
						if (hit != null) {
							hits.add(hit);
						}
					}
					return new ResolvedHits(check, hits);
				})
				.filter(result -> !result.hits().isEmpty())
				.collect(java.util.stream.Collectors.toList());
		} finally {
			if (parallel) {
				for (PendingCheck check : batch) {
					check.vehicle().tudursvehiclemod$finishParallelHitCheck();
				}
			}
		}

		// One projectile, one hit. Vehicles close together (a formation, an aircraft on a carrier's deck) can all find the same projectile inside their meshes in this pass - and every one of them used to take its damage, the explosion going off once per vehicle. Inline, the first vehicle to tick discards it, so the others never see it: the same here, the first in tick order (the batch keeps submission order - collect() preserves the encounter order even in parallel) takes it.
		java.util.Set<ProjectileEntity> applied = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (ResolvedHits result : resolved) {
			List<AbstractVehicleEntity.ProjectileHit> hits = new ArrayList<>(result.hits().size());
			for (AbstractVehicleEntity.ProjectileHit hit : result.hits()) {
				if (!hit.projectile().isRemoved() && applied.add(hit.projectile())) {
					hits.add(hit);
				}
			}
			if (!hits.isEmpty()) {
				result.check().vehicle().tudursvehiclemod$applyProjectileHits(result.check().world(), hits);
			}
		}
	}

	/** Called when a world/server shuts down, so a leftover batch from an interrupted tick can't carry over into an unrelated later one holding references to entities that no longer exist. */
	public static void tudursvehiclemod$clear() {
		PENDING.clear();
	}
}
