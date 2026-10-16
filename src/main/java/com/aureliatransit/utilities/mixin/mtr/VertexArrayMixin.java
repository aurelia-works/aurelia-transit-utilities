package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.GlGarbage;
import com.aureliatransit.utilities.perf.Perf;
import org.mtr.mapping.render.model.Mesh;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts MTR's draw calls for the F3 lines; frees the GPU buffers of dropped models ({@link GlGarbage}). */
@Mixin(value = VertexArray.class, remap = false)
public abstract class VertexArrayMixin {

	@Shadow
	private int id;

	@Unique
	private GlGarbage.Buffers atu$buffers;

	@Inject(method = "<init>(Lorg/mtr/mapping/render/model/Mesh;Lorg/mtr/mapping/render/vertex/VertexAttributeMapping;)V", at = @At("RETURN"))
	private void atu$trackBuffers(Mesh mesh, VertexAttributeMapping mapping, CallbackInfo ci) {
		Perf.STATS.arrayCreated();
		if (Perf.settings().freeUnusedModels) {
			atu$buffers = GlGarbage.track(this, id, ((VertexBufferAccess) (Object) mesh.vertexBuffer).atu$id(), ((VertexBufferAccess) (Object) mesh.indexBuffer).atu$id());
		}
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void atu$closeBuffers(CallbackInfo ci) {
		if (atu$buffers != null) {
			atu$buffers.closedByMtr();
		}
	}

	@Inject(method = "draw", at = @At("HEAD"))
	private void atu$countDraw(CallbackInfo ci) {
		Perf.STATS.draw();
	}
}
