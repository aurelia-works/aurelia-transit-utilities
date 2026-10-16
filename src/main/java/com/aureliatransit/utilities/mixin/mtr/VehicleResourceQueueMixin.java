package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.TrainLodClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.OptimizedRendererWrapper;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks MTR's queue call for a train car's model (MTR 4.0.5: the lambda in {@code VehicleResource.queue}). */
@Mixin(value = VehicleResource.class, remap = false)
public abstract class VehicleResourceQueueMixin {

	@WrapOperation(method = "lambda$queue$22", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/resource/OptimizedRendererWrapper;queue(Lorg/mtr/mod/resource/OptimizedModelWrapper;Lorg/mtr/mapping/mapper/GraphicsHolder;I)V"))
	private static void atu$markVehicleQueue(OptimizedRendererWrapper renderer, OptimizedModelWrapper model, GraphicsHolder graphicsHolder, int light, Operation<Void> original) {
		TrainLodClient.beginVehicleQueue();
		try {
			original.call(renderer, model, graphicsHolder, light);
		} finally {
			TrainLodClient.endVehicleQueue();
		}
	}
}
