package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.perf.AsyncModels;
import com.aureliatransit.utilities.perf.DoorShare;
import com.aureliatransit.utilities.perf.Perf;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.resource.CachedResource;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.VehicleModel;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MTR 4.0.5 {@code VehicleResource}: the lambdas that build a car's models from its model files. */
@Mixin(value = VehicleResource.class, remap = false)
public abstract class VehicleResourceBuildMixin {

	/**
	 * The car's model files are about to be built one per tick: start parsing all of them in the background at once
	 * ({@link AsyncModels}) instead of one after another.
	 */
	@Inject(method = "lambda$cachedVehicleResourceInitializer$18", at = @At("HEAD"))
	private void atu$prefetchModels(ObjectArrayList<VehicleModel> modelsToInitialize, boolean force, ObjectArrayList<VehicleModel> allModelsList, int modelLifespan, CallbackInfoReturnable<CachedResource<?>> cir) {
		if (force) {
			return;
		}
		for (final VehicleModel vehicleModel : modelsToInitialize) {
			if (vehicleModel != null) {
				((AsyncModels.Request) (Object) vehicleModel).atu$request();
			}
		}
	}

	/** Turns a car's part lists into its four models: link doors open and doors closed first ({@link DoorShare}). */
	@Inject(method = "lambda$cachedVehicleResourceInitializer$17", at = @At("HEAD"))
	private static void atu$linkDoorStates(Object2ObjectOpenHashMap<?, ?> materialGroupsModel, Object2ObjectOpenHashMap<?, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>> objModelsModel, int modelLifespan, Object2ObjectOpenHashMap<?, ?> materialGroupsModelDoorsClosed, Object2ObjectOpenHashMap<?, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>> objModelsModelDoorsClosed, Object2ObjectOpenHashMap<?, ?> materialGroupsBogie1Model, Object2ObjectOpenHashMap<?, ?> objModelsBogie1Model, Object2ObjectOpenHashMap<?, ?> materialGroupsBogie2Model, Object2ObjectOpenHashMap<?, ?> objModelsBogie2Model, ObjectArrayList<?> floors, ObjectArrayList<?> doorways, CallbackInfoReturnable<?> cir) {
		if (!Perf.settings().mergeTrainParts) {
			return;
		}
		try {
			DoorShare.link(objModelsModel, objModelsModelDoorsClosed, wrapper -> ((OptimizedModelWrapper.ObjModelWrapper) wrapper).objModel);
		} catch (RuntimeException e) {
			Perf.LOGGER.warn("Could not link a train's door states; its parts are uploaded for each", e);
		}
	}
}
