package com.aureliatransit.utilities.perf;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.Mesh;
import org.mtr.mapping.render.model.RawModel;
import org.mtr.mapping.render.object.IndexBuffer;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.object.VertexBuffer;
import org.mtr.mapping.render.tool.OffHeapAllocator;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * ATU's version of MTR 4.0.5's {@code OptimizedModel.fromObjModels}. MTR uploads every OBJ group of a car on its own,
 * so a car is drawn with one call per group per material. Here all groups of the list go into one {@link RawModel},
 * which keeps one mesh per material (MTR's own {@code MaterialProperties} equality: shader, texture, colour, light),
 * and the car is drawn with one call per material. A group listed twice is uploaded once.
 * <p>
 * Same vertices, same materials, same shaders: only the number of buffers and draw calls changes. Uses MTR types only,
 * so the headless benchmark in {@code tools/objbench} runs it against MTR's own path.
 */
public final class MergedObjUpload {

	/** {@code GL_UNSIGNED_INT}, MTR's index type. */
	private static final int GL_UNSIGNED_INT = 5125;

	/** Time spent in {@link FastMeshBuild#pack} so far (render thread; not {@link Perf}, which needs Fabric). */
	public static long packNanos;

	private MergedObjUpload() {
	}

	public static List<VertexArray> upload(Collection<OptimizedModel.ObjModel> objModels, Function<OptimizedModel.ObjModel, RawModel> rawModelOf, VertexAttributeMapping mapping) {
		return merge(objModels, rawModelOf).upload(mapping);
	}

	/**
	 * Same buffers as {@link #upload}, built by {@link FastMeshBuild}; null when that refuses the data (MTR's own path
	 * should then run). Render thread.
	 */
	public static List<VertexArray> uploadFast(Collection<OptimizedModel.ObjModel> objModels, Function<OptimizedModel.ObjModel, RawModel> rawModelOf, VertexAttributeMapping mapping) {
		final long start = System.nanoTime();
		final List<FastMeshBuild.Packed> packedMeshes = FastMeshBuild.pack(objModels, rawModelOf, mapping);
		packNanos += System.nanoTime() - start;
		if (packedMeshes == null) {
			return null;
		}
		return uploadPacked(packedMeshes, mapping);
	}

	/** Uploads meshes packed by {@link FastMeshBuild#pack} (here or on a worker). Render thread. */
	public static List<VertexArray> uploadPacked(List<FastMeshBuild.Packed> packedMeshes, VertexAttributeMapping mapping) {
		final List<VertexArray> vertexArrays = new ArrayList<>(packedMeshes.size());
		for (final FastMeshBuild.Packed packed : packedMeshes) {
			final VertexBuffer vertexBuffer = new VertexBuffer();
			final IndexBuffer indexBuffer = new IndexBuffer(packed.faceCount, GL_UNSIGNED_INT);
			final ByteBuffer vertices = OffHeapAllocator.allocate(packed.vertexBytes.length);
			vertices.put(packed.vertexBytes);
			vertexBuffer.upload(vertices, VertexBuffer.USAGE_STATIC_DRAW);
			OffHeapAllocator.free(vertices);
			final ByteBuffer indices = OffHeapAllocator.allocate(packed.indices.length * 4);
			indices.asIntBuffer().put(packed.indices);
			indexBuffer.upload(indices, VertexBuffer.USAGE_STATIC_DRAW);
			indexBuffer.setFaceCount(packed.faceCount);
			OffHeapAllocator.free(indices);
			vertexArrays.add(new VertexArray(new Mesh(vertexBuffer, indexBuffer, packed.materialProperties), mapping));
		}
		return vertexArrays;
	}

	/** The merged model before upload. Steps per group are MTR's own: normals, then de-duplication. */
	public static RawModel merge(Collection<OptimizedModel.ObjModel> objModels, Function<OptimizedModel.ObjModel, RawModel> rawModelOf) {
		final Set<OptimizedModel.ObjModel> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		final RawModel merged = new RawModel();
		for (final OptimizedModel.ObjModel objModel : objModels) {
			if (!seen.add(objModel)) {
				continue;
			}
			final RawModel rawModel = rawModelOf.apply(objModel);
			rawModel.generateNormals();
			rawModel.distinct();
			merged.append(rawModel);
		}
		return merged;
	}
}
