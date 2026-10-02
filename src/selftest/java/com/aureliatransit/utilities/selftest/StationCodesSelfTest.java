package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import com.aureliatransit.utilities.client.screen.StationCodesScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Station codes: number a route AT01.., verify names in MTR core, remove, verify originals exactly. */
final class StationCodesSelfTest {

	private static long routeId;
	private static final Map<Long, String> ORIGINAL = new LinkedHashMap<>();

	private StationCodesSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		openScreen(test);
		test.act("number a route", () -> {
			final List<Route> routes = new ArrayList<>(MtrBridge.dashboardData().routes);
			routes.sort((a, b) -> Integer.compare(b.getRoutePlatforms().size(), a.getRoutePlatforms().size()));
			final Route route = routes.get(0);
			routeId = route.getId();
			ORIGINAL.clear();
			for (final RoutePlatformData data : route.getRoutePlatforms()) {
				if (data.platform != null && data.platform.area != null) {
					ORIGINAL.putIfAbsent(data.platform.area.getId(), data.platform.area.getName());
				}
			}
			final StationCodesScreen screen = (StationCodesScreen) MinecraftClient.getInstance().currentScreen;
			screen.selectRouteForTest(route);
			screen.prefixFieldForTest().setText("at");
			test.check("preview numbers every station", screen.previewForTest().size() == ORIGINAL.size() && screen.previewForTest().get(0).endsWith("|AT01"), screen.previewForTest().toString());
			press(test, "Apply codes");
		});
		test.pause(3);
		test.screenshot("60_codes");
		test.pause(17);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.screenshot("61_codes_in_mtr_dashboard");
		test.act("verify codes in MTR core", () -> {
			final List<String> names = new ArrayList<>();
			int index = 1;
			boolean ok = true;
			for (final Map.Entry<Long, String> entry : ORIGINAL.entrySet()) {
				final String name = MtrBridge.dashboardData().stationIdMap.get(entry.getKey()).getName();
				names.add(name);
				ok &= name.equals(entry.getValue() + "|AT0" + index++);
			}
			test.check("codes persisted in MTR, in route order", ok, names.toString());
		});
		openScreenFromDashboard(test);
		test.act("remove the codes", () -> {
			final StationCodesScreen screen = (StationCodesScreen) MinecraftClient.getInstance().currentScreen;
			screen.selectRouteForTest(MtrBridge.dashboardData().routeIdMap.get(routeId));
			screen.prefixFieldForTest().setText("AT");
			press(test, "Remove this prefix");
		});
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify original names", () -> {
			boolean ok = true;
			for (final Map.Entry<Long, String> entry : ORIGINAL.entrySet()) {
				ok &= MtrBridge.dashboardData().stationIdMap.get(entry.getKey()).getName().equals(entry.getValue());
			}
			test.check("original station names restored exactly", ok, ORIGINAL.values().toString());
			MinecraftClient.getInstance().setScreen(null);
		});
	}

	private static void openScreen(AtuSelfTest test) {
		test.openDashboard();
		openScreenFromDashboard(test);
	}

	private static void openScreenFromDashboard(AtuSelfTest test) {
		test.act("press ATU button", () -> press(test, "ATU"));
		test.waitFor("ATU tools open", () -> MinecraftClient.getInstance().currentScreen instanceof AtuToolsScreen);
		test.act("press Station codes", () -> press(test, "Station codes"));
		test.waitFor("codes screen open", () -> MinecraftClient.getInstance().currentScreen instanceof StationCodesScreen);
		test.pause(3);
	}

	private static void press(AtuSelfTest test, String label) {
		final ClickableWidget button = PresetSelfTest.findButton(label);
		if (button == null || !button.active) {
			test.check("button '" + label + "' usable", false, button == null ? "missing" : "inactive");
			return;
		}
		button.onClick(button.getX() + 1, button.getY() + 1);
	}
}
