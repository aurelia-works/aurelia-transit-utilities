package com.aureliatransit.utilities.mixin.mtr;

import org.mtr.mapping.render.object.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VertexBuffer.class, remap = false)
public interface VertexBufferAccess {

	@Accessor("id")
	int atu$id();
}
