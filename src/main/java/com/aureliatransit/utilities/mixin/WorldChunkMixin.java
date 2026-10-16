package com.aureliatransit.utilities.mixin;

import com.aureliatransit.utilities.client.perf.BlockChanges;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client block changes for {@link BlockChanges}. */
@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {

	@Shadow
	@Final
	World world;

	@Inject(method = "setBlockState", at = @At("HEAD"))
	private void atu$blockChanged(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
		if (world.isClient()) {
			BlockChanges.changed();
		}
	}
}
