package com.aureliatransit.utilities.mixin;

import com.aureliatransit.utilities.client.perf.PerfOverlay;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** ATU's MTR performance lines on the right side of the F3 screen. */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

	@Inject(method = "getRightText", at = @At("RETURN"))
	private void atu$addPerfLines(CallbackInfoReturnable<List<String>> cir) {
		PerfOverlay.addLines(cir.getReturnValue());
	}
}
