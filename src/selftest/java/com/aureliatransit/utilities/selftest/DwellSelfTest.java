package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import com.aureliatransit.utilities.client.screen.PlatformDwellScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** U4 dwell: untick one platform of a route, Set 42.5 s, then +5 s; verify against MTR core; restore. */
final class DwellSelfTest {

	private static final Map<Long, Long> ORIGINAL = new LinkedHashMap<>();
	private static long untickedId;

	private DwellSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		openScreen(test);
		test.act("pick route, untick one, set 42.5 s", () -> {
			final List<Route> routes = new ArrayList<>(MtrBridge.dashboardData().routes);
			routes.sort((a, b) -> Integer.compare(b.getRoutePlatforms().size(), a.getRoutePlatforms().size()));
			final Route route = routes.get(0);
			ORIGINAL.clear();
			for (final RoutePlatformData data : route.getRoutePlatforms()) {
				ORIGINAL.put(data.platform.getId(), data.platform.getDwellTime());
			}
			test.check("route has 2+ platforms", ORIGINAL.size() >= 2, ORIGINAL.toString());
			screen().selectScopeForTest(route);
			test.check("route scope ticks all its platforms", screen().tickedForTest().size() == ORIGINAL.size(), screen().tickedForTest().size() + " ticked");
			final Platform first = MtrBridge.dashboardData().platformIdMap.get(ORIGINAL.keySet().iterator().next());
			untickedId = first.getId();
			screen().clickPlatformForTest(first);
			test.check("clicking a row unticks it", !screen().tickedForTest().contains(first), "");
			screen().dwellFieldForTest().setText("0");
			test.check("invalid dwell keeps Set disabled", !button(test, "Set dwell").active, "");
			screen().dwellFieldForTest().setText("42.5");
			press(test, "Set dwell");
			press(test, "+5 s");
		});
		test.pause(3);
		test.screenshot("20_dwell");
		test.pause(17);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		openScreen(test);
		test.act("verify dwell came back from MTR core, then restore", () -> {
			final StringBuilder detail = new StringBuilder();
			boolean ok = true;
			for (final Map.Entry<Long, Long> entry : ORIGINAL.entrySet()) {
				final long actual = MtrBridge.dashboardData().platformIdMap.get(entry.getKey()).getDwellTime();
				final long expected = entry.getKey() == untickedId ? entry.getValue() : 47_500;
				ok &= actual == expected;
				detail.append(actual).append(entry.getKey() == untickedId ? "(unticked) " : " ");
			}
			test.check("dwell persisted in MTR (unticked platform untouched)", ok, detail.toString().strip());
			MtrBridge.send(request -> ORIGINAL.forEach((id, millis) -> {
				final Platform platform = MtrBridge.dashboardData().platformIdMap.get(id);
				platform.setDwellTime(millis);
				request.addPlatform(platform);
			}));
		});
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify dwell restored", () -> {
			boolean ok = true;
			for (final Map.Entry<Long, Long> entry : ORIGINAL.entrySet()) {
				ok &= MtrBridge.dashboardData().platformIdMap.get(entry.getKey()).getDwellTime() == entry.getValue();
			}
			test.check("original dwell restored in MTR", ok, ORIGINAL.toString());
			MinecraftClient.getInstance().setScreen(null);
		});
	}

	private static void openScreen(AtuSelfTest test) {
		test.openDashboard();
		test.act("press ATU button", () -> press(test, "ATU"));
		test.waitFor("ATU tools open", () -> MinecraftClient.getInstance().currentScreen instanceof AtuToolsScreen);
		test.act("press Platform dwell times", () -> press(test, "Platform dwell times"));
		test.waitFor("dwell screen open", () -> MinecraftClient.getInstance().currentScreen instanceof PlatformDwellScreen);
		test.pause(3);
	}

	private static PlatformDwellScreen screen() {
		return (PlatformDwellScreen) MinecraftClient.getInstance().currentScreen;
	}

	private static ClickableWidget button(AtuSelfTest test, String label) {
		final ClickableWidget button = PresetSelfTest.findButton(label);
		if (button == null) {
			test.check("button '" + label + "' exists", false, "missing");
		}
		return button;
	}

	private static void press(AtuSelfTest test, String label) {
		final ClickableWidget button = button(test, label);
		if (button != null && !button.active) {
			test.check("button '" + label + "' usable", false, "inactive");
		} else if (button != null) {
			button.onClick(button.getX() + 1, button.getY() + 1);
		}
	}
}
