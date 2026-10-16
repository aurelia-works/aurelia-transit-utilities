package com.aureliatransit.utilities.mixin.mtr;

import org.mtr.core.data.Position;
import org.mtr.core.generated.data.RailSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RailSchema.class, remap = false)
public interface RailSchemaAccess {

	@Accessor("position1")
	Position atu$position1();
}
