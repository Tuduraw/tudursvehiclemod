package com.example.tudursvehiclemod.client.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A minimal Wavefront.obj parser: reads v/vt/vn/f and flattens each named group (from "o name" / "g name" lines) into its own triangle list. */
public class ObjModel {

	public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {}

	/** Flat-array conversion, following a reported CPU single-thread rendering bottleneck with the GPU sitting nearly idle: a group's own triangles stored as ONE contiguous float[] rather than a List of Vertex objects.
	 *
	 * WHY THIS EXISTS: the vertex-submission loop is the measured bottleneck, and it walks this data once per vertex per frame - on the order of 650,000 vertices per frame for a single high-poly vehicle. Reading that from a List of Vertex costs, for every single vertex, an ArrayList bounds check, a pointer dereference to an object sitting wherever the heap happened to put it, and then field loads from that scattered location - so the CPU stalls on cache misses far more than it spends doing the actual work. The identical data in one contiguous float[] is walked linearly, which is the access pattern hardware prefetching is built for.
	 *
	 * It is also substantially SMALLER: a Vertex object costs its own header plus eight fields plus a slot in the backing pointer array, where the same vertex here is five floats. Only x/y/z/u/v are stored, because the authored per-vertex normals are consumed at construction time (see faceNormals below) and never read again during rendering.
	 *
	 * Each instance owns its own precomputed faceNormals. That deliberately replaces what used to be a static IdentityHashMap cache in VehicleEntityRenderer: normals now live and die with the geometry they belong to, so there is no global map retaining keys and values for the rest of the session - a pattern that has caused real leaks in this codebase before. */
	public static final class Triangles {
		private final float[] data;
		private final int vertexCount;
		private final float[] faceNormals;
		private final int verticesPerFace;

		private Triangles(float[] data, int vertexCount, float[] faceNormals, int verticesPerFace) {
			this.data = data;
			this.vertexCount = vertexCount;
			this.faceNormals = faceNormals;
			this.verticesPerFace = verticesPerFace;
		}

		/** How many consecutive vertices in data() make up one face, and therefore how many share one entry in faceNormals(). 4 on a QUADS pipeline, 3 on a TRIANGLES one - see pack()'s own doc for why this matters so much. */
		public int verticesPerFace() {
			return this.verticesPerFace;
		}

		/** Five floats per vertex, laid out x, y, z, u, v - see this class's own doc. */
		public float[] data() {
			return this.data;
		}

		public int vertexCount() {
			return this.vertexCount;
		}

		/** Three floats per TRIANGLE (not per vertex), computed once at construction - see this class's own doc. */
		public float[] faceNormals() {
			return this.faceNormals;
		}

		public boolean isEmpty() {
			return this.vertexCount == 0;
		}

		/** Floats per vertex in data() - see this class's own doc. */
		public static final int FLOATS_PER_VERTEX = 5;

		static final Triangles EMPTY = new Triangles(new float[0], 0, new float[0], 4);

		/** Packs a group's own FACES into this flat form, keeping quads whole wherever the render pipeline can accept them.
		 *
		 * WHAT WAS WRONG BEFORE: the sample model is 100% quads, and the old path split every one of them into two triangles at parse time, then - because the pipeline in use is QUADS-based - padded each of those triangles back out to four vertices at draw time. That is EIGHT vertices submitted per source quad, when the pipeline would have taken the original four. The split and the padding undo each other, so both were pure overhead: 868,072 vertices per frame where 434,036 suffice.
		 *
		 * WHAT THIS DOES: on a QUADS pipeline every face occupies four slots - a quad is stored exactly as authored, a triangle repeats its own third vertex (the degenerate form the pipeline requires anyway), and a face with more corners is fanned into triangles which are each padded the same way. On a TRIANGLES pipeline everything is fanned into triangles at three slots each, as before. Either way one normal is computed per stored face, so flat shading is unchanged.
		 *
		 * Per-vertex authored normals are consumed here for the degenerate-face fallback and then dropped, exactly as before - only x/y/z/u/v are kept. */
		static Triangles pack(List<List<Vertex>> faces, boolean quadPipeline) {
			int verticesPerFace = quadPipeline ? 4 : 3;
			List<Vertex[]> stored = new ArrayList<>();
			for (List<Vertex> face : faces) {
				if (face.size() < 3) {
					continue;
				}
				if (quadPipeline && face.size() == 4) {
					// The case this whole method exists for: straight through, four vertices, no split and no padding.
					stored.add(new Vertex[]{face.get(0), face.get(1), face.get(2), face.get(3)});
					continue;
				}
				// Everything else fans into triangles; on a QUADS pipeline each one then repeats its own last vertex.
				for (int i = 1; i < face.size() - 1; i++) {
					Vertex a = face.get(0);
					Vertex b = face.get(i);
					Vertex c = face.get(i + 1);
					stored.add(quadPipeline ? new Vertex[]{a, b, c, c} : new Vertex[]{a, b, c});
				}
			}
			if (stored.isEmpty()) {
				return EMPTY;
			}
			int vertexCount = stored.size() * verticesPerFace;
			float[] data = new float[vertexCount * FLOATS_PER_VERTEX];
			float[] faceNormals = new float[stored.size() * 3];
			for (int f = 0; f < stored.size(); f++) {
				Vertex[] face = stored.get(f);
				for (int v = 0; v < verticesPerFace; v++) {
					Vertex vertex = face[v];
					int base = (f * verticesPerFace + v) * FLOATS_PER_VERTEX;
					data[base] = vertex.x();
					data[base + 1] = vertex.y();
					data[base + 2] = vertex.z();
					data[base + 3] = vertex.u();
					data[base + 4] = vertex.v();
				}
				tudursvehiclemod$writeFaceNormal(face[0], face[1], face[2], faceNormals, f * 3);
			}
			return new Triangles(data, vertexCount, faceNormals, verticesPerFace);
		}

		/** One face's own normal, from its first three corners, falling back to the first corner's own authored normal when the face is degenerate (zero area) rather than dividing by ~0. */
		private static void tudursvehiclemod$writeFaceNormal(Vertex a, Vertex b, Vertex c, float[] out, int offset) {
			float ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z();
			float vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
			float nx = uy * vz - uz * vy;
			float ny = uz * vx - ux * vz;
			float nz = ux * vy - uy * vx;
			float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (length < 1.0e-6f) {
				out[offset] = a.nx();
				out[offset + 1] = a.ny();
				out[offset + 2] = a.nz();
			} else {
				out[offset] = nx / length;
				out[offset + 1] = ny / length;
				out[offset + 2] = nz / length;
			}
		}

		/** Concatenates several groups' own packed triangles into one. Face normals are copied rather than recomputed, since concatenation never changes any triangle's own three vertices. */
		static Triangles concat(List<Triangles> parts) {
			int totalVertices = 0;
			int verticesPerFace = 4;
			for (Triangles part : parts) {
				totalVertices += part.vertexCount;
				if (part.vertexCount > 0) {
					verticesPerFace = part.verticesPerFace;
				}
			}
			if (totalVertices == 0) {
				return EMPTY;
			}
			float[] data = new float[totalVertices * FLOATS_PER_VERTEX];
			float[] faceNormals = new float[(totalVertices / verticesPerFace) * 3];
			int dataOffset = 0;
			int normalOffset = 0;
			for (Triangles part : parts) {
				System.arraycopy(part.data, 0, data, dataOffset, part.vertexCount * FLOATS_PER_VERTEX);
				dataOffset += part.vertexCount * FLOATS_PER_VERTEX;
				System.arraycopy(part.faceNormals, 0, faceNormals, normalOffset, (part.vertexCount / verticesPerFace) * 3);
				normalOffset += (part.vertexCount / verticesPerFace) * 3;
			}
			return new Triangles(data, totalVertices, faceNormals, verticesPerFace);
		}
	}

	/** Group name -> its triangles, in file order. */
	private final Map<String, Triangles> groups;

	public ObjModel(Map<String, Triangles> groups) {
		this.groups = groups;
	}

	/** All triangles from every group combined, in group order. */
	public Triangles getTriangles() {
		return Triangles.concat(new ArrayList<>(groups.values()));
	}

	/** For a genuinely high-poly model (an 11,000+
	 * triangle main body was the specific case reported), rebuilding this
	 * filtered list from scratch (a fresh ArrayList, with every single
	 * retained vertex copied into it one at a time) EVERY SINGLE FRAME,
	 * for EVERY visible vehicle using this model, was real, avoidable,
	 * single-threaded work piling up specifically on the render thread
	 * (which, unlike hit detection's own already-parallelized candidate
	 * checks, genuinely can't be spread across multiple threads - actual
	 * GPU vertex submission has to happen from one thread) - since
	 * excludedGroupNames is always the exact same Set for a given vehicle
	 * DEFINITION (derived from its own spinningParts()/toggleParts()/
	 * weaponParts(), which never change at runtime), the filtered result
	 * itself never actually changes either, so it only ever needs
	 * computing once and can just be reused every subsequent frame
	 * instead. Keyed by the exact excludedGroupNames Set itself (relying
	 * on a standard Set's own structural equals()/hashCode(), so two
	 * different Set instances with the same actual contents still hit
	 * the same cache entry). */
	private final Map<java.util.Set<String>, Triangles> excludingCache = new java.util.concurrent.ConcurrentHashMap<>();

	/** Triangles belonging to every group EXCEPT the given names. */
	public Triangles getTrianglesExcluding(java.util.Set<String> excludedGroupNames) {
		return excludingCache.computeIfAbsent(excludedGroupNames, names -> {
			List<Triangles> retained = new ArrayList<>();
			for (Map.Entry<String, Triangles> entry : groups.entrySet()) {
				if (!names.contains(entry.getKey())) {
					retained.add(entry.getValue());
				}
			}
			return Triangles.concat(retained);
		});
	}

	/** Triangles for one named group (e.g. */
	public Triangles getGroup(String name) {
		return groups.getOrDefault(name, Triangles.EMPTY);
	}

	/** All group names in this model, including the unnamed default group (""). */
	public java.util.Set<String> getGroupNames() {
		return groups.keySet();
	}

	public static ObjModel parse(BufferedReader reader) throws IOException {
		List<float[]> positions = new ArrayList<>();
		List<float[]> uvs = new ArrayList<>();
		List<float[]> normals = new ArrayList<>();
		Map<String, List<List<Vertex>>> groups = new LinkedHashMap<>();
		String currentGroup = "";
		groups.put(currentGroup, new ArrayList<>());

		String line;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) continue;

			String[] parts = line.split("\\s+");
			switch (parts[0]) {
				case "v" -> positions.add(new float[]{
						Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])
				});
				case "vt" -> uvs.add(new float[]{
						Float.parseFloat(parts[1]), parts.length > 2 ? Float.parseFloat(parts[2]) : 0f
				});
				case "vn" -> normals.add(new float[]{
						Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])
				});
				case "o", "g" -> {
					currentGroup = parts.length > 1 ? parts[1] : "";
					groups.computeIfAbsent(currentGroup, k -> new ArrayList<>());
				}
				case "f" -> {
					// Faces are kept WHOLE here rather than fan-triangulated on the spot, so that Triangles.pack() can still see that a quad is a quad. Splitting here was what forced the split-then-pad round trip that made every source quad cost eight submitted vertices instead of four.
					List<Vertex> face = new ArrayList<>(parts.length - 1);
					for (int i = 1; i < parts.length; i++) {
						face.add(resolveVertex(parts[i], positions, uvs, normals));
					}
					groups.computeIfAbsent(currentGroup, k -> new ArrayList<>()).add(face);
				}
				default -> { /* usemtl / mtllib / s ignored by this minimal parser */ }
			}
		}

		// Per Triangles' own doc: parsing still builds Vertex objects (the OBJ format is read one indexed corner at a time, so they're the natural intermediate), but nothing beyond this point keeps them - each group is packed into its own flat form here, and the intermediate lists become garbage immediately.
		//
		// Whether quads survive depends on the pipeline actually in use, which is fixed for the session: a QUADS pipeline (the default dither_texture mode, and translucent) takes them as they are, while the experimental TRIANGLES path cannot and needs everything fanned. Reading it once here keeps the decision out of the per-frame path entirely.
		boolean quadPipeline = !DitherCutoutLayers.EXPERIMENTAL_TRIANGLES;
		Map<String, Triangles> packed = new LinkedHashMap<>();
		for (Map.Entry<String, List<List<Vertex>>> entry : groups.entrySet()) {
			packed.put(entry.getKey(), Triangles.pack(entry.getValue(), quadPipeline));
		}

		return new ObjModel(packed);
	}

	private static Vertex resolveVertex(String token, List<float[]> positions, List<float[]> uvs, List<float[]> normals) {
		String[] idx = token.split("/");
		float[] pos = positions.get(Integer.parseInt(idx[0]) - 1);

		float u = 0, v = 0;
		if (idx.length > 1 && !idx[1].isEmpty()) {
			float[] uv = uvs.get(Integer.parseInt(idx[1]) - 1);
			u = uv[0];
			v = 1.0f - uv[1]; // OBJ's v is bottom-up; Minecraft texture UVs are top-down
		}

		float nx = 0, ny = 1, nz = 0;
		if (idx.length > 2 && !idx[2].isEmpty()) {
			float[] n = normals.get(Integer.parseInt(idx[2]) - 1);
			nx = n[0];
			ny = n[1];
			nz = n[2];
		}

		return new Vertex(pos[0], pos[1], pos[2], u, v, nx, ny, nz);
	}
}
