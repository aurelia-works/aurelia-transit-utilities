package com.aureliatransit.utilities.perf;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/** Shared state of ATU's MTR performance fixes: settings, counters and whether the mixins are active at all. */
public final class Perf {

	public static final Logger LOGGER = LoggerFactory.getLogger("ATU performance");
	public static final PerfStats STATS = new PerfStats();
	private static PerfSettings settings;

	private Perf() {
	}

	public static PerfSettings settings() {
		if (settings == null) {
			settings = PerfSettings.load(file());
		}
		return settings;
	}

	public static void save() {
		try {
			settings().save(file());
		} catch (IOException e) {
			LOGGER.warn("Could not save {}", file(), e);
		}
	}

	/** True when the installed MTR is a version the mixins were checked against (the mixin plugin uses the same test). */
	public static boolean mtrChecked() {
		return FabricLoader.getInstance().getModContainer("mtr").map(mod -> PerfCompat.isChecked(mod.getMetadata().getVersion().getFriendlyString())).orElse(false);
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(PerfSettings.FILE_NAME);
	}
}
