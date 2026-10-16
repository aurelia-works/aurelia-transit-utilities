package com.aureliatransit.utilities.mixin.mtr;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.RawModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = OptimizedModel.ObjModel.class, remap = false)
public interface ObjModelAccess {

	@Accessor("rawModel")
	RawModel atu$rawModel();
}
