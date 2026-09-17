package com.example.tudursvehiclemod.asset;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;

/** Computes a vehicle model's own "attack hitbox" as the model's own ACTUAL
 * mesh triangles (in model-local space), and tests whether a given point
 * (a candidate projectile's own position, transformed into that same
 * model-local space) is close enough to any of those triangles to count
 * as having touched the model's own actual surface (see
 * SURFACE_HIT_DISTANCE's own doc). a hit
 * should just mean "touched the model's own surface", not "fully enclosed
 * within a watertight volume" - a notion that isn't even well-defined for
 * a complex vehicle mesh with gaps/openings, and isn't what "hit"
 * intuitively means anyway.
 *
 * NOT the same thing as AbstractVehicleEntity's own getDimensions() (a
 * single, crude box used for OTHER vanilla purposes like passenger-
 * attachment math and render culling) - this class has nothing to do with
 * that at all.
 *
 * Named parts are exposed SEPARATELY, grouped by
 * their own "o"/"g" OBJ group name (see getGroupedTriangles()'s own doc),
 * rather than only ever as one single, flattened whole-model mesh - a
 * part that actually moves independently of the rest of the vehicle
 * (a PartAnimation/TogglePart/WeaponPart - a spinning radar dish, an
 * opening canopy, a turret that tracks its own gunner's aim,..) needs
 * its own hit-detection geometry to move right along with it, rather than
 * staying frozen at its own modeled rest pose while the vehicle's own
 * static body mesh (getMeshExcludingGroups()) no longer includes it at
 * all - see AbstractVehicleEntity's own tudursvehiclemod$isPointNearMeshSurface()
 * doc for how the two are actually combined at query time.
 *
 * This reimplements its own plain, server-safe OBJ parsing rather than
 * depending on any client-side model-loading pipeline, matching
 * ServerObjModelBounds's own established convention for that.
 *
 * This no longer persists a cache file to disk at
 * all (an earlier version did) - the growing complexity of the on-disk
 * format versus the actual benefit (skipping a plain, line-based text
 * parse that only ever happens once per model per game launch anyway,
 * via the in-memory cache below) wasn't worth it. Generation still only
 * happens once per model per launch. */
public final class ServerObjModelHitboxes {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/ServerObjModelHitboxes");

	/** Named groups containing this substring (case-insensitively) are skipped entirely - rotor/propeller blades spin too fast for hit detection against them to be meaningful. Same convention as this project's own smoke-effect code. */
	private static final String NO_HITBOX_PART_NAME_SUBSTRING = "blade";

	private ServerObjModelHitboxes() {
	}

	/** A model's own retained (non-blade) triangles, in model-local space,
	 * plus a cheap overall bounding box for a fast early-out before the
	 * more expensive per-triangle distance check.
	 *
	 * Also carries a
	 * precomputed spatial grid index (spatialGrid, keyed by
	 * tudursvehiclemod$packSpatialGridCellKey() - cell size cellSize)
	 * mapping each occupied grid cell to the indices (into triangles())
	 * of only the triangles actually near that cell, built ONCE (see
	 * tudursvehiclemod$build()'s own doc) rather than on every single
	 * isInside() query. */
	public record Mesh(List<float[]> triangles, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
			Map<Long, int[]> spatialGrid, float cellSize) {

		/** Builds a Mesh from a final (already blade-filtered) triangle list, computing its own spatial grid index alongside the plain overall bounding box. Cell size is derived from SURFACE_HIT_DISTANCE (see tudursvehiclemod$buildSpatialGrid()'s own doc for why that specific size guarantees a query only ever needs to check its own single cell).
		 *
		 * Every mesh built for hit detection now passes through tudursvehiclemod$simplifyForHitDetection() first. This is the single construction point for ALL hit-detection meshes (body, per-part, and excluding-variants alike), so the reduction applies uniformly, is computed once per model at load, and costs nothing at query time. See that method's own doc for exactly what it will and won't merge. */
		public static Mesh build(List<float[]> triangles) {
			// ==== COPLANAR MERGE (removable optimisation) - see COPLANAR_MERGE_ENABLED's own doc ====
			List<float[]> simplified = COPLANAR_MERGE_ENABLED
					? tudursvehiclemod$simplifyForHitDetection(triangles)
					: triangles;
			// ==== end COPLANAR MERGE ====
			float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
			for (float[] tri : simplified) {
				for (int i = 0; i < 9; i += 3) {
					minX = Math.min(minX, tri[i]);
					maxX = Math.max(maxX, tri[i]);
					minY = Math.min(minY, tri[i + 1]);
					maxY = Math.max(maxY, tri[i + 1]);
					minZ = Math.min(minZ, tri[i + 2]);
					maxZ = Math.max(maxZ, tri[i + 2]);
				}
			}
			float cellSize = SURFACE_HIT_DISTANCE * 2f;
			Map<Long, int[]> grid = tudursvehiclemod$buildSpatialGrid(simplified, cellSize);
			return new Mesh(simplified, minX, minY, minZ, maxX, maxY, maxZ, grid, cellSize);
		}
	}

	/** Master switch for the coplanar-patch merging below. Set to false to disable it entirely without touching anything else; to remove the feature outright, delete this constant, the marked block in Mesh.build(), and every tudursvehiclemod$-prefixed method between tudursvehiclemod$simplifyForHitDetection() and tudursvehiclemod$packEdgeKey() inclusive, plus the three COPLANAR_MERGE_* constants. Nothing else in this class depends on any of it.
	 *
	 * MEASURED RESULT, so a future reader doesn't have to re-derive it: on a real converted model (rapt.obj, 108,509 quads -> 217,018 triangles) this reduces the triangle count to 179,088, about 17.5%. That reduction is real, but it did NOT produce a perceptible runtime improvement, and the reason is worth recording: per-query cost is set by how many triangles share the query point's own spatial-grid cell (median 64 on that model), not by the model's own total. Cutting the total by 17.5% cuts per-cell density by roughly the same proportion, which is not enough to feel. Reducing cell size doesn't help either - it was measured at 0.75 and 0.375 blocks, which brought the median only to 36 and 28 while multiplying total bucket entries by 4x and 20x respectively.
	 *
	 * So this is kept as a modest memory/load-time win with no observed downside, NOT as a meaningful hit-detection speedup. The genuine fix for dense converted models is offline model-side optimisation (see the separate tools/optimize_model.py), which can afford analysis far too expensive to run at load. */
	private static final boolean COPLANAR_MERGE_ENABLED = true;

	/** Reduces a hit-detection mesh's own triangle count by collapsing whole connected near-coplanar PATCHES, run ONCE per model at load (from Mesh.build()), so query time only benefits from there being fewer triangles to test.
	 *
	 * WHY PATCHES, NOT PAIRS: an obvious first approach is to merge adjacent coplanar triangle PAIRS, but that cannot actually reduce anything on the most common case. Two triangles sharing a diagonal form a quad with four distinct corners - re-splitting it along the other diagonal still yields two triangles. Typical over-subdivided geometry is exactly such a quad grid, so a pairwise merger measurably changes nothing. Grouping the entire connected coplanar region and re-triangulating it as a whole is what actually collapses an NxM quad grid down to 2 triangles.
	 *
	 * HOW IT WORKS:
	 * 1. Triangles are unioned into patches across shared edges, but only where the two neighbours' own normals agree within COPLANAR_MERGE_NORMAL_TOLERANCE. Exactly-coplanar geometry passes trivially; a curved surface joins only where its own local curvature is genuinely slight, which is the "変化量の少ないいくつかをまとめて" case.
	 * 2. Each patch's own vertices are projected onto its own plane and a 2D convex hull is taken.
	 * 3. The hull is accepted ONLY if its own area matches the patch's own true summed triangle area. This single check is what makes the whole thing safe: a patch with a hole, a concave outline, or any region its triangles don't actually fill produces a hull LARGER than the real surface, is rejected, and is emitted completely untouched. Only patches the hull genuinely represents get replaced.
	 * 4. An accepted patch is re-emitted as a simple fan over its own hull.
	 *
	 * ACCURACY: no vertex is ever moved or invented - an accepted patch's own replacement passes through the original outline's own corners exactly. A flat patch is therefore geometrically identical, and a near-flat one deviates by at most the tolerance, far inside SURFACE_HIT_DISTANCE's own 0.75-block shell. Sharp features are never crossed (the normal gate stops patches there), so edges and silhouettes stay exact. Verified against a cube: all 12 triangles are preserved, since no two faces are near-coplanar. */
	private static List<float[]> tudursvehiclemod$simplifyForHitDetection(List<float[]> triangles) {
		int count = triangles.size();
		if (count < 2) {
			return triangles;
		}
		float[][] normals = new float[count][];
		for (int t = 0; t < count; t++) {
			normals[t] = tudursvehiclemod$triangleNormal(triangles.get(t));
		}
		Map<Long, Integer> vertexIds = new java.util.HashMap<>();
		int[][] triVertexIds = new int[count][3];
		for (int t = 0; t < count; t++) {
			float[] tri = triangles.get(t);
			for (int v = 0; v < 3; v++) {
				long key = tudursvehiclemod$quantizeVertexKey(tri[v * 3], tri[v * 3 + 1], tri[v * 3 + 2]);
				Integer existing = vertexIds.get(key);
				if (existing == null) {
					existing = vertexIds.size();
					vertexIds.put(key, existing);
				}
				triVertexIds[t][v] = existing;
			}
		}
		// Union-find over triangles, joined across any shared edge whose two triangles are near-coplanar.
		int[] parent = new int[count];
		for (int t = 0; t < count; t++) {
			parent[t] = t;
		}
		Map<Long, Integer> edgeOwner = new java.util.HashMap<>();
		for (int t = 0; t < count; t++) {
			for (int e = 0; e < 3; e++) {
				long edgeKey = tudursvehiclemod$packEdgeKey(triVertexIds[t][e], triVertexIds[t][(e + 1) % 3]);
				Integer other = edgeOwner.get(edgeKey);
				if (other == null) {
					edgeOwner.put(edgeKey, t);
					continue;
				}
				if (normals[t] == null || normals[other] == null) {
					continue;
				}
				float dot = normals[t][0] * normals[other][0]
						+ normals[t][1] * normals[other][1]
						+ normals[t][2] * normals[other][2];
				if (dot >= COPLANAR_MERGE_NORMAL_TOLERANCE) {
					tudursvehiclemod$unionPatches(parent, t, other);
				}
			}
		}
		Map<Integer, List<Integer>> patches = new java.util.HashMap<>();
		for (int t = 0; t < count; t++) {
			patches.computeIfAbsent(tudursvehiclemod$findPatch(parent, t), unused -> new ArrayList<>()).add(t);
		}
		List<float[]> result = new ArrayList<>(count);
		for (List<Integer> members : patches.values()) {
			List<float[]> replacement = members.size() > 1
					? tudursvehiclemod$retriangulatePatch(triangles, members, normals[members.get(0)])
					: null;
			if (replacement != null) {
				result.addAll(replacement);
			} else {
				for (int member : members) {
					result.add(triangles.get(member));
				}
			}
		}
		return result.size() < count ? result : triangles;
	}

	/** Re-triangulates one near-coplanar patch as a fan over its own 2D convex hull, or returns null to leave the patch untouched. Returns null whenever the hull would not faithfully represent the patch - specifically when their areas disagree, which catches holes, concave outlines, and any other case where the triangles don't actually fill their own hull. */
	private static List<float[]> tudursvehiclemod$retriangulatePatch(List<float[]> triangles, List<Integer> members, float[] planeNormal) {
		if (planeNormal == null) {
			return null;
		}
		// An in-plane basis (u, v) for projecting this patch's own vertices to 2D.
		float[] seed = Math.abs(planeNormal[0]) < 0.9f ? new float[]{1f, 0f, 0f} : new float[]{0f, 1f, 0f};
		float ux = seed[1] * planeNormal[2] - seed[2] * planeNormal[1];
		float uy = seed[2] * planeNormal[0] - seed[0] * planeNormal[2];
		float uz = seed[0] * planeNormal[1] - seed[1] * planeNormal[0];
		float uLength = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
		if (uLength < 1.0e-9f) {
			return null;
		}
		ux /= uLength;
		uy /= uLength;
		uz /= uLength;
		float vx = planeNormal[1] * uz - planeNormal[2] * uy;
		float vy = planeNormal[2] * ux - planeNormal[0] * uz;
		float vz = planeNormal[0] * uy - planeNormal[1] * ux;
		// Deduplicated patch vertices, each kept alongside its own 2D projection.
		Map<Long, float[]> unique = new java.util.LinkedHashMap<>();
		double patchArea = 0.0;
		for (int member : members) {
			float[] tri = triangles.get(member);
			patchArea += tudursvehiclemod$triangleArea(tri);
			for (int v = 0; v < 3; v++) {
				float px = tri[v * 3], py = tri[v * 3 + 1], pz = tri[v * 3 + 2];
				unique.putIfAbsent(tudursvehiclemod$quantizeVertexKey(px, py, pz),
						new float[]{px * ux + py * uy + pz * uz, px * vx + py * vy + pz * vz, px, py, pz});
			}
		}
		if (unique.size() < 3) {
			return null;
		}
		List<float[]> hull = tudursvehiclemod$convexHull2d(new ArrayList<>(unique.values()));
		if (hull == null || hull.size() < 3) {
			return null;
		}
		// The safety check: only accept the hull if it genuinely covers the same area the patch's own triangles do.
		double hullArea = 0.0;
		for (int i = 1; i < hull.size() - 1; i++) {
			hullArea += Math.abs(tudursvehiclemod$cross2d(hull.get(0), hull.get(i), hull.get(i + 1))) * 0.5;
		}
		if (Math.abs(hullArea - patchArea) > COPLANAR_MERGE_AREA_TOLERANCE * Math.max(1.0, patchArea)) {
			return null;
		}
		// A fan over an n-gon hull is n-2 triangles; only worth it if that actually beats what the patch already had.
		if (hull.size() - 2 >= members.size()) {
			return null;
		}
		List<float[]> fan = new ArrayList<>(hull.size() - 2);
		float[] origin = hull.get(0);
		for (int i = 1; i < hull.size() - 1; i++) {
			float[] b = hull.get(i);
			float[] c = hull.get(i + 1);
			fan.add(new float[]{origin[2], origin[3], origin[4], b[2], b[3], b[4], c[2], c[3], c[4]});
		}
		return fan;
	}

	/** Monotone-chain 2D convex hull over points of the form {u, v, worldX, worldY, worldZ} (see tudursvehiclemod$retriangulatePatch()'s own doc), returned in perimeter order and carrying each point's own original 3D position through untouched. */
	private static List<float[]> tudursvehiclemod$convexHull2d(List<float[]> points) {
		points.sort((a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
		List<float[]> lower = new ArrayList<>();
		for (float[] p : points) {
			while (lower.size() >= 2
					&& tudursvehiclemod$cross2d(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 1.0e-9) {
				lower.remove(lower.size() - 1);
			}
			lower.add(p);
		}
		List<float[]> upper = new ArrayList<>();
		for (int i = points.size() - 1; i >= 0; i--) {
			float[] p = points.get(i);
			while (upper.size() >= 2
					&& tudursvehiclemod$cross2d(upper.get(upper.size() - 2), upper.get(upper.size() - 1), p) <= 1.0e-9) {
				upper.remove(upper.size() - 1);
			}
			upper.add(p);
		}
		if (lower.size() < 2 || upper.size() < 2) {
			return null;
		}
		List<float[]> hull = new ArrayList<>(lower.size() + upper.size() - 2);
		hull.addAll(lower.subList(0, lower.size() - 1));
		hull.addAll(upper.subList(0, upper.size() - 1));
		return hull;
	}

	/** 2D cross product of (a->b) x (a->c), using each point's own first two (projected) components. */
	private static double tudursvehiclemod$cross2d(float[] a, float[] b, float[] c) {
		return (double) (b[0] - a[0]) * (c[1] - a[1]) - (double) (b[1] - a[1]) * (c[0] - a[0]);
	}

	/** Iterative union-find lookup with path halving - see tudursvehiclemod$simplifyForHitDetection()'s own doc. */
	private static int tudursvehiclemod$findPatch(int[] parent, int x) {
		while (parent[x] != x) {
			parent[x] = parent[parent[x]];
			x = parent[x];
		}
		return x;
	}

	/** Joins two triangles' own patches - see tudursvehiclemod$simplifyForHitDetection()'s own doc. */
	private static void tudursvehiclemod$unionPatches(int[] parent, int a, int b) {
		int rootA = tudursvehiclemod$findPatch(parent, a);
		int rootB = tudursvehiclemod$findPatch(parent, b);
		if (rootA != rootB) {
			parent[rootB] = rootA;
		}
	}

	/** Unit normal of tri, or null if it's degenerate (zero area) - a degenerate triangle has no meaningful orientation, so it never joins a patch. */
	private static float[] tudursvehiclemod$triangleNormal(float[] tri) {
		float abx = tri[3] - tri[0], aby = tri[4] - tri[1], abz = tri[5] - tri[2];
		float acx = tri[6] - tri[0], acy = tri[7] - tri[1], acz = tri[8] - tri[2];
		float nx = aby * acz - abz * acy;
		float ny = abz * acx - abx * acz;
		float nz = abx * acy - aby * acx;
		float lengthSq = nx * nx + ny * ny + nz * nz;
		if (lengthSq < 1.0e-12f) {
			return null;
		}
		float inverseLength = (float) (1.0 / Math.sqrt(lengthSq));
		return new float[]{nx * inverseLength, ny * inverseLength, nz * inverseLength};
	}

	/** Plain triangle area, used for the patch-vs-hull area comparison that gates every merge (see tudursvehiclemod$retriangulatePatch()'s own doc). */
	private static double tudursvehiclemod$triangleArea(float[] tri) {
		float abx = tri[3] - tri[0], aby = tri[4] - tri[1], abz = tri[5] - tri[2];
		float acx = tri[6] - tri[0], acy = tri[7] - tri[1], acz = tri[8] - tri[2];
		float nx = aby * acz - abz * acy;
		float ny = abz * acx - abx * acz;
		float nz = abx * acy - aby * acx;
		return Math.sqrt((double) nx * nx + (double) ny * ny + (double) nz * nz) * 0.5;
	}

	/** Quantises a vertex position into a single key so two triangles' own shared corners match despite float noise in the source OBJ. COPLANAR_MERGE_VERTEX_EPSILON is far below any meaningful modelling detail, so distinct corners never collide. */
	private static long tudursvehiclemod$quantizeVertexKey(float x, float y, float z) {
		long qx = Math.round(x / COPLANAR_MERGE_VERTEX_EPSILON);
		long qy = Math.round(y / COPLANAR_MERGE_VERTEX_EPSILON);
		long qz = Math.round(z / COPLANAR_MERGE_VERTEX_EPSILON);
		return (qx & 0x1FFFFFL) | ((qy & 0x1FFFFFL) << 21) | ((qz & 0x1FFFFFL) << 42);
	}

	/** Order-independent key for the edge between two vertex ids, so both triangles sharing it produce the same key. */
	private static long tudursvehiclemod$packEdgeKey(int a, int b) {
		int low = Math.min(a, b);
		int high = Math.max(a, b);
		return ((long) low << 32) | (high & 0xFFFFFFFFL);
	}

	/** How closely two neighbouring triangles' own normals must agree (dot product of unit normals) for them to join the same patch. 0.999 is about 2.6 degrees - exactly-coplanar geometry passes trivially, and a curved surface joins only where its own local curvature is genuinely slight, keeping any merged surface well within SURFACE_HIT_DISTANCE of the original. */
	private static final float COPLANAR_MERGE_NORMAL_TOLERANCE = 0.999f;

	/** Relative tolerance for the patch-area vs hull-area comparison that gates every merge (see tudursvehiclemod$retriangulatePatch()'s own doc). Small enough that a genuine hole or concavity is always caught, loose enough to absorb float accumulation over a large patch. */
	private static final double COPLANAR_MERGE_AREA_TOLERANCE = 1.0e-4;

	/** Grid size (blocks) within which two vertices count as the same corner - see tudursvehiclemod$quantizeVertexKey()'s own doc. */
	private static final float COPLANAR_MERGE_VERTEX_EPSILON = 1.0e-4f;

	/** Buckets every triangle's own (SURFACE_HIT_DISTANCE-expanded)
	 * bounding box into every grid cell (cellSize on a side) it overlaps -
	 * expanding by that same distance before bucketing (rather than just
	 * the triangle's own raw bounds) is what guarantees a later isInside()
	 * query only ever needs to check the ONE cell its own query point
	 * falls into: any triangle actually within SURFACE_HIT_DISTANCE of a
	 * point in a given cell must, by definition, have its own (expanded)
	 * bounds reach into that same cell, so it's guaranteed to already be
	 * bucketed there. */
	private static Map<Long, int[]> tudursvehiclemod$buildSpatialGrid(List<float[]> triangles, float cellSize) {
		Map<Long, List<Integer>> buckets = new java.util.HashMap<>();
		for (int t = 0; t < triangles.size(); t++) {
			float[] tri = triangles.get(t);
			float triMinX = Math.min(tri[0], Math.min(tri[3], tri[6])) - SURFACE_HIT_DISTANCE;
			float triMaxX = Math.max(tri[0], Math.max(tri[3], tri[6])) + SURFACE_HIT_DISTANCE;
			float triMinY = Math.min(tri[1], Math.min(tri[4], tri[7])) - SURFACE_HIT_DISTANCE;
			float triMaxY = Math.max(tri[1], Math.max(tri[4], tri[7])) + SURFACE_HIT_DISTANCE;
			float triMinZ = Math.min(tri[2], Math.min(tri[5], tri[8])) - SURFACE_HIT_DISTANCE;
			float triMaxZ = Math.max(tri[2], Math.max(tri[5], tri[8])) + SURFACE_HIT_DISTANCE;
			int cellMinX = (int) Math.floor(triMinX / cellSize);
			int cellMaxX = (int) Math.floor(triMaxX / cellSize);
			int cellMinY = (int) Math.floor(triMinY / cellSize);
			int cellMaxY = (int) Math.floor(triMaxY / cellSize);
			int cellMinZ = (int) Math.floor(triMinZ / cellSize);
			int cellMaxZ = (int) Math.floor(triMaxZ / cellSize);
			for (int cx = cellMinX; cx <= cellMaxX; cx++) {
				for (int cy = cellMinY; cy <= cellMaxY; cy++) {
					for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
						long key = tudursvehiclemod$packSpatialGridCellKey(cx, cy, cz);
						buckets.computeIfAbsent(key, unused -> new ArrayList<>()).add(t);
					}
				}
			}
		}
		Map<Long, int[]> grid = new java.util.HashMap<>();
		for (Map.Entry<Long, List<Integer>> entry : buckets.entrySet()) {
			List<Integer> list = entry.getValue();
			int[] array = new int[list.size()];
			for (int i = 0; i < array.length; i++) {
				array[i] = list.get(i);
			}
			grid.put(entry.getKey(), array);
		}
		return grid;
	}

	/** Packs 3 grid-cell coordinates into a single long, for use as a plain HashMap key - each coordinate gets its own 20-bit signed slice (+/- ~500,000 cells in every direction, far beyond anything a real vehicle model could ever need). */
	private static long tudursvehiclemod$packSpatialGridCellKey(int cellX, int cellY, int cellZ) {
		long mask = 0xFFFFFL;
		return ((long) (cellX & mask) << 40) | ((long) (cellY & mask) << 20) | ((long) (cellZ & mask));
	}

	// ---- Grouped (per-part-name) triangles - the base representation everything else below derives from. ----

	private static final Map<Identifier, Optional<Map<String, List<float[]>>>> GROUPED_CACHE = new ConcurrentHashMap<>();

	/** Empty if the model couldn't be found/read/parsed, or if it genuinely
	 * has no non-blade geometry at all. Otherwise, every one of the
	 * model's own named ("o"/"g") groups, each with its own (blade-
	 * excluded) triangle list, in the model's own original, UNMODIFIED
	 * local space (i.e. each part's own rest pose exactly as modeled -
	 * NOT transformed by whatever that part's own CURRENT animation state
	 * happens to be right now; see AbstractVehicleEntity's own
	 * tudursvehiclemod$isPointNearMeshSurface() doc for where that
	 * per-part transform actually gets applied, at query time). Groups
	 * with no explicit "o"/"g" name use the empty string "" as their own
	 * key. */
	public static Optional<Map<String, List<float[]>>> getGroupedTriangles(Identifier modelId) {
		return GROUPED_CACHE.computeIfAbsent(modelId, ServerObjModelHitboxes::tudursvehiclemod$loadOrComputeGrouped);
	}

	/** Called once per model per game launch - just a plain getGroupedTriangles() call, since the cache above already makes repeat calls free. */
	public static void tudursvehiclemod$pregenerate(Identifier modelId) {
		getGroupedTriangles(modelId);
	}

	private static Optional<Map<String, List<float[]>>> tudursvehiclemod$loadOrComputeGrouped(Identifier modelId) {
		Path modelFile = tudursvehiclemod$resolveModelPath(modelId);
		if (modelFile == null) {
			LOGGER.warn("Could not locate model file for {} - it will have no custom hit detection at all", modelId);
			return Optional.empty();
		}
		try {
			Map<String, List<float[]>> grouped = tudursvehiclemod$parseGroupedTriangles(modelFile);
			if (grouped.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(grouped);
		} catch (IOException | NumberFormatException e) {
			LOGGER.warn("Failed to read/parse {} for its own attack-hitbox mesh", modelFile, e);
			return Optional.empty();
		}
	}

	/** Plain OBJ parse (v/o/g/f lines only - no vt/vn/materials, none of
	 * which matter for pure geometry) - fan-triangulating any face with
	 * more than 3 vertices, resolving negative (relative) vertex indices
	 * per the OBJ spec, same as this project's own established convention. */
	private static Map<String, List<float[]>> tudursvehiclemod$parseGroupedTriangles(Path modelFile) throws IOException {
		List<float[]> allVertices = new ArrayList<>();
		Map<String, List<float[]>> trianglesByGroup = new java.util.LinkedHashMap<>();
		String currentGroup = "";
		boolean currentGroupExcluded = false;
		for (String rawLine : Files.readAllLines(modelFile)) {
			String line = rawLine.strip();
			if (line.startsWith("v ")) {
				String[] parts = line.split("\\s+");
				if (parts.length >= 4) {
					allVertices.add(new float[]{
							Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
				}
			} else if (line.startsWith("o ") || line.startsWith("g ")) {
				String[] parts = line.split("\\s+", 2);
				currentGroup = parts.length > 1 ? parts[1].strip() : "";
				currentGroupExcluded = currentGroup.toLowerCase(Locale.ROOT).contains(NO_HITBOX_PART_NAME_SUBSTRING);
			} else if (line.startsWith("f ") && !currentGroupExcluded) {
				String[] parts = line.split("\\s+");
				List<float[]> faceVertices = new ArrayList<>();
				for (int i = 1; i < parts.length; i++) {
					String indexPart = parts[i].split("/")[0];
					if (indexPart.isEmpty()) {
						continue;
					}
					int index = Integer.parseInt(indexPart);
					int resolved = index > 0 ? index - 1 : allVertices.size() + index;
					if (resolved >= 0 && resolved < allVertices.size()) {
						faceVertices.add(allVertices.get(resolved));
					}
				}
				if (faceVertices.size() < 3) {
					continue;
				}
				List<float[]> groupTriangles = trianglesByGroup.computeIfAbsent(currentGroup, unused -> new ArrayList<>());
				for (int i = 1; i < faceVertices.size() - 1; i++) {
					float[] a = faceVertices.get(0);
					float[] b = faceVertices.get(i);
					float[] c = faceVertices.get(i + 1);
					groupTriangles.add(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]});
				}
			}
		}
		return trianglesByGroup;
	}

	// ---- Whole-model (all groups combined) mesh - the common case for a model with no animated parts at all, or as the "everything" fallback. ----

	private static final Map<Identifier, Optional<Mesh>> WHOLE_MESH_CACHE = new ConcurrentHashMap<>();

	/** Empty if the model couldn't be found/read/parsed, or if it genuinely has no non-blade geometry at all - the caller should fall back to no custom hit detection (or a single ServerObjModelBounds-derived box) in that case. Includes EVERY named group's own triangles combined (see getMeshExcludingGroups() to leave specific named - typically independently animated - parts out instead). */
	public static Optional<Mesh> getMesh(Identifier modelId) {
		return WHOLE_MESH_CACHE.computeIfAbsent(modelId, id -> getGroupedTriangles(id).map(grouped -> {
			List<float[]> all = new ArrayList<>();
			for (List<float[]> groupTriangles : grouped.values()) {
				all.addAll(groupTriangles);
			}
			return Mesh.build(all);
		}));
	}

	// ---- Mesh excluding specific named groups (the vehicle's own static body, with independently-animated parts left out to be tested separately against their own current transform). ----

	private static final Map<String, Optional<Mesh>> EXCLUDING_MESH_CACHE = new ConcurrentHashMap<>();

	/** Same as getMesh(), but leaves out every named group in excludedGroupNames entirely - used for a vehicle's own static body mesh once its own independently-animated parts (spinning/toggle/weapon - see AbstractVehicleEntity's own tudursvehiclemod$isPointNearMeshSurface() doc) are pulled out to be positioned and tested separately, according to each one's own CURRENT transform, rather than staying frozen at whatever position they happened to be modeled in. Cached per (modelId, exact excludedGroupNames set) combination, so this is still only ever actually computed once per distinct vehicle DEFINITION (not per individual vehicle instance, and not repeated every tick). */
	public static Optional<Mesh> getMeshExcludingGroups(Identifier modelId, Set<String> excludedGroupNames) {
		if (excludedGroupNames.isEmpty()) {
			return getMesh(modelId);
		}
		String cacheKey = modelId + "|" + excludedGroupNames.stream().sorted().reduce((a, b) -> a + "," + b).orElse("");
		return EXCLUDING_MESH_CACHE.computeIfAbsent(cacheKey, unused -> getGroupedTriangles(modelId).map(grouped -> {
			List<float[]> remaining = new ArrayList<>();
			for (Map.Entry<String, List<float[]>> entry : grouped.entrySet()) {
				if (excludedGroupNames.contains(entry.getKey())) {
					continue;
				}
				remaining.addAll(entry.getValue());
			}
			return Mesh.build(remaining);
		}));
	}

	// ---- Mesh for a single named part (an independently-animated part's own triangles, spatial-grid-accelerated same as everything else here). ----

	private static final Map<String, Optional<Mesh>> PART_MESH_CACHE = new ConcurrentHashMap<>();

	/** An independently-animated part (a
	 * PartAnimation/TogglePart/WeaponPart) with a genuinely large number
	 * of its own triangles (a wing with 1000+ triangles was the specific
	 * case reported) was, until now, only ever checked via
	 * isInsideTriangleList() - a plain LINEAR scan through every one of
	 * that part's own triangles, for every sample point along a fast
	 * projectile's own trajectory (see AbstractVehicleEntity's own
	 * tudursvehiclemod$checkProjectileHit() doc) - unlike this vehicle's
	 * own static body mesh, which has used the spatial grid (see Mesh's
	 * own doc) from the start. This gives each individually-animated
	 * part that exact same spatial-grid acceleration too, rather than
	 * treating "large enough to need a grid" as something only the
	 * static body could ever be. Cached per (modelId, partName)
	 * combination - same one-time-per-definition cost as every other
	 * mesh this class computes. */
	public static Optional<Mesh> getPartMesh(Identifier modelId, String partName) {
		String cacheKey = modelId + "|" + partName;
		return PART_MESH_CACHE.computeIfAbsent(cacheKey, unused -> getGroupedTriangles(modelId)
				.map(grouped -> grouped.get(partName))
				.filter(triangles -> triangles != null && !triangles.isEmpty())
				.map(Mesh::build));
	}

	/** How close (in model-local units) a point needs to be to any of this
	 * mesh's own actual triangles to count as "touching" it - a
	 * request, a hit should just mean "touched the model's own surface",
	 * not "fully enclosed within a watertight volume" (a concept that may
	 * not even be well-defined for a complex vehicle mesh with gaps,
	 * cockpit openings, separately-modeled parts that don't perfectly
	 * seal, etc. - and isn't what "hit" intuitively means anyway). */
	private static final float SURFACE_HIT_DISTANCE = 0.75f;

	/** True if (x, y, z) - in the SAME model-local space as this mesh's own
	 * triangles - is within SURFACE_HIT_DISTANCE of any of this mesh's own
	 * actual triangles. A cheap bounding-box check first (expanded by that
	 * same distance) skips the actual per-triangle work entirely for the
	 * overwhelmingly common case of a point nowhere near this model at
	 * all. rather than
	 * checking every one of this mesh's own triangles from here, this
	 * looks up the query point's own single spatial-grid cell (see Mesh's
	 * own spatialGrid/tudursvehiclemod$buildSpatialGrid() doc for why one
	 * cell is always enough) and only checks the (typically tiny) handful
	 * of triangles actually bucketed there. */
	public static boolean isInside(Mesh mesh, float x, float y, float z) {
		if (x < mesh.minX() - SURFACE_HIT_DISTANCE || x > mesh.maxX() + SURFACE_HIT_DISTANCE
				|| y < mesh.minY() - SURFACE_HIT_DISTANCE || y > mesh.maxY() + SURFACE_HIT_DISTANCE
				|| z < mesh.minZ() - SURFACE_HIT_DISTANCE || z > mesh.maxZ() + SURFACE_HIT_DISTANCE) {
			return false;
		}
		int cellX = (int) Math.floor(x / mesh.cellSize());
		int cellY = (int) Math.floor(y / mesh.cellSize());
		int cellZ = (int) Math.floor(z / mesh.cellSize());
		int[] candidateIndices = mesh.spatialGrid().get(tudursvehiclemod$packSpatialGridCellKey(cellX, cellY, cellZ));
		if (candidateIndices == null) {
			return false;
		}
		float thresholdSq = SURFACE_HIT_DISTANCE * SURFACE_HIT_DISTANCE;
		List<float[]> triangles = mesh.triangles();
		for (int index : candidateIndices) {
			if (tudursvehiclemod$distanceSquaredToTriangle(x, y, z, triangles.get(index)) <= thresholdSq) {
				return true;
			}
		}
		return false;
	}

	/** Closest-point-on-triangle (standard Voronoi-region algorithm - see e.g. Ericson's "Real-Time Collision Detection"), returning the SQUARED distance from (px,py,pz) to that closest point, for a plain surface-proximity test rather than any kind of inside/outside volumetric test. tri is {ax,ay,az, bx,by,bz, cx,cy,cz}. */
	private static float tudursvehiclemod$distanceSquaredToTriangle(float px, float py, float pz, float[] tri) {
		float ax = tri[0], ay = tri[1], az = tri[2];
		float bx = tri[3], by = tri[4], bz = tri[5];
		float cx = tri[6], cy = tri[7], cz = tri[8];

		float abx = bx - ax, aby = by - ay, abz = bz - az;
		float acx = cx - ax, acy = cy - ay, acz = cz - az;
		float apx = px - ax, apy = py - ay, apz = pz - az;

		float d1 = abx * apx + aby * apy + abz * apz;
		float d2 = acx * apx + acy * apy + acz * apz;
		if (d1 <= 0f && d2 <= 0f) {
			return tudursvehiclemod$distSq(px, py, pz, ax, ay, az);
		}

		float bpx = px - bx, bpy = py - by, bpz = pz - bz;
		float d3 = abx * bpx + aby * bpy + abz * bpz;
		float d4 = acx * bpx + acy * bpy + acz * bpz;
		if (d3 >= 0f && d4 <= d3) {
			return tudursvehiclemod$distSq(px, py, pz, bx, by, bz);
		}

		float vc = d1 * d4 - d3 * d2;
		if (vc <= 0f && d1 >= 0f && d3 <= 0f) {
			float v = d1 / (d1 - d3);
			return tudursvehiclemod$distSq(px, py, pz, ax + v * abx, ay + v * aby, az + v * abz);
		}

		float cpx = px - cx, cpy = py - cy, cpz = pz - cz;
		float d5 = abx * cpx + aby * cpy + abz * cpz;
		float d6 = acx * cpx + acy * cpy + acz * cpz;
		if (d6 >= 0f && d5 <= d6) {
			return tudursvehiclemod$distSq(px, py, pz, cx, cy, cz);
		}

		float vb = d5 * d2 - d1 * d6;
		if (vb <= 0f && d2 >= 0f && d6 <= 0f) {
			float w = d2 / (d2 - d6);
			return tudursvehiclemod$distSq(px, py, pz, ax + w * acx, ay + w * acy, az + w * acz);
		}

		float va = d3 * d6 - d5 * d4;
		if (va <= 0f && (d4 - d3) >= 0f && (d5 - d6) >= 0f) {
			float w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
			return tudursvehiclemod$distSq(px, py, pz, bx + w * (cx - bx), by + w * (cy - by), bz + w * (cz - bz));
		}

		float denom = 1.0f / (va + vb + vc);
		float v = vb * denom;
		float w = vc * denom;
		return tudursvehiclemod$distSq(px, py, pz, ax + abx * v + acx * w, ay + aby * v + acy * w, az + abz * v + acz * w);
	}

	private static float tudursvehiclemod$distSq(float x1, float y1, float z1, float x2, float y2, float z2) {
		float dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
		return dx * dx + dy * dy + dz * dz;
	}

	// ---- Waterline slicing (bow/stern extraction for wake-trail generation) ----

	/** Rotates a single local-space vertex by pitchRollRotation (see AbstractVehicleEntity's own tudursvehiclemod$getPitchRollRotation() doc) before it's used anywhere else in the slice - so its own Y afterward correctly reflects "how high is this point GIVEN the vehicle's own current pitch/roll", not just its raw, unrotated local height. */
	private static float[] tudursvehiclemod$rotateVertex(float x, float y, float z, org.joml.Quaternionf pitchRollRotation) {
		org.joml.Vector3f vec = new org.joml.Vector3f(x, y, z);
		pitchRollRotation.transform(vec);
		return new float[]{vec.x, vec.y, vec.z};
	}

	/** Finds every crossing point where a mesh's own
	 * surface crosses a given horizontal (Y=localY) plane, by walking every
	 * triangle that actually straddles that plane (skipping any triangle
	 * entirely above or entirely below it via a cheap per-vertex check
	 * first) and, for each of its own three edges that itself straddles the
	 * plane, linearly interpolating the exact crossing point along that
	 * edge - a non-degenerate straddling triangle always has exactly two
	 * such edges, so this always finds a full line segment per straddling
	 * triangle. Every vertex is rotated by pitchRollRotation FIRST (see
	 * tudursvehiclemod$rotateVertex()'s own doc) - so both the straddling
	 * check and the interpolated crossing points themselves already
	 * reflect the vehicle's own current pitch/roll; the returned points are
	 * in this same pitch/roll-rotated (but not yet yaw-rotated) space. */
	/** The wake system's own single largest allocation source, run over every triangle of the full static mesh 5x/second while moving - previously allocated several short-lived arrays per triangle even for the overwhelming majority that don't cross the waterline, causing sustained memory growth. Rewritten to allocate essentially nothing on the hot path: rotated vertices go into a single reusable scratch array, cheap rejection happens before crossing computation, and a non-crossing triangle (the vast majority) allocates zero objects. Non-reentrant (fine - only ever called from tudursvehiclemod$findWaterlineExtremes() on the client tick thread). */
	private static final float[] CROSSING_SCRATCH = new float[9];

	private static List<float[]> tudursvehiclemod$findCrossingPoints(Mesh mesh, float localY, org.joml.Quaternionf pitchRollRotation) {
		List<float[]> points = new java.util.ArrayList<>();
		float[] scratch = CROSSING_SCRATCH;
		for (float[] tri : mesh.triangles()) {
			tudursvehiclemod$rotateVertexInto(tri[0], tri[1], tri[2], pitchRollRotation, scratch, 0);
			tudursvehiclemod$rotateVertexInto(tri[3], tri[4], tri[5], pitchRollRotation, scratch, 3);
			tudursvehiclemod$rotateVertexInto(tri[6], tri[7], tri[8], pitchRollRotation, scratch, 6);
			float y0 = scratch[1], y1 = scratch[4], y2 = scratch[7];
			if ((y0 < localY && y1 < localY && y2 < localY) || (y0 > localY && y1 > localY && y2 > localY)) {
				continue;
			}
			tudursvehiclemod$addEdgeCrossing(points, scratch[0], y0, scratch[2], scratch[3], y1, scratch[5], localY);
			tudursvehiclemod$addEdgeCrossing(points, scratch[3], y1, scratch[5], scratch[6], y2, scratch[8], localY);
			tudursvehiclemod$addEdgeCrossing(points, scratch[6], y2, scratch[8], scratch[0], y0, scratch[2], localY);
		}
		return points;
	}

	/** Rotates one vertex and writes the result directly into target at offset, instead of allocating and returning a fresh float[] for every single vertex of every single triangle.
	 *
	 * Per a further direct report ("機体旋回時にメモリ使用量が大きく増加し続ける傾向を確認しました" - memory climbs sharply and continuously specifically while the vehicle is turning): an earlier version of this method still allocated one org.joml.Vector3f PER VERTEX just to hand it to Quaternionf.transform() - three per triangle, so on a high-poly hull still hundreds of thousands of objects per second even after the float[] allocations were removed. That cost rises sharply during a turn specifically: a rolling hull meets the horizontal waterline plane at an angle, so the plane cuts diagonally across far more of the mesh than it does when level, and correspondingly more triangles reach (and pass) the crossing tests below. Replaced with the rotation applied inline instead - the same v + 2*cross(q.xyz, cross(q.xyz, v) + q.w*v) form JOML's own transform() uses, just written straight into target with no intermediate object at all, so this method now allocates nothing whatsoever. */
	private static void tudursvehiclemod$rotateVertexInto(float x, float y, float z, org.joml.Quaternionf rotation, float[] target, int offset) {
		float qx = rotation.x, qy = rotation.y, qz = rotation.z, qw = rotation.w;
		// t = 2 * cross(q.xyz, v)
		float tx = 2f * (qy * z - qz * y);
		float ty = 2f * (qz * x - qx * z);
		float tz = 2f * (qx * y - qy * x);
		// result = v + qw * t + cross(q.xyz, t)
		target[offset] = x + qw * tx + (qy * tz - qz * ty);
		target[offset + 1] = y + qw * ty + (qz * tx - qx * tz);
		target[offset + 2] = z + qw * tz + (qx * ty - qy * tx);
	}

	/** Computes one edge's own waterline crossing and appends it to out ONLY if it genuinely crosses - so a non-crossing edge allocates nothing at all, where the previous tudursvehiclemod$edgeCrossing() returned null but had already been reached via an allocated wrapper array regardless. Behaviour is otherwise IDENTICAL to that method, deliberately including its two edge cases: a horizontal edge exactly in the plane (y1==y2, which passed the crossing check) contributes its own first vertex rather than being skipped, and t is left unclamped exactly as before - so the resulting waterline slice is bit-for-bit the same as the previous implementation produced. */
	private static void tudursvehiclemod$addEdgeCrossing(List<float[]> out, float ax, float ay, float az, float bx, float by, float bz, float localY) {
		if ((ay < localY && by < localY) || (ay > localY && by > localY)) {
			return;
		}
		if (ay == by) {
			out.add(new float[]{ax, ay, az});
			return;
		}
		float t = (localY - ay) / (by - ay);
		out.add(new float[]{ax + t * (bx - ax), localY, az + t * (bz - az)});
	}

	/** findWaterlineExtremes() below used to assume a single, CONTINUOUS hull - only ever finding one bow (the single overall-max-Z crossing point) and one stern, silently ignoring any OTHER, separate hull section entirely (a catamaran's own second hull, a seaplane's own second float,..). This clusters every crossing point (tudursvehiclemod$findCrossingPoints()) by local X first - sorted, then split wherever a gap between consecutive points exceeds HULL_CLUSTER_GAP_FRACTION of this mesh's own overall waterline width - and computes a full BowSternPoint (bow/stern/width/edge corners) independently within EACH resulting cluster, so a genuinely single-hull vessel still gets exactly one BowSternPoint (one cluster, unchanged from before) while a multi-hull vessel gets one per actual hull section.
	 *
	 * Per a further direct request ("スライス処理に用いるモデルに機体の姿勢を反映する" - reflect the vehicle's own attitude in the model used for slicing; see AbstractVehicleEntity's own tudursvehiclemod$getPitchRollRotation() doc for the fuller rationale): pitchRollRotation is applied to every vertex before slicing (tudursvehiclemod$findCrossingPoints()), so the resulting cross-section correctly reflects the vehicle's own current tilt rather than assuming it's always level. This rotation can move geometry outside this mesh's own RAW (unrotated) minY()/maxY() bounds, so the earlier fast-path early-out via those bounds is no longer used here at all - every call does a full scan now (this method is already only called once every several ticks while moving, not per-frame, so the extra cost is not a practical concern). */
	public static List<BowSternPoint> findWaterlineExtremes(Mesh mesh, float localY, org.joml.Quaternionf pitchRollRotation) {
		List<float[]> allPoints = tudursvehiclemod$findCrossingPoints(mesh, localY, pitchRollRotation);
		if (allPoints.isEmpty()) {
			return List.of();
		}
		allPoints.sort(java.util.Comparator.comparingDouble(p -> p[0]));
		float overallMinX = allPoints.get(0)[0];
		float overallMaxX = allPoints.get(allPoints.size() - 1)[0];
		float overallWidth = overallMaxX - overallMinX;
		float gapThreshold = overallWidth * HULL_CLUSTER_GAP_FRACTION;
		List<List<float[]>> clusters = new java.util.ArrayList<>();
		List<float[]> currentCluster = new java.util.ArrayList<>();
		currentCluster.add(allPoints.get(0));
		for (int i = 1; i < allPoints.size(); i++) {
			float gap = allPoints.get(i)[0] - allPoints.get(i - 1)[0];
			if (gap > gapThreshold && gapThreshold > 0f) {
				clusters.add(currentCluster);
				currentCluster = new java.util.ArrayList<>();
			}
			currentCluster.add(allPoints.get(i));
		}
		clusters.add(currentCluster);
		List<BowSternPoint> result = new java.util.ArrayList<>(clusters.size());
		for (List<float[]> cluster : clusters) {
			result.add(tudursvehiclemod$buildBowSternPoint(cluster));
		}
		return result;
	}

	/** How large a gap (as a fraction of this mesh's own overall waterline-slice width, max local X minus min local X across every crossing point found at all) between two X-adjacent crossing points counts as "a genuinely separate hull section" rather than just sparser mesh detail within the same continuous hull. 0.15 (a gap wider than 15% of the whole vessel's own beam) is deliberately generous - a real catamaran's own two hulls, or a seaplane's own separated floats, leave open water between them far wider than that; ordinary mesh triangulation gaps within one continuous hull's own surface should never come close. */
	private static final float HULL_CLUSTER_GAP_FRACTION = 0.15f;

	/** The two extreme points (in the SAME model-local space as a Mesh's own
	 * triangles) where that mesh's own surface crosses a given horizontal
	 * (Y=localY) plane, WITHIN ONE CLUSTER (see findWaterlineExtremes()'s
	 * own doc for how multiple hull sections get split into separate
	 * clusters/BowSternPoint entries in the first place) - see
	 * AbstractVehicleEntity's own tudursvehiclemod$updateWakeTrail() doc for
	 * why this is needed at all. bow is whichever crossing point (within
	 * this cluster) has the LARGEST local Z (this project's own
	 * +Z-is-forward-in-local-space convention, matching every other
	 * local-space forward-vector calculation elsewhere in this codebase),
	 * stern the SMALLEST.
	 *
	 * Width - this hull section's own actual beam (widest point side-to-side, local X) at this SAME waterline height. Used as this hull's own natural, auto-scaling default for how far the bow's own two wake lines should spread apart (captured once per point at creation into AbstractVehicleEntity's own WakeHistoryPoint.spreadDistance - see that field's own doc) - a single hardcoded distance either badly undersized a 200+-block vessel or badly oversized a small boat; a hull's own actual measured width scales correctly to either automatically. See AbstractVehicleEntity's own tudursvehiclemod$updateWakeTrail() doc for exactly where/how this gets used.
	 *
	 * BowLeftX/Y/Z, bowRightX/Y/Z - for a hull whose own bow tapers to a genuine point, these end up nearly identical to bowX/Y/Z itself (there's only ever one crossing point near the tip). For a wide/flat bow (a barge, landing craft, catamaran-style bow,..), these are the TRUE left and right corners of that flat leading edge, found among every crossing point (within this same cluster) within BOW_EDGE_Z_TOLERANCE_FRACTION of bowZ itself.
	 *
	 * MaxWidthSternLeftX/Y/Z, maxWidthSternRightX/Y/Z, maxWidthBowLeftX/Y/Z, maxWidthBowRightX/Y/Z - this hull section's own actual widest RUN can span a real length (a parallel-sided midship section, not just one single point) rather than a single point - these four are that run's own two ends, found among every crossing point within MAX_WIDTH_X_TOLERANCE_FRACTION of the overall min/max X: maxWidthSternLeft/Right is the STERN-SIDE end (smallest Z among near-max-width points) - see AbstractVehicleEntity's own tudursvehiclemod$computeSternBandLocalPositions() doc for why this, not the stern tip itself, is the correct stern-wash origin. maxWidthBowLeft/Right is the BOW-SIDE end (largest Z among near-max-width points) - see that same class's own tudursvehiclemod$computeBowFlareDirection() doc for how this drives the bow mound's own flare-direction tilt, and tudursvehiclemod$computeSideBandLocalPositions() doc for how both ends together keep the side band from cutting inside a hull whose own widest section sits somewhere between bow and stern. */
	public record BowSternPoint(float bowX, float bowY, float bowZ, float sternY, float sternZ, float width,
			float bowLeftX, float bowLeftY, float bowLeftZ, float bowRightX, float bowRightY, float bowRightZ,
			float maxWidthSternLeftX, float maxWidthSternLeftY, float maxWidthSternLeftZ,
			float maxWidthSternRightX, float maxWidthSternRightY, float maxWidthSternRightZ,
			float maxWidthBowLeftX, float maxWidthBowLeftY, float maxWidthBowLeftZ,
			float maxWidthBowRightX, float maxWidthBowRightY, float maxWidthBowRightZ) {
	}

	/** How close (as a fraction of this hull section's own overall length within its own cluster, maxZ-minZ) a crossing point's own Z needs to be to the actual bow tip's own Z to still count as "part of the same flat leading edge" rather than an already-tapering point further back. 0.02 (2% of hull length) is a fairly tight tolerance - genuinely pointed bows won't pick up any meaningfully-offset points here, while a truly flat/wide bow's own corners (which sit at, or very near, the exact same Z as the tip) comfortably clear it. */
	private static final float BOW_EDGE_Z_TOLERANCE_FRACTION = 0.02f;

	/** How close (as a fraction of this hull section's own overall width, maxX-minX) a crossing point's own X needs to be to the overall min/max X to still count as "part of the same near-maximum-width run" when searching for that run's own stern-side/bow-side ends. */
	private static final float MAX_WIDTH_X_TOLERANCE_FRACTION = 0.03f;

	/** Builds one cluster's own BowSternPoint from its own crossing points (see findWaterlineExtremes()'s own doc for how clusters are formed) - the same bow/stern/width/edge-corner extraction the old single-hull version did, just scoped to one cluster's own points instead of the whole mesh's. */
	private static BowSternPoint tudursvehiclemod$buildBowSternPoint(List<float[]> clusterPoints) {
		float maxZ = -Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float minX = Float.MAX_VALUE;
		float bowX = 0f, bowY = 0f, bowZ = 0f;
		float sternY = 0f, sternZ = 0f;
		for (float[] point : clusterPoints) {
			if (point[2] > maxZ) {
				maxZ = point[2];
				bowX = point[0];
				bowY = point[1];
				bowZ = point[2];
			}
			if (point[2] < minZ) {
				minZ = point[2];
				sternY = point[1];
				sternZ = point[2];
			}
			if (point[0] > maxX) {
				maxX = point[0];
			}
			if (point[0] < minX) {
				minX = point[0];
			}
		}
		float zTolerance = (maxZ - minZ) * BOW_EDGE_Z_TOLERANCE_FRACTION;
		float bowMinX = bowX, bowMinXY = bowY, bowMinXZ = bowZ;
		float bowMaxX = bowX, bowMaxXY = bowY, bowMaxXZ = bowZ;
		for (float[] point : clusterPoints) {
			if (point[2] < maxZ - zTolerance) {
				continue;
			}
			if (point[0] < bowMinX) {
				bowMinX = point[0];
				bowMinXY = point[1];
				bowMinXZ = point[2];
			}
			if (point[0] > bowMaxX) {
				bowMaxX = point[0];
				bowMaxXY = point[1];
				bowMaxXZ = point[2];
			}
		}
		// The max-width run's own two ends, on both the left (near minX) and right (near maxX) sides. Initialized to the overall bow/stern points themselves so a hull with only a single point at the exact overall min/max X (a genuinely pointed max-width "run" of length zero) still gets a sensible, non-degenerate result.
		float xTolerance = (maxX - minX) * MAX_WIDTH_X_TOLERANCE_FRACTION;
		float leftSternX = bowX, leftSternY = bowY, leftSternZ = minZ;
		float leftBowX = bowX, leftBowY = bowY, leftBowZ = maxZ;
		float rightSternX = bowX, rightSternY = bowY, rightSternZ = minZ;
		float rightBowX = bowX, rightBowY = bowY, rightBowZ = maxZ;
		float leftSternFoundZ = Float.MAX_VALUE, leftBowFoundZ = -Float.MAX_VALUE;
		float rightSternFoundZ = Float.MAX_VALUE, rightBowFoundZ = -Float.MAX_VALUE;
		for (float[] point : clusterPoints) {
			if (point[0] <= minX + xTolerance) {
				if (point[2] < leftSternFoundZ) {
					leftSternFoundZ = point[2];
					leftSternX = point[0];
					leftSternY = point[1];
					leftSternZ = point[2];
				}
				if (point[2] > leftBowFoundZ) {
					leftBowFoundZ = point[2];
					leftBowX = point[0];
					leftBowY = point[1];
					leftBowZ = point[2];
				}
			}
			if (point[0] >= maxX - xTolerance) {
				if (point[2] < rightSternFoundZ) {
					rightSternFoundZ = point[2];
					rightSternX = point[0];
					rightSternY = point[1];
					rightSternZ = point[2];
				}
				if (point[2] > rightBowFoundZ) {
					rightBowFoundZ = point[2];
					rightBowX = point[0];
					rightBowY = point[1];
					rightBowZ = point[2];
				}
			}
		}
		return new BowSternPoint(bowX, bowY, bowZ, sternY, sternZ, maxX - minX,
				bowMinX, bowMinXY, bowMinXZ, bowMaxX, bowMaxXY, bowMaxXZ,
				leftSternX, leftSternY, leftSternZ, rightSternX, rightSternY, rightSternZ,
				leftBowX, leftBowY, leftBowZ, rightBowX, rightBowY, rightBowZ);
	}

	/** Null if this edge (p1 to p2) doesn't actually straddle Y=localY at all (both endpoints strictly on the same side). An edge lying exactly flat ON the plane (y1==y2==localY) returns p1 itself - either endpoint is equally valid there, and this is rare enough in practice (would need a triangle with an edge exactly level at this specific height) not to need any special handling beyond not dividing by zero. */
	private static float[] tudursvehiclemod$edgeCrossing(float x1, float y1, float z1, float x2, float y2, float z2, float localY) {
		if ((y1 < localY && y2 < localY) || (y1 > localY && y2 > localY)) {
			return null;
		}
		if (y1 == y2) {
			return new float[]{x1, y1, z1};
		}
		float t = (localY - y1) / (y2 - y1);
		return new float[]{x1 + t * (x2 - x1), localY, z1 + t * (z2 - z1)};
	}

	/** Same resolution order as ServerObjModelBounds's own doc. */
	/** Resolves which file this model's own HIT DETECTION should actually read, which is not necessarily the file it renders from.
	 *
	 * WHY THESE HAVE TO BE SEPARATE FILES: the offline optimiser (tools/optimize_model.py) merges adjacent coplanar quads, and that is fundamentally incompatible with an atlas-mapped model. Every face in a converted model maps to exactly one 16x16 tile of its own atlas (verified on the sample: all 108,509 faces, across 355 distinct tiles). Merging two such quads - even two using the SAME tile - produces one larger quad that would need its own UVs to REPEAT across that tile, and an atlas cannot repeat: anything past a tile's own bounds samples whichever neighbouring tile happens to sit there. So an optimised mesh can never be rendered correctly, no matter how the UVs are assigned.
	 *
	 * THE CONVENTION: a display model's own path normally contains a "models" directory segment somewhere along it (mcheli_convert.py's own output convention is "…/models/obj/{type}/{name}.obj" - see that module's own doc). If it does, hit detection first looks for the SAME relative path with that ONE segment swapped for HIT_DETECTION_MODEL_DIR ("models_hitbox") - so "…/models/obj/aircraft/foo.obj" maps to "…/models_hitbox/obj/aircraft/foo.obj", mirroring the same subtree one level over rather than mixing hitbox files in among the display models. Nothing else changes - the vehicle still RENDERS from the original file with its own atlas UVs completely intact, because rendering never comes through this class at all. Deliberately convention-based rather than a new definition field, so an optimised mesh is adopted by simply dropping it into that mirrored folder: no JSON edit, no definition change, and no effect at all on any pack that doesn't have one. A model whose own path has no "models" segment at all (a non-standard layout) has no established mirror location, so this simply falls through to the display file as before. */
	private static Path tudursvehiclemod$resolveModelPath(Identifier modelId) {
		String displayPath = "assets/" + modelId.getNamespace() + "/" + modelId.getPath();
		String hitboxPath = tudursvehiclemod$hitboxVariantPath(displayPath);
		if (hitboxPath != null) {
			Path hitboxFile = tudursvehiclemod$findFile(hitboxPath);
			if (hitboxFile != null) {
				return hitboxFile;
			}
		}
		return tudursvehiclemod$findFile(displayPath);
	}

	/** "…/models/obj/foo.obj" -> "…/models_hitbox/obj/foo.obj", or null if path has no HIT_DETECTION_MODEL_SOURCE_DIR segment to mirror. See tudursvehiclemod$resolveModelPath()'s own doc for the convention this implements. Only the first matching segment is swapped, which is unambiguous for every path this mod actually produces (a single "models" segment). */
	private static String tudursvehiclemod$hitboxVariantPath(String path) {
		String[] segments = path.split("/");
		for (int i = 0; i < segments.length; i++) {
			if (segments[i].equals(HIT_DETECTION_MODEL_SOURCE_DIR)) {
				StringBuilder mirrored = new StringBuilder();
				for (int j = 0; j < segments.length; j++) {
					mirrored.append(j == i ? HIT_DETECTION_MODEL_DIR : segments[j]);
					if (j < segments.length - 1) {
						mirrored.append('/');
					}
				}
				return mirrored.toString();
			}
		}
		return null;
	}

	/** Locates one asset-relative path across the addon directories and then the loaded mods, or null if no such file exists in either. */
	private static Path tudursvehiclemod$findFile(String relativePath) {
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path candidate = addonDir.resolve(relativePath);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}

		for (var mod : FabricLoader.getInstance().getAllMods()) {
			Optional<Path> found = mod.findPath(relativePath);
			if (found.isPresent() && Files.isRegularFile(found.get())) {
				return found.get();
			}
		}

		return null;
	}

	/** Path segment identifying a display model's own directory - see tudursvehiclemod$resolveModelPath()'s own doc. Matches mcheli_convert.py's own output convention. */
	private static final String HIT_DETECTION_MODEL_SOURCE_DIR = "models";

	/** Path segment a dedicated hit-detection mesh's own mirrored directory uses in place of HIT_DETECTION_MODEL_SOURCE_DIR - see tudursvehiclemod$resolveModelPath()'s own doc. */
	private static final String HIT_DETECTION_MODEL_DIR = "models_hitbox";
}
