package com.example.tudursvehiclemod.asset;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/** A vehicle model as a solid block of cells: every cell its surface passes through, plus every cell it encloses. What contact detection (entity.VehicleContacts) tests points against - "is this point inside the vehicle", which the surface mesh alone (ServerObjModelHitboxes.Mesh, a thin shell for hit detection) can't answer cheaply.
 *
 * <p>Model-local coordinates, like the Mesh it's built from. Built once per definition, in the background (entity.HitMeshPreloader).
 *
 * <p><b>How it's filled.</b> Surface: every cell whose centre lies within half a cell diagonal of a triangle. Inside: rays along each axis through every row of cell centres, solid between the 1st and 2nd surface crossing, the 3rd and 4th, and so on; a cell counts as inside when at least two of the three axes say so. A vehicle model is rarely watertight (an open cockpit, a hatch, parts that don't quite meet), and a gap makes one ray miss a crossing and fill the rest of its row wrongly - the other two axes outvote it.
 *
 * <p><b>Resolution.</b> BASE_CELL world blocks per cell, coarsened just enough to stay within MAX_CELLS (a 270-block battleship gets about 0.55-block cells). A second, coarse layer (COARSE_FACTOR cells to a side) lets a test throw away empty space without touching the fine one. */
public final class MeshVoxelGrid {

	/** Preferred cell size, in world blocks. */
	public static final float BASE_CELL = 0.5f;
	/** Most fine cells one grid may have: 1 bit each, so at most 512 KiB per definition. */
	public static final long MAX_CELLS = 1L << 22;
	/** Fine cells to a coarse cell, per side. */
	public static final int COARSE_FACTOR = 4;

	private final float originX;
	private final float originY;
	private final float originZ;
	private final float cell;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final BitSet solid;
	private final int coarseX;
	private final int coarseY;
	private final int coarseZ;
	private final BitSet coarse;
	private final int solidCount;

	private MeshVoxelGrid(float originX, float originY, float originZ, float cell, int sizeX, int sizeY, int sizeZ, BitSet solid) {
		this.originX = originX;
		this.originY = originY;
		this.originZ = originZ;
		this.cell = cell;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.solid = solid;
		this.coarseX = (sizeX + COARSE_FACTOR - 1) / COARSE_FACTOR;
		this.coarseY = (sizeY + COARSE_FACTOR - 1) / COARSE_FACTOR;
		this.coarseZ = (sizeZ + COARSE_FACTOR - 1) / COARSE_FACTOR;
		this.coarse = new BitSet(this.coarseX * this.coarseY * this.coarseZ);
		for (int i = solid.nextSetBit(0); i >= 0; i = solid.nextSetBit(i + 1)) {
			int x = i % sizeX;
			int y = (i / sizeX) % sizeY;
			int z = i / (sizeX * sizeY);
			this.coarse.set(this.coarseIndex(x / COARSE_FACTOR, y / COARSE_FACTOR, z / COARSE_FACTOR));
		}
		this.solidCount = solid.cardinality();
	}

	/** Builds the grid for mesh, a model drawn at scale (world blocks per model unit - only used to pick the cell size). */
	public static MeshVoxelGrid build(ServerObjModelHitboxes.Mesh mesh, float scale) {
		float safeScale = scale > 0f ? scale : 1f;
		float extentX = mesh.maxX() - mesh.minX();
		float extentY = mesh.maxY() - mesh.minY();
		float extentZ = mesh.maxZ() - mesh.minZ();
		float cell = BASE_CELL / safeScale;
		// Coarsened (never refined) until the grid - one cell of padding each side - fits MAX_CELLS.
		while ((long) (Math.ceil(extentX / cell) + 2) * (long) (Math.ceil(extentY / cell) + 2) * (long) (Math.ceil(extentZ / cell) + 2) > MAX_CELLS) {
			cell *= 1.1f;
		}
		int sizeX = (int) Math.ceil(extentX / cell) + 2;
		int sizeY = (int) Math.ceil(extentY / cell) + 2;
		int sizeZ = (int) Math.ceil(extentZ / cell) + 2;
		float originX = mesh.minX() - cell;
		float originY = mesh.minY() - cell;
		float originZ = mesh.minZ() - cell;
		int total = sizeX * sizeY * sizeZ;
		List<float[]> triangles = mesh.triangles();

		// Surface.
		BitSet surface = new BitSet(total);
		float reachSq = (cell * 0.8660254f) * (cell * 0.8660254f);
		for (float[] tri : triangles) {
			int x0 = Math.max(0, (int) Math.floor((Math.min(tri[0], Math.min(tri[3], tri[6])) - originX) / cell) - 1);
			int x1 = Math.min(sizeX - 1, (int) Math.floor((Math.max(tri[0], Math.max(tri[3], tri[6])) - originX) / cell) + 1);
			int y0 = Math.max(0, (int) Math.floor((Math.min(tri[1], Math.min(tri[4], tri[7])) - originY) / cell) - 1);
			int y1 = Math.min(sizeY - 1, (int) Math.floor((Math.max(tri[1], Math.max(tri[4], tri[7])) - originY) / cell) + 1);
			int z0 = Math.max(0, (int) Math.floor((Math.min(tri[2], Math.min(tri[5], tri[8])) - originZ) / cell) - 1);
			int z1 = Math.min(sizeZ - 1, (int) Math.floor((Math.max(tri[2], Math.max(tri[5], tri[8])) - originZ) / cell) + 1);
			for (int z = z0; z <= z1; z++) {
				float cz = originZ + (z + 0.5f) * cell;
				for (int y = y0; y <= y1; y++) {
					float cy = originY + (y + 0.5f) * cell;
					for (int x = x0; x <= x1; x++) {
						float cx = originX + (x + 0.5f) * cell;
						if (ServerObjModelHitboxes.tudursvehiclemod$distanceSquaredToTriangle(cx, cy, cz, tri) <= reachSq) {
							surface.set(x + sizeX * (y + sizeY * z));
						}
					}
				}
			}
		}

		// Inside, by parity along each axis, then a two-of-three vote.
		BitSet[] inside = new BitSet[3];
		int[] sizes = {sizeX, sizeY, sizeZ};
		float[] origins = {originX, originY, originZ};
		for (int axis = 0; axis < 3; axis++) {
			inside[axis] = tudursvehiclemod$parityFill(triangles, axis, sizes, origins, cell);
		}
		BitSet solid = (BitSet) surface.clone();
		BitSet twoOfThree = (BitSet) inside[0].clone();
		twoOfThree.and(inside[1]);
		BitSet other = (BitSet) inside[0].clone();
		other.and(inside[2]);
		twoOfThree.or(other);
		other = (BitSet) inside[1].clone();
		other.and(inside[2]);
		twoOfThree.or(other);
		solid.or(twoOfThree);
		return new MeshVoxelGrid(originX, originY, originZ, cell, sizeX, sizeY, sizeZ, solid);
	}

	/** Cells inside the mesh by parity of crossings along axis (0 x, 1 y, 2 z): each row of cell centres along that axis is solid between the 1st and 2nd crossing, the 3rd and 4th, ... Crossings at (nearly) the same place - a double-sided face, an edge shared by two triangles - count once. */
	private static BitSet tudursvehiclemod$parityFill(List<float[]> triangles, int axis, int[] sizes, float[] origins, float cell) {
		int u = (axis + 1) % 3;
		int v = (axis + 2) % 3;
		int sizeU = sizes[u];
		int sizeV = sizes[v];
		float[][] crossings = new float[sizeU * sizeV][];
		int[] counts = new int[sizeU * sizeV];
		for (float[] tri : triangles) {
			float au = tri[u], av = tri[v], aw = tri[axis];
			float bu = tri[3 + u], bv = tri[3 + v], bw = tri[3 + axis];
			float cu = tri[6 + u], cv = tri[6 + v], cw = tri[6 + axis];
			float area = (bu - au) * (cv - av) - (cu - au) * (bv - av);
			if (Math.abs(area) < 1.0e-9f) {
				continue; // edge-on to this axis: no crossing to count
			}
			int u0 = Math.max(0, (int) Math.floor((Math.min(au, Math.min(bu, cu)) - origins[u]) / cell - 0.5f));
			int u1 = Math.min(sizeU - 1, (int) Math.ceil((Math.max(au, Math.max(bu, cu)) - origins[u]) / cell - 0.5f));
			int v0 = Math.max(0, (int) Math.floor((Math.min(av, Math.min(bv, cv)) - origins[v]) / cell - 0.5f));
			int v1 = Math.min(sizeV - 1, (int) Math.ceil((Math.max(av, Math.max(bv, cv)) - origins[v]) / cell - 0.5f));
			for (int iv = v0; iv <= v1; iv++) {
				float pv = origins[v] + (iv + 0.5f) * cell;
				for (int iu = u0; iu <= u1; iu++) {
					float pu = origins[u] + (iu + 0.5f) * cell;
					// Barycentric coordinates of (pu, pv) in the triangle's projection.
					float w1 = ((bu - pu) * (cv - pv) - (cu - pu) * (bv - pv)) / area;
					float w2 = ((cu - pu) * (av - pv) - (au - pu) * (cv - pv)) / area;
					float w3 = 1f - w1 - w2;
					if (w1 < 0f || w2 < 0f || w3 < 0f) {
						continue;
					}
					float w = w1 * aw + w2 * bw + w3 * cw;
					int column = iu + sizeU * iv;
					float[] list = crossings[column];
					if (list == null) {
						list = new float[4];
						crossings[column] = list;
					} else if (counts[column] == list.length) {
						list = Arrays.copyOf(list, list.length * 2);
						crossings[column] = list;
					}
					list[counts[column]++] = w;
				}
			}
		}
		BitSet inside = new BitSet(sizes[0] * sizes[1] * sizes[2]);
		int[] index = new int[3];
		for (int column = 0; column < crossings.length; column++) {
			float[] list = crossings[column];
			if (list == null) {
				continue;
			}
			int count = counts[column];
			Arrays.sort(list, 0, count);
			// Merge near-equal crossings.
			int unique = 0;
			for (int i = 0; i < count; i++) {
				if (unique == 0 || list[i] - list[unique - 1] > cell * 1.0e-3f) {
					list[unique++] = list[i];
				}
			}
			index[u] = column % sizeU;
			index[v] = column / sizeU;
			for (int pair = 0; pair + 1 < unique; pair += 2) {
				int from = Math.max(0, (int) Math.ceil((list[pair] - origins[axis]) / cell - 0.5f));
				int to = Math.min(sizes[axis] - 1, (int) Math.floor((list[pair + 1] - origins[axis]) / cell - 0.5f));
				for (int w = from; w <= to; w++) {
					index[axis] = w;
					inside.set(index[0] + sizes[0] * (index[1] + sizes[1] * index[2]));
				}
			}
		}
		return inside;
	}

	private int coarseIndex(int x, int y, int z) {
		return x + this.coarseX * (y + this.coarseY * z);
	}

	/** Whether the model-local point is in a solid cell. */
	public boolean isSolid(float x, float y, float z) {
		int ix = (int) Math.floor((x - this.originX) / this.cell);
		int iy = (int) Math.floor((y - this.originY) / this.cell);
		int iz = (int) Math.floor((z - this.originZ) / this.cell);
		if (ix < 0 || iy < 0 || iz < 0 || ix >= this.sizeX || iy >= this.sizeY || iz >= this.sizeZ) {
			return false;
		}
		if (!this.coarse.get(this.coarseIndex(ix / COARSE_FACTOR, iy / COARSE_FACTOR, iz / COARSE_FACTOR))) {
			return false;
		}
		return this.solid.get(ix + this.sizeX * (iy + this.sizeY * iz));
	}

	/** Model-local bounds of the grid: {minX, minY, minZ, maxX, maxY, maxZ}. */
	public float[] bounds() {
		return new float[]{this.originX, this.originY, this.originZ,
				this.originX + this.sizeX * this.cell, this.originY + this.sizeY * this.cell, this.originZ + this.sizeZ * this.cell};
	}

	/** Cell size, model units. */
	public float cellSize() {
		return this.cell;
	}

	public int solidCellCount() {
		return this.solidCount;
	}

	public long totalCellCount() {
		return (long) this.sizeX * this.sizeY * this.sizeZ;
	}

	/** Approximate memory held, bytes. */
	public long memoryBytes() {
		return (this.solid.size() + this.coarse.size()) / 8L;
	}
}
