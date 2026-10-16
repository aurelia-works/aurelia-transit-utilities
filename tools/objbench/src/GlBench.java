import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.CGL;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;
import org.mtr.mapping.mapper.OptimizedModel;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * GL half of the benchmark, on a headless CGL context (no window, no Dock icon):
 * 1. what one ShaderManager.reloadShaders() costs (MTR calls it at the start of every model load), and
 * 2. MTR's real OptimizedModel.fromObjModels (with GL upload) vs FastObj + one upload per unique group.
 *
 * Usage: GlBench <minecraft-client.jar> <packDir>...
 */
public final class GlBench {

	public static void main(String[] args) throws Exception {
		createContext();
		// GlStateTracker.capture() needs Minecraft's RenderSystem; only its "protected" flag matters for uploads
		final java.lang.reflect.Field protectedFlag = Class.forName("org.mtr.mapping.render.tool.GlStateTracker").getDeclaredField("isStateProtected");
		protectedFlag.setAccessible(true);
		protectedFlag.set(null, true);
		System.out.println("GL_RENDERER: " + GL33.glGetString(GL33.GL_RENDERER) + ", GL_VERSION: " + GL33.glGetString(GL33.GL_VERSION));

		// ------------------------------------------------ 1. shader reload cost
		final Map<String, String> sources = new HashMap<>();
		try (ZipFile zip = new ZipFile(args[0])) {
			for (final String name : new String[]{"core/rendertype_entity_cutout.vsh", "core/rendertype_entity_cutout.fsh", "core/rendertype_entity_translucent_cull.vsh", "core/rendertype_entity_translucent_cull.fsh", "core/rendertype_beacon_beam.vsh", "core/rendertype_beacon_beam.fsh", "include/light.glsl", "include/fog.glsl"}) {
				sources.put(name, new String(zip.getInputStream(zip.getEntry("assets/minecraft/shaders/" + name)).readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		final String[] programs = {"rendertype_entity_cutout", "rendertype_entity_translucent_cull", "rendertype_beacon_beam"};
		final List<Double> reloadTimes = new ArrayList<>();
		for (int run = 0; run < 12; run++) {
			final long t0 = System.nanoTime();
			final List<Integer> created = new ArrayList<>();
			for (final String program : programs) {
				final int vertex = compile(GL33.GL_VERTEX_SHADER, patchVertex(resolveImports(sources.get("core/" + program + ".vsh"), sources)));
				final int fragment = compile(GL33.GL_FRAGMENT_SHADER, resolveImports(sources.get("core/" + program + ".fsh"), sources));
				final int id = GL33.glCreateProgram();
				GL33.glAttachShader(id, vertex);
				GL33.glAttachShader(id, fragment);
				GL33.glLinkProgram(id);
				if (GL33.glGetProgrami(id, GL33.GL_LINK_STATUS) == 0) {
					throw new IllegalStateException(GL33.glGetProgramInfoLog(id));
				}
				// Minecraft looks up every uniform location after linking
				for (final String uniform : new String[]{"Sampler0", "Sampler1", "Sampler2", "ModelViewMat", "ProjMat", "ColorModulator", "Light0_Direction", "Light1_Direction", "FogStart", "FogEnd", "FogColor", "FogShape", "IViewRotMat"}) {
					GL33.glGetUniformLocation(id, uniform);
				}
				GL33.glUseProgram(id);
				created.add(id);
				created.add(-vertex - 1);
				created.add(-fragment - 1);
			}
			GL33.glUseProgram(0);
			GL33.glFinish();
			reloadTimes.add((System.nanoTime() - t0) / 1e6);
			for (final int id : created) {
				if (id >= 0) {
					GL33.glDeleteProgram(id);
				} else {
					GL33.glDeleteShader(-id - 1);
				}
			}
		}
		System.out.printf("%n== One ShaderManager.reloadShaders() (3 programs: compile + link + uniform lookup)%n  first: %.1f ms, then median of next 11: %.1f ms%n", reloadTimes.get(0), median(reloadTimes.subList(1, reloadTimes.size())));

		// ------------------------------------------------ 2. upload: MTR fromObjModels vs FastObj
		final List<Bench.Job> jobs = new ArrayList<>();
		for (int i = 1; i < args.length; i++) {
			jobs.addAll(Bench.scan(Path.of(args[i])));
		}
		System.out.printf("%n== Build + GL upload per vehicle model (MTR: real OptimizedModel.fromObjModels per list entry; FastObj: one upload per unique group)%n");
		System.out.printf("  %-60s %8s %10s %8s | %9s %8s %8s%n", "model", "MTR VAOs", "MTR ms", "ms/1st", "Fast VAOs", "Fast ms", "speedup");
		double sumMtr = 0, sumFast = 0;
		for (final Bench.Job job : jobs) {
			// warm-up once, then measure the second run
			double mtrMs = 0, fastMs = 0, mtrFirst = 0;
			int mtrVaos = 0, fastVaos = 0;
			for (int run = 0; run < 2; run++) {
				final long t0 = System.nanoTime();
				final List<OptimizedModel> uploaded = mtrUpload(job);
				GL33.glFinish();
				final double ms = (System.nanoTime() - t0) / 1e6;
				mtrVaos = uploaded.stream().mapToInt(GlBench::countParts).sum();
				uploaded.forEach(OptimizedModel::close);
				final long t1 = System.nanoTime();
				final int[] vaos = fastUpload(job);
				GL33.glFinish();
				final double fms = (System.nanoTime() - t1) / 1e6;
				fastVaos = vaos.length / 3;
				for (int i = 0; i < vaos.length; i += 3) {
					GL33.glDeleteVertexArrays(vaos[i]);
					GL33.glDeleteBuffers(vaos[i + 1]);
					GL33.glDeleteBuffers(vaos[i + 2]);
				}
				if (run == 0) {
					mtrFirst = ms;
				} else {
					mtrMs = ms;
					fastMs = fms;
				}
			}
			sumMtr += mtrMs;
			sumFast += fastMs;
			System.out.printf("  %-60s %8d %10.1f %8.1f | %9d %8.1f %7.1fx%n", job.label(), mtrVaos, mtrMs, mtrFirst, fastVaos, fastMs, mtrMs / fastMs);
		}
		System.out.printf("  %-60s %19.1f %8s | %9s %8.1f %7.1fx%n", "SUM", sumMtr, "", "", sumFast, sumMtr / sumFast);
	}

	static int countParts(OptimizedModel model) {
		try {
			final java.lang.reflect.Field field = OptimizedModel.class.getDeclaredField("uploadedParts");
			field.setAccessible(true);
			return ((List<?>) field.get(model)).size();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** VehicleModel.createModel + writeToOptimizedModels, with MTR's real classes and GL upload. */
	static List<OptimizedModel> mtrUpload(Bench.Job job) {
		final String objString = new String(Bench.read(job.packRoot(), Bench.id(Bench.formatWithDefault(job.modelResource(), "obj"))), StandardCharsets.UTF_8);
		final Map<String, OptimizedModel.ObjModel> objModels = OptimizedModel.ObjModel.loadModel(objString, mtl -> new String(Bench.read(job.packRoot(), Bench.id(Bench.sameDir(job.modelResource(), mtl, "mtl"))), StandardCharsets.UTF_8), texture -> Bench.resolveTexture(job, texture), null, true, job.flipV());
		final Map<String, List<OptimizedModel.ObjModel>> lists = new LinkedHashMap<>();
		for (final Bench.PartPlacement placement : Bench.placements(job)) {
			final OptimizedModel.ObjModel objModel = objModels.get(placement.name());
			if (objModel != null) {
				objModel.addTransformation(OptimizedModel.ShaderType.values()[placement.shader()], placement.x(), placement.y(), placement.z(), placement.flipped());
				lists.computeIfAbsent(placement.map() + "/" + placement.condition(), k -> new ArrayList<>()).add(objModel);
			}
		}
		final List<OptimizedModel> result = new ArrayList<>();
		lists.values().forEach(list -> result.add(OptimizedModel.fromObjModels(list)));
		return result;
	}

	/** FastObj build + one VBO/IBO/VAO per final mesh, same 24-byte layout. Returns {vao, vbo, ibo}... */
	static int[] fastUpload(Bench.Job job) {
		final Bench.FastResult ignored = null;
		final Map<String, List<FastObj.FinalMesh>> finals = Bench.buildFast(job);
		final List<Integer> ids = new ArrayList<>();
		for (final List<FastObj.FinalMesh> meshes : finals.values()) {
			for (final FastObj.FinalMesh mesh : meshes) {
				final int vao = GL33.glGenVertexArrays();
				GL33.glBindVertexArray(vao);
				final int vbo = GL33.glGenBuffers();
				GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
				final ByteBuffer vertices = org.lwjgl.system.MemoryUtil.memAlloc(mesh.vertexBytes.length);
				vertices.put(mesh.vertexBytes).flip();
				GL33.glBufferData(GL33.GL_ARRAY_BUFFER, vertices, GL33.GL_STATIC_DRAW);
				org.lwjgl.system.MemoryUtil.memFree(vertices);
				GL33.glEnableVertexAttribArray(0);
				GL33.glVertexAttribPointer(0, 3, GL33.GL_FLOAT, false, 24, 0);
				GL33.glEnableVertexAttribArray(1);
				GL33.glVertexAttribPointer(1, 2, GL33.GL_FLOAT, false, 24, 12);
				GL33.glEnableVertexAttribArray(2);
				GL33.glVertexAttribPointer(2, 3, GL33.GL_BYTE, true, 24, 20);
				final int ibo = GL33.glGenBuffers();
				GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, ibo);
				final IntBuffer indices = org.lwjgl.system.MemoryUtil.memAllocInt(mesh.indices.length);
				indices.put(mesh.indices).flip();
				GL33.glBufferData(GL33.GL_ELEMENT_ARRAY_BUFFER, indices, GL33.GL_STATIC_DRAW);
				org.lwjgl.system.MemoryUtil.memFree(indices);
				GL33.glBindVertexArray(0);
				ids.add(vao);
				ids.add(vbo);
				ids.add(ibo);
			}
		}
		return ids.stream().mapToInt(Integer::intValue).toArray();
	}

	static void createContext() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final IntBuffer attributes = stack.ints(CGL.kCGLPFAAccelerated, CGL.kCGLPFAOpenGLProfile, CGL.kCGLOGLPVersion_3_2_Core, 0);
			final PointerBuffer pixelFormat = stack.mallocPointer(1);
			final IntBuffer count = stack.mallocInt(1);
			check(CGL.CGLChoosePixelFormat(attributes, pixelFormat, count));
			final PointerBuffer context = stack.mallocPointer(1);
			check(CGL.CGLCreateContext(pixelFormat.get(0), 0, context));
			check(CGL.CGLSetCurrentContext(context.get(0)));
			GL.createCapabilities();
		}
	}

	static void check(int error) {
		if (error != 0) {
			throw new IllegalStateException("CGL error " + error);
		}
	}

	static int compile(int type, String source) {
		final int id = GL33.glCreateShader(type);
		GL33.glShaderSource(id, source);
		GL33.glCompileShader(id);
		if (GL33.glGetShaderi(id, GL33.GL_COMPILE_STATUS) == 0) {
			throw new IllegalStateException(GL33.glGetShaderInfoLog(id) + "\n" + source);
		}
		return id;
	}

	static String resolveImports(String source, Map<String, String> sources) {
		final StringBuilder builder = new StringBuilder();
		for (final String line : source.split("\n")) {
			if (line.trim().startsWith("#moj_import")) {
				final String name = line.substring(line.indexOf('<') + 1, line.indexOf('>'));
				builder.append(sources.get("include/" + name).replaceAll("(?m)^#version.*$", "")).append('\n');
			} else {
				builder.append(line).append('\n');
			}
		}
		return builder.toString();
	}

	/** PatchingResourceProvider.patchVertexShaderSource */
	static String patchVertex(String sourceContent) {
		final String[] contentParts = sourceContent.split("void main");
		contentParts[0] = contentParts[0].replace("uniform mat4 ModelViewMat;", "uniform mat4 ModelViewMat;\nin mat4 ModelMat;");
		contentParts[1] = contentParts[1].replaceAll("\\bPosition\\b", "(MODELVIEWMAT * ModelMat * vec4(Position, 1.0)).xyz").replaceAll("\\bNormal\\b", "normalize(mat3(MODELVIEWMAT * ModelMat) * Normal)").replace("ModelViewMat", "mat4(1.0)").replace("MODELVIEWMAT", "ModelViewMat");
		return contentParts[0] + "void main" + contentParts[1];
	}

	static double median(List<Double> values) {
		final List<Double> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		return sorted.get(sorted.size() / 2);
	}
}
