import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prototype of a faster OBJ path for MTR 4 vehicle models. Produces the same vertex/index data that
 * MTR 4.0.5 uploads (ObjModelLoader + ObjModel + RawMesh.generateNormals/distinct/upload), but parses
 * bytes directly into primitive arrays and fuses normal generation with de-duplication.
 */
public final class FastObj {

	// ---------------------------------------------------------------- parsed file

	public static final class Parsed {
		float[] v = new float[3 * 1024];
		int vCount;
		float[] vt = new float[2 * 1024];
		int vtCount;
		float[] vn = new float[3 * 1024];
		int vnCount;
		// faces
		int[] faceStart = new int[1024];
		int[] faceSize = new int[1024];
		int[] faceGroupSet = new int[1024];
		int[] faceMaterial = new int[1024];
		int faceCount;
		// corners: v, vt, vn (vt / vn = -1 when absent)
		int[] corners = new int[3 * 4096];
		int cornerCount;
		final List<String[]> groupSets = new ArrayList<>();
		final List<String> materials = new ArrayList<>();
		final List<String> mtlFileNames = new ArrayList<>();
	}

	public static Parsed parse(byte[] data) {
		final Parsed p = new Parsed();
		final Map<String, Integer> materialIds = new HashMap<>();
		final Map<String, Integer> groupSetIds = new HashMap<>();
		p.groupSets.add(new String[]{"default"});
		groupSetIds.put("default", 0);
		int activeGroupSet = 0;
		int activeMaterial = -1;
		String groupOrObject = "";
		final int length = data.length;
		int pos = 0;
		final float[] tmp = new float[4];
		while (pos < length) {
			int lineEnd = pos;
			while (lineEnd < length && data[lineEnd] != '\n') {
				lineEnd++;
			}
			final int nextLine = lineEnd + 1;
			int end = lineEnd;
			while (end > pos && (data[end - 1] == '\r' || data[end - 1] == ' ' || data[end - 1] == '\t')) {
				end--;
			}
			int start = pos;
			while (start < end && (data[start] == ' ' || data[start] == '\t')) {
				start++;
			}
			if (start < end && data[end - 1] == '\\') {
				// Line continuation is rare: fall back to building the joined line.
				final StringBuilder joined = new StringBuilder(new String(data, start, end - start, StandardCharsets.US_ASCII));
				int cursor = nextLine;
				while (joined.length() > 0 && joined.charAt(joined.length() - 1) == '\\' && cursor < length) {
					joined.setLength(Math.max(0, joined.length() - 2));
					int e = cursor;
					while (e < length && data[e] != '\n') {
						e++;
					}
					joined.append(' ').append(new String(data, cursor, e - cursor, StandardCharsets.US_ASCII).trim());
					cursor = e + 1;
				}
				final byte[] line = joined.toString().getBytes(StandardCharsets.US_ASCII);
				final int[] state = {activeGroupSet, activeMaterial};
				final String[] goState = {groupOrObject};
				handleLine(p, line, 0, line.length, tmp, materialIds, groupSetIds, state, goState);
				activeGroupSet = state[0];
				activeMaterial = state[1];
				groupOrObject = goState[0];
				pos = cursor;
				continue;
			}
			if (start < end) {
				final byte c0 = data[start];
				if (c0 == 'v' && start + 1 < end && data[start + 1] == ' ') {
					final int n = readFloats(data, start + 2, end, tmp);
					ensureV(p);
					p.v[3 * p.vCount] = n > 0 ? tmp[0] : 0;
					p.v[3 * p.vCount + 1] = n > 1 ? tmp[1] : 0;
					p.v[3 * p.vCount + 2] = n > 2 ? tmp[2] : 0;
					p.vCount++;
				} else if (c0 == 'f' && start + 1 < end && (data[start + 1] == ' ' || data[start + 1] == '\t')) {
					addFace(p, data, start + 2, end, activeGroupSet, activeMaterial);
				} else {
					final int[] state = {activeGroupSet, activeMaterial};
					final String[] goState = {groupOrObject};
					handleLine(p, data, start, end, tmp, materialIds, groupSetIds, state, goState);
					activeGroupSet = state[0];
					activeMaterial = state[1];
					groupOrObject = goState[0];
				}
			}
			pos = nextLine;
		}
		return p;
	}

	private static void handleLine(Parsed p, byte[] data, int start, int end, float[] tmp, Map<String, Integer> materialIds, Map<String, Integer> groupSetIds, int[] state, String[] goState) {
		int tokenEnd = start;
		while (tokenEnd < end && data[tokenEnd] != ' ' && data[tokenEnd] != '\t') {
			tokenEnd++;
		}
		final String identifier = new String(data, start, tokenEnd - start, StandardCharsets.US_ASCII).toLowerCase();
		final String line = new String(data, start, end - start, StandardCharsets.US_ASCII);
		switch (identifier) {
			case "v": {
				final int n = readFloats(data, tokenEnd, end, tmp);
				ensureV(p);
				p.v[3 * p.vCount] = n > 0 ? tmp[0] : 0;
				p.v[3 * p.vCount + 1] = n > 1 ? tmp[1] : 0;
				p.v[3 * p.vCount + 2] = n > 2 ? tmp[2] : 0;
				p.vCount++;
				break;
			}
			case "vt": {
				final int n = readFloats(data, tokenEnd, end, tmp);
				if (2 * p.vtCount + 2 > p.vt.length) {
					p.vt = Arrays.copyOf(p.vt, p.vt.length * 2);
				}
				p.vt[2 * p.vtCount] = n > 0 ? tmp[0] : 0;
				p.vt[2 * p.vtCount + 1] = n > 1 ? tmp[1] : 0;
				p.vtCount++;
				break;
			}
			case "vn": {
				final int n = readFloats(data, tokenEnd, end, tmp);
				if (3 * p.vnCount + 3 > p.vn.length) {
					p.vn = Arrays.copyOf(p.vn, p.vn.length * 2);
				}
				p.vn[3 * p.vnCount] = n > 0 ? tmp[0] : 0;
				p.vn[3 * p.vnCount + 1] = n > 1 ? tmp[1] : 0;
				p.vn[3 * p.vnCount + 2] = n > 2 ? tmp[2] : 0;
				p.vnCount++;
				break;
			}
			case "f":
				addFace(p, data, tokenEnd, end, state[0], state[1]);
				break;
			case "mtllib":
				p.mtlFileNames.clear();
				p.mtlFileNames.add(line.substring(6).trim());
				break;
			case "usemtl": {
				final String name = line.substring(6).trim();
				Integer id = materialIds.get(name);
				if (id == null) {
					id = p.materials.size();
					p.materials.add(name);
					materialIds.put(name, id);
				}
				state[1] = id;
				break;
			}
			case "g":
			case "o": {
				// javagl: whichever of g / o appears first is used, the other one is ignored
				if (goState[0].isEmpty() || goState[0].equals(identifier)) {
					final String rest = line.substring(1).trim();
					final String[] names = rest.isEmpty() ? new String[]{"default"} : Arrays.stream(rest.split("\\s+")).distinct().toArray(String[]::new);
					final String key = String.join("\u0000", names);
					Integer id = groupSetIds.get(key);
					if (id == null) {
						id = p.groupSets.size();
						p.groupSets.add(names);
						groupSetIds.put(key, id);
					}
					state[0] = id;
					goState[0] = identifier;
				}
				break;
			}
			default:
				break;
		}
	}

	private static void ensureV(Parsed p) {
		if (3 * p.vCount + 3 > p.v.length) {
			p.v = Arrays.copyOf(p.v, p.v.length * 2);
		}
	}

	private static void addFace(Parsed p, byte[] data, int start, int end, int groupSet, int material) {
		if (p.faceCount == p.faceStart.length) {
			final int n = p.faceCount * 2;
			p.faceStart = Arrays.copyOf(p.faceStart, n);
			p.faceSize = Arrays.copyOf(p.faceSize, n);
			p.faceGroupSet = Arrays.copyOf(p.faceGroupSet, n);
			p.faceMaterial = Arrays.copyOf(p.faceMaterial, n);
		}
		final int faceIndex = p.faceCount;
		p.faceStart[faceIndex] = p.cornerCount;
		int size = 0;
		int i = start;
		while (i < end) {
			while (i < end && (data[i] == ' ' || data[i] == '\t')) {
				i++;
			}
			if (i >= end) {
				break;
			}
			if (3 * p.cornerCount + 3 > p.corners.length) {
				p.corners = Arrays.copyOf(p.corners, p.corners.length * 2);
			}
			final int base = 3 * p.cornerCount;
			p.corners[base + 1] = -1;
			p.corners[base + 2] = -1;
			for (int slot = 0; slot < 3; slot++) {
				boolean negative = false;
				int value = 0;
				boolean any = false;
				if (i < end && data[i] == '-') {
					negative = true;
					i++;
				}
				while (i < end && data[i] >= '0' && data[i] <= '9') {
					value = value * 10 + (data[i] - '0');
					any = true;
					i++;
				}
				if (any) {
					final int count = slot == 0 ? p.vCount : slot == 1 ? p.vtCount : p.vnCount;
					p.corners[base + slot] = negative ? count - value : value - 1;
				}
				if (i < end && data[i] == '/') {
					i++;
				} else {
					break;
				}
			}
			while (i < end && data[i] != ' ' && data[i] != '\t') {
				i++;
			}
			p.cornerCount++;
			size++;
		}
		p.faceSize[faceIndex] = size;
		p.faceGroupSet[faceIndex] = groupSet;
		p.faceMaterial[faceIndex] = material;
		p.faceCount++;
	}

	private static final float[] POW10 = {1f, 1e1f, 1e2f, 1e3f, 1e4f, 1e5f, 1e6f, 1e7f, 1e8f, 1e9f, 1e10f};

	/** Reads up to 4 floats; result identical to Float.parseFloat (exact fast path, else falls back). */
	private static int readFloats(byte[] data, int start, int end, float[] out) {
		int count = 0;
		int i = start;
		while (i < end && count < 4) {
			while (i < end && (data[i] == ' ' || data[i] == '\t')) {
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
			while (i < end && data[i] != ' ' && data[i] != '\t') {
				final byte c = data[i];
				if (c >= '0' && c <= '9') {
					if (mantissa != 0 || c != '0') {
						digits++;
					}
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
			if (simple && mantissa < (1 << 24) && fractionDigits <= 10) {
				final float value = fractionDigits == 0 ? (float) mantissa : (float) mantissa / POW10[fractionDigits];
				out[count++] = negative ? -value : value;
			} else {
				out[count++] = Float.parseFloat(new String(data, tokenStart, i - tokenStart, StandardCharsets.US_ASCII));
			}
		}
		return count;
	}

	// ---------------------------------------------------------------- mesh building

	/** One material of one group, corners already resolved and rotated (ObjModel.loadModel space). */
	public static final class BaseMesh {
		public final String materialName;
		public final int baseShader; // ordinal of OptimizedModel.ShaderType
		public final String texture;
		public final Integer color;
		// per corner: px py pz nx ny nz u v (8 floats), 3 corners per triangle
		float[] corners;
		int cornerCount;

		BaseMesh(String materialName, int baseShader, String texture, Integer color) {
			this.materialName = materialName;
			this.baseShader = baseShader;
			this.texture = texture;
			this.color = color;
			this.corners = new float[8 * 3 * 64];
		}

		void add(float px, float py, float pz, float nx, float ny, float nz, float u, float v) {
			if (8 * cornerCount + 8 > corners.length) {
				corners = Arrays.copyOf(corners, corners.length * 2);
			}
			final int b = 8 * cornerCount;
			corners[b] = px;
			corners[b + 1] = py;
			corners[b + 2] = pz;
			corners[b + 3] = nx;
			corners[b + 4] = ny;
			corners[b + 5] = nz;
			corners[b + 6] = u;
			corners[b + 7] = v;
			cornerCount++;
		}

		public int triangleCount() {
			return cornerCount / 3;
		}
	}

	public interface MaterialResolver {
		/** Returns {texture identifier string, color or null, baseShaderOrdinal, flipV(0/1)} for a usemtl name. */
		Object[] resolve(String materialName);
	}

	/** Builds base meshes per group name; only groups in {@code wanted} (null = all). */
	public static Map<String, List<BaseMesh>> buildGroups(Parsed p, MaterialResolver resolver, boolean flipTextureV, java.util.Set<String> wanted) {
		final org.joml.Vector3f tmp = new org.joml.Vector3f();
		final float angle = (float) Math.toRadians(180.0f);
		final Map<String, List<BaseMesh>> result = new LinkedHashMap<>();
		final Map<String, Map<Integer, BaseMesh>> byGroupMaterial = new HashMap<>();
		final Object[][] resolved = new Object[p.materials.size()][];
		for (int f = 0; f < p.faceCount; f++) {
			final int material = p.faceMaterial[f];
			if (material < 0) {
				continue; // javagl puts faces before the first usemtl in no material group: MTR drops them
			}
			Object[] r = resolved[material];
			if (r == null) {
				r = resolver.resolve(p.materials.get(material));
				resolved[material] = r;
			}
			final boolean flipMaterialV = (Integer) r[3] == 1;
			for (final String group : p.groupSets.get(p.faceGroupSet[f])) {
				if (wanted != null && !wanted.contains(group)) {
					continue;
				}
				final Object[] finalR = r;
				final BaseMesh mesh = byGroupMaterial.computeIfAbsent(group, k -> new LinkedHashMap<>()).computeIfAbsent(material, k -> {
					final BaseMesh m = new BaseMesh(p.materials.get(material), (Integer) finalR[2], (String) finalR[0], (Integer) finalR[1]);
					result.computeIfAbsent(group, k2 -> new ArrayList<>()).add(m);
					return m;
				});
				final int start = p.faceStart[f];
				final int size = p.faceSize[f];
				for (int j = 0; j + 2 < size || (j == 0 && size == 3); j++) {
					emitCorner(p, mesh, start, tmp, angle, flipMaterialV, flipTextureV);
					emitCorner(p, mesh, start + j + 1, tmp, angle, flipMaterialV, flipTextureV);
					emitCorner(p, mesh, start + j + 2, tmp, angle, flipMaterialV, flipTextureV);
					if (size == 3) {
						break;
					}
				}
			}
		}
		return result;
	}

	private static void emitCorner(Parsed p, BaseMesh mesh, int corner, org.joml.Vector3f tmp, float angle, boolean flipMaterialV, boolean flipTextureV) {
		final int vi = p.corners[3 * corner];
		final int ti = p.corners[3 * corner + 1];
		final int ni = p.corners[3 * corner + 2];
		tmp.set(p.v[3 * vi], p.v[3 * vi + 1], p.v[3 * vi + 2]).rotateAxis(angle, 1, 0, 0);
		final float px = tmp.x, py = tmp.y, pz = tmp.z;
		if (ni >= 0) {
			tmp.set(p.vn[3 * ni], p.vn[3 * ni + 1], p.vn[3 * ni + 2]);
		} else {
			tmp.set(0, 0, 0);
		}
		tmp.rotateAxis(angle, 1, 0, 0);
		float u = ti >= 0 ? p.vt[2 * ti] : 0;
		float v = ti >= 0 ? p.vt[2 * ti + 1] : 0;
		if (flipMaterialV) {
			v = 1.0f - v;
		}
		if (flipTextureV) {
			v = 1.0f - v;
		}
		mesh.add(px, py, pz, tmp.x, tmp.y, tmp.z, u, v);
	}

	// ---------------------------------------------------------------- placement + final meshes

	public static final class Placement {
		final int shader;
		final float x, y, z;
		final boolean flipped;

		public Placement(int shader, double x, double y, double z, boolean flipped) {
			this.shader = shader;
			this.x = (float) x;
			this.y = (float) y;
			this.z = (float) z;
			this.flipped = flipped;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Placement && ((Placement) o).shader == shader && Float.floatToIntBits(((Placement) o).x) == Float.floatToIntBits(x) && Float.floatToIntBits(((Placement) o).y) == Float.floatToIntBits(y) && Float.floatToIntBits(((Placement) o).z) == Float.floatToIntBits(z) && ((Placement) o).flipped == flipped;
		}

		@Override
		public int hashCode() {
			return ((shader * 31 + Float.floatToIntBits(x)) * 31 + Float.floatToIntBits(y)) * 31 + Float.floatToIntBits(z) + (flipped ? 1 : 0);
		}
	}

	/** GPU-ready mesh: 24-byte vertices (pos 3f, uv 2f, normal 3b, pad) and triangle indices. */
	public static final class FinalMesh {
		public final int shader;
		public final String texture;
		public final Integer color;
		public byte[] vertexBytes;
		public int vertexCount;
		public int[] indices;
		public int triangleCount;

		FinalMesh(int shader, String texture, Integer color) {
			this.shader = shader;
			this.texture = texture;
			this.color = color;
		}

		public String key() {
			return shader + "|" + texture + "|" + color;
		}
	}

	private static final int CUTOUT = 0;

	/** Applies placements to a group's base meshes, generates flat normals where missing, de-duplicates and packs. */
	public static List<FinalMesh> finish(List<BaseMesh> baseMeshes, java.util.Collection<Placement> placements) {
		final Map<String, Builder> builders = new LinkedHashMap<>();
		final org.joml.Vector3f tmp = new org.joml.Vector3f();
		final float angle = (float) Math.toRadians(180.0f);
		final float[] tri = new float[24];
		for (final Placement placement : placements) {
			for (final BaseMesh base : baseMeshes) {
				final int shader = base.baseShader == CUTOUT ? placement.shader : base.baseShader;
				final String key = shader + "|" + base.texture + "|" + base.color;
				final Builder builder = builders.computeIfAbsent(key, k -> new Builder(new FinalMesh(shader, base.texture, base.color), base.cornerCount));
				final float[] c = base.corners;
				for (int t = 0; t < base.cornerCount; t += 3) {
					for (int k = 0; k < 3; k++) {
						final int b = 8 * (t + k);
						tmp.set(c[b], c[b + 1], c[b + 2]).add(placement.x, placement.y, placement.z);
						if (placement.flipped) {
							tmp.rotateAxis(angle, 0, 1, 0);
						}
						tri[8 * k] = tmp.x;
						tri[8 * k + 1] = tmp.y;
						tri[8 * k + 2] = tmp.z;
						tmp.set(c[b + 3], c[b + 4], c[b + 5]);
						if (placement.flipped) {
							tmp.rotateAxis(angle, 0, 1, 0);
						}
						tri[8 * k + 3] = tmp.x;
						tri[8 * k + 4] = tmp.y;
						tri[8 * k + 5] = tmp.z;
						tri[8 * k + 6] = c[b + 6];
						tri[8 * k + 7] = c[b + 7];
					}
					builder.addTriangle(tri, tmp);
				}
			}
		}
		final List<FinalMesh> result = new ArrayList<>();
		builders.values().forEach(builder -> result.add(builder.pack(tmp)));
		return result;
	}

	private static final class Builder {
		final FinalMesh mesh;
		// distinct vertices: 8 floats each
		float[] vertices;
		int vertexCount;
		int[] vertexTable; // open addressing: index+1
		int[] triangles;
		int triangleCount;
		long[] triangleTable; // open addressing on packed (a,b,c) – stores index+1 into triangles

		Builder(FinalMesh mesh, int expectedCorners) {
			this.mesh = mesh;
			final int capacity = Math.max(64, expectedCorners);
			vertices = new float[8 * capacity];
			vertexTable = new int[tableSize(capacity)];
			triangles = new int[capacity];
			triangleTable = new long[tableSize(capacity / 3 + 1)];
		}

		private static int tableSize(int n) {
			return Integer.highestOneBit(Math.max(16, n * 2)) << 1;
		}

		void addTriangle(float[] tri, org.joml.Vector3f tmp) {
			// RawMesh.generateNormals: float differences widened to double
			final double ax = tri[8] - tri[0];
			final double ay = tri[9] - tri[1];
			final double az = tri[10] - tri[2];
			final double bx = tri[16] - tri[0];
			final double by = tri[17] - tri[1];
			final double bz = tri[18] - tri[2];
			final double nx = ay * bz - az * by;
			final double ny = az * bx - ax * bz;
			final double nz = ax * by - ay * bx;
			final double t1 = nx * nx + ny * ny + nz * nz;
			final float mx, my, mz;
			if (t1 != 0) {
				final double t2 = 1.0 / Math.sqrt(t1);
				mx = (float) (nx * t2);
				my = (float) (ny * t2);
				mz = (float) (nz * t2);
			} else {
				mx = 0;
				my = 1;
				mz = 0;
			}
			final int a = vertex(tri, 0, mx, my, mz);
			final int b = vertex(tri, 8, mx, my, mz);
			final int c = vertex(tri, 16, mx, my, mz);
			// distinct(): faces are a HashSet of index triples
			final long hash = ((a * 0x9E3779B1L) ^ (b * 0x85EBCA77L) ^ (c * 0xC2B2AE3DL));
			int mask = triangleTable.length - 1;
			int slot = (int) (hash ^ (hash >>> 29)) & mask;
			while (triangleTable[slot] != 0) {
				final int t = (int) triangleTable[slot] - 1;
				if (triangles[3 * t] == a && triangles[3 * t + 1] == b && triangles[3 * t + 2] == c) {
					return;
				}
				slot = (slot + 1) & mask;
			}
			if (3 * triangleCount + 3 > triangles.length) {
				triangles = Arrays.copyOf(triangles, triangles.length * 2);
			}
			triangles[3 * triangleCount] = a;
			triangles[3 * triangleCount + 1] = b;
			triangles[3 * triangleCount + 2] = c;
			triangleCount++;
			triangleTable[slot] = triangleCount;
			if (triangleCount * 2 > triangleTable.length) {
				rehashTriangles();
			}
		}

		private void rehashTriangles() {
			triangleTable = new long[triangleTable.length * 2];
			final int mask = triangleTable.length - 1;
			for (int t = 0; t < triangleCount; t++) {
				final long hash = ((triangles[3 * t] * 0x9E3779B1L) ^ (triangles[3 * t + 1] * 0x85EBCA77L) ^ (triangles[3 * t + 2] * 0xC2B2AE3DL));
				int slot = (int) (hash ^ (hash >>> 29)) & mask;
				while (triangleTable[slot] != 0) {
					slot = (slot + 1) & mask;
				}
				triangleTable[slot] = t + 1;
			}
		}

		private int vertex(float[] tri, int o, float mx, float my, float mz) {
			float nx = tri[o + 3], ny = tri[o + 4], nz = tri[o + 5];
			if (nx == 0 && ny == 0 && nz == 0) {
				nx = mx;
				ny = my;
				nz = mz;
			}
			final int b0 = Float.floatToIntBits(tri[o]), b1 = Float.floatToIntBits(tri[o + 1]), b2 = Float.floatToIntBits(tri[o + 2]);
			final int b3 = Float.floatToIntBits(nx), b4 = Float.floatToIntBits(ny), b5 = Float.floatToIntBits(nz);
			final int b6 = Float.floatToIntBits(tri[o + 6]), b7 = Float.floatToIntBits(tri[o + 7]);
			int h = b0;
			h = h * 0x01000193 ^ b1;
			h = h * 0x01000193 ^ b2;
			h = h * 0x01000193 ^ b3;
			h = h * 0x01000193 ^ b4;
			h = h * 0x01000193 ^ b5;
			h = h * 0x01000193 ^ b6;
			h = h * 0x01000193 ^ b7;
			h ^= h >>> 16;
			final int mask = vertexTable.length - 1;
			int slot = h & mask;
			while (vertexTable[slot] != 0) {
				final int i = vertexTable[slot] - 1;
				final int vb = 8 * i;
				if (Float.floatToIntBits(vertices[vb]) == b0 && Float.floatToIntBits(vertices[vb + 1]) == b1 && Float.floatToIntBits(vertices[vb + 2]) == b2 && Float.floatToIntBits(vertices[vb + 3]) == b3 && Float.floatToIntBits(vertices[vb + 4]) == b4 && Float.floatToIntBits(vertices[vb + 5]) == b5 && Float.floatToIntBits(vertices[vb + 6]) == b6 && Float.floatToIntBits(vertices[vb + 7]) == b7) {
					return i;
				}
				slot = (slot + 1) & mask;
			}
			if (8 * vertexCount + 8 > vertices.length) {
				vertices = Arrays.copyOf(vertices, vertices.length * 2);
			}
			final int vb = 8 * vertexCount;
			vertices[vb] = tri[o];
			vertices[vb + 1] = tri[o + 1];
			vertices[vb + 2] = tri[o + 2];
			vertices[vb + 3] = nx;
			vertices[vb + 4] = ny;
			vertices[vb + 5] = nz;
			vertices[vb + 6] = tri[o + 6];
			vertices[vb + 7] = tri[o + 7];
			vertexCount++;
			vertexTable[slot] = vertexCount;
			if (vertexCount * 2 > vertexTable.length) {
				vertexTable = new int[vertexTable.length * 2];
				final int m = vertexTable.length - 1;
				for (int i = 0; i < vertexCount; i++) {
					final int ib = 8 * i;
					int hh = Float.floatToIntBits(vertices[ib]);
					for (int k = 1; k < 8; k++) {
						hh = hh * 0x01000193 ^ Float.floatToIntBits(vertices[ib + k]);
					}
					hh ^= hh >>> 16;
					int s = hh & m;
					while (vertexTable[s] != 0) {
						s = (s + 1) & m;
					}
					vertexTable[s] = i + 1;
				}
			}
			return vertexCount - 1;
		}

		FinalMesh pack(org.joml.Vector3f tmp) {
			final java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(vertexCount * 24).order(java.nio.ByteOrder.nativeOrder());
			for (int i = 0; i < vertexCount; i++) {
				final int b = 8 * i;
				buffer.putFloat(vertices[b]).putFloat(vertices[b + 1]).putFloat(vertices[b + 2]);
				buffer.putFloat(vertices[b + 6]).putFloat(vertices[b + 7]);
				tmp.set(vertices[b + 3], vertices[b + 4], vertices[b + 5]).normalize();
				buffer.put((byte) (tmp.x * 127.0f)).put((byte) (tmp.y * 127.0f)).put((byte) (tmp.z * 127.0f));
				buffer.put((byte) 0);
			}
			mesh.vertexBytes = buffer.array();
			mesh.vertexCount = vertexCount;
			mesh.indices = Arrays.copyOf(triangles, 3 * triangleCount);
			mesh.triangleCount = triangleCount;
			return mesh;
		}
	}
}
