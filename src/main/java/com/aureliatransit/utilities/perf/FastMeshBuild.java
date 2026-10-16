package com.aureliatransit.utilities.perf;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.model.RawModel;
import org.mtr.mapping.render.vertex.Vertex;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.mtr.mapping.render.vertex.VertexAttributeSource;
import org.mtr.mapping.render.vertex.VertexAttributeType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The CPU half of {@link MergedObjUpload}, done the fast way: same bytes, same order. MTR 4.0.5 builds a car by copying
 * every triangle corner into a new {@link Vertex} (two {@code Vector3f} each) to fill in normals, then removes
 * duplicates twice with a {@code HashMap<Vertex, Integer>} whose keys hash eight boxed floats. Here the corners live in
 * primitive arrays and duplicates are found with an open-addressing table on the floats' bits, which is exactly
 * {@code Vertex.equals}. Face order still comes from a real {@code HashSet<Face>} filled in the same order, so the
 * index buffer is identical too.
 * <p>
 * Steps, as MTR runs them for a merged car: per OBJ group and material {@code generateNormals}, {@code distinct};
 * append groups of the same material in MTR's {@code RawModel} order; per material {@code distinct} again (inside
 * {@code RawMesh.upload}) and pack. The source models are not modified.
 */
public final class FastMeshBuild {

	private static final int FLOATS = 8;

	private FastMeshBuild() {
	}

	/** One material's vertex and index data (native byte order), as MTR's {@code RawMesh.upload} would hand it to the GPU. */
	public static final class Packed {

		public final MaterialProperties materialProperties;
		public final byte[] vertexBytes;
		public final int[] indices;
		public final int faceCount;

		private Packed(MaterialProperties materialProperties, byte[] vertexBytes, int[] indices, int faceCount) {
			this.materialProperties = materialProperties;
			this.vertexBytes = vertexBytes;
			this.indices = indices;
			this.faceCount = faceCount;
		}
	}

	/** Corners or vertices: 8 floats (position, normal, u, v) and 2 ints (color, light) each. */
	private static final class Corners {

		float[] floats;
		int[] ints;
		int count;

		Corners(int capacity) {
			floats = new float[Math.max(1, capacity) * FLOATS];
			ints = new int[Math.max(1, capacity) * 2];
		}

		int add(float[] sourceFloats, int[] sourceInts, int sourceIndex) {
			ensure(count + 1);
			System.arraycopy(sourceFloats, sourceIndex * FLOATS, floats, count * FLOATS, FLOATS);
			System.arraycopy(sourceInts, sourceIndex * 2, ints, count * 2, 2);
			return count++;
		}

		void ensure(int needed) {
			if (needed * FLOATS > floats.length) {
				final int capacity = Math.max(needed, count * 2);
				floats = Arrays.copyOf(floats, capacity * FLOATS);
				ints = Arrays.copyOf(ints, capacity * 2);
			}
		}
	}

	/** A mesh in flight: vertices plus faces as MTR {@link Face}s (so {@code HashSet} order matches MTR's). */
	private static final class Work {

		Corners vertices;
		List<Face> faces;

		Work(Corners vertices, List<Face> faces) {
			this.vertices = vertices;
			this.faces = faces;
		}
	}

	/**
	 * Packs a car like MTR's merged upload would; null when the data is something MTR itself would reject (a face
	 * pointing past the vertex list), so the caller can use MTR's own code for that model.
	 */
	public static List<Packed> pack(Collection<OptimizedModel.ObjModel> objModels, Function<OptimizedModel.ObjModel, RawModel> rawModelOf, VertexAttributeMapping mapping) {
		final Set<OptimizedModel.ObjModel> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		// MTR's RawModel decides the material order: a HashMap filled with put() (RawModel.append), so colliding keys keep
		// insertion order. Not computeIfAbsent, which links a new key at the head of its bin and reverses them.
		final Map<MaterialProperties, Work> merged = new HashMap<>();
		for (final OptimizedModel.ObjModel objModel : objModels) {
			if (!seen.add(objModel)) {
				continue;
			}
			final List<RawMesh> sourceMeshes = new ArrayList<>();
			rawModelOf.apply(objModel).iterateRawMeshList(sourceMeshes::add);
			for (final RawMesh source : sourceMeshes) {
				final Work work = readMesh(source);
				if (work == null) {
					return null;
				}
				generateNormals(work);
				distinct(work);
				final Work target = merged.get(source.materialProperties);
				if (target == null) {
					final Work copy = new Work(new Corners(work.vertices.count), new ArrayList<>(work.faces.size()));
					append(copy, work);
					merged.put(source.materialProperties, copy);
				} else {
					append(target, work);
				}
			}
		}
		final List<Packed> result = new ArrayList<>();
		final org.joml.Vector3f normal = new org.joml.Vector3f();
		final boolean[] writes = writes(mapping);
		for (final Map.Entry<MaterialProperties, Work> entry : merged.entrySet()) {
			final Work work = entry.getValue();
			if (work.faces.isEmpty()) {
				continue;
			}
			for (final Face face : work.faces) {
				for (final int index : face.vertices) {
					if (index < 0 || index >= work.vertices.count) {
						throw new IllegalStateException("face points past the vertex list");
					}
				}
			}
			distinct(work);
			result.add(packMesh(entry.getKey(), work, mapping, writes, normal));
		}
		return result;
	}

	private static Work readMesh(RawMesh source) {
		final int size = source.vertices.size();
		final Corners corners = new Corners(size);
		corners.count = size;
		for (int i = 0; i < size; i++) {
			final Vertex vertex = source.vertices.get(i);
			final int f = i * FLOATS;
			corners.floats[f] = vertex.position.getX();
			corners.floats[f + 1] = vertex.position.getY();
			corners.floats[f + 2] = vertex.position.getZ();
			corners.floats[f + 3] = vertex.normal.getX();
			corners.floats[f + 4] = vertex.normal.getY();
			corners.floats[f + 5] = vertex.normal.getZ();
			corners.floats[f + 6] = vertex.u;
			corners.floats[f + 7] = vertex.v;
			corners.ints[i * 2] = vertex.color;
			corners.ints[i * 2 + 1] = vertex.light;
		}
		final List<Face> faces = new ArrayList<>(source.faces.size());
		for (final Face face : source.faces) {
			for (final int index : face.vertices) {
				if (index < 0 || index >= size) {
					return null;
				}
			}
			faces.add(new Face(face));
		}
		return new Work(corners, faces);
	}

	/** {@code RawMesh.generateNormals}: every corner of a face becomes its own vertex; zero normals get the face normal. */
	private static void generateNormals(Work work) {
		final float[] in = work.vertices.floats;
		final int[] inInts = work.vertices.ints;
		final Corners out = new Corners(work.faces.size() * 3);
		for (final Face face : work.faces) {
			final int[] v = face.vertices;
			if (v.length < 3) {
				// MTR leaves such a face untouched (its indices then point into the new list).
				continue;
			}
			final int p0 = v[0] * FLOATS;
			final int p1 = v[1] * FLOATS;
			final int p2 = v[2] * FLOATS;
			// Float subtraction first, then widened: as MTR's getX() - getX() assigned to a double.
			final double ax = in[p1] - in[p0];
			final double ay = in[p1 + 1] - in[p0 + 1];
			final double az = in[p1 + 2] - in[p0 + 2];
			final double bx = in[p2] - in[p0];
			final double by = in[p2 + 1] - in[p0 + 1];
			final double bz = in[p2 + 2] - in[p0 + 2];
			final double nx = ay * bz - az * by;
			final double ny = az * bx - ax * bz;
			final double nz = ax * by - ay * bx;
			final double t1 = nx * nx + ny * ny + nz * nz;
			final float mx;
			final float my;
			final float mz;
			if (t1 != 0) {
				final double t2 = 1 / Math.sqrt(t1);
				mx = (float) (nx * t2);
				my = (float) (ny * t2);
				mz = (float) (nz * t2);
			} else {
				mx = 0;
				my = 1;
				mz = 0;
			}
			for (int j = 0; j < v.length; j++) {
				final int index = out.add(in, inInts, v[j]);
				final int f = index * FLOATS;
				if (out.floats[f + 3] == 0 && out.floats[f + 4] == 0 && out.floats[f + 5] == 0) {
					out.floats[f + 3] = mx;
					out.floats[f + 4] = my;
					out.floats[f + 5] = mz;
				}
				v[j] = index;
			}
		}
		work.vertices = out;
	}

	/** {@code RawMesh.distinct}: first-seen vertex order over the faces, faces through a {@code HashSet} as MTR does. */
	private static void distinct(Work work) {
		final Corners in = work.vertices;
		final Corners out = new Corners(in.count);
		int tableSize = Integer.highestOneBit(Math.max(4, in.count * 2 - 1)) << 1;
		final int[] table = new int[tableSize];
		Arrays.fill(table, -1);
		final int mask = tableSize - 1;
		final int[] remap = new int[in.count];
		Arrays.fill(remap, -1);
		final Set<Face> distinctFaces = new HashSet<>(work.faces.size());
		for (final Face face : work.faces) {
			final int[] v = face.vertices;
			for (int i = 0; i < v.length; i++) {
				final int source = v[i];
				int index = remap[source];
				if (index < 0) {
					final int hash = hash(in.floats, source);
					int slot = hash & mask;
					while (true) {
						final int candidate = table[slot];
						if (candidate < 0) {
							index = out.add(in.floats, in.ints, source);
							table[slot] = index;
							break;
						}
						if (sameKey(out.floats, candidate, in.floats, source)) {
							index = candidate;
							break;
						}
						slot = (slot + 1) & mask;
					}
					remap[source] = index;
				}
				v[i] = index;
			}
			distinctFaces.add(face);
		}
		work.vertices = out;
		work.faces = new ArrayList<>(distinctFaces);
	}

	/** {@code RawMesh.append}: faces shifted by the vertices already there. */
	private static void append(Work target, Work source) {
		final int offset = target.vertices.count;
		target.vertices.ensure(offset + source.vertices.count);
		System.arraycopy(source.vertices.floats, 0, target.vertices.floats, offset * FLOATS, source.vertices.count * FLOATS);
		System.arraycopy(source.vertices.ints, 0, target.vertices.ints, offset * 2, source.vertices.count * 2);
		target.vertices.count += source.vertices.count;
		for (final Face face : source.faces) {
			final Face shifted = new Face(face);
			for (int i = 0; i < shifted.vertices.length; i++) {
				shifted.vertices[i] += offset;
			}
			target.faces.add(shifted);
		}
	}

	/** Bytes as {@code RawMesh.upload(Mesh, VertexAttributeMapping)} writes them. */
	private static Packed packMesh(MaterialProperties materialProperties, Work work, VertexAttributeMapping mapping, boolean[] writes, org.joml.Vector3f normal) {
		final Corners vertices = work.vertices;
		final ByteBuffer buffer = ByteBuffer.allocate(vertices.count * mapping.strideVertex).order(ByteOrder.nativeOrder());
		for (int i = 0; i < vertices.count; i++) {
			final int f = i * FLOATS;
			if (writes[0]) {
				buffer.putFloat(vertices.floats[f]).putFloat(vertices.floats[f + 1]).putFloat(vertices.floats[f + 2]);
			}
			if (writes[1]) {
				buffer.putInt(vertices.ints[i * 2]);
			}
			if (writes[2]) {
				buffer.putFloat(vertices.floats[f + 6]).putFloat(vertices.floats[f + 7]);
			}
			if (writes[3]) {
				buffer.putInt(vertices.ints[i * 2 + 1]);
			}
			if (writes[4]) {
				normal.set(vertices.floats[f + 3], vertices.floats[f + 4], vertices.floats[f + 5]).normalize();
				buffer.put((byte) (normal.x * 127)).put((byte) (normal.y * 127)).put((byte) (normal.z * 127));
			}
			if (mapping.paddingVertex > 0) {
				buffer.put((byte) 0);
			}
		}
		int indexCount = 0;
		for (final Face face : work.faces) {
			indexCount += face.vertices.length;
		}
		final int[] indices = new int[indexCount];
		int position = 0;
		for (final Face face : work.faces) {
			for (final int index : face.vertices) {
				indices[position++] = index;
			}
		}
		return new Packed(materialProperties, buffer.array(), indices, work.faces.size());
	}

	/** POSITION, COLOR, UV_TEXTURE, UV_LIGHTMAP, NORMAL: which ones go into the vertex buffer. */
	private static boolean[] writes(VertexAttributeMapping mapping) {
		final VertexAttributeType[] types = {VertexAttributeType.POSITION, VertexAttributeType.COLOR, VertexAttributeType.UV_TEXTURE, VertexAttributeType.UV_LIGHTMAP, VertexAttributeType.NORMAL};
		final boolean[] writes = new boolean[types.length];
		for (int i = 0; i < types.length; i++) {
			writes[i] = mapping.sources.get(types[i]) == VertexAttributeSource.VERTEX_BUFFER;
		}
		return writes;
	}

	private static int hash(float[] floats, int index) {
		int hash = 1;
		final int f = index * FLOATS;
		for (int i = 0; i < FLOATS; i++) {
			hash = hash * 31 + Float.floatToIntBits(floats[f + i]);
		}
		return hash ^ (hash >>> 16);
	}

	/** {@code Vertex.equals}: position, normal, u and v compared by their float bits. */
	private static boolean sameKey(float[] a, int indexA, float[] b, int indexB) {
		final int fa = indexA * FLOATS;
		final int fb = indexB * FLOATS;
		for (int i = 0; i < FLOATS; i++) {
			if (Float.floatToIntBits(a[fa + i]) != Float.floatToIntBits(b[fb + i])) {
				return false;
			}
		}
		return true;
	}
}
