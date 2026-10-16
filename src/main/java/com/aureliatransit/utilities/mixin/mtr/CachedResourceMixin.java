package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.perf.AsyncModels;
import com.aureliatransit.utilities.perf.Perf;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.resource.CachedResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * MTR 4.0.5 keeps every {@link CachedResource} ever made in a static list. Train models build new nested caches each
 * time they reload, and each one keeps its geometry suppliers alive, so the heap grows for the whole session. With
 * "free unused models" on, new caches are listed weakly: the expiry tick still sees every cache somebody can reach,
 * and the rest can be collected. Also times model builds for the F3 lines.
 */
@Mixin(value = CachedResource.class, remap = false)
public abstract class CachedResourceMixin {

	@Unique
	private static final List<WeakReference<CachedResource<?>>> ATU$WEAK = new ArrayList<>();
	@Shadow
	private static boolean canFetchCache;

	@Unique
	private static int atu$depth;

	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;add(Ljava/lang/Object;)Z"))
	private boolean atu$registerWeakly(ObjectArrayList<CachedResource<?>> list, Object cachedResource, Operation<Boolean> original) {
		if (Perf.settings().freeUnusedModels) {
			synchronized (ATU$WEAK) {
				return ATU$WEAK.add(new WeakReference<>((CachedResource<?>) cachedResource));
			}
		}
		return original.call(list, cachedResource);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;forEach(Ljava/util/function/Consumer;)V"))
	private static void atu$tickWeak(ObjectArrayList<CachedResource<?>> list, Consumer<CachedResource<?>> expire, Operation<Void> original) {
		original.call(list, expire);
		synchronized (ATU$WEAK) {
			ATU$WEAK.removeIf(reference -> {
				final CachedResource<?> cachedResource = reference.get();
				if (cachedResource == null) {
					return true;
				}
				expire.accept(cachedResource);
				return false;
			});
		}
	}

	@WrapOperation(method = "getData", at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"))
	private Object atu$timeBuild(Supplier<?> supplier, Operation<Object> original, @Local(argsOnly = true) boolean force) {
		AsyncModels.enterBuild(force);
		// Caches nest (a train's cache builds its model caches); only the outermost build is one stall.
		if (atu$depth++ > 0) {
			try {
				return original.call(supplier);
			} finally {
				atu$depth--;
				AsyncModels.exitBuild(force);
			}
		}
		final long start = System.nanoTime();
		try {
			return original.call(supplier);
		} finally {
			atu$depth--;
			AsyncModels.exitBuild(force);
			Perf.STATS.load(System.nanoTime() - start);
		}
	}

	/**
	 * MTR builds one cache per client tick. When the build was only "not ready yet" (a model still loading in the
	 * background), it cost nothing, so the next cache may build in the same tick.
	 */
	@Inject(method = "getData", at = @At("RETURN"))
	private void atu$returnUnusedSlot(boolean force, CallbackInfoReturnable<Object> cir) {
		if (atu$depth == 0 && AsyncModels.takeDeferred()) {
			canFetchCache = true;
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private static void atu$tickBackgroundLoading(CallbackInfo ci) {
		AsyncModels.tick();
	}
}
