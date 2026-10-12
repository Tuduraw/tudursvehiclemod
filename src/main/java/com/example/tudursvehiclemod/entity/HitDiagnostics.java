package com.example.tudursvehiclemod.entity;

import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** HITDIAG: TEMPORARY diagnostics for the "mesh hit detection stops working" report - to be removed once the cause is found. Every line is tagged [TVM-HITDIAG]. */
public final class HitDiagnostics {
	private static final Logger LOG = LoggerFactory.getLogger("TVM-HITDIAG");
	private static final java.util.Map<Integer, Long> LAST_LOGGED = new java.util.HashMap<>();
	private static long finalSegmentHits;
	private static long tickHits;
	private static long passThroughs;
	private static long projectilesGone;
	private static long lateHits;
	private static long nearMisses;
	private static final java.util.Map<String, Long> GONE_BY_REASON = new java.util.TreeMap<>();
	/** How close (blocks, from the static body's surface) a finished projectile's path has to have come to a vehicle to be logged as a NEAR-MISS. */
	private static final double NEAR_MISS_LOG_DISTANCE = 4.0;

	private HitDiagnostics() {
	}

	static void countTickHits(int n) {
		tickHits += n;
	}

	/** A projectile has struck a block. If its path went through (or right past) a vehicle without registering a hit, log everything about that vehicle's hit detection state. */
	public static void onBlockImpact(ServerWorld world, ProjectileEntity projectile, Vec3d from, Vec3d to, boolean vehicleHit) {
		if (vehicleHit) {
			finalSegmentHits++;
			return;
		}
		Vec3d velocity = projectile.getVelocity();
		// The recent path: the last few ticks back from the block, so a vehicle passed through a couple of ticks earlier is covered too.
		Vec3d pathStart = to.subtract(velocity.multiply(4.0));
		Vec3d path = to.subtract(pathStart);
		double pathLength = path.length();
		net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(pathStart, to).expand(24.0);
		long now = world.getTime();
		for (AbstractVehicleEntity vehicle : world.getEntitiesByClass(AbstractVehicleEntity.class, box, v -> true)) {
			if (vehicle.tudursvehiclemod$diagWasFiredBy(projectile)) {
				continue;
			}
			var def = vehicle.getDefinition();
			var mesh = com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.getMesh(def.model());
			double reach = mesh.map(m -> Math.sqrt(Math.pow(m.maxX() - m.minX(), 2) + Math.pow(m.maxY() - m.minY(), 2) + Math.pow(m.maxZ() - m.minZ(), 2)) * def.scale() * 0.5).orElse(3.0);
			double t = pathLength < 1.0e-6 ? 0.0 : net.minecraft.util.math.MathHelper.clamp(vehicle.getEntityPos().subtract(pathStart).dotProduct(path) / (pathLength * pathLength), 0.0, 1.0);
			double closest = vehicle.getEntityPos().distanceTo(pathStart.add(path.multiply(t)));
			if (closest > reach) {
				continue;
			}
			// Would the mesh, as it stands now, have been hit anywhere along that path?
			int steps = Math.max(1, (int) Math.ceil(pathLength / 0.25));
			int meshSamples = 0;
			for (int i = 0; i <= steps; i++) {
				if (vehicle.tudursvehiclemod$isPointNearMeshSurface(pathStart.add(path.multiply((double) i / steps)))) {
					meshSamples++;
				}
			}
			if (meshSamples == 0) {
				continue; // a genuine near miss
			}
			passThroughs++;
			Long last = LAST_LOGGED.get(vehicle.getId());
			if (last != null && now - last < 20) {
				continue;
			}
			LAST_LOGGED.put(vehicle.getId(), now);
			var nearestPlayer = world.getClosestPlayer(vehicle, -1.0);
			LOG.info("[TVM-HITDIAG] PASS-THROUGH projectile={}#{} owner={} from=({}) hit=({}) vel={} | vehicle={}#{} def={} pos=({}) yaw={} pitch={} roll={} destroyed={} removed={} age={} vehicleOf={} passengers={}"
							+ " | lastCheck={} ticksAgo={} reason={} tickEntityHere={} nearestPlayer={} simDist={} | mesh={} static={} meshSamplesOnPath={} | loadedProjectiles={} parallel={}",
					net.minecraft.entity.EntityType.getId(projectile.getType()), projectile.getId(), projectile.getOwner() == null ? "-" : projectile.getOwner().getName().getString(),
					fmt(from), fmt(to), String.format("%.2f", velocity.length()),
					net.minecraft.entity.EntityType.getId(vehicle.getType()), vehicle.getId(), vehicle.tudursvehiclemod$diagDefinitionId(), fmt(vehicle.getEntityPos()),
					String.format("%.1f", vehicle.getYaw()), String.format("%.1f", vehicle.getPitch()), String.format("%.1f", vehicle.getRoll()),
					vehicle.tudursvehiclemod$isDestroyed(), vehicle.isRemoved(), vehicle.age, vehicle.getVehicle(), vehicle.getPassengerList().size(),
					vehicle.tudursvehiclemod$diagLastCheckTime, vehicle.tudursvehiclemod$diagLastCheckTime < 0 ? "never" : String.valueOf(now - vehicle.tudursvehiclemod$diagLastCheckTime),
					vehicle.tudursvehiclemod$diagLastCheckReason, world.shouldTickEntityAt(vehicle.getBlockPos()),
					nearestPlayer == null ? "none" : String.format("%.1f", nearestPlayer.distanceTo(vehicle)), world.getServer().getPlayerManager().getSimulationDistance(),
					mesh.isPresent(), AbstractVehicleEntity.tudursvehiclemod$getStaticBodyMesh(def).isPresent(), meshSamples,
					LoadedProjectiles.any(world), com.example.tudursvehiclemod.VehicleModServerConfig.get().parallelHitDetectionAcrossVehicles);
		}
	}

	/** A projectile has gone (LoadedProjectiles.onEndWorldTick()). lateVehicle: the vehicle the after-the-fact path check found it went through (and has now damaged), or null. Otherwise, if its path came within NEAR_MISS_LOG_DISTANCE of a vehicle's body surface, how close and where (body-local) - so a shot that looked like a hit but wasn't shows how far off the server's mesh it really was. */
	static void onProjectileGone(ServerWorld world, ProjectileEntity projectile, java.util.List<Vec3d> path, String reason, AbstractVehicleEntity lateVehicle) {
		projectilesGone++;
		String key = reason + "/" + net.minecraft.entity.EntityType.getId(projectile.getType()).getPath();
		GONE_BY_REASON.merge(key, 1L, Long::sum);
		if (lateVehicle != null) {
			lateHits++;
			LOG.info("[TVM-HITDIAG] LATE-HIT projectile={}#{} reason={} path={} | vehicle={}#{} def={} pos=({}) yaw={} pitch={} roll={} parallel={}",
					net.minecraft.entity.EntityType.getId(projectile.getType()), projectile.getId(), reason, fmtPath(path),
					net.minecraft.entity.EntityType.getId(lateVehicle.getType()), lateVehicle.getId(), lateVehicle.tudursvehiclemod$diagDefinitionId(), fmt(lateVehicle.getEntityPos()),
					String.format("%.1f", lateVehicle.getYaw()), String.format("%.1f", lateVehicle.getPitch()), String.format("%.1f", lateVehicle.getRoll()),
					com.example.tudursvehiclemod.VehicleModServerConfig.get().parallelHitDetectionAcrossVehicles);
			return;
		}
		net.minecraft.util.math.Box box = null;
		for (Vec3d point : path) {
			net.minecraft.util.math.Box pointBox = new net.minecraft.util.math.Box(point, point);
			box = box == null ? pointBox : box.union(pointBox);
		}
		long now = world.getTime();
		for (AbstractVehicleEntity vehicle : world.getEntitiesByClass(AbstractVehicleEntity.class, box.expand(48.0), v -> !v.isRemoved())) {
			if (vehicle.tudursvehiclemod$diagWasFiredBy(projectile)) {
				continue;
			}
			var def = vehicle.getDefinition();
			var mesh = com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.getMesh(def.model());
			if (mesh.isEmpty()) {
				continue;
			}
			var m = mesh.get();
			double reach = Math.sqrt(Math.pow(m.maxX() - m.minX(), 2) + Math.pow(m.maxY() - m.minY(), 2) + Math.pow(m.maxZ() - m.minZ(), 2)) * def.scale() * 0.5 + NEAR_MISS_LOG_DISTANCE;
			// Coarse first: does the path come within reach of the vehicle at all?
			boolean inReach = false;
			for (int i = 0; i + 1 < path.size() && !inReach; i++) {
				Vec3d from = path.get(i);
				Vec3d segment = path.get(i + 1).subtract(from);
				double length = segment.length();
				double t = length < 1.0e-6 ? 0.0 : net.minecraft.util.math.MathHelper.clamp(vehicle.getEntityPos().subtract(from).dotProduct(segment) / (length * length), 0.0, 1.0);
				inReach = vehicle.getEntityPos().distanceTo(from.add(segment.multiply(t))) <= reach;
			}
			if (!inReach) {
				continue;
			}
			Long last = LAST_LOGGED.get(vehicle.getId());
			if (last != null && now - last < 10) {
				continue;
			}
			// Closest approach to the body surface, sampled every half block.
			double[] best = null;
			Vec3d bestPoint = null;
			for (int i = 0; i + 1 < path.size(); i++) {
				Vec3d from = path.get(i);
				Vec3d segment = path.get(i + 1).subtract(from);
				int steps = Math.max(1, (int) Math.ceil(segment.length() / 0.5));
				for (int step = 0; step <= steps; step++) {
					Vec3d sample = from.add(segment.multiply((double) step / steps));
					if (sample.distanceTo(vehicle.getEntityPos()) > reach) {
						continue;
					}
					double[] near = vehicle.tudursvehiclemod$diagNearestBodySurface(sample);
					if (near != null && (best == null || near[0] < best[0])) {
						best = near;
						bestPoint = sample;
					}
				}
			}
			if (best == null || best[0] > NEAR_MISS_LOG_DISTANCE) {
				continue;
			}
			nearMisses++;
			LAST_LOGGED.put(vehicle.getId(), now);
			LOG.info("[TVM-HITDIAG] NEAR-MISS projectile={}#{} reason={} vel={} path={} | vehicle={}#{} def={} pos=({}) yaw={} pitch={} roll={} | closest={} blocks at ({}) bodyLocal=({}) meshBounds=({} .. {}) meshFile={} parallel={}",
					net.minecraft.entity.EntityType.getId(projectile.getType()), projectile.getId(), reason, String.format("%.2f", projectile.getVelocity().length()), fmtPath(path),
					net.minecraft.entity.EntityType.getId(vehicle.getType()), vehicle.getId(), vehicle.tudursvehiclemod$diagDefinitionId(), fmt(vehicle.getEntityPos()),
					String.format("%.1f", vehicle.getYaw()), String.format("%.1f", vehicle.getPitch()), String.format("%.1f", vehicle.getRoll()),
					String.format("%.2f", best[0]), fmt(bestPoint), String.format("%.2f,%.2f,%.2f", best[1], best[2], best[3]),
					String.format("%.2f,%.2f,%.2f", m.minX(), m.minY(), m.minZ()), String.format("%.2f,%.2f,%.2f", m.maxX(), m.maxY(), m.maxZ()),
					com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.diagResolvedModelPath(def.model()),
					com.example.tudursvehiclemod.VehicleModServerConfig.get().parallelHitDetectionAcrossVehicles);
		}
	}

	private static String fmtPath(java.util.List<Vec3d> path) {
		StringBuilder builder = new StringBuilder();
		for (Vec3d point : path) {
			if (builder.length() > 0) {
				builder.append(" -> ");
			}
			builder.append('(').append(fmt(point)).append(')');
		}
		return builder.toString();
	}

	/** Once a minute: overall counts. */
	public static void onServerTick(net.minecraft.server.MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		if (world.getTime() % 1200 != 0) {
			return;
		}
		int vehicles = 0;
		int projectiles = 0;
		for (net.minecraft.entity.Entity entity : world.iterateEntities()) {
			if (entity instanceof AbstractVehicleEntity) {
				vehicles++;
			} else if (entity instanceof ProjectileEntity) {
				projectiles++;
			}
		}
		LOG.info("[TVM-HITDIAG] summary vehicles={} projectilesLoaded={} loadedProjectilesGate={} forcedChunks={} hits(tick={} finalSegment={} late={}) passThroughs={} nearMisses={} gone={} {} parallel={} commonPoolQueued={}",
				vehicles, projectiles, LoadedProjectiles.any(world), world.getForcedChunks().size(), tickHits, finalSegmentHits, lateHits, passThroughs, nearMisses, projectilesGone, GONE_BY_REASON,
				com.example.tudursvehiclemod.VehicleModServerConfig.get().parallelHitDetectionAcrossVehicles,
				java.util.concurrent.ForkJoinPool.commonPool().getQueuedSubmissionCount() + java.util.concurrent.ForkJoinPool.commonPool().getQueuedTaskCount());
	}

	private static String fmt(Vec3d v) {
		return String.format("%.1f,%.1f,%.1f", v.x, v.y, v.z);
	}
}
