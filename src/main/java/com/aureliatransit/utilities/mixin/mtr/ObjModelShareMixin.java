package com.aureliatransit.utilities.mixin.mtr;

import com.aureliatransit.utilities.perf.DoorShare;
import org.mtr.mapping.mapper.OptimizedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the {@link DoorShare} link of an OBJ part. */
@Mixin(value = OptimizedModel.ObjModel.class, remap = false)
public abstract class ObjModelShareMixin implements DoorShare.Holder {

	@Unique
	private DoorShare atu$doorShare;

	@Override
	public DoorShare atu$doorShare() {
		return atu$doorShare;
	}

	@Override
	public void atu$setDoorShare(DoorShare share) {
		atu$doorShare = share;
	}
}
