package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.perf.AsyncModels;
import com.aureliatransit.utilities.perf.FastObjLoader;
import com.mojang.blaze3d.systems.RenderSystem;
import com.aureliatransit.utilities.perf.Perf;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.obj.AtlasManager;
import org.mtr.mapping.render.obj.ObjModelLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Reads OBJ files with {@link FastObjLoader} (same meshes as javagl gives MTR); files it does not take go to MTR. */
@Mixin(value = ObjModelLoader.class, remap = false)
public abstract class ObjModelLoaderMixin {

	@Unique
	private static volatile boolean atu$failed;

	@Inject(method = "loadModel(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;Lorg/mtr/mapping/render/obj/AtlasManager;Z)Ljava/util/Map;", at = @At("HEAD"), cancellable = true)
	private static void atu$fastParse(String objString, Function<String, String> mtlResolver, Function<String, Identifier> textureResolver, AtlasManager atlasManager, boolean splitModel, CallbackInfoReturnable<Map<String, List<RawMesh>>> cir) {
		if (atu$failed || objString == null || !Perf.settings().fasterModelLoading) {
			return;
		}
		// Parsed in the background for this model build (AsyncModels): only materials and checks are left.
		final FastObjLoader.Prepared prepared = RenderSystem.isOnRenderThread() ? AsyncModels.take(objString, splitModel) : null;
		if (prepared != null) {
			final long start = System.nanoTime();
			try {
				cir.setReturnValue(FastObjLoader.assemble(prepared, mtlResolver, textureResolver, atlasManager));
				Perf.STATS.buildAssemble(System.nanoTime() - start);
				return;
			} catch (RuntimeException e) {
				atu$failed = true;
				Perf.LOGGER.error("Fast OBJ reading failed; using MTR's own OBJ reader for the rest of this session", e);
				return;
			}
		}
		final long start = System.nanoTime();
		try {
			final Map<String, List<RawMesh>> result = FastObjLoader.load(objString, mtlResolver, textureResolver, atlasManager, splitModel);
			if (result != null) {
				cir.setReturnValue(result);
			} else {
				Perf.STATS.parseFallback();
			}
		} catch (RuntimeException e) {
			atu$failed = true;
			Perf.LOGGER.error("Fast OBJ reading failed; using MTR's own OBJ reader for the rest of this session", e);
		} finally {
			Perf.STATS.parse(System.nanoTime() - start);
			if (RenderSystem.isOnRenderThread()) {
				Perf.STATS.buildParse(System.nanoTime() - start);
			}
		}
	}
}
