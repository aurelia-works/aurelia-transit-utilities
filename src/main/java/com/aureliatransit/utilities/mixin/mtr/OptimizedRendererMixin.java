package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.TrainLodClient;
import com.aureliatransit.utilities.perf.Perf;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.batch.BatchManager;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Times MTR's batched renderer (CPU side: shader setup, binds, draw submission) for the F3 lines; middle train LOD tier. */
@Mixin(value = OptimizedRenderer.class, remap = false)
public abstract class OptimizedRendererMixin {

	@Unique
	private long atu$renderStart;

	@Inject(method = "render", at = @At("HEAD"))
	private void atu$startTimer(boolean renderTranslucent, CallbackInfo ci) {
		atu$renderStart = System.nanoTime();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void atu$stopTimer(boolean renderTranslucent, CallbackInfo ci) {
		Perf.STATS.render(System.nanoTime() - atu$renderStart);
	}

	@WrapOperation(method = "queue", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/render/batch/BatchManager;queue(Ljava/util/List;Lorg/mtr/mapping/render/vertex/VertexAttributeState;)V"))
	private void atu$trainInteriorLod(BatchManager batchManager, List<VertexArray> parts, VertexAttributeState state, Operation<Void> original, @Local(argsOnly = true) OptimizedModel model) {
		original.call(batchManager, TrainLodClient.partsToQueue(model, parts, state), state);
	}
}
