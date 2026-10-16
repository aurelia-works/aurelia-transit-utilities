package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.RailLodClient;
import com.aureliatransit.utilities.perf.AsyncPacks;
import com.aureliatransit.utilities.perf.DoorShare;
import com.aureliatransit.utilities.perf.FastMeshBuild;
import com.aureliatransit.utilities.perf.MergedObjUpload;
import com.aureliatransit.utilities.perf.Perf;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.RawModel;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * One buffer per material per car instead of one per OBJ group per material ({@link MergedObjUpload}), and the parts
 * both door states use uploaded once ({@link DoorShare}).
 */
@Mixin(value = OptimizedModel.class, remap = false)
public abstract class OptimizedModelMixin {

	@Shadow
	@Final
	private static VertexAttributeMapping DEFAULT_MAPPING;

	@Unique
	private static boolean atu$failed;
	@Unique
	private static boolean atu$fastFailed;

	@Inject(method = "fromObjModels", at = @At("HEAD"), cancellable = true)
	private static void atu$mergeParts(Collection<OptimizedModel.ObjModel> objModels, CallbackInfoReturnable<OptimizedModel> cir) {
		if (atu$failed || !Perf.settings().mergeTrainParts) {
			return;
		}
		try {
			final OptimizedModel model = new OptimizedModel(atu$upload(objModels));
			RailLodClient.recordHeight(model, atu$height(objModels));
			cir.setReturnValue(model);
		} catch (RuntimeException e) {
			// MTR's own path runs for this model and every later one this session.
			atu$failed = true;
			AsyncPacks.disable();
			Perf.LOGGER.error("Merging train parts failed; using MTR's own model upload for the rest of this session", e);
		}
	}

	/** Fast build when on and the data allows it, else the merge through MTR's own mesh code. */
	@Unique
	private static List<VertexArray> atu$upload(Collection<OptimizedModel.ObjModel> objModels) {
		final long start = System.nanoTime();
		final long packBefore = MergedObjUpload.packNanos;
		try {
			return atu$uploadTimed(objModels);
		} finally {
			Perf.STATS.buildPack(MergedObjUpload.packNanos - packBefore);
			Perf.STATS.merge(System.nanoTime() - start);
		}
	}

	/** Parts both door states use are uploaded once ({@link DoorShare}); the rest as one merged upload. */
	@Unique
	private static List<VertexArray> atu$uploadTimed(Collection<OptimizedModel.ObjModel> objModels) {
		final DoorShare.Split split = DoorShare.split(objModels);
		if (split.share == null) {
			return atu$uploadMerged(split.unique);
		}
		List<VertexArray> commonArrays = split.share.uploaded();
		if (commonArrays == null) {
			commonArrays = atu$uploadMerged(split.common);
			split.share.store(commonArrays);
		} else {
			Perf.STATS.shared(commonArrays.size());
		}
		final List<VertexArray> vertexArrays = new ArrayList<>(commonArrays);
		if (!split.rest.isEmpty()) {
			vertexArrays.addAll(atu$uploadMerged(split.rest));
		}
		return vertexArrays;
	}

	@Unique
	private static List<VertexArray> atu$uploadMerged(List<OptimizedModel.ObjModel> objModels) {
		final Function<OptimizedModel.ObjModel, RawModel> rawModelOf = AsyncPacks.RAW_MODEL_OF;
		if (Perf.settings().fasterModelLoading && !atu$fastFailed) {
			try {
				final List<FastMeshBuild.Packed> packed = AsyncPacks.take(objModels, DEFAULT_MAPPING);
				if (packed != null) {
					return MergedObjUpload.uploadPacked(packed, DEFAULT_MAPPING);
				}
				final List<VertexArray> vertexArrays = MergedObjUpload.uploadFast(objModels, rawModelOf, DEFAULT_MAPPING);
				if (vertexArrays != null) {
					return vertexArrays;
				}
			} catch (RuntimeException e) {
				atu$fastFailed = true;
				AsyncPacks.disable();
				Perf.LOGGER.error("Fast model build failed; using MTR's mesh code for the rest of this session", e);
			}
		}
		return MergedObjUpload.upload(objModels, rawModelOf, DEFAULT_MAPPING);
	}

	/** MTR's own upload path (merge off or failed): still record the height for rail LOD. */
	@Inject(method = "fromObjModels", at = @At("RETURN"))
	private static void atu$recordHeight(Collection<OptimizedModel.ObjModel> objModels, CallbackInfoReturnable<OptimizedModel> cir) {
		RailLodClient.recordHeight(cir.getReturnValue(), atu$height(objModels));
	}

	/** Vertical extent of the model's OBJ groups, in blocks; NaN when empty. */
	@Unique
	private static float atu$height(Collection<OptimizedModel.ObjModel> objModels) {
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		for (final OptimizedModel.ObjModel objModel : objModels) {
			final ObjModelAccess access = (ObjModelAccess) (Object) objModel;
			min = Math.min(min, access.atu$minY());
			max = Math.max(max, access.atu$maxY());
		}
		return max >= min ? max - min : Float.NaN;
	}
}
