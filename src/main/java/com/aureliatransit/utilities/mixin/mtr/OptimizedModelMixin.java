package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.perf.MergedObjUpload;
import com.aureliatransit.utilities.perf.Perf;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** One buffer per material per car instead of one per OBJ group per material ({@link MergedObjUpload}). */
@Mixin(value = OptimizedModel.class, remap = false)
public abstract class OptimizedModelMixin {

	@Shadow
	@Final
	private static VertexAttributeMapping DEFAULT_MAPPING;

	@Unique
	private static boolean atu$failed;

	@Inject(method = "fromObjModels", at = @At("HEAD"), cancellable = true)
	private static void atu$mergeParts(Collection<OptimizedModel.ObjModel> objModels, CallbackInfoReturnable<OptimizedModel> cir) {
		if (atu$failed || !Perf.settings().mergeTrainParts) {
			return;
		}
		try {
			cir.setReturnValue(new OptimizedModel(MergedObjUpload.upload(objModels, objModel -> ((ObjModelAccess) (Object) objModel).atu$rawModel(), DEFAULT_MAPPING)));
		} catch (RuntimeException e) {
			// MTR's own path runs for this model and every later one this session.
			atu$failed = true;
			Perf.LOGGER.error("Merging train parts failed; using MTR's own model upload for the rest of this session", e);
		}
	}
}
