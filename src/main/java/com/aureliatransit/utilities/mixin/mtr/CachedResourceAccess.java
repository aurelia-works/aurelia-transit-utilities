package com.aureliatransit.utilities.mixin.mtr;

import org.mtr.mod.resource.CachedResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CachedResource.class, remap = false)
public interface CachedResourceAccess {

	@Accessor("data")
	Object atu$data();
}
