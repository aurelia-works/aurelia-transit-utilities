package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.RailLodClient;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mod.render.RenderRails;
import org.mtr.mod.resource.RailResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rail LOD ({@link RailLodClient}). In MTR 4.0.5, {@code lambda$renderRailStandard$17} walks one rail for one 3D style
 * and {@code lambda$renderRailStandard$16} renders one piece of it.
 */
@Mixin(value = RenderRails.class, remap = false)
public abstract class RenderRailsMixin {

	@Inject(method = "lambda$renderRailStandard$17", at = @At("HEAD"))
	private static void atu$beginRail(Rail rail, ClientWorld clientWorld, boolean flip, boolean[] renderType, RailResource railResource, CallbackInfo ci) {
		RailLodClient.beginRail(rail);
	}

	@Inject(method = "lambda$renderRailStandard$16", at = @At("HEAD"), cancellable = true)
	private static void atu$railLod(ClientWorld clientWorld, RailResource railResource, boolean flip, boolean[] renderType, BlockPos blockPos, double x1, double z1, double x2, double z2, double x3, double z3, double x4, double z4, double y1, double y2, CallbackInfo ci) {
		if (RailLodClient.renderPiece(clientWorld, railResource, flip, renderType, blockPos, x1, z1, x3, z3, y1, y2)) {
			ci.cancel();
		}
	}
}
