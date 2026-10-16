package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.ShadowPass;
import com.aureliatransit.utilities.perf.Perf;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.render.MainRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Works out once per MTR render pass whether it is a shadow pass ({@link ShadowPass}); times MTR's whole pass for the F3 lines. */
@Mixin(value = MainRenderer.class, remap = false)
public abstract class MainRendererMixin {

	@Unique
	private static long atu$start;

	@Inject(method = "render(Lorg/mtr/mapping/mapper/GraphicsHolder;Lorg/mtr/mapping/holder/Vector3d;)V", at = @At("HEAD"))
	private static void atu$shadowPass(GraphicsHolder graphicsHolder, Vector3d offset, CallbackInfo ci) {
		atu$start = System.nanoTime();
		ShadowPass.begin();
	}

	@Inject(method = "render(Lorg/mtr/mapping/mapper/GraphicsHolder;Lorg/mtr/mapping/holder/Vector3d;)V", at = @At("RETURN"))
	private static void atu$shadowPassEnd(GraphicsHolder graphicsHolder, Vector3d offset, CallbackInfo ci) {
		ShadowPass.end();
		Perf.STATS.mtrRender(System.nanoTime() - atu$start);
	}
}
