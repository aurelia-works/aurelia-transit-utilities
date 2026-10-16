package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.ShaderBatchFastPath;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import org.mtr.mapping.render.vertex.VertexAttributeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;

/**
 * With a shader pack: per draw, upload the matrix uniforms instead of re-binding the whole shader ({@link ShaderBatchFastPath}).
 * Without one: reuse one 64-byte buffer for the model matrix instead of allocating one per draw (render thread only;
 * MTR reads it back with absolute gets right away).
 */
@Mixin(value = VertexAttributeState.class, remap = false)
public abstract class VertexAttributeStateMixin {

	@Unique
	private static final ByteBuffer ATU$MATRIX = ByteBuffer.allocate(64);

	@WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Ljava/nio/ByteBuffer;allocate(I)Ljava/nio/ByteBuffer;"))
	private ByteBuffer atu$reuseMatrixBuffer(int capacity, Operation<ByteBuffer> original) {
		if (capacity != 64 || !RenderSystem.isOnRenderThread()) {
			return original.call(capacity);
		}
		return ATU$MATRIX.clear();
	}

	@WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/ShaderProgram;bind()V", remap = true))
	private void atu$uploadMatrixOnly(ShaderProgram program, Operation<Void> original) {
		if (!ShaderBatchFastPath.uploadMatrixOnly(program)) {
			original.call(program);
		}
	}
}
