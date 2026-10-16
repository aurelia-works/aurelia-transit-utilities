package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.ShaderBatchFastPath;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gl.ShaderProgram;
import org.mtr.mapping.render.vertex.VertexAttributeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** With a shader pack: per draw, upload the matrix uniforms instead of re-binding the whole shader ({@link ShaderBatchFastPath}). */
@Mixin(value = VertexAttributeState.class, remap = false)
public abstract class VertexAttributeStateMixin {

	@WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/ShaderProgram;bind()V", remap = true))
	private void atu$uploadMatrixOnly(ShaderProgram program, Operation<Void> original) {
		if (!ShaderBatchFastPath.uploadMatrixOnly(program)) {
			original.call(program);
		}
	}
}
