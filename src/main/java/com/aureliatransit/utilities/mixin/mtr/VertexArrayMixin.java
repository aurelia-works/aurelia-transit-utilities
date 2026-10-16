package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.perf.Perf;
import org.mtr.mapping.render.object.VertexArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts MTR's draw calls for the F3 lines. */
@Mixin(value = VertexArray.class, remap = false)
public abstract class VertexArrayMixin {

	@Inject(method = "draw", at = @At("HEAD"))
	private void atu$countDraw(CallbackInfo ci) {
		Perf.STATS.draw();
	}
}
