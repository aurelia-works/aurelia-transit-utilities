package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.TrainProxy;
import com.aureliatransit.utilities.perf.Perf;
import com.aureliatransit.utilities.perf.PerfSettings;
import com.aureliatransit.utilities.perf.TrainLod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.VehicleCar;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectBooleanImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.PositionAndRotation;
import org.mtr.mod.render.RenderVehicles;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * Far LOD tier for train cars. MTR 4.0.5 renders each car in a lambda ({@code lambda$render$15}) that looks the
 * vehicle up and then loads, queues and animates its model. Past the box distance ATU looks the vehicle up for its
 * colour only and queues a box instead, so the model is never requested (and never loaded) for that car.
 */
@Mixin(value = RenderVehicles.class, remap = false)
public abstract class RenderVehiclesMixin {

	@WrapOperation(method = "lambda$render$15", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/client/CustomResourceLoader;getVehicleById(Lorg/mtr/core/data/TransportMode;Ljava/lang/String;Ljava/util/function/Consumer;)V"))
	private static void atu$boxFarCars(TransportMode transportMode, String vehicleId, Consumer<ObjectBooleanImmutablePair<VehicleResource>> consumer, Operation<Void> original, @Local(argsOnly = true) VehicleExtension vehicle, @Local(argsOnly = true) ObjectObjectImmutablePair<VehicleCar, ObjectObjectImmutablePair<?, PositionAndRotation>> vehicleCarDetails) {
		final PerfSettings settings = Perf.settings();
		if (settings.trainBoxChunks > 0) {
			final PositionAndRotation positionAndRotation = vehicleCarDetails.right().right();
			final Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
			final double dx = positionAndRotation.position.x - camera.x;
			final double dy = positionAndRotation.position.y - camera.y;
			final double dz = positionAndRotation.position.z - camera.z;
			final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (TrainLod.tier(distance, VehicleRidingMovement.isRiding(vehicle.getId()), 0, settings.trainBoxChunks) == TrainLod.Tier.BOX) {
				final VehicleCar vehicleCar = vehicleCarDetails.left();
				original.call(transportMode, vehicleId, (Consumer<ObjectBooleanImmutablePair<VehicleResource>>) details -> TrainProxy.queue(vehicleCar, positionAndRotation, details.left().getColor()));
				return;
			}
		}
		original.call(transportMode, vehicleId, consumer);
	}
}
