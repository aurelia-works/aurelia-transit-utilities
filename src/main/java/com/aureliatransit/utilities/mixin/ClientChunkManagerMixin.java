package com.aureliatransit.utilities.mixin;

import com.aureliatransit.utilities.client.perf.BlockChanges;
import net.minecraft.client.world.ClientChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chunks arriving, leaving or moving out of the client's reach, for {@link BlockChanges}. */
@Mixin(ClientChunkManager.class)
public abstract class ClientChunkManagerMixin {

	@Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
	private void atu$chunkLoaded(CallbackInfoReturnable<?> cir) {
		BlockChanges.changed();
	}

	@Inject(method = {"unload", "setChunkMapCenter", "updateLoadDistance"}, at = @At("HEAD"))
	private void atu$chunksChanged(CallbackInfo ci) {
		BlockChanges.changed();
	}
}
