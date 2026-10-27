package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.asset.ServerObjModelHitboxes;
import com.example.tudursvehiclemod.asset.VehicleDefinition;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

/** Builds a vehicle definition's hit-detection meshes (the static body, each animated part, the whole model - each with its spatial grid) in the background as soon as a vehicle of that definition is in a world, instead of on its first hit. A high-poly model used to stall the server thread - or a parallel hit-check worker - for the whole build, right in the middle of a fight.
 *
 * <p>How many definitions are built at once is VehicleModServerConfig.hitMeshBuildMaxConcurrent: -1 (the default) for no limit (the shared fork-join pool), a positive number for a pool of that many threads. A hit that comes before its mesh is ready simply builds it then, as before (the mesh caches' computeIfAbsent() makes it wait for the build already running rather than start a second one). */
public final class HitMeshPreloader {
	/** Definitions already queued. By identity: a resource reload makes new definition objects, which are queued afresh - and clear() is called then anyway, with the mesh caches. */
	private static final Set<VehicleDefinition> REQUESTED = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

	private static ExecutorService executor;

	private HitMeshPreloader() {
	}

	/** Queues def's meshes for building, once per definition. Server side. */
	public static void request(VehicleDefinition def) {
		if (def == null || !REQUESTED.add(def)) {
			return;
		}
		try {
			tudursvehiclemod$executor().execute(() -> tudursvehiclemod$build(def));
		} catch (RuntimeException e) {
			// A rejected task (an executor being shut down) - the meshes will simply be built on first use.
			REQUESTED.remove(def);
		}
	}

	private static void tudursvehiclemod$build(VehicleDefinition def) {
		try {
			AbstractVehicleEntity.tudursvehiclemod$getStaticBodyMesh(def);
			for (String partName : AbstractVehicleEntity.tudursvehiclemod$getAnimatedPartNames(def)) {
				ServerObjModelHitboxes.getPartMesh(def.model(), partName);
			}
			// The whole-model mesh: the hit search box is sized from it (AbstractVehicleEntity.tudursvehiclemod$updateCustomHitDetection()).
			ServerObjModelHitboxes.getMesh(def.model());
			// The solid-cell grid contact detection uses (VehicleContacts) - only ever built here.
			VehicleContacts.tudursvehiclemod$buildVoxels(def);
		} catch (RuntimeException e) {
			org.slf4j.LoggerFactory.getLogger("tudursvehiclemod").warn("Building the hit meshes for {} in the background failed - they'll be built on first use", def.model(), e);
		}
	}

	private static synchronized ExecutorService tudursvehiclemod$executor() {
		if (executor == null) {
			int maxConcurrent = com.example.tudursvehiclemod.VehicleModServerConfig.get().hitMeshBuildMaxConcurrent;
			if (maxConcurrent <= 0) {
				executor = ForkJoinPool.commonPool();
			} else {
				executor = Executors.newFixedThreadPool(maxConcurrent, runnable -> {
					Thread thread = new Thread(runnable, "tudursvehiclemod-hit-mesh-builder");
					thread.setDaemon(true);
					thread.setPriority(Thread.MIN_PRIORITY);
					return thread;
				});
			}
		}
		return executor;
	}

	/** Forgets what was queued - with the mesh caches, on a resource reload (ServerObjModelHitboxes.clearCaches()). */
	public static void clear() {
		REQUESTED.clear();
		generation++;
	}

	/** Bumped by every clear(): a vehicle keeps its definition object across a reload, so it compares this rather than the definition alone to know it has to ask again. */
	private static volatile int generation;

	public static int generation() {
		return generation;
	}
}
