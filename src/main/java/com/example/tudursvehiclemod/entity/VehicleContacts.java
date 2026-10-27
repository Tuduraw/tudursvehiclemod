package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.asset.MeshVoxelGrid;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Contact detection by the vehicles' own shapes: which other vehicles and which living entities a vehicle is touching or overlapping, judged by its model (asset.MeshVoxelGrid - the mesh as solid cells) rather than by its entity bounding box (a fixed small box, AbstractVehicleEntity.getDimensions(), that has nothing to do with the vehicle's size).
 *
 * <p><b>Vehicle against vehicle.</b> The two vehicles' boxes in the world (their grids' bounds, turned with them) are intersected, and points are sampled through the intersection a cell apart: a point solid in both is a point of contact. A pair whose boxes don't meet costs one box test. A large intersection is sampled more coarsely (at most MAX_PAIR_SAMPLES points), and each grid throws away empty space with its coarse layer first.
 *
 * <p><b>Vehicle against a living entity.</b> The entity's bounding box is sampled the same way against the vehicle's grid.
 *
 * <p><b>Cost.</b> Nothing runs on its own: contacts are worked out when asked for (contactsFor()), once per vehicle per tick at most. Aircraft ask when flying fast enough for a collision to do damage (AircraftEntity.tudursvehiclemod$checkEntityCrashDamage()); an addon can ask for any vehicle; the debug display (/tvm debug contacts) asks for every loaded vehicle each tick.
 *
 * <p><b>What a contact doesn't do (yet).</b> Nothing is pushed apart: a vehicle still passes through another, and a mob through a vehicle, as before - this only tells who is touching what, and where. A grid is the vehicle at rest: animated parts that move (spinning, toggling, VTOL rotors, ammo parts) are left out, turrets are taken at their rest position.
 *
 * <p>Server thread only. */
public final class VehicleContacts {

	/** One contact: what the vehicle touches, roughly where (the average of the overlapping sample points), and how many sample points overlap - a rough measure of how deep. */
	public record Contact(Entity other, Vec3d point, int overlap) {
	}

	/** Most points sampled for one pair of vehicles. */
	private static final int MAX_PAIR_SAMPLES = 32768;

	private static final Map<VehicleDefinition, Optional<MeshVoxelGrid>> VOXELS = Collections.synchronizedMap(new IdentityHashMap<>());
	private static final Map<ServerWorld, Set<AbstractVehicleEntity>> LOADED = new HashMap<>();
	/** Contacts already worked out this tick, by vehicle. */
	private static final Map<AbstractVehicleEntity, List<Contact>> CACHE = new IdentityHashMap<>();
	private static long cacheTick = Long.MIN_VALUE;
	private static ServerWorld cacheWorld;

	private VehicleContacts() {
	}

	// ---------------------------------------------------------------- grids

	/** Builds def's contact grid (if it isn't already) - called from entity.HitMeshPreloader, in the background. */
	public static void tudursvehiclemod$buildVoxels(VehicleDefinition def) {
		if (VOXELS.containsKey(def)) {
			return;
		}
		Set<String> moving = new java.util.HashSet<>();
		def.spinningParts().forEach(part -> moving.add(part.part()));
		def.toggleParts().forEach(part -> moving.add(part.part()));
		def.vtolRotorParts().forEach(part -> moving.add(part.part()));
		def.ammoParts().forEach(part -> moving.add(part.part()));
		Optional<MeshVoxelGrid> grid = com.example.tudursvehiclemod.asset.ServerObjModelHitboxes.getMeshExcludingGroups(def.model(), moving)
				.map(mesh -> MeshVoxelGrid.build(mesh, def.scale()));
		VOXELS.put(def, grid);
	}

	/** def's contact grid, or empty if there is none or it isn't built yet (it's asked for then, and is there in a moment). */
	public static Optional<MeshVoxelGrid> tudursvehiclemod$voxels(VehicleDefinition def) {
		Optional<MeshVoxelGrid> grid = VOXELS.get(def);
		if (grid == null) {
			HitMeshPreloader.request(def);
			return Optional.empty();
		}
		return grid;
	}

	/** With the other per-definition caches, on a resource reload. */
	public static void tudursvehiclemod$clearVoxels() {
		VOXELS.clear();
	}

	// ---------------------------------------------------------------- loaded vehicles

	/** Called by every vehicle on each server tick: the set of vehicles to test against is the ones that are ticking (ENTITY_LOAD alone missed vehicles spawned into a chunk that only became entity-ticking later). Cheap - a set insertion. */
	public static void tudursvehiclemod$markTicking(AbstractVehicleEntity vehicle, ServerWorld world) {
		LOADED.computeIfAbsent(world, w -> Collections.newSetFromMap(new IdentityHashMap<>())).add(vehicle);
	}

	public static void onLoad(Entity entity, ServerWorld world) {
		if (entity instanceof AbstractVehicleEntity vehicle) {
			LOADED.computeIfAbsent(world, w -> Collections.newSetFromMap(new IdentityHashMap<>())).add(vehicle);
		}
	}

	public static void onUnload(Entity entity, ServerWorld world) {
		if (entity instanceof AbstractVehicleEntity vehicle) {
			Set<AbstractVehicleEntity> loaded = LOADED.get(world);
			if (loaded != null) {
				loaded.remove(vehicle);
			}
			CACHE.remove(vehicle);
		}
	}

	public static void clear() {
		LOADED.clear();
		CACHE.clear();
		cacheWorld = null;
	}

	/** The vehicles world has loaded. Live view. */
	public static Set<AbstractVehicleEntity> loadedIn(ServerWorld world) {
		Set<AbstractVehicleEntity> loaded = LOADED.get(world);
		if (loaded == null) {
			return Set.of();
		}
		loaded.removeIf(Entity::isRemoved);
		return loaded;
	}

	// ---------------------------------------------------------------- contacts

	/** Everything vehicle touches this tick: other vehicles first, then living entities. Not its own passengers. Empty while its grid isn't built yet. */
	public static List<Contact> contactsFor(AbstractVehicleEntity vehicle) {
		if (!(vehicle.getEntityWorld() instanceof ServerWorld world)) {
			return List.of();
		}
		long now = world.getTime();
		if (now != cacheTick || world != cacheWorld) {
			CACHE.clear();
			cacheTick = now;
			cacheWorld = world;
		}
		List<Contact> cached = CACHE.get(vehicle);
		if (cached != null) {
			return cached;
		}
		Pose self = Pose.of(vehicle);
		if (self == null) {
			CACHE.put(vehicle, List.of());
			return List.of();
		}
		List<Contact> contacts = new ArrayList<>();
		for (AbstractVehicleEntity other : loadedIn(world)) {
			if (other == vehicle || other.isRemoved() || other.hasPassenger(vehicle) || vehicle.hasPassenger(other)) {
				continue;
			}
			// The other half of a pair already worked out this tick.
			List<Contact> otherContacts = CACHE.get(other);
			if (otherContacts != null) {
				for (Contact contact : otherContacts) {
					if (contact.other() == vehicle) {
						contacts.add(new Contact(other, contact.point(), contact.overlap()));
					}
				}
				continue;
			}
			Pose pose = Pose.of(other);
			if (pose == null || !self.worldBox().intersects(pose.worldBox())) {
				continue;
			}
			Contact contact = tudursvehiclemod$overlap(self, pose, other);
			if (contact != null) {
				contacts.add(contact);
			}
		}
		for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, self.worldBox(),
				entity -> entity.isAlive() && !entity.hasVehicle() && !(entity instanceof CarrierRunwayPlatformEntity))) {
			Contact contact = tudursvehiclemod$entityOverlap(self, living);
			if (contact != null) {
				contacts.add(contact);
			}
		}
		List<Contact> result = List.copyOf(contacts);
		CACHE.put(vehicle, result);
		return result;
	}

	/** A vehicle's grid placed in the world this tick: what's needed to take a world point into its grid. */
	private record Pose(AbstractVehicleEntity vehicle, MeshVoxelGrid grid, Vec3d position, Quaternionf inverseRotation, float scale, Box worldBox) {
		static Pose of(AbstractVehicleEntity vehicle) {
			VehicleDefinition def = vehicle.getDefinition();
			Optional<MeshVoxelGrid> gridOpt = tudursvehiclemod$voxels(def);
			if (gridOpt.isEmpty()) {
				return null;
			}
			MeshVoxelGrid grid = gridOpt.get();
			float scale = def.scale() > 0f ? def.scale() : 1f;
			Quaternionf rotation = vehicle.tudursvehiclemod$getCurrentRotationForRendering();
			Vec3d position = vehicle.getEntityPos();
			float[] b = grid.bounds();
			double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
			double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
			for (int corner = 0; corner < 8; corner++) {
				Vector3f p = new Vector3f((corner & 1) == 0 ? b[0] : b[3], (corner & 2) == 0 ? b[1] : b[4], (corner & 4) == 0 ? b[2] : b[5]).mul(scale);
				rotation.transform(p);
				minX = Math.min(minX, p.x);
				minY = Math.min(minY, p.y);
				minZ = Math.min(minZ, p.z);
				maxX = Math.max(maxX, p.x);
				maxY = Math.max(maxY, p.y);
				maxZ = Math.max(maxZ, p.z);
			}
			Box box = new Box(position.x + minX, position.y + minY, position.z + minZ, position.x + maxX, position.y + maxY, position.z + maxZ);
			return new Pose(vehicle, grid, position, rotation.conjugate(new Quaternionf()), scale, box);
		}

		boolean isSolidAt(double x, double y, double z) {
			Vector3f local = new Vector3f((float) (x - this.position.x), (float) (y - this.position.y), (float) (z - this.position.z));
			this.inverseRotation.transform(local);
			local.div(this.scale);
			return this.grid.isSolid(local.x, local.y, local.z);
		}

		/** World size of one cell. */
		double cellWorld() {
			return this.grid.cellSize() * this.scale;
		}
	}

	private static Contact tudursvehiclemod$overlap(Pose a, Pose b, Entity other) {
		Box region = a.worldBox().intersection(b.worldBox());
		double spacing = Math.max(a.cellWorld(), b.cellWorld());
		double volume = region.getLengthX() * region.getLengthY() * region.getLengthZ();
		double samples = volume / (spacing * spacing * spacing);
		if (samples > MAX_PAIR_SAMPLES) {
			spacing *= Math.cbrt(samples / MAX_PAIR_SAMPLES);
		}
		return tudursvehiclemod$sample(region, spacing, other, (x, y, z) -> a.isSolidAt(x, y, z) && b.isSolidAt(x, y, z));
	}

	private static Contact tudursvehiclemod$entityOverlap(Pose vehicle, LivingEntity entity) {
		Box box = entity.getBoundingBox();
		double spacing = Math.min(vehicle.cellWorld(), 0.5);
		return tudursvehiclemod$sample(box, spacing, entity, vehicle::isSolidAt);
	}

	@FunctionalInterface
	private interface PointTest {
		boolean test(double x, double y, double z);
	}

	/** Samples region on a grid of the given spacing (always reaching both faces of each axis), returning a contact at the average of the points that pass, or null if none does. */
	private static Contact tudursvehiclemod$sample(Box region, double spacing, Entity other, PointTest test) {
		int nx = Math.max(1, (int) Math.ceil(region.getLengthX() / spacing));
		int ny = Math.max(1, (int) Math.ceil(region.getLengthY() / spacing));
		int nz = Math.max(1, (int) Math.ceil(region.getLengthZ() / spacing));
		double sumX = 0;
		double sumY = 0;
		double sumZ = 0;
		int hits = 0;
		for (int iz = 0; iz <= nz; iz++) {
			double z = region.minZ + region.getLengthZ() * iz / nz;
			for (int iy = 0; iy <= ny; iy++) {
				double y = region.minY + region.getLengthY() * iy / ny;
				for (int ix = 0; ix <= nx; ix++) {
					double x = region.minX + region.getLengthX() * ix / nx;
					if (test.test(x, y, z)) {
						sumX += x;
						sumY += y;
						sumZ += z;
						hits++;
					}
				}
			}
		}
		return hits == 0 ? null : new Contact(other, new Vec3d(sumX / hits, sumY / hits, sumZ / hits), hits);
	}

	/** Where point is on the vehicle's own vertical axis, from the bottom of its grid (0) to the top (1) - for telling the underside of an aircraft resting on a deck from its nose flying into a superstructure. NaN without a grid. */
	public static double tudursvehiclemod$heightFraction(AbstractVehicleEntity vehicle, Vec3d point) {
		Pose pose = Pose.of(vehicle);
		if (pose == null) {
			return Double.NaN;
		}
		Vector3f local = new Vector3f((float) (point.x - pose.position().x), (float) (point.y - pose.position().y), (float) (point.z - pose.position().z));
		pose.inverseRotation().transform(local);
		local.div(pose.scale());
		float[] b = pose.grid().bounds();
		return (local.y - b[1]) / Math.max(1.0e-6, b[4] - b[1]);
	}

	// ---------------------------------------------------------------- debug display

	private static boolean debugDisplay;

	public static boolean tudursvehiclemod$toggleDebugDisplay() {
		debugDisplay = !debugDisplay;
		return debugDisplay;
	}

	/** "N grids, X cells solid, Y KiB" over every grid built so far. */
	public static String tudursvehiclemod$describeVoxelStats() {
		int grids = 0;
		long solid = 0;
		long bytes = 0;
		synchronized (VOXELS) {
			for (Optional<MeshVoxelGrid> grid : VOXELS.values()) {
				if (grid.isPresent()) {
					grids++;
					solid += grid.get().solidCellCount();
					bytes += grid.get().memoryBytes();
				}
			}
		}
		return grids + " shape grid(s) built, " + solid + " solid cells, " + (bytes / 1024) + " KiB";
	}

	/** End of each world tick: with the debug display on (/tvm debug contacts), every loaded vehicle's contacts are worked out every few ticks and shown as particles. */
	public static void tudursvehiclemod$tickDebugDisplay(ServerWorld world) {
		if (!debugDisplay || world.getTime() % 5 != 0) {
			return;
		}
		net.minecraft.particle.DustParticleEffect vehicleDust = new net.minecraft.particle.DustParticleEffect(0xFF3030, 1.5f);
		net.minecraft.particle.DustParticleEffect entityDust = new net.minecraft.particle.DustParticleEffect(0xFFD020, 1.2f);
		for (AbstractVehicleEntity vehicle : List.copyOf(loadedIn(world))) {
			for (Contact contact : contactsFor(vehicle)) {
				Vec3d p = contact.point();
				world.spawnParticles(contact.other() instanceof AbstractVehicleEntity ? vehicleDust : entityDust,
						true, true, p.x, p.y, p.z, 6, 0.15, 0.15, 0.15, 0.0);
			}
		}
	}

	/** The vehicle's box in the world by its shape (see this class's doc), or null without a grid yet. */
	public static Box tudursvehiclemod$worldBox(AbstractVehicleEntity vehicle) {
		Pose pose = Pose.of(vehicle);
		return pose == null ? null : pose.worldBox();
	}

	/** Whether the world point is inside vehicle's shape. False without a grid yet. */
	public static boolean tudursvehiclemod$isInside(AbstractVehicleEntity vehicle, Vec3d point) {
		Pose pose = Pose.of(vehicle);
		return pose != null && pose.isSolidAt(point.x, point.y, point.z);
	}
}
