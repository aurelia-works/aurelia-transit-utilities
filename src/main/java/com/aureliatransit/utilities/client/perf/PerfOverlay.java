package com.aureliatransit.utilities.client.perf;

import com.aureliatransit.utilities.perf.AsyncModels;
import com.aureliatransit.utilities.perf.AsyncPacks;
import com.aureliatransit.utilities.perf.Perf;
import com.aureliatransit.utilities.perf.PerfSettings;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.mtr.mapping.render.tool.Utilities;

import java.util.List;
import java.util.Locale;

/** F3 lines: MTR draw calls and time in MTR's batched renderer per frame, and which ATU fixes are on. */
public final class PerfOverlay {

	private PerfOverlay() {
	}

	public static void register() {
		WorldRenderEvents.END.register(context -> {
			Perf.STATS.frame(System.nanoTime());
			GlGarbage.drain();
		});
	}

	public static void addLines(List<String> lines) {
		final PerfSettings settings = Perf.settings();
		if (!settings.debugOverlay) {
			return;
		}
		lines.add("");
		if (!Perf.mtrChecked()) {
			lines.add("[ATU] MTR fixes off: MTR version not checked");
			return;
		}
		if (Perf.STATS.hasResult()) {
			lines.add(String.format(Locale.ROOT, "[ATU] MTR draws/frame: %.0f", Perf.STATS.drawsPerFrame()));
			lines.add(String.format(Locale.ROOT, "[ATU] MTR render: %.2f ms/frame (all of MTR %.2f ms); door checks %d remembered, %d searched", Perf.STATS.renderMillisPerFrame(), Perf.STATS.mtrMillisPerFrame(), DoorChecks.hits(), DoorChecks.misses()));
		}
		lines.add(String.format(Locale.ROOT, "[ATU] MTR model builds: %d, %.0f ms total, longest %.0f ms; freed %d GPU arrays", Perf.STATS.loads(), Perf.STATS.loadMillis(), Perf.STATS.maxLoadMillis(), GlGarbage.freedArrays()));
		lines.add(String.format(Locale.ROOT, "[ATU] OBJ reading %.0f ms (%d left to MTR); background %d started, %d built, wait %.0f ms, %d meshes; %d arrays shared", Perf.STATS.parseMillis(), Perf.STATS.parseFallbacks(), AsyncModels.started(), AsyncModels.built(), AsyncModels.averageWaitMillis(), AsyncPacks.built(), Perf.STATS.sharedArrays()));
		lines.add(String.format(Locale.ROOT, "[ATU] merge %s, shader-pack path %s (%s)", onOff(settings.mergeTrainParts), onOff(settings.shaderPackFastPath), Utilities.canUseCustomShader() ? "no shader pack" : "shader pack"));
	}

	private static String onOff(boolean value) {
		return value ? "on" : "off";
	}
}
