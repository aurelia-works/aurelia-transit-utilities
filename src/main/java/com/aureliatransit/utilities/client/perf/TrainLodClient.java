package com.aureliatransit.utilities.client.perf;

import com.aureliatransit.utilities.perf.Perf;
import com.aureliatransit.utilities.perf.PerfSettings;
import com.aureliatransit.utilities.perf.TrainLod;
import org.joml.Matrix4f;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Middle LOD tier: a train car past {@link PerfSettings#trainInteriorChunks} is queued without its interior parts
 * (MTR render stages INTERIOR and INTERIOR_TRANSLUCENT, i.e. shader types CUTOUT_BRIGHT and TRANSLUCENT_BRIGHT).
 * Flagged around MTR's vehicle queue call, applied inside {@code OptimizedRenderer.queue}; both on the render thread.
 */
public final class TrainLodClient {

	private static boolean vehicleQueue;
	/** Exterior-only part lists, per uploaded model; weak so rebuilt models drop out. */
	private static final Map<OptimizedModel, List<VertexArray>> EXTERIOR_PARTS = new WeakHashMap<>();

	private TrainLodClient() {
	}

	/** Around MTR's queue call for one train car's model (not rails, signs or other MTR models). */
	public static void beginVehicleQueue() {
		vehicleQueue = true;
	}

	public static void endVehicleQueue() {
		vehicleQueue = false;
	}

	/** The parts {@code OptimizedRenderer.queue} should batch for this model right now. */
	public static List<VertexArray> partsToQueue(OptimizedModel model, List<VertexArray> parts, VertexAttributeState state) {
		if (!vehicleQueue || state.matrix4f == null) {
			return parts;
		}
		final int interiorChunks = Perf.settings().trainInteriorChunks;
		if (interiorChunks <= 0) {
			return parts;
		}
		// MTR builds the model matrix relative to the camera, so its translation is the car's distance from it. The
		// ridden train is never this far: inside it the camera is within a car length.
		final Matrix4f matrix = state.matrix4f.data;
		final double distance = Math.sqrt(matrix.m30() * matrix.m30() + matrix.m31() * matrix.m31() + matrix.m32() * matrix.m32());
		if (TrainLod.tier(distance, false, interiorChunks, 0) != TrainLod.Tier.NO_INTERIOR) {
			return parts;
		}
		return EXTERIOR_PARTS.computeIfAbsent(model, key -> {
			final List<VertexArray> exterior = new ArrayList<>(parts.size());
			for (final VertexArray part : parts) {
				final OptimizedModel.ShaderType shaderType = part.materialProperties.shaderType;
				if (shaderType != OptimizedModel.ShaderType.CUTOUT_BRIGHT && shaderType != OptimizedModel.ShaderType.TRANSLUCENT_BRIGHT) {
					exterior.add(part);
				}
			}
			return exterior;
		});
	}
}
