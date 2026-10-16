package com.aureliatransit.utilities.mixin.mtr;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = OptimizedModelWrapper.class, remap = false)
public interface OptimizedModelWrapperAccess {

	@Accessor("optimizedModel")
	OptimizedModel atu$optimizedModel();
}
