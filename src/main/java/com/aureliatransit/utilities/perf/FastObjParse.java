package com.aureliatransit.utilities.perf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Reads an OBJ file into exactly the meshes MTR 4.0.5's {@code ObjModelLoader} gets from javagl, without javagl's
 * object per number and five full copies of the model. Per group (or the whole file) and per {@code usemtl} material:
 * the same vertices in the same order, the same UVs and normals, the same triangles. javagl's steps, in its order:
 * <ol>
 *     <li>{@code ObjReader}: the last {@code g} (or {@code o}, whichever comes first in the file) and {@code usemtl}
 *     before a face give its groups and material; indices are 1-based or negative (relative); {@code mtllib} keeps
 *     only the last line.</li>
 *     <li>{@code splitByGroups}, {@code splitByMaterialGroups}: vertices, UVs and normals renumbered in the order the
 *     faces first use them. Faces before any {@code usemtl} have no material and are dropped.</li>
 *     <li>{@code triangulate}: n-gons become fans; faces with fewer than three corners are dropped (their vertices
 *     stay).</li>
 *     <li>{@code makeTexCoordsUnique}, {@code makeNormalsUnique}: a vertex used with a second UV (normal) is copied to
 *     the end, again for each later conflicting use.</li>
 *     <li>{@code makeVertexIndexed}: each vertex takes the UV and normal of its last use.</li>
 * </ol>
 * Anything outside the plain subset that real exporters write (line continuations, tabs, odd face syntax, too few
 * coordinates, indices out of range, a number {@link Float#parseFloat} rejects) returns null, and the caller lets
 * javagl read the file as before. Pure Java; thread-safe.
 */
public final class FastObjParse {

	private FastObjParse() {
	}

	/** One material of one group: what MTR turns into one {@code RawMesh}. */
	public static final class Mesh {

		/** The {@code usemtl} name, as javagl reports it. */
		public final String material;
		public final int vertexCount;
		/** x, y, z per vertex. */
		public final float[] positions;
		/** x, y, z per vertex, or null when no face of the mesh had normals. */
		public final float[] normals;
		/** u, v per vertex (as written; MTR's {@code flipv} is not applied), or null when no face had UVs. */
		public final float[] uvs;
		/** Three vertex indices per triangle. */
		public final int[] triangles;

		private Mesh(String material, int vertexCount, float[] positions, float[] normals, float[] uvs, int[] triangles) {
			this.material = material;
			this.vertexCount = vertexCount;
			this.positions = positions;
			this.normals = normals;
			this.uvs = uvs;
			this.triangles = triangles;
		}

		public int triangleCount() {
			return triangles.length / 3;
		}
	}

	public static final class Result {

		/** javagl's {@code getMtlFileNames()}: empty, or the text after the last {@code mtllib}. */
		public final List<String> mtlFileNames;
		/** Group name (or "" when not split) to its meshes, in javagl's material order. */
		public final Map<String, List<Mesh>> groups;

		private Result(List<String> mtlFileNames, Map<String, List<Mesh>> groups) {
			this.mtlFileNames = mtlFileNames;
			this.groups = groups;
		}
	}

	/** The file is outside what this reader reproduces exactly. */
	private static final class Unsupported extends Exception {

		Unsupported() {
			super(null, null, false, false);
		}
	}

	private static final Unsupported UNSUPPORTED = new Unsupported();

	/** Null when javagl should read this file instead. */
	public static Result parse(byte[] data, boolean splitByGroups) {
		try {
			return new Reader(data).read().build(splitByGroups);
		} catch (Unsupported | RuntimeException e) {
			return null;
		}
	}

	// ---------------------------------------------------------------- reading

	private static final class Reader {

		private final byte[] data;
		private final float[] tmp = new float[4];

		float[] v = new float[3 * 1024];
		int vCount;
		float[] vt = new float[2 * 1024];
		int vtCount;
		float[] vn = new float[3 * 1024];
		int vnCount;

		int[] faceStart = new int[1024];
		int[] faceSize = new int[1024];
		int[] faceGroupSet = new int[1024];
		int[] faceMaterial = new int[1024];
		/** Bit 1: has UV indices, bit 2: has normal indices. */
		byte[] faceFlags = new byte[1024];
		int faceCount;
		/** Absolute 0-based indices; UV / normal slots unused when the face has none. */
		int[] cornerV = new int[4096];
		int[] cornerVt = new int[4096];
		int[] cornerVn = new int[4096];
		int cornerCount;

		final List<int[]> groupSets = new ArrayList<>();
		final Map<List<String>, Integer> groupSetIds = new HashMap<>();
		final List<String> groupNames = new ArrayList<>();
		final Map<String, Integer> groupIds = new HashMap<>();
		final List<String> materials = new ArrayList<>();
		final Map<String, Integer> materialIds = new HashMap<>();
		List<String> mtlFileNames = Collections.emptyList();

		Reader(byte[] data) {
			this.data = data;
		}

		Reader read() throws Unsupported {
			int groupSet = groupSet(Collections.singletonList("default"));
			int material = -1;
			String groupOrObject = "";
			final byte[] data = this.data;
			final int length = data.length;
			int pos = 0;
			while (pos < length) {
				// BufferedReader.readLine: \n, \r or \r\n end a line.
				int lineEnd = pos;
				while (lineEnd < length && data[lineEnd] != '\n' && data[lineEnd] != '\r') {
					lineEnd++;
				}
				int next = lineEnd + 1;
				if (lineEnd < length && data[lineEnd] == '\r' && next < length && data[next] == '\n') {
					next++;
				}
				// String.trim
				int start = pos;
				int end = lineEnd;
				while (start < end && (data[start] & 0xFF) <= ' ') {
					start++;
				}
				while (end > start && (data[end - 1] & 0xFF) <= ' ') {
					end--;
				}
				pos = next;
				if (start == end) {
					continue;
				}
				if (data[end - 1] == '\\') {
					throw UNSUPPORTED;
				}
				for (int i = start; i < end; i++) {
					final int c = data[i] & 0xFF;
					// Tabs and other control characters split tokens differently in javagl's two parsers.
					if (c < ' ') {
						throw UNSUPPORTED;
					}
				}
				int tokenEnd = start;
				while (tokenEnd < end && data[tokenEnd] != ' ') {
					tokenEnd++;
				}
				final int tokenLength = tokenEnd - start;
				final byte c0 = data[start];
				if (tokenLength == 1 && (c0 == 'v' || c0 == 'V')) {
					if (readFloats(tokenEnd, end) < 3) {
						throw UNSUPPORTED;
					}
					if (3 * vCount + 3 > v.length) {
						v = Arrays.copyOf(v, v.length * 2);
					}
					v[3 * vCount] = tmp[0];
					v[3 * vCount + 1] = tmp[1];
					v[3 * vCount + 2] = tmp[2];
					vCount++;
				} else if (tokenLength == 1 && (c0 == 'f' || c0 == 'F')) {
					addFace(tokenEnd, end, groupSet, material);
				} else if (tokenLength == 2 && (c0 == 'v' || c0 == 'V') && (data[start + 1] == 't' || data[start + 1] == 'T')) {
					if (readFloats(tokenEnd, end) < 2) {
						throw UNSUPPORTED;
					}
					if (2 * vtCount + 2 > vt.length) {
						vt = Arrays.copyOf(vt, vt.length * 2);
					}
					vt[2 * vtCount] = tmp[0];
					vt[2 * vtCount + 1] = tmp[1];
					vtCount++;
				} else if (tokenLength == 2 && (c0 == 'v' || c0 == 'V') && (data[start + 1] == 'n' || data[start + 1] == 'N')) {
					if (readFloats(tokenEnd, end) < 3) {
						throw UNSUPPORTED;
					}
					if (3 * vnCount + 3 > vn.length) {
						vn = Arrays.copyOf(vn, vn.length * 2);
					}
					vn[3 * vnCount] = tmp[0];
					vn[3 * vnCount + 1] = tmp[1];
					vn[3 * vnCount + 2] = tmp[2];
					vnCount++;
				} else {
					// Same tests as javagl: first token lowercased, rest of the trimmed line from a fixed offset.
					final String identifier = ascii(start, tokenEnd).toLowerCase();
					switch (identifier) {
						case "mtllib" -> mtlFileNames = Collections.singletonList(ascii(start, end).substring(6).trim());
						case "usemtl" -> material = material(ascii(start, end).substring(6).trim());
						case "g", "o" -> {
							if (groupOrObject.isEmpty() || groupOrObject.equals(identifier)) {
								final StringTokenizer tokenizer = new StringTokenizer(ascii(start, end).substring(1).trim());
								final List<String> names = new ArrayList<>();
								while (tokenizer.hasMoreTokens()) {
									names.add(tokenizer.nextToken());
								}
								groupSet = groupSet(names.isEmpty() ? Collections.singletonList("default") : new ArrayList<>(new LinkedHashSet<>(names)));
								groupOrObject = identifier;
							}
						}
						default -> {
						}
					}
				}
			}
			// javagl resolves indices against the final lists only when it splits, so forward references are fine.
			for (int i = 0; i < faceCount; i++) {
				final int from = faceStart[i];
				final int to = from + faceSize[i];
				final byte flags = faceFlags[i];
				for (int c = from; c < to; c++) {
					if (cornerV[c] < 0 || cornerV[c] >= vCount || (flags & 1) != 0 && (cornerVt[c] < 0 || cornerVt[c] >= vtCount) || (flags & 2) != 0 && (cornerVn[c] < 0 || cornerVn[c] >= vnCount)) {
						throw UNSUPPORTED;
					}
				}
			}
			return this;
		}

		/** Bytes as javagl's US-ASCII reader decodes them (anything else becomes U+FFFD). */
		private String ascii(int start, int end) {
			return new String(data, start, end - start, StandardCharsets.US_ASCII);
		}

		private int groupSet(List<String> names) {
			final Integer existing = groupSetIds.get(names);
			if (existing != null) {
				return existing;
			}
			final int[] ids = new int[names.size()];
			for (int i = 0; i < ids.length; i++) {
				final String name = names.get(i);
				Integer id = groupIds.get(name);
				if (id == null) {
					id = groupNames.size();
					groupNames.add(name);
					groupIds.put(name, id);
				}
				ids[i] = id;
			}
			groupSets.add(ids);
			groupSetIds.put(names, groupSets.size() - 1);
			return groupSets.size() - 1;
		}

		private int material(String name) {
			Integer id = materialIds.get(name);
			if (id == null) {
				id = materials.size();
				materials.add(name);
				materialIds.put(name, id);
			}
			return id;
		}

		/** Corners {@code a}, {@code a/b}, {@code a/b/c} or {@code a//c}, the same form for every corner, separated by spaces. */
		private void addFace(int start, int end, int groupSet, int material) throws Unsupported {
			if (faceCount == faceStart.length) {
				final int size = faceCount * 2;
				faceStart = Arrays.copyOf(faceStart, size);
				faceSize = Arrays.copyOf(faceSize, size);
				faceGroupSet = Arrays.copyOf(faceGroupSet, size);
				faceMaterial = Arrays.copyOf(faceMaterial, size);
				faceFlags = Arrays.copyOf(faceFlags, size);
			}
			final byte[] data = this.data;
			int form = -1;
			int size = 0;
			int i = start;
			while (true) {
				while (i < end && data[i] == ' ') {
					i++;
				}
				if (i >= end) {
					break;
				}
				if (cornerCount == cornerV.length) {
					final int capacity = cornerCount * 2;
					cornerV = Arrays.copyOf(cornerV, capacity);
					cornerVt = Arrays.copyOf(cornerVt, capacity);
					cornerVn = Arrays.copyOf(cornerVn, capacity);
				}
				int cornerForm = 0;
				int value = index(i, end);
				i = indexEnd;
				cornerV[cornerCount] = absolute(value, vCount);
				if (i < end && data[i] == '/') {
					i++;
					if (i < end && data[i] == '/') {
						i++;
						cornerForm = 2;
						cornerVn[cornerCount] = absolute(index(i, end), vnCount);
						i = indexEnd;
					} else {
						cornerForm = 1;
						cornerVt[cornerCount] = absolute(index(i, end), vtCount);
						i = indexEnd;
						if (i < end && data[i] == '/') {
							i++;
							cornerForm = 3;
							cornerVn[cornerCount] = absolute(index(i, end), vnCount);
							i = indexEnd;
						}
					}
				}
				if (i < end && data[i] != ' ' || form >= 0 && form != cornerForm) {
					throw UNSUPPORTED;
				}
				form = cornerForm;
				cornerCount++;
				size++;
			}
			faceStart[faceCount] = cornerCount - size;
			faceSize[faceCount] = size;
			faceGroupSet[faceCount] = groupSet;
			faceMaterial[faceCount] = material;
			faceFlags[faceCount] = (byte) Math.max(form, 0);
			faceCount++;
		}

		private int indexEnd;

		/** A non-zero integer, optionally negative; sets {@link #indexEnd}. */
		private int index(int start, int end) throws Unsupported {
			int i = start;
			boolean negative = false;
			if (i < end && data[i] == '-') {
				negative = true;
				i++;
			}
			final int digitsStart = i;
			long value = 0;
			while (i < end && data[i] >= '0' && data[i] <= '9') {
				value = value * 10 + (data[i] - '0');
				if (value > Integer.MAX_VALUE) {
					throw UNSUPPORTED;
				}
				i++;
			}
			if (i == digitsStart || value == 0) {
				throw UNSUPPORTED;
			}
			indexEnd = i;
			return negative ? (int) -value : (int) value;
		}

		/** javagl's {@code makeIndicesAbsolute}, against the count read so far. */
		private static int absolute(int index, int count) {
			return index < 0 ? count + index : index - 1;
		}

		private static final float[] POW10 = {1e0f, 1e1f, 1e2f, 1e3f, 1e4f, 1e5f, 1e6f, 1e7f, 1e8f, 1e9f, 1e10f};

		/**
		 * javagl's {@code readFloatTuple}: up to four space-separated numbers into {@link #tmp}; returns how many. Each
		 * is bit for bit {@link Float#parseFloat}: short plain decimals are an exact integer divided by an exact power of
		 * ten (one correctly rounded IEEE division, as parseFloat rounds), anything else goes to parseFloat.
		 */
		private int readFloats(int start, int end) {
			final byte[] data = this.data;
			int count = 0;
			int i = start;
			while (count < 4) {
				while (i < end && data[i] == ' ') {
					i++;
				}
				if (i >= end) {
					break;
				}
				final int tokenStart = i;
				boolean negative = false;
				if (data[i] == '-' || data[i] == '+') {
					negative = data[i] == '-';
					i++;
				}
				long mantissa = 0;
				int digits = 0;
				int fractionDigits = 0;
				boolean dot = false;
				boolean simple = true;
				while (i < end && data[i] != ' ') {
					final byte c = data[i];
					if (c >= '0' && c <= '9') {
						digits++;
						mantissa = mantissa * 10 + (c - '0');
						if (dot) {
							fractionDigits++;
						}
						if (digits > 18) {
							simple = false;
						}
					} else if (c == '.' && !dot) {
						dot = true;
					} else {
						simple = false;
					}
					i++;
				}
				if (simple && digits > 0 && mantissa < (1 << 24) && fractionDigits < POW10.length) {
					final float value = fractionDigits == 0 ? (float) mantissa : (float) mantissa / POW10[fractionDigits];
					tmp[count++] = negative ? -value : value;
				} else {
					// Throws NumberFormatException where javagl throws, so the file goes back to javagl.
					tmp[count++] = Float.parseFloat(ascii(tokenStart, i));
				}
			}
			return count;
		}

		// ------------------------------------------------------------ splitting

		Result build(boolean splitByGroups) {
			final Builder builder = new Builder(this);
			final Map<String, List<Mesh>> groups = new LinkedHashMap<>();
			if (splitByGroups) {
				// Faces per group, in file order.
				final int[][] groupFaces = new int[groupNames.size()][];
				final int[] groupFaceCount = new int[groupNames.size()];
				for (int f = 0; f < faceCount; f++) {
					for (final int group : groupSets.get(faceGroupSet[f])) {
						int[] faces = groupFaces[group];
						if (faces == null) {
							faces = groupFaces[group] = new int[16];
						} else if (groupFaceCount[group] == faces.length) {
							faces = groupFaces[group] = Arrays.copyOf(faces, faces.length * 2);
						}
						faces[groupFaceCount[group]++] = f;
					}
				}
				for (int group = 0; group < groupNames.size(); group++) {
					if (groupFaceCount[group] > 0) {
						groups.put(groupNames.get(group), builder.meshes(groupFaces[group], groupFaceCount[group]));
					}
				}
			} else {
				final int[] all = new int[faceCount];
				for (int f = 0; f < faceCount; f++) {
					all[f] = f;
				}
				groups.put("", builder.meshes(all, faceCount));
			}
			return new Result(mtlFileNames, groups);
		}
	}

	/** Turns face lists into meshes; scratch arrays are shared and reset with a generation stamp. */
	private static final class Builder {

		private final Reader r;
		private final int[] vStamp;
		private final int[] vLocal;
		private final int[] vtStamp;
		private final int[] vtLocal;
		private final int[] vnStamp;
		private final int[] vnLocal;
		private int generation;

		Builder(Reader r) {
			this.r = r;
			vStamp = new int[r.vCount];
			vLocal = new int[r.vCount];
			vtStamp = new int[r.vtCount];
			vtLocal = new int[r.vtCount];
			vnStamp = new int[r.vnCount];
			vnLocal = new int[r.vnCount];
		}

		/** One mesh per material, in the order the faces first use them; faces without a material are dropped. */
		List<Mesh> meshes(int[] faces, int count) {
			final int[] materialOrder = new int[r.materials.size()];
			int materialCount = 0;
			final int[][] materialFaces = new int[r.materials.size()][];
			final int[] materialFaceCount = new int[r.materials.size()];
			for (int i = 0; i < count; i++) {
				final int f = faces[i];
				final int material = r.faceMaterial[f];
				if (material < 0) {
					continue;
				}
				int[] list = materialFaces[material];
				if (list == null) {
					list = materialFaces[material] = new int[16];
					materialOrder[materialCount++] = material;
				} else if (materialFaceCount[material] == list.length) {
					list = materialFaces[material] = Arrays.copyOf(list, list.length * 2);
				}
				list[materialFaceCount[material]++] = f;
			}
			final List<Mesh> meshes = new ArrayList<>(materialCount);
			for (int m = 0; m < materialCount; m++) {
				final int material = materialOrder[m];
				meshes.add(mesh(r.materials.get(material), materialFaces[material], materialFaceCount[material]));
			}
			return meshes;
		}

		private Mesh mesh(String material, int[] faces, int count) {
			generation++;
			// groupToObj: renumber in order of first use (all faces, also those triangulate drops later).
			final IntList positionSource = new IntList(count * 2);
			final IntList uvSource = new IntList(count * 2);
			final IntList normalSource = new IntList(count * 2);
			// Triangles: per corner the local vertex, UV and normal index.
			final IntList triV = new IntList(count * 3);
			final IntList triVt = new IntList(count * 3);
			final IntList triVn = new IntList(count * 3);
			final IntList triFlags = new IntList(count);
			final int[] localV = new int[8];
			final int[] localVt = new int[8];
			final int[] localVn = new int[8];
			int[] cornerV = localV;
			int[] cornerVt = localVt;
			int[] cornerVn = localVn;
			for (int i = 0; i < count; i++) {
				final int f = faces[i];
				final int start = r.faceStart[f];
				final int size = r.faceSize[f];
				final byte flags = r.faceFlags[f];
				if (size > cornerV.length) {
					cornerV = new int[size];
					cornerVt = new int[size];
					cornerVn = new int[size];
				}
				for (int j = 0; j < size; j++) {
					cornerV[j] = local(r.cornerV[start + j], vStamp, vLocal, positionSource);
				}
				if ((flags & 1) != 0) {
					for (int j = 0; j < size; j++) {
						cornerVt[j] = local(r.cornerVt[start + j], vtStamp, vtLocal, uvSource);
					}
				}
				if ((flags & 2) != 0) {
					for (int j = 0; j < size; j++) {
						cornerVn[j] = local(r.cornerVn[start + j], vnStamp, vnLocal, normalSource);
					}
				}
				// triangulate: a triangle as it is, a polygon as a fan, fewer corners dropped.
				for (int j = 0; j < size - 2; j++) {
					final int b = j + 1;
					final int c = j + 2;
					triV.add(cornerV[0]);
					triV.add(cornerV[b]);
					triV.add(cornerV[c]);
					triVt.add(cornerVt[0]);
					triVt.add(cornerVt[b]);
					triVt.add(cornerVt[c]);
					triVn.add(cornerVn[0]);
					triVn.add(cornerVn[b]);
					triVn.add(cornerVn[c]);
					triFlags.add(flags);
				}
			}
			final int triangles = triFlags.size;
			// makeTexCoordsUnique, then makeNormalsUnique (on the result of the first).
			unique(positionSource, triV, triVt, triFlags, 1);
			unique(positionSource, triV, triVn, triFlags, 2);
			// makeVertexIndexed: each vertex takes the UV / normal of its last use; unused vertices index 0.
			final int vertexCount = positionSource.size;
			final int[] uvOf = new int[vertexCount];
			final int[] normalOf = new int[vertexCount];
			boolean anyUv = false;
			boolean anyNormal = false;
			for (int t = 0; t < triangles; t++) {
				final int flags = triFlags.data[t];
				for (int k = 3 * t; k < 3 * t + 3; k++) {
					if ((flags & 1) != 0) {
						uvOf[triV.data[k]] = triVt.data[k];
						anyUv = true;
					}
					if ((flags & 2) != 0) {
						normalOf[triV.data[k]] = triVn.data[k];
						anyNormal = true;
					}
				}
			}
			final float[] positions = new float[vertexCount * 3];
			for (int i = 0; i < vertexCount; i++) {
				System.arraycopy(r.v, positionSource.data[i] * 3, positions, i * 3, 3);
			}
			float[] uvs = null;
			if (anyUv) {
				uvs = new float[vertexCount * 2];
				for (int i = 0; i < vertexCount; i++) {
					System.arraycopy(r.vt, uvSource.data[uvOf[i]] * 2, uvs, i * 2, 2);
				}
			}
			float[] normals = null;
			if (anyNormal) {
				normals = new float[vertexCount * 3];
				for (int i = 0; i < vertexCount; i++) {
					System.arraycopy(r.vn, normalSource.data[normalOf[i]] * 3, normals, i * 3, 3);
				}
			}
			return new Mesh(material, vertexCount, positions, normals, uvs, Arrays.copyOf(triV.data, triangles * 3));
		}

		/** Local index of a source index, assigned on first use in this mesh. */
		private int local(int source, int[] stamp, int[] local, IntList order) {
			if (stamp[source] == generation) {
				return local[source];
			}
			stamp[source] = generation;
			local[source] = order.size;
			order.add(source);
			return order.size - 1;
		}

		/**
		 * javagl's {@code makePropertiesUnique}: the first property (UV or normal) seen for a vertex sticks; a corner
		 * with another one gets a fresh copy of the vertex at the end (never reused, and never recorded).
		 */
		private static void unique(IntList positionSource, IntList triV, IntList triProperty, IntList triFlags, int flag) {
			final int vertexCount = positionSource.size;
			final int[] used = new int[vertexCount];
			Arrays.fill(used, -1);
			final int triangles = triFlags.size;
			for (int t = 0; t < triangles; t++) {
				if ((triFlags.data[t] & flag) == 0) {
					continue;
				}
				for (int k = 3 * t; k < 3 * t + 3; k++) {
					final int vertex = triV.data[k];
					final int property = triProperty.data[k];
					if (used[vertex] != -1 && used[vertex] != property) {
						triV.data[k] = positionSource.size;
						positionSource.add(positionSource.data[vertex]);
					} else {
						used[vertex] = property;
					}
				}
			}
		}
	}

	private static final class IntList {

		int[] data;
		int size;

		IntList(int capacity) {
			data = new int[Math.max(4, capacity)];
		}

		void add(int value) {
			if (size == data.length) {
				data = Arrays.copyOf(data, data.length * 2);
			}
			data[size++] = value;
		}
	}
}
