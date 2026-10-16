package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.perf.AsyncModels;
import com.aureliatransit.utilities.perf.FastObjLoader;
import com.aureliatransit.utilities.perf.Perf;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.mapping.holder.Identifier;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.mod.generated.resource.VehicleModelSchema;
import org.mtr.mod.render.DynamicVehicleModel;
import org.mtr.mod.resource.CachedResource;
import org.mtr.mod.resource.CustomResourceTools;
import org.mtr.mod.resource.ModelProperties;
import org.mtr.mod.resource.PositionDefinitions;
import org.mtr.mod.resource.ResourceProvider;
import org.mtr.mod.resource.VehicleModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Background loading of a car's OBJ file ({@link AsyncModels}): {@code createModel} returns null ("not ready", which
 * MTR's caches handle) until a worker has parsed the file, then builds the model with MTR's own code from the parsed
 * meshes. Other model kinds (Blockbench, MQO) and forced builds run as MTR wrote them.
 */
@Mixin(value = VehicleModel.class, remap = false)
public abstract class VehicleModelMixin extends VehicleModelSchema implements AsyncModels.Request {

	@Shadow
	@Final
	CachedResource<DynamicVehicleModel> cachedModel;

	@Unique
	private AsyncModels.Job atu$job;
	@Unique
	private long atu$createStart;
	@Unique
	private long atu$createMergeStart;

	private VehicleModelMixin(ReaderBase readerBase, ResourceProvider resourceProvider) {
		super(readerBase, resourceProvider);
	}

	@Inject(method = "createModel", at = @At("HEAD"), cancellable = true)
	private void atu$loadInBackground(ModelProperties modelProperties, PositionDefinitions positionDefinitions, String id, CallbackInfoReturnable<DynamicVehicleModel> cir) {
		atu$createStart = System.nanoTime();
		atu$createMergeStart = Perf.STATS.buildMergeNanos();
		if (atu$job != null && atu$job.isDone()) {
			final AsyncModels.Job job = atu$job;
			atu$job = null;
			final FastObjLoader.Prepared prepared = AsyncModels.claim(job);
			if (prepared != null) {
				// Build now, from the parsed file (ObjModelLoaderMixin takes it).
				AsyncModels.offer(prepared);
				return;
			}
			if (!job.isDropped()) {
				// ATU's reader declined the file: MTR reads it here, as it always did.
				return;
			}
		}
		if (atu$job == null && !atu$start()) {
			return;
		}
		AsyncModels.deferred();
		cir.setReturnValue(null);
	}

	@Inject(method = "createModel", at = @At("RETURN"))
	private void atu$endBuild(ModelProperties modelProperties, PositionDefinitions positionDefinitions, String id, CallbackInfoReturnable<DynamicVehicleModel> cir) {
		AsyncModels.clearOffer();
		Perf.STATS.buildCreate(System.nanoTime() - atu$createStart, Perf.STATS.buildMergeNanos() - atu$createMergeStart);
	}

	@WrapOperation(method = "createModel", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/resource/ModelResourceLoader;loadModel(Ljava/lang/String;Lorg/mtr/mapping/holder/Identifier;ZLorg/mtr/mod/resource/ResourceProvider;)Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectAVLTreeMap;"))
	private Object2ObjectAVLTreeMap<?, ?> atu$timeLoad(String modelResource, Identifier identifier, boolean flag, ResourceProvider provider, Operation<Object2ObjectAVLTreeMap<?, ?>> original) {
		final long start = System.nanoTime();
		try {
			return original.call(modelResource, identifier, flag, provider);
		} finally {
			Perf.STATS.buildCreateLoad(System.nanoTime() - start);
		}
	}

	/** Prefetch from the car's model list: only models MTR has not built yet. */
	@Override
	public boolean atu$request() {
		return atu$job != null || ((CachedResourceAccess) (Object) cachedModel).atu$data() == null && atu$start();
	}

	/** Starts the background parse if this model may wait for it; false when MTR should build it right now. */
	@Unique
	private boolean atu$start() {
		if (atu$job != null) {
			return true;
		}
		if (!modelResource.endsWith(".obj") || !AsyncModels.mayDefer(MinecraftClient.getInstance().getOverlay() != null)) {
			return false;
		}
		// Read on the render thread, through the same provider (and cache) MTR's own load uses next.
		atu$job = AsyncModels.submit(resourceProvider.get(CustomResourceTools.formatIdentifierWithDefault(modelResource, "obj")));
		return atu$job != null;
	}
}
