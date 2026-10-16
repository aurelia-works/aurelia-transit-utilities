package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.client.perf.DoorChecks;
import org.mtr.mapping.holder.Box;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mod.render.PositionAndRotation;
import org.mtr.mod.render.RenderVehicleHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The per-frame doorway search for platforms and platform screen doors, remembered while no block changes ({@link DoorChecks}). */
@Mixin(value = RenderVehicleHelper.class, remap = false)
public abstract class RenderVehicleHelperMixin {

	@Unique
	private static boolean atu$failed;
	/** Set while MTR's own check runs as a comparison. */
	@Unique
	private static boolean atu$bypass;

	@Inject(method = "canOpenDoors", at = @At("HEAD"), cancellable = true)
	private static void atu$rememberedDoorCheck(Box doorway, PositionAndRotation positionAndRotation, double doorValue, CallbackInfoReturnable<Boolean> cir) {
		if (atu$bypass || atu$failed || !DoorChecks.enabled()) {
			return;
		}
		final ClientWorld clientWorld = MinecraftClient.getInstance().getWorldMapped();
		if (clientWorld == null) {
			return;
		}
		try {
			final boolean canOpenDoors = DoorChecks.canOpenDoors(clientWorld, doorway, positionAndRotation, doorValue);
			if (DoorChecks.shouldCompare()) {
				// MTR's own search too (it hands the same door value to the same doors again): the answers must agree.
				atu$bypass = true;
				final boolean mtr;
				try {
					mtr = RenderVehicleHelper.canOpenDoors(doorway, positionAndRotation, doorValue);
				} finally {
					atu$bypass = false;
				}
				if (mtr != canOpenDoors) {
					atu$failed = true;
					DoorChecks.mismatch();
					cir.setReturnValue(mtr);
					return;
				}
			}
			cir.setReturnValue(canOpenDoors);
		} catch (RuntimeException e) {
			atu$failed = true;
			com.aureliatransit.utilities.perf.Perf.LOGGER.error("Remembered door checks failed; MTR's own check runs for the rest of this session", e);
		}
	}
}
