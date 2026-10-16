package com.aureliatransit.utilities.client.perf;

import com.aureliatransit.utilities.perf.Perf;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * With a shader pack on, MTR 4.0.5 sets each part's model matrix by calling {@link ShaderProgram#bind()} for every draw
 * call ({@code VertexAttributeState.apply}). Under Iris that is a full {@code ExtendedShader} bind each time: texture
 * binds, every sampler, uniform and image update, and a framebuffer bind. Inside one of MTR's material batches only the
 * matrix changes between draws, because {@code ShaderManager.setupShaderBatchState} already did the full bind.
 * <p>
 * This uploads just the matrix uniforms instead: {@code ModelViewMat}, and for Iris programs the two it derives from it
 * ({@code iris_ModelViewMatInverse}, {@code iris_NormalMat}, computed the way Iris computes them). Any program other than
 * the one bound for the current batch still gets MTR's full bind.
 */
public final class ShaderBatchFastPath {

	private static ShaderProgram batchProgram;
	private static GlUniform modelViewInverse;
	private static GlUniform normalMatrix;
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Matrix4f INVERSE = new Matrix4f();
	private static final Matrix3f NORMAL = new Matrix3f();
	private static final float[] FLOATS_16 = new float[16];
	private static final float[] FLOATS_9 = new float[9];

	private ShaderBatchFastPath() {
	}

	/** End of {@code setupShaderBatchState}: the program MTR just bound for this batch. */
	public static void beginBatch() {
		batchProgram = null;
		if (!Perf.settings().shaderPackFastPath) {
			return;
		}
		final ShaderProgram program = RenderSystem.getShader();
		if (program == null || program.modelViewMat == null) {
			return;
		}
		batchProgram = program;
		// An Iris program prefixes "iris_" in getUniform; a vanilla program has neither uniform and returns null.
		modelViewInverse = program.getUniform("ModelViewMatInverse");
		normalMatrix = program.getUniform("NormalMat");
	}

	/** Start of {@code cleanupShaderBatchState}: back to MTR's full binds. */
	public static void endBatch() {
		batchProgram = null;
		modelViewInverse = null;
		normalMatrix = null;
	}

	/**
	 * Replaces one per-draw {@code bind()} when possible. MTR has already put the part's matrix into
	 * {@code ModelViewMat}. Returns false when the caller must do the full bind.
	 */
	public static boolean uploadMatrixOnly(ShaderProgram program) {
		if (program != batchProgram || program == null) {
			return false;
		}
		final GlUniform modelView = program.modelViewMat;
		modelView.upload();
		if (modelViewInverse != null || normalMatrix != null) {
			MATRIX.set(modelView.getFloatData());
			if (modelViewInverse != null) {
				MATRIX.invert(INVERSE).get(FLOATS_16);
				modelViewInverse.set(FLOATS_16);
				modelViewInverse.upload();
			}
			if (normalMatrix != null) {
				NORMAL.set(MATRIX).invert().transpose().get(FLOATS_9);
				normalMatrix.set(FLOATS_9);
				normalMatrix.upload();
			}
		}
		return true;
	}
}
