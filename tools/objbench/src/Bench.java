import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mtr.libraries.de.javagl.obj.FloatTuple;
import org.mtr.libraries.de.javagl.obj.Mtl;
import org.mtr.libraries.de.javagl.obj.MtlReader;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.model.RawModel;
import org.mtr.mapping.render.vertex.Vertex;

import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Headless benchmark: MTR 4.0.5's own OBJ vehicle path (real MTR classes, everything up to the GL
 * upload) against the FastObj prototype, on real resource packs.
 *
 * Usage: Bench <packDir>... [--runs N] [--threads N]
 */
public final class Bench {

	static final Map<String, Integer> STAGE_TO_SHADER = Map.of("LIGHT", 4, "ALWAYS_ON_LIGHT", 5, "INTERIOR", 2, "INTERIOR_TRANSLUCENT", 3, "EXTERIOR", 0);
	static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

	record Job(Path packRoot, String vehicleId, String modelResource, String textureResource, boolean flipV, JsonObject properties, JsonObject positions) {
		String label() {
			return modelResource.substring(modelResource.lastIndexOf('/') + 1) + " (" + vehicleId + ")";
		}
	}

	/** One addObjModelPosition call, as MTR's ModelPropertiesPart.writeCache would make it. */
	record PartPlacement(String name, String map, String condition, int shader, double x, double y, double z, boolean flipped) {
	}

	public static void main(String[] args) throws Exception {
		int runs = 5;
		int threads = Runtime.getRuntime().availableProcessors() / 2;
		boolean coldOnly = false;
		final List<Path> packs = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--runs" -> runs = Integer.parseInt(args[++i]);
				case "--threads" -> threads = Integer.parseInt(args[++i]);
				case "--cold" -> coldOnly = true;
				case "--draws" -> System.setProperty("drawsOnly", "true");
				default -> packs.add(Path.of(args[i]));
			}
		}
		final List<Job> jobs = new ArrayList<>();
		for (final Path pack : packs) {
			jobs.addAll(scan(pack));
		}
		System.out.printf("JVM %s, %d cores, max heap %d MB, %d OBJ vehicle models%n", System.getProperty("java.version"), Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() >> 20, jobs.size());
		if (System.getProperty("drawsOnly") != null) {
			drawReport(jobs);
			return;
		}

		// ---------------- correctness: identical GPU data
		System.out.println("\n== Equivalence (MTR 4.0.5 upload data vs FastObj, per model, bitwise on sorted triangles)");
		for (final Job job : jobs) {
			final MtrResult mtr = runMtr(job, true);
			final FastResult fast = runFast(job);
			final String verdict = compare(mtr.firstUploadPerGroup, fast.meshesPerGroup);
			System.out.printf("  %-60s %s%n", job.label(), verdict);
		}

		// ---------------- cold (first run in this JVM is done above; report a fresh-process number separately via --cold)
		if (coldOnly) {
			return;
		}

		// ---------------- timing
		System.out.printf("%n== Timing, median of %d runs after 2 warm-ups (single thread)%n", runs);
		System.out.printf("  %-60s %9s %9s %9s %10s %9s | %9s %10s %8s%n", "model", "parse ms", "place ms", "build ms", "MTR total", "alloc MB", "Fast ms", "alloc MB", "speedup");
		double sumMtr = 0, sumFast = 0, sumSecondDistinct = 0;
		for (final Job job : jobs) {
			for (int w = 0; w < 2; w++) {
				runMtr(job, false);
				runFast(job);
			}
			final double[][] mtrRuns = new double[runs][];
			final double[][] fastRuns = new double[runs][];
			for (int r = 0; r < runs; r++) {
				final MtrResult m = runMtr(job, false);
				mtrRuns[r] = new double[]{m.parseMs, m.placeMs, m.buildMs, m.parseMs + m.placeMs + m.buildMs, m.allocBytes / 1048576.0, m.secondDistinctMs};
				final FastResult f = runFast(job);
				fastRuns[r] = new double[]{f.totalMs, f.allocBytes / 1048576.0};
			}
			final double mtrTotal = median(mtrRuns, 3);
			final double fastTotal = median(fastRuns, 0);
			sumMtr += mtrTotal;
			sumSecondDistinct += median(mtrRuns, 5);
			sumFast += fastTotal;
			System.out.printf("  %-60s %9.1f %9.1f %9.1f %10.1f %9.0f | %9.1f %10.0f %7.1fx%n", job.label(), median(mtrRuns, 0), median(mtrRuns, 1), median(mtrRuns, 2), mtrTotal, median(mtrRuns, 4), fastTotal, median(fastRuns, 1), mtrTotal / fastTotal);
		}
		System.out.printf("  %-60s %41.1f | %9.1f %18.1fx%n", "SUM", sumMtr, sumFast, sumMtr / sumFast);
		System.out.printf("  of which the second distinct() inside upload (what JCM 2.2.5 skips): %.1f ms; MTR+JCM ~ %.1f ms -> FastObj %.1fx%n", sumSecondDistinct, sumMtr - sumSecondDistinct, (sumMtr - sumSecondDistinct) / sumFast);
		if (System.getProperty("timingOnly") != null) {
			return;
		}

		// ---------------- geometry multiplication (what reaches the GPU)
		System.out.println("\n== Geometry MTR uploads vs unique geometry (per vehicle model)");
		System.out.printf("  %-60s %8s %12s %12s %10s %12s%n", "model", "uploads", "MTR tris", "unique tris", "factor", "MTR VBO MB");
		for (final Job job : jobs) {
			final MtrResult m = runMtr(job, false);
			System.out.printf("  %-60s %8d %12d %12d %9.1fx %12.1f%n", job.label(), m.uploads, m.uploadedTriangles, m.uniqueTriangles, m.uniqueTriangles == 0 ? 0 : (double) m.uploadedTriangles / m.uniqueTriangles, m.uploadedBytes / 1048576.0);
		}

		// ---------------- retained heap
		System.out.println("\n== Heap kept alive after loading all models (MTR keeps the ObjModels referenced from its caches)");
		final long base = usedHeap();
		List<Object> keepMtr = new ArrayList<>();
		long mtrHeap;
		int kept = 0;
		try {
			for (final Job job : jobs) {
				keepMtr.add(runMtr(job, false).retained);
				kept++;
			}
			mtrHeap = usedHeap() - base;
		} catch (OutOfMemoryError e) {
			keepMtr = null;
			mtrHeap = -1;
			System.out.printf("  MTR path ran out of the %d MB heap after keeping %d of %d models%n", Runtime.getRuntime().maxMemory() >> 20, kept, jobs.size());
		}
		keepMtr = null;
		final long base2 = usedHeap();
		final List<Object> keepFast = new ArrayList<>();
		for (final Job job : jobs) {
			keepFast.add(runFast(job).meshesPerGroup);
		}
		final long fastHeap = usedHeap() - base2;
		System.out.printf("  MTR objects: %d MB, FastObj packed buffers: %d MB (and FastObj can drop them once uploaded)%n", mtrHeap >> 20, fastHeap >> 20);
		keepFast.clear();

		// ---------------- parallel
		System.out.printf("%n== All models, wall clock: MTR path (serial, render thread) vs FastObj on %d worker threads%n", threads);
		long t0 = System.nanoTime();
		for (final Job job : jobs) {
			runMtr(job, false);
		}
		final double serialMtr = (System.nanoTime() - t0) / 1e6;
		final ExecutorService pool = Executors.newFixedThreadPool(threads);
		t0 = System.nanoTime();
		final List<Future<FastResult>> futures = new ArrayList<>();
		for (final Job job : jobs) {
			futures.add(pool.submit(() -> runFast(job)));
		}
		for (final Future<FastResult> future : futures) {
			future.get();
		}
		final double parallelFast = (System.nanoTime() - t0) / 1e6;
		pool.shutdown();
		System.out.printf("  MTR serial: %.0f ms, FastObj parallel: %.0f ms (%.1fx)%n", serialMtr, parallelFast, serialMtr / parallelFast);
	}

	// ------------------------------------------------------------------ pack scanning

	static List<Job> scan(Path pack) throws Exception {
		final List<Job> jobs = new ArrayList<>();
		final Set<String> seen = new HashSet<>();
		try (var stream = Files.walk(pack.resolve("assets"))) {
			for (final Path json : stream.filter(p -> p.getFileName().toString().equals("mtr_custom_resources.json")).toList()) {
				final JsonObject root = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
				if (!root.has("vehicles")) {
					continue;
				}
				for (final JsonElement vehicleElement : root.getAsJsonArray("vehicles")) {
					final JsonObject vehicle = vehicleElement.getAsJsonObject();
					if (!vehicle.has("models")) {
						continue; // legacy MTR 3 format: not handled by this benchmark
					}
					final List<JsonElement> models = new ArrayList<>();
					for (final String key : new String[]{"models", "bogie1Models", "bogie2Models"}) {
						if (vehicle.has(key)) {
							vehicle.getAsJsonArray(key).forEach(models::add);
						}
					}
					for (final JsonElement modelElement : models) {
						final JsonObject model = modelElement.getAsJsonObject();
						final String modelResource = model.get("modelResource").getAsString();
						if (!modelResource.endsWith(".obj")) {
							continue;
						}
						final String key = modelResource + "|" + model.get("modelPropertiesResource").getAsString() + "|" + model.get("positionDefinitionsResource").getAsString();
						if (!seen.add(key)) {
							continue;
						}
						final JsonObject properties = JsonParser.parseString(new String(read(pack, model.get("modelPropertiesResource").getAsString()), StandardCharsets.UTF_8)).getAsJsonObject();
						final JsonObject positions = JsonParser.parseString(new String(read(pack, model.get("positionDefinitionsResource").getAsString()), StandardCharsets.UTF_8)).getAsJsonObject();
						jobs.add(new Job(pack, vehicle.get("id").getAsString(), modelResource, model.has("textureResource") ? model.get("textureResource").getAsString() : "", model.has("flipTextureV") && model.get("flipTextureV").getAsBoolean(), properties, positions));
					}
				}
			}
		}
		return jobs;
	}

	static byte[] read(Path pack, String resource) {
		final String[] split = resource.split(":", 2);
		final Path path = split.length == 2 ? pack.resolve("assets").resolve(split[0]).resolve(split[1]) : pack.resolve("assets/minecraft").resolve(resource);
		try {
			return Files.readAllBytes(path);
		} catch (Exception e) {
			return new byte[0];
		}
	}

	/** ModelPropertiesPart.writeCache for OBJ models: every addObjModelPosition call, in order. */
	static List<PartPlacement> placements(Job job) {
		final List<PartPlacement> result = new ArrayList<>();
		final double modelYOffset = job.properties.has("modelYOffset") ? job.properties.get("modelYOffset").getAsDouble() : 0;
		final Map<String, JsonObject> definitions = new HashMap<>();
		if (job.positions.has("positionDefinitions")) {
			for (final JsonElement element : job.positions.getAsJsonArray("positionDefinitions")) {
				definitions.put(element.getAsJsonObject().get("name").getAsString(), element.getAsJsonObject());
			}
		}
		for (final JsonElement partElement : job.properties.getAsJsonArray("parts")) {
			final JsonObject part = partElement.getAsJsonObject();
			final String type = part.has("type") ? part.get("type").getAsString() : "NORMAL";
			if (!type.equals("NORMAL")) {
				continue;
			}
			final boolean isDoor = (part.has("doorXMultiplier") && part.get("doorXMultiplier").getAsDouble() != 0) || (part.has("doorZMultiplier") && part.get("doorZMultiplier").getAsDouble() != 0);
			final int shader = STAGE_TO_SHADER.getOrDefault(part.has("renderStage") ? part.get("renderStage").getAsString() : "EXTERIOR", 0);
			final String condition = part.has("condition") ? part.get("condition").getAsString() : "NORMAL";
			for (final JsonElement definitionName : part.getAsJsonArray("positionDefinitions")) {
				final JsonObject definition = definitions.get(definitionName.getAsString());
				if (definition == null) {
					continue;
				}
				for (final String key : new String[]{"positions", "positionsFlipped"}) {
					if (!definition.has(key)) {
						continue;
					}
					for (final JsonElement positionElement : definition.getAsJsonArray(key)) {
						final JsonObject position = positionElement.getAsJsonObject();
						final double x = position.get("x").getAsDouble(), y = position.get("y").getAsDouble(), z = position.get("z").getAsDouble();
						final boolean flipped = key.equals("positionsFlipped");
						for (final String map : isDoor ? new String[]{"doorsClosed"} : new String[]{"normal", "doorsClosed"}) {
							for (final JsonElement name : part.getAsJsonArray("names")) {
								// door animation offset at time 0 is 0 for MTR's door types
								result.add(new PartPlacement(name.getAsString(), map, condition, shader, x / 16.0, y / 16.0 - modelYOffset, z / 16.0, flipped));
							}
						}
					}
				}
			}
		}
		return result;
	}

	// ------------------------------------------------------------------ MTR path

	static final class MtrResult {
		double parseMs, placeMs, buildMs, secondDistinctMs;
		long allocBytes;
		int uploads;
		long uploadedTriangles, uniqueTriangles, uploadedBytes;
		Map<String, Map<String, List<byte[]>>> firstUploadPerGroup = new TreeMap<>();
		Object retained;
	}

	static final Field RAW_MODEL;
	static final Method GET_RESOURCE_FROM_SAME_PATH;
	static final Method FORMAT_IDENTIFIER_WITH_DEFAULT;
	static final Identifier WHITE_TEXTURE = new Identifier("mtr", "textures/block/white.png");

	static {
		try {
			RAW_MODEL = OptimizedModel.ObjModel.class.getDeclaredField("rawModel");
			RAW_MODEL.setAccessible(true);
			final Class<?> tools = Class.forName("org.mtr.mod.resource.CustomResourceTools");
			GET_RESOURCE_FROM_SAME_PATH = tools.getDeclaredMethod("getResourceFromSamePath", String.class, String.class, String.class);
			GET_RESOURCE_FROM_SAME_PATH.setAccessible(true);
			FORMAT_IDENTIFIER_WITH_DEFAULT = tools.getDeclaredMethod("formatIdentifierWithDefault", String.class, String.class);
			FORMAT_IDENTIFIER_WITH_DEFAULT.setAccessible(true);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static Identifier sameDir(String base, String resource, String extension) {
		try {
			return (Identifier) GET_RESOURCE_FROM_SAME_PATH.invoke(null, base, resource, extension);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static Identifier formatWithDefault(String resource, String extension) {
		try {
			return (Identifier) FORMAT_IDENTIFIER_WITH_DEFAULT.invoke(null, resource, extension);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** ModelResourceLoader's texture resolver. */
	static Identifier resolveTexture(Job job, String textureString) {
		if (textureString == null || textureString.isEmpty()) {
			return WHITE_TEXTURE;
		}
		return textureString.equals("default.png") ? formatWithDefault(job.textureResource, "png") : sameDir(job.modelResource, textureString, "png");
	}

	static MtrResult runMtr(Job job, boolean capture) {
		final MtrResult result = new MtrResult();
		final long allocStart = THREADS.getThreadAllocatedBytes(Thread.currentThread().getId());
		long t0 = System.nanoTime();
		// VehicleModel.createModel -> ModelResourceLoader.loadModel (the resource provider returns a String)
		final String objString = new String(read(job.packRoot, id(formatWithDefault(job.modelResource, "obj"))), StandardCharsets.UTF_8);
		final Map<String, OptimizedModel.ObjModel> objModels = OptimizedModel.ObjModel.loadModel(objString, mtl -> new String(read(job.packRoot, id(sameDir(job.modelResource, mtl, "mtl"))), StandardCharsets.UTF_8), texture -> resolveTexture(job, texture), null, true, job.flipV);
		long t1 = System.nanoTime();
		// DynamicVehicleModel -> ModelPropertiesPart.writeCache -> addObjModelPosition
		final Map<String, List<OptimizedModel.ObjModel>> lists = new LinkedHashMap<>();
		final Map<OptimizedModel.ObjModel, String> names = new IdentityHashMap<>();
		for (final PartPlacement placement : placements(job)) {
			final OptimizedModel.ObjModel objModel = objModels.get(placement.name);
			if (objModel == null) {
				continue;
			}
			names.put(objModel, placement.name);
			objModel.addTransformation(OptimizedModel.ShaderType.values()[placement.shader], placement.x, placement.y, placement.z, placement.flipped);
			lists.computeIfAbsent(placement.map + "/" + placement.condition, k -> new ArrayList<>()).add(objModel);
		}
		long t2 = System.nanoTime();
		// VehicleResource.writeToOptimizedModels -> OptimizedModel.fromObjModels: for EVERY list entry
		// generateNormals + distinct + RawModel.upload (distinct again + pack + glBufferData)
		final Set<OptimizedModel.ObjModel> capturedGroups = Collections.newSetFromMap(new IdentityHashMap<>());
		for (final List<OptimizedModel.ObjModel> list : lists.values()) {
			for (final OptimizedModel.ObjModel objModel : list) {
				final RawModel rawModel = rawModel(objModel);
				rawModel.generateNormals();
				rawModel.distinct();
				final boolean captureThis = capture && capturedGroups.add(objModel);
				final Map<String, List<byte[]>> captured = captureThis ? new TreeMap<>() : null;
				rawModel.iterateRawMeshList(rawMesh -> {
					if (!rawMesh.hasFaces()) {
						return;
					}
					rawMesh.validateVertexIndex();
					final long d0 = System.nanoTime();
					rawMesh.distinct();
					result.secondDistinctMs += (System.nanoTime() - d0) / 1e6;
					final ByteBuffer vertices = ByteBuffer.allocateDirect(rawMesh.vertices.size() * 24).order(ByteOrder.nativeOrder());
					for (final Vertex vertex : rawMesh.vertices) {
						vertices.putFloat(vertex.position.getX()).putFloat(vertex.position.getY()).putFloat(vertex.position.getZ());
						vertices.putFloat(vertex.u).putFloat(vertex.v);
						final org.joml.Vector3f normal = new org.joml.Vector3f(vertex.normal.getX(), vertex.normal.getY(), vertex.normal.getZ()).normalize();
						vertices.put((byte) (normal.x * 127.0f)).put((byte) (normal.y * 127.0f)).put((byte) (normal.z * 127.0f)).put((byte) 0);
					}
					final ByteBuffer indices = ByteBuffer.allocateDirect(rawMesh.faces.size() * 12).order(ByteOrder.nativeOrder());
					rawMesh.faces.forEach(face -> {
						for (final int index : face.vertices) {
							indices.putInt(index);
						}
					});
					result.uploads++;
					result.uploadedTriangles += rawMesh.faces.size();
					result.uploadedBytes += vertices.capacity() + indices.capacity();
					if (captured != null) {
						captured.put(rawMesh.materialProperties.shaderType.ordinal() + "|" + id(rawMesh.materialProperties.getTexture()) + "|" + rawMesh.materialProperties.vertexAttributeState.color, triangles(vertices, rawMesh.faces.stream().map(face -> face.vertices).toList()));
					}
				});
				if (captured != null) {
					result.firstUploadPerGroup.put(names.get(objModel), captured);
				}
			}
		}
		final long t3 = System.nanoTime();
		final Set<OptimizedModel.ObjModel> unique = Collections.newSetFromMap(new IdentityHashMap<>());
		lists.values().forEach(unique::addAll);
		for (final OptimizedModel.ObjModel objModel : unique) {
			rawModel(objModel).iterateRawMeshList(rawMesh -> result.uniqueTriangles += rawMesh.faces.size());
		}
		result.parseMs = (t1 - t0) / 1e6;
		result.placeMs = (t2 - t1) / 1e6;
		result.buildMs = (t3 - t2) / 1e6;
		result.allocBytes = THREADS.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocStart;
		result.retained = objModels;
		return result;
	}

	/**
	 * Draw calls per car per frame, doors closed: what MTR 4.0.5 issues (one per mesh per list entry, so a group placed
	 * N times is drawn N times with all N copies), against one draw per material for the car (ATU merge), and one per
	 * texture + shader type with the colour moved into the vertex data.
	 */
	static void drawReport(List<Job> jobs) {
		System.out.println("\n== Draw calls per car per frame, doors closed, all part conditions summed (upper bound)");
		System.out.printf("  %-60s %8s %10s %10s | %12s %12s%n", "model", "MTR", "merged", "merged-col", "MTR tris", "merged tris");
		long sumMtr = 0, sumMerged = 0, sumMergedColour = 0;
		for (final Job job : jobs) {
			final String objString = new String(read(job.packRoot, id(formatWithDefault(job.modelResource, "obj"))), StandardCharsets.UTF_8);
			final Map<String, OptimizedModel.ObjModel> objModels = OptimizedModel.ObjModel.loadModel(objString, mtl -> new String(read(job.packRoot, id(sameDir(job.modelResource, mtl, "mtl"))), StandardCharsets.UTF_8), texture -> resolveTexture(job, texture), null, true, job.flipV);
			final Map<String, List<OptimizedModel.ObjModel>> lists = new LinkedHashMap<>();
			for (final PartPlacement placement : placements(job)) {
				final OptimizedModel.ObjModel objModel = objModels.get(placement.name);
				if (objModel != null) {
					objModel.addTransformation(OptimizedModel.ShaderType.values()[placement.shader], placement.x, placement.y, placement.z, placement.flipped);
					lists.computeIfAbsent(placement.map + "/" + placement.condition, k -> new ArrayList<>()).add(objModel);
				}
			}
			int mtr = 0, merged = 0, mergedColour = 0;
			long mtrTriangles = 0, mergedTriangles = 0;
			for (final Map.Entry<String, List<OptimizedModel.ObjModel>> entry : lists.entrySet()) {
				if (!entry.getKey().startsWith("doorsClosed/")) {
					continue;
				}
				final Set<Object> materials = new HashSet<>();
				final Set<Object> materialsNoColour = new HashSet<>();
				final Set<OptimizedModel.ObjModel> unique = Collections.newSetFromMap(new IdentityHashMap<>());
				for (final OptimizedModel.ObjModel objModel : entry.getValue()) {
					final boolean first = unique.add(objModel);
					final long[] triangles = {0};
					final int[] meshes = {0};
					rawModel(objModel).iterateRawMeshList(rawMesh -> {
						if (rawMesh.hasFaces()) {
							meshes[0]++;
							triangles[0] += rawMesh.faces.size();
							if (first) {
								materials.add(rawMesh.materialProperties);
								materialsNoColour.add(List.of(rawMesh.materialProperties.shaderType, id(rawMesh.materialProperties.getTexture())));
							}
						}
					});
					mtr += meshes[0];
					mtrTriangles += triangles[0];
					if (first) {
						mergedTriangles += triangles[0];
					}
				}
				merged += materials.size();
				mergedColour += materialsNoColour.size();
			}
			sumMtr += mtr;
			sumMerged += merged;
			sumMergedColour += mergedColour;
			System.out.printf("  %-60s %8d %10d %10d | %12d %12d  %s%n", job.label(), mtr, merged, mergedColour, mtrTriangles, mergedTriangles, verifyMerge(job));
		}
		System.out.printf("  %-60s %8d %10d %10d%n", "SUM", sumMtr, sumMerged, sumMergedColour);
	}

	/**
	 * ATU's MergedObjUpload against MTR's fromObjModels, per doors-closed list: the triangles that would reach the GPU
	 * for each material (shader, texture, colour), bitwise, MTR's side being the union of its per-group uploads.
	 */
	static String verifyMerge(Job job) {
		final Map<String, Map<String, List<byte[]>>> mtr = new TreeMap<>();
		final Map<String, Map<String, List<byte[]>>> atu = new TreeMap<>();
		final int[] buffers = {0, 0};
		for (int side = 0; side < 2; side++) {
			final String objString = new String(read(job.packRoot, id(formatWithDefault(job.modelResource, "obj"))), StandardCharsets.UTF_8);
			final Map<String, OptimizedModel.ObjModel> objModels = OptimizedModel.ObjModel.loadModel(objString, mtl -> new String(read(job.packRoot, id(sameDir(job.modelResource, mtl, "mtl"))), StandardCharsets.UTF_8), texture -> resolveTexture(job, texture), null, true, job.flipV);
			final Map<String, List<OptimizedModel.ObjModel>> lists = new LinkedHashMap<>();
			for (final PartPlacement placement : placements(job)) {
				final OptimizedModel.ObjModel objModel = objModels.get(placement.name);
				if (objModel != null) {
					objModel.addTransformation(OptimizedModel.ShaderType.values()[placement.shader], placement.x, placement.y, placement.z, placement.flipped);
					lists.computeIfAbsent(placement.map + "/" + placement.condition, k -> new ArrayList<>()).add(objModel);
				}
			}
			for (final Map.Entry<String, List<OptimizedModel.ObjModel>> entry : lists.entrySet()) {
				if (!entry.getKey().startsWith("doorsClosed/")) {
					continue;
				}
				final Map<String, List<byte[]>> out = new TreeMap<>();
				final int which = side;
				final java.util.function.Consumer<RawMesh> collect = rawMesh -> {
					if (rawMesh.hasFaces()) {
						buffers[which]++;
						out.computeIfAbsent(rawMesh.materialProperties.shaderType.ordinal() + "|" + id(rawMesh.materialProperties.getTexture()) + "|" + rawMesh.materialProperties.vertexAttributeState.color, k -> new ArrayList<>()).addAll(uploadTriangles(rawMesh));
					}
				};
				if (side == 0) {
					// MTR: each group on its own (a group listed twice is uploaded twice, both copies identical)
					final Set<OptimizedModel.ObjModel> unique = Collections.newSetFromMap(new IdentityHashMap<>());
					for (final OptimizedModel.ObjModel objModel : entry.getValue()) {
						final RawModel rawModel = rawModel(objModel);
						rawModel.generateNormals();
						rawModel.distinct();
						if (unique.add(objModel)) {
							rawModel.iterateRawMeshList(collect);
						}
					}
					mtr.put(entry.getKey(), out);
				} else {
					com.aureliatransit.utilities.perf.MergedObjUpload.merge(entry.getValue(), Bench::rawModel).iterateRawMeshList(collect);
					atu.put(entry.getKey(), out);
				}
			}
		}
		for (final Map<String, Map<String, List<byte[]>>> side : List.of(mtr, atu)) {
			side.values().forEach(perMaterial -> perMaterial.values().forEach(list -> list.sort(Arrays::compare)));
		}
		if (!mtr.keySet().equals(atu.keySet())) {
			return "DIFFERENT lists";
		}
		for (final String list : mtr.keySet()) {
			final Map<String, List<byte[]>> a = mtr.get(list), b = atu.get(list);
			if (!a.keySet().equals(b.keySet())) {
				return "DIFFERENT materials in " + list;
			}
			for (final String material : a.keySet()) {
				final List<byte[]> ta = a.get(material), tb = b.get(material);
				if (ta.size() != tb.size()) {
					return "DIFFERENT triangle count " + list + " " + material + ": " + ta.size() + " vs " + tb.size();
				}
				for (int i = 0; i < ta.size(); i++) {
					if (!Arrays.equals(ta.get(i), tb.get(i))) {
						return "DIFFERENT triangle data " + list + " " + material;
					}
				}
			}
		}
		return "identical (" + buffers[0] + " -> " + buffers[1] + " buffers)";
	}

	/** What RawMesh.upload would put in the buffers (distinct, then 24-byte vertices), as 72-byte triangles. */
	static List<byte[]> uploadTriangles(RawMesh rawMesh) {
		rawMesh.distinct();
		final ByteBuffer vertices = ByteBuffer.allocate(rawMesh.vertices.size() * 24).order(ByteOrder.nativeOrder());
		for (final Vertex vertex : rawMesh.vertices) {
			vertices.putFloat(vertex.position.getX()).putFloat(vertex.position.getY()).putFloat(vertex.position.getZ());
			vertices.putFloat(vertex.u).putFloat(vertex.v);
			final org.joml.Vector3f normal = new org.joml.Vector3f(vertex.normal.getX(), vertex.normal.getY(), vertex.normal.getZ()).normalize();
			vertices.put((byte) (normal.x * 127.0f)).put((byte) (normal.y * 127.0f)).put((byte) (normal.z * 127.0f)).put((byte) 0);
		}
		return triangles(vertices, rawMesh.faces.stream().map(face -> face.vertices).toList());
	}

	static RawModel rawModel(OptimizedModel.ObjModel objModel) {
		try {
			return (RawModel) RAW_MODEL.get(objModel);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	/** Each triangle as 72 bytes (3 vertices of 24 bytes), sorted: order-independent comparison. */
	static List<byte[]> triangles(ByteBuffer vertices, List<int[]> faces) {
		final List<byte[]> result = new ArrayList<>(faces.size());
		for (final int[] face : faces) {
			final byte[] triangle = new byte[72];
			for (int k = 0; k < 3; k++) {
				for (int b = 0; b < 24; b++) {
					triangle[24 * k + b] = vertices.get(face[k] * 24 + b);
				}
			}
			result.add(triangle);
		}
		result.sort(Arrays::compare);
		return result;
	}

	// ------------------------------------------------------------------ FastObj path

	static final class FastResult {
		double totalMs;
		long allocBytes;
		Map<String, Map<String, List<byte[]>>> meshesPerGroup = new TreeMap<>();
	}

	static Map<String, List<FastObj.FinalMesh>> lastFinals;

	static Map<String, List<FastObj.FinalMesh>> buildFast(Job job) {
		runFast(job);
		return lastFinals;
	}

	static FastResult runFast(Job job) {
		final FastResult result = new FastResult();
		final long allocStart = THREADS.getThreadAllocatedBytes(Thread.currentThread().getId());
		final long t0 = System.nanoTime();
		final byte[] objBytes = read(job.packRoot, id(formatWithDefault(job.modelResource, "obj")));
		final FastObj.Parsed parsed = FastObj.parse(objBytes);
		final Map<String, Mtl> materials = new HashMap<>();
		for (final String mtlFile : parsed.mtlFileNames) {
			try {
				MtlReader.read(new ByteArrayInputStream(read(job.packRoot, id(sameDir(job.modelResource, mtlFile.replace("\\\\", "/").replace("\\", "/"), "mtl"))))).forEach(mtl -> materials.put(mtl.getName(), mtl));
			} catch (Exception ignored) {
			}
		}
		final List<PartPlacement> placementList = placements(job);
		final Map<String, LinkedHashSet<FastObj.Placement>> placementsPerGroup = new LinkedHashMap<>();
		for (final PartPlacement placement : placementList) {
			placementsPerGroup.computeIfAbsent(placement.name, k -> new LinkedHashSet<>()).add(new FastObj.Placement(placement.shader, placement.x, placement.y, placement.z, placement.flipped));
		}
		final Map<String, List<FastObj.BaseMesh>> groups = FastObj.buildGroups(parsed, materialName -> resolveMaterial(job, materials, materialName), job.flipV, placementsPerGroup.keySet());
		final Map<String, List<FastObj.FinalMesh>> finals = new LinkedHashMap<>();
		placementsPerGroup.forEach((group, placements) -> {
			final List<FastObj.BaseMesh> base = groups.get(group);
			if (base != null) {
				finals.put(group, FastObj.finish(base, placements));
			}
		});
		lastFinals = finals;
		result.totalMs = (System.nanoTime() - t0) / 1e6;
		result.allocBytes = THREADS.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocStart;
		finals.forEach((group, meshes) -> {
			final Map<String, List<byte[]>> perMaterial = new TreeMap<>();
			for (final FastObj.FinalMesh mesh : meshes) {
				final List<int[]> faces = new ArrayList<>();
				for (int t = 0; t < mesh.triangleCount; t++) {
					faces.add(new int[]{mesh.indices[3 * t], mesh.indices[3 * t + 1], mesh.indices[3 * t + 2]});
				}
				perMaterial.put(mesh.key(), triangles(ByteBuffer.wrap(mesh.vertexBytes).order(ByteOrder.nativeOrder()), faces));
			}
			result.meshesPerGroup.put(group, perMaterial);
		});
		return result;
	}

	/** ObjModelLoader's material handling: {texture, color, shader ordinal, flipv}. */
	static Object[] resolveMaterial(Job job, Map<String, Mtl> materials, String key) {
		final Map<String, String> options = new HashMap<>();
		final String[] majorParts = key.split("#", 2);
		options.put("", majorParts[0]);
		if (majorParts.length > 1) {
			for (final String minorPart : majorParts[1].split(",")) {
				final String[] tokens = minorPart.split("=", 2);
				if (tokens.length > 1) {
					options.put(tokens[0], tokens[1]);
				} else if (!options.containsKey("#")) {
					options.put("#", tokens[0]);
				} else {
					options.put(tokens[0].toLowerCase(Locale.ROOT), "1");
				}
			}
		}
		final int shader = switch (options.getOrDefault("#", "").toLowerCase(Locale.ENGLISH)) {
			case "exteriortranslucent" -> 1;
			case "light" -> 4;
			case "always_on_light", "lighttranslucent" -> 5;
			case "interior" -> 2;
			case "interior_translucent", "interiortranslucent" -> 3;
			default -> 0;
		};
		final boolean flipV = options.getOrDefault("flipv", "0").equals("1");
		String texture;
		Integer color;
		if (!materials.isEmpty()) {
			final Mtl mtl = materials.get(key);
			if (mtl == null) {
				texture = "";
				color = null;
			} else {
				texture = mtl.getMapKd() == null || mtl.getMapKd().isEmpty() ? "" : mtl.getMapKd();
				final FloatTuple kd = mtl.getKd();
				color = kd == null ? merge(255, 255, 255, 255) : merge((int) (kd.getX() * 255.0f), (int) (kd.getY() * 255.0f), (int) (kd.getZ() * 255.0f), mtl.getD() == null ? 255 : (int) (mtl.getD() * 255.0f));
			}
		} else {
			texture = majorParts[0].equals("_") ? "" : majorParts[0];
			color = merge(255, 255, 255, 255);
		}
		return new Object[]{id(resolveTexture(job, texture.replace("\\\\", "/").replace("\\", "/"))), color, shader, flipV ? 1 : 0};
	}

	static String id(Identifier identifier) {
		return String.valueOf(identifier.data);
	}

	static int merge(int r, int g, int b, int a) {
		return (r << 24) | (g << 16) | (b << 8) | a;
	}

	// ------------------------------------------------------------------ helpers

	static String compare(Map<String, Map<String, List<byte[]>>> mtr, Map<String, Map<String, List<byte[]>>> fast) {
		if (!mtr.keySet().equals(fast.keySet())) {
			return "DIFFERENT groups " + mtr.keySet() + " vs " + fast.keySet();
		}
		long triangles = 0;
		for (final String group : mtr.keySet()) {
			final Map<String, List<byte[]>> a = mtr.get(group), b = fast.get(group);
			if (!a.keySet().equals(b.keySet())) {
				return "DIFFERENT materials in " + group + ": " + a.keySet() + " vs " + b.keySet();
			}
			for (final String material : a.keySet()) {
				final List<byte[]> x = a.get(material), y = b.get(material);
				if (x.size() != y.size()) {
					return "DIFFERENT triangle count in " + group + "/" + material + ": " + x.size() + " vs " + y.size();
				}
				for (int i = 0; i < x.size(); i++) {
					if (!Arrays.equals(x.get(i), y.get(i))) {
						return "DIFFERENT bytes in " + group + "/" + material + " triangle " + i;
					}
				}
				triangles += x.size();
			}
		}
		return "IDENTICAL (" + mtr.size() + " groups, " + triangles + " triangles)";
	}

	static double median(double[][] runs, int column) {
		final double[] values = Arrays.stream(runs).mapToDouble(r -> r[column]).sorted().toArray();
		return values[values.length / 2];
	}

	static long usedHeap() {
		for (int i = 0; i < 3; i++) {
			System.gc();
		}
		final Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}
}
