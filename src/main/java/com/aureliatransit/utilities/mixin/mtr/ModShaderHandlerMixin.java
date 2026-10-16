package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.ShadowPass;
import org.mtr.mapping.render.shader.ModShaderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MTR's shadow pass check without a stack walk per call ({@link ShadowPass}). */
@Mixin(value = ModShaderHandler.class, remap = false)
public abstract class ModShaderHandlerMixin {

	@Inject(method = "renderingShadows", at = @At("HEAD"), cancellable = true)
	private static void atu$cachedShadowPass(CallbackInfoReturnable<Boolean> cir) {
		final Boolean renderingShadows = ShadowPass.mtrRenderingShadows();
		if (renderingShadows != null) {
			cir.setReturnValue(renderingShadows);
		}
	}
}
