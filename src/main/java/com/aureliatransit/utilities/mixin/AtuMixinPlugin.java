package com.aureliatransit.utilities.mixin;

import com.aureliatransit.utilities.perf.Perf;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the mixins into MTR (package {@code mixin.mtr}) only on MTR versions they were checked against. On any other
 * version MTR runs untouched and the log says why.
 */
public final class AtuMixinPlugin implements IMixinConfigPlugin {

	private static final String MTR_PACKAGE = "com.aureliatransit.utilities.mixin.mtr.";
	private Boolean mtrChecked;

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (!mixinClassName.startsWith(MTR_PACKAGE)) {
			return true;
		}
		if (mtrChecked == null) {
			mtrChecked = Perf.mtrChecked();
			if (!mtrChecked) {
				Perf.LOGGER.warn("This MTR version was not checked with ATU's performance fixes; they stay off and MTR runs as it is");
			}
		}
		return mtrChecked;
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
