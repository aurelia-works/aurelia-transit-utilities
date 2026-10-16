import com.aureliatransit.utilities.perf.FastObjLoader;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.obj.AtlasManager;
import org.mtr.mapping.render.obj.ObjModelLoader;
import org.mtr.mapping.render.vertex.Vertex;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * ATU's OBJ reader ({@code FastObjLoader}) against MTR 4.0.5's {@code ObjModelLoader.loadModel} (javagl), on
 * hand-written edge cases and on real packs: every RawMesh must match exactly (material, vertex fields by their bits,
 * faces, order). Files ATU's reader declines must be declined on purpose (the "fallback" fixtures).
 * Usage: ParseBench [packDir...] [--runs N]
 */
public final class ParseBench {

	static final Identifier WHITE = new Identifier("mtr", "textures/block/white.png");

	public static void main(String[] args) throws Exception {
		int runs = 5;
		final List<Path> packs = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--runs")) {
				runs = Integer.parseInt(args[++i]);
			} else {
				packs.add(Path.of(args[i]));
			}
		}
		int failures = 0;
		System.out.println("== Fixtures");
		for (final Map.Entry<String, String[]> fixture : fixtures().entrySet()) {
			final String obj = fixture.getValue()[0];
			final String mtl = fixture.getValue()[1];
			final boolean expectFallback = fixture.getKey().startsWith("fallback");
			for (final boolean split : new boolean[]{true, false}) {
				final Function<String, String> mtlResolver = name -> mtl;
				final Map<String, List<RawMesh>> mtr = ObjModelLoader.loadModel(obj, mtlResolver, ParseBench::texture, new AtlasManager(), split);
				final Map<String, List<RawMesh>> fast = FastObjLoader.load(obj, mtlResolver, ParseBench::texture, new AtlasManager(), split);
				final String verdict;
				if (fast == null) {
					verdict = expectFallback ? "ok (left to MTR)" : "FAIL: declined";
				} else if (expectFallback) {
					verdict = "FAIL: should have been left to MTR, " + compare(mtr, fast);
				} else {
					verdict = compare(mtr, fast);
				}
				if (verdict.startsWith("FAIL")) {
					failures++;
				}
				System.out.printf("  %-34s split=%-5s %s%n", fixture.getKey(), split, verdict);
			}
		}
		final List<Bench.Job> jobs = new ArrayList<>();
		for (final Path pack : packs) {
			jobs.addAll(Bench.scan(pack));
		}
		if (!jobs.isEmpty()) {
			System.out.printf("%n== Packs: %d OBJ models, median of %d runs after 2 warm-ups%n", jobs.size(), runs);
			System.out.printf("  %-70s %10s %10s %8s  %s%n", "model", "javagl ms", "ATU ms", "speedup", "result");
			double sumMtr = 0;
			double sumFast = 0;
			final java.util.Set<String> seen = new java.util.HashSet<>();
			for (final Bench.Job job : jobs) {
				if (!seen.add(job.packRoot() + job.modelResource() + job.textureResource())) {
					continue;
				}
				final String obj = new String(Bench.read(job.packRoot(), Bench.id(Bench.formatWithDefault(job.modelResource(), "obj"))), StandardCharsets.UTF_8);
				final Function<String, String> mtlResolver = name -> new String(Bench.read(job.packRoot(), Bench.id(Bench.sameDir(job.modelResource(), name, "mtl"))), StandardCharsets.UTF_8);
				final Function<String, Identifier> textureResolver = name -> Bench.resolveTexture(job, name);
				final Map<String, List<RawMesh>> mtr = ObjModelLoader.loadModel(obj, mtlResolver, textureResolver, new AtlasManager(), true);
				final Map<String, List<RawMesh>> fast = FastObjLoader.load(obj, mtlResolver, textureResolver, new AtlasManager(), true);
				final String verdict = fast == null ? "FAIL: declined" : compare(mtr, fast);
				if (verdict.startsWith("FAIL")) {
					failures++;
				}
				final double[] mtrTimes = new double[runs];
				final double[] fastTimes = new double[runs];
				for (int r = -2; r < runs; r++) {
					long t0 = System.nanoTime();
					ObjModelLoader.loadModel(obj, mtlResolver, textureResolver, new AtlasManager(), true);
					long t1 = System.nanoTime();
					FastObjLoader.load(obj, mtlResolver, textureResolver, new AtlasManager(), true);
					long t2 = System.nanoTime();
					if (r >= 0) {
						mtrTimes[r] = (t1 - t0) / 1e6;
						fastTimes[r] = (t2 - t1) / 1e6;
					}
				}
				Arrays.sort(mtrTimes);
				Arrays.sort(fastTimes);
				final double m = mtrTimes[runs / 2];
				final double f = fastTimes[runs / 2];
				sumMtr += m;
				sumFast += f;
				System.out.printf("  %-70s %10.1f %10.1f %7.1fx  %s%n", job.modelResource(), m, f, m / f, verdict);
			}
			System.out.printf("  %-70s %10.1f %10.1f %7.1fx%n", "SUM", sumMtr, sumFast, sumMtr / sumFast);
		}
		System.out.println(failures == 0 ? "\nRESULT PASS" : "\nRESULT FAIL (" + failures + ")");
	}

	static Identifier texture(String name) {
		return name == null || name.isEmpty() ? WHITE : new Identifier("atu", "tex/" + name.toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
	}

	static String compare(Map<String, List<RawMesh>> mtr, Map<String, List<RawMesh>> fast) {
		if (!mtr.keySet().equals(fast.keySet())) {
			return "FAIL: groups " + new TreeSet<>(mtr.keySet()) + " vs " + new TreeSet<>(fast.keySet());
		}
		int meshes = 0;
		int vertices = 0;
		int faces = 0;
		for (final String group : mtr.keySet()) {
			final List<RawMesh> a = mtr.get(group);
			final List<RawMesh> b = fast.get(group);
			if (a.size() != b.size()) {
				return "FAIL: group " + group + " has " + a.size() + " vs " + b.size() + " meshes";
			}
			for (int i = 0; i < a.size(); i++) {
				final RawMesh x = a.get(i);
				final RawMesh y = b.get(i);
				final String where = "group " + group + " mesh " + i;
				if (!x.materialProperties.equals(y.materialProperties) || x.materialProperties.shaderType != y.materialProperties.shaderType || !String.valueOf(x.materialProperties.getTexture()).equals(String.valueOf(y.materialProperties.getTexture()))) {
					return "FAIL: " + where + " material differs";
				}
				if (x.vertices.size() != y.vertices.size()) {
					return "FAIL: " + where + " vertices " + x.vertices.size() + " vs " + y.vertices.size();
				}
				for (int v = 0; v < x.vertices.size(); v++) {
					if (!sameVertex(x.vertices.get(v), y.vertices.get(v))) {
						return "FAIL: " + where + " vertex " + v + " " + describe(x.vertices.get(v)) + " vs " + describe(y.vertices.get(v));
					}
				}
				if (x.faces.size() != y.faces.size()) {
					return "FAIL: " + where + " faces " + x.faces.size() + " vs " + y.faces.size();
				}
				for (int f = 0; f < x.faces.size(); f++) {
					final Face p = x.faces.get(f);
					final Face q = y.faces.get(f);
					if (!Arrays.equals(p.vertices, q.vertices)) {
						return "FAIL: " + where + " face " + f + " " + Arrays.toString(p.vertices) + " vs " + Arrays.toString(q.vertices);
					}
				}
				meshes++;
				vertices += x.vertices.size();
				faces += x.faces.size();
			}
		}
		return String.format("IDENTICAL (%d groups, %d meshes, %d vertices, %d triangles)", mtr.size(), meshes, vertices, faces);
	}

	static boolean sameVertex(Vertex a, Vertex b) {
		return bits(a.position.getX()) == bits(b.position.getX()) && bits(a.position.getY()) == bits(b.position.getY()) && bits(a.position.getZ()) == bits(b.position.getZ())
				&& bits(a.normal.getX()) == bits(b.normal.getX()) && bits(a.normal.getY()) == bits(b.normal.getY()) && bits(a.normal.getZ()) == bits(b.normal.getZ())
				&& bits(a.u) == bits(b.u) && bits(a.v) == bits(b.v) && a.color == b.color && a.light == b.light;
	}

	static int bits(float value) {
		return Float.floatToRawIntBits(value);
	}

	static String describe(Vertex v) {
		return String.format("(%s %s %s | %s %s %s | %s %s)", v.position.getX(), v.position.getY(), v.position.getZ(), v.normal.getX(), v.normal.getY(), v.normal.getZ(), v.u, v.v);
	}

	/** name -> {obj, mtl}. Names starting with "fallback" must be declined by ATU's reader. */
	static Map<String, String[]> fixtures() {
		final Map<String, String[]> f = new LinkedHashMap<>();
		final String quad = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nvn 0 0 1\n";
		f.put("triangle v/vt/vn", new String[]{quad + "usemtl a\nf 1/1/1 2/2/1 3/3/1\n", ""});
		f.put("quad and pentagon", new String[]{quad + "v 2 2 2\nusemtl a\nf 1/1/1 2/2/1 3/3/1 4/4/1\nf 1/1/1 2/2/1 3/3/1 4/4/1 5/1/1\n", ""});
		f.put("v only", new String[]{quad + "usemtl a\nf 1 2 3\nf 3 4 1\n", ""});
		f.put("v//vn", new String[]{quad + "usemtl a\nf 1//1 2//1 3//1\n", ""});
		f.put("v/vt", new String[]{quad + "usemtl a\nf 1/1 2/2 3/3\nf 3/3 4/4 1/1\n", ""});
		f.put("mixed forms across faces", new String[]{quad + "usemtl a\nf 1/1 2/2 3/3\nf 3 4 1\nf 1//1 3//1 4//1\nf 2/2/1 3/1/1 4/4/1\n", ""});
		f.put("negative indices", new String[]{quad + "usemtl a\nf -4/-4/-1 -3/-3/-1 -2/-2/-1\nv 5 5 5\nf -1 -2 -3\n", ""});
		f.put("forward references", new String[]{"usemtl a\nf 1 2 3\nv 0 0 0\nv 1 0 0\nv 0 1 0\n", ""});
		f.put("groups g", new String[]{quad + "g one\nusemtl a\nf 1 2 3\ng two\nf 2 3 4\nusemtl b\nf 1 3 4\ng one\nf 4 1 2\n", ""});
		f.put("g first then o ignored", new String[]{quad + "g one\nusemtl a\nf 1 2 3\no two\nf 2 3 4\ng three\nf 1 3 4\n", ""});
		f.put("o first then g ignored", new String[]{quad + "o one\nusemtl a\nf 1 2 3\ng two\nf 2 3 4\no three\nf 1 3 4\n", ""});
		f.put("multi-name groups", new String[]{quad + "g a b a\nusemtl m\nf 1 2 3\ng b\nf 2 3 4\ng\nf 1 3 4\n", ""});
		f.put("faces before usemtl", new String[]{quad + "f 1 2 3\ng x\nf 2 3 4\nusemtl m\nf 1 3 4\ng y\nf 1 2 4\n", ""});
		f.put("material switches across groups", new String[]{quad + "g a\nusemtl m1\nf 1 2 3\ng b\nusemtl m2\nf 2 3 4\ng a\nf 1 3 4\nusemtl m1\nf 1 2 4\ng b\nf 1 2 3\n", ""});
		f.put("uv seams", new String[]{quad + "usemtl a\nf 1/1 2/2 3/3\nf 1/2 3/3 4/4\nf 1/3 2/2 4/4\nf 1/1 2/2 3/3\nf 1/2 2/1 3/4\n", ""});
		f.put("normal seams", new String[]{quad + "vn 1 0 0\nvn 0 1 0\nusemtl a\nf 1/1/1 2/2/1 3/3/1\nf 1/1/2 3/3/2 4/4/3\nf 1/1/3 2/2/2 3/3/1\n", ""});
		f.put("uv and normal seams", new String[]{quad + "vn 1 0 0\nusemtl a\nf 1/1/1 2/2/1 3/3/1\nf 1/2/2 3/3/1 4/4/2\nf 1/2/1 2/2/2 4/4/1\n", ""});
		f.put("degenerate faces", new String[]{quad + "v 9 9 9\nusemtl a\nf 1 2\nf 5\nf 1 2 3\ng only_degenerate\nf 4 5\n", ""});
		f.put("line endings and spacing", new String[]{"v 0 0 0\r\nv 1 0 0\r\n  v   0 1 0  \r\n\r\n# comment\rusemtl  a b \r\nf  1   2  3 \r\n", ""});
		f.put("uppercase keywords", new String[]{"V 0 0 0\nV 1 0 0\nV 0 1 0\nVT 0 0\nVN 0 0 1\nUSEMTL a\nG grp\nF 1/1/1 2/1/1 3/1/1\n", ""});
		f.put("material options", new String[]{quad + "usemtl tex.png#light,flipv=1\nf 1/1 2/2 3/3\nusemtl _#interior\nf 1/1 3/3 4/4\nusemtl x#always_on_light,flipv\nf 2/2 3/3 4/4\nusemtl y#exteriortranslucent,FLIPV\nf 1/1 2/2 4/4\n", ""});
		f.put("mtl colours and textures", new String[]{quad + "mtllib first.mtl\nmtllib m.mtl\nusemtl red\nf 1/1 2/2 3/3\nusemtl glass\nf 1/1 3/3 4/4\nusemtl missing\nf 2/2 3/3 4/4\nusemtl plain\nf 1/1 2/2 4/4\n",
				"newmtl red\nKd 1 0 0\nnewmtl glass\nKd 0.5 0.5 1\nd 0.25\nmap_Kd textures/glass.png\nnewmtl plain\n"});
		f.put("floats", new String[]{"v 1e-3 -0 +1.5\nv .5 5. -0.000001\nv 123456789.123 0.1234567890123 -1.0E2\nv 16777217 3.4028235e38 1.4e-45\nvt 0.333333333333333333 0.1\nvt 0.30000000000000004 1\nusemtl a\nf 1/1 2/2 3/1\nf 2/2 3/1 4/2\n", ""});
		f.put("four component vertices", new String[]{"v 0 0 0 1\nv 1 0 0 1\nv 0 1 0 1 extra\nvt 0 0 0\nvn 0 0 1 0\nusemtl a\nf 1/1/1 2/1/1 3/1/1\n", ""});
		f.put("unused data", new String[]{quad + "v 7 7 7\nvt 0.5 0.5\nvn 1 0 0\ns 1\nl 1 2\nusemtl a\nf 4/4/1 3/3/1 2/2/1\n", ""});
		f.put("non-ascii names", new String[]{quad + "g wagené\nusemtl mät\nf 1 2 3\n", ""});
		f.put("empty usemtl and default group", new String[]{quad + "usemtl\nf 1 2 3\ng default\nf 2 3 4\n", ""});
		f.put("fallback continuation", new String[]{quad + "usemtl a\nf 1 2 \\\n 3\n", ""});
		f.put("fallback tab", new String[]{quad + "usemtl a\nf 1\t2 3\n", ""});
		f.put("fallback mixed corner forms", new String[]{quad + "usemtl a\nf 1/1 2 3/3\n", ""});
		f.put("fallback index zero", new String[]{quad + "usemtl a\nf 0 1 2\n", ""});
		f.put("fallback out of range", new String[]{quad + "usemtl a\nf 1 2 9\n", ""});
		f.put("fallback bad number", new String[]{"v 0 0 x\nv 1 0 0\nv 0 1 0\nusemtl a\nf 1 2 3\n", ""});
		f.put("fallback short vertex", new String[]{"v 0 0\nv 1 0 0\nv 0 1 0\nusemtl a\nf 1 2 3\n", ""});
		f.put("fallback trailing slash", new String[]{quad + "usemtl a\nf 1/ 2/ 3/\n", ""});
		return f;
	}
}
