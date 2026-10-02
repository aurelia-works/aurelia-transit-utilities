package com.aureliatransit.utilities.selftest.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev self-test only (never shipped): the game never captures the owner's mouse. Because the cursor is never locked,
 * {@code unlockCursor} also never re-centres it.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

	@Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
	private void atuSelftest$neverLock(CallbackInfo ci) {
		ci.cancel();
	}
}
