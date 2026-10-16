package com.aureliatransit.utilities.mixin.mtr;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = OptimizedModel.class, remap = false)
public interface OptimizedModelAccess {

	@Accessor("DEFAULT_MAPPING")
	static VertexAttributeMapping atu$defaultMapping() {
		throw new AssertionError();
	}
}
