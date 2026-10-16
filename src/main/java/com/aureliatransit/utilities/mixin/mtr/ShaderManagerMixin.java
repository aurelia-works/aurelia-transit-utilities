package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.ShaderBatchFastPath;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.shader.ShaderManager;
import org.mtr.mapping.render.tool.Utilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the start and end of each MTR material batch for {@link ShaderBatchFastPath}. */
@Mixin(value = ShaderManager.class, remap = false)
public abstract class ShaderManagerMixin {

	@Inject(method = "setupShaderBatchState", at = @At("TAIL"))
	private void atu$beginBatch(MaterialProperties materialProperties, CallbackInfo ci) {
		// MTR's own shaders (no shader pack) never re-bind per draw, so there is nothing to speed up.
		if (!Utilities.canUseCustomShader()) {
			ShaderBatchFastPath.beginBatch();
		}
	}

	@Inject(method = "cleanupShaderBatchState", at = @At("HEAD"))
	private void atu$endBatch(CallbackInfo ci) {
		ShaderBatchFastPath.endBatch();
	}
}
