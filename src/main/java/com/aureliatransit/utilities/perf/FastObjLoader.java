package com.aureliatransit.utilities.perf;

import org.apache.commons.lang3.StringUtils;
import org.mtr.libraries.de.javagl.obj.FloatTuple;
import org.mtr.libraries.de.javagl.obj.Mtl;
import org.mtr.libraries.de.javagl.obj.MtlReader;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Vector3f;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.obj.AtlasManager;
import org.mtr.mapping.render.vertex.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * ATU's version of MTR 4.0.5's {@code ObjModelLoader.loadModel(String, ...)}: the same {@link RawMesh}es, read by
 * {@link FastObjParse} instead of javagl. Materials follow MTR's own rules (MTL colour and texture, the
 * {@code name#shader,flipv=1} options in a material name, the legacy shader names), copied from MTR (MIT).
 * Returns null when the file should go through MTR's own loader.
 */
public final class FastObjLoader {

	/** Not {@link Perf#LOGGER}: this class also runs in the headless benchmark, without Fabric. */
	private static final Logger LOGGER = LoggerFactory.getLogger("ATU performance");

	private FastObjLoader() {
	}

	public static Map<String, List<RawMesh>> load(String objString, Function<String, String> mtlResolver, Function<String, Identifier> textureResolver, AtlasManager atlasManager, boolean splitModel) {
		final Prepared prepared = prepare(objString, splitModel);
		return prepared == null ? null : assemble(prepared, mtlResolver, textureResolver, atlasManager);
	}

	/**
	 * The part of a load that needs no resources and no MTR state: parsing and the vertices and faces of every mesh.
	 * Any thread. Null when MTR's own loader should read the file.
	 */
	public static Prepared prepare(String objString, boolean splitModel) {
		// MTR hands javagl the string as UTF-8 bytes, which javagl reads as US-ASCII.
		final FastObjParse.Result parsed = FastObjParse.parse(objString.getBytes(StandardCharsets.UTF_8), splitModel);
		if (parsed == null) {
			return null;
		}
		final Map<String, List<PreparedMesh>> groups = new HashMap<>();
		parsed.groups.forEach((group, meshes) -> {
			final List<PreparedMesh> preparedMeshes = new ArrayList<>(meshes.size());
			for (final FastObjParse.Mesh mesh : meshes) {
				preparedMeshes.add(new PreparedMesh(mesh));
			}
			groups.put(group, preparedMeshes);
		});
		return new Prepared(objString, splitModel, parsed.mtlFileNames, groups);
	}

	/** The rest of a load: MTL files, materials and textures, then MTR's own mesh checks. Use each {@link Prepared} once. */
	public static Map<String, List<RawMesh>> assemble(Prepared prepared, Function<String, String> mtlResolver, Function<String, Identifier> textureResolver, AtlasManager atlasManager) {
		final Map<String, Mtl> materials = new HashMap<>();
		for (final String mtlFileName : prepared.mtlFileNames) {
			try {
				final String mtl = mtlResolver.apply(mtlFileName.replace("\\\\", "/").replace("\\", "/"));
				for (final Mtl material : MtlReader.read(new ByteArrayInputStream(mtl.getBytes(StandardCharsets.UTF_8)))) {
					materials.put(material.getName(), material);
				}
			} catch (Exception e) {
				// MTR logs and carries on without this file's materials.
				LOGGER.debug("Could not read MTL file {}", mtlFileName, e);
			}
		}
		final Map<String, List<RawMesh>> result = new HashMap<>();
		prepared.groups.forEach((group, meshes) -> {
			final List<RawMesh> rawMeshes = new ArrayList<>(meshes.size());
			for (final PreparedMesh mesh : meshes) {
				final RawMesh rawMesh = new RawMesh(materialProperties(mesh, materials, textureResolver));
				rawMesh.vertices.addAll(mesh.vertices);
				rawMesh.faces.addAll(mesh.faces);
				atlasManager.applyToMesh(rawMesh);
				rawMesh.validateVertexIndex();
				rawMeshes.add(rawMesh);
			}
			result.put(group, rawMeshes);
		});
		return result;
	}

	/** A parsed OBJ file waiting for its materials. */
	public static final class Prepared {

		public final String objString;
		public final boolean splitModel;
		private final List<String> mtlFileNames;
		private final Map<String, List<PreparedMesh>> groups;

		private Prepared(String objString, boolean splitModel, List<String> mtlFileNames, Map<String, List<PreparedMesh>> groups) {
			this.objString = objString;
			this.splitModel = splitModel;
			this.mtlFileNames = mtlFileNames;
			this.groups = groups;
		}
	}

	/** MTR's vertices and faces for one mesh; {@code flipv} comes from the material name, so it is known here. */
	private static final class PreparedMesh {

		private final String material;
		private final List<Vertex> vertices;
		private final List<Face> faces;

		private PreparedMesh(FastObjParse.Mesh mesh) {
			material = mesh.material;
			final boolean flipTextureV = splitMaterialOptions(mesh.material).getOrDefault("flipv", "0").equals("1");
			final float[] positions = mesh.positions;
			final float[] normals = mesh.normals;
			final float[] uvs = mesh.uvs;
			vertices = new ArrayList<>(mesh.vertexCount);
			for (int i = 0; i < mesh.vertexCount; i++) {
				final Vertex vertex = new Vertex();
				vertex.position = new Vector3f(positions[3 * i], positions[3 * i + 1], positions[3 * i + 2]);
				vertex.normal = normals == null ? new Vector3f(0, 0, 0) : new Vector3f(normals[3 * i], normals[3 * i + 1], normals[3 * i + 2]);
				if (uvs != null) {
					vertex.u = uvs[2 * i];
					vertex.v = flipTextureV ? 1 - uvs[2 * i + 1] : uvs[2 * i + 1];
				} else {
					vertex.u = 0;
					vertex.v = flipTextureV ? 1 : 0;
				}
				vertices.add(vertex);
			}
			final int[] triangles = mesh.triangles;
			faces = new ArrayList<>(triangles.length / 3);
			for (int t = 0; t < triangles.length; t += 3) {
				faces.add(new Face(new int[]{triangles[t], triangles[t + 1], triangles[t + 2]}));
			}
		}
	}

	/** {@code ObjModelLoader}'s material rules for one material group. */
	private static MaterialProperties materialProperties(PreparedMesh mesh, Map<String, Mtl> materials, Function<String, Identifier> textureResolver) {
		final String key = mesh.material;
		final Map<String, String> materialOptions = splitMaterialOptions(key);
		final String materialGroupName = materialOptions.get("");
		final OptimizedModel.ShaderType shaderType = legacyMapping(materialOptions.getOrDefault("#", ""));
		final String texture;
		final Integer color;
		if (!materials.isEmpty()) {
			final Mtl objMaterial = materials.getOrDefault(key, null);
			if (objMaterial == null) {
				texture = "";
				color = null;
			} else {
				texture = StringUtils.isEmpty(objMaterial.getMapKd()) ? "" : objMaterial.getMapKd();
				final FloatTuple kd = objMaterial.getKd();
				color = kd == null ? mergeColor(255, 255, 255, 255) : mergeColor((int) (kd.getX() * 255), (int) (kd.getY() * 255), (int) (kd.getZ() * 255), objMaterial.getD() == null ? 255 : (int) (objMaterial.getD() * 255));
			}
		} else {
			texture = materialGroupName.equals("_") ? "" : materialGroupName;
			color = mergeColor(255, 255, 255, 255);
		}
		return new MaterialProperties(shaderType, textureResolver.apply(texture.replace("\\\\", "/").replace("\\", "/")), color);
	}

	/** {@code ObjModelLoader.splitMaterialOptions}: {@code name#shader,key=value,flag}. */
	private static Map<String, String> splitMaterialOptions(String source) {
		final Map<String, String> result = new HashMap<>();
		final String[] majorParts = source.split("#", 2);
		result.put("", majorParts[0]);
		if (majorParts.length > 1) {
			for (final String minorPart : majorParts[1].split(",")) {
				final String[] tokens = minorPart.split("=", 2);
				if (tokens.length > 1) {
					result.put(tokens[0], tokens[1]);
				} else if (!result.containsKey("#")) {
					result.put("#", tokens[0]);
				} else {
					result.put(tokens[0].toLowerCase(Locale.ROOT), "1");
				}
			}
		}
		return result;
	}

	private static int mergeColor(int r, int g, int b, int a) {
		return (r << 24) | (g << 16) | (b << 8) | a;
	}

	/** {@code ObjModelLoader.legacyMapping}. */
	private static OptimizedModel.ShaderType legacyMapping(String type) {
		return switch (type.toLowerCase(Locale.ENGLISH)) {
			case "exteriortranslucent" -> OptimizedModel.ShaderType.TRANSLUCENT;
			case "light" -> OptimizedModel.ShaderType.CUTOUT_GLOWING;
			case "always_on_light", "lighttranslucent" -> OptimizedModel.ShaderType.TRANSLUCENT_GLOWING;
			case "interior" -> OptimizedModel.ShaderType.CUTOUT_BRIGHT;
			case "interior_translucent", "interiortranslucent" -> OptimizedModel.ShaderType.TRANSLUCENT_BRIGHT;
			default -> OptimizedModel.ShaderType.CUTOUT;
		};
	}
}
