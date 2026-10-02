package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import com.aureliatransit.utilities.client.screen.DragListWidget;
import com.aureliatransit.utilities.client.screen.RouteStopsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;

import java.util.ArrayList;
import java.util.List;

/** U3: drag a stop with real mouse events, save, re-fetch from MTR core, then restore with the Move up button. */
final class RouteStopsSelfTest {

	private static long routeId;
	private static List<String> originalStops;
	private static List<String> expectedStops;

	private RouteStopsSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		openStopsScreen(test);
		test.screenshot("10_stops_empty");
		test.act("select route and drag first stop to the end", () -> {
			final List<Route> routes = new ArrayList<>(MtrBridge.dashboardData().routes);
			routes.sort((a, b) -> Integer.compare(b.getRoutePlatforms().size(), a.getRoutePlatforms().size()));
			test.check("dashboard data has a route with 2+ stops", !routes.isEmpty() && routes.get(0).getRoutePlatforms().size() >= 2, routes.isEmpty() ? "none" : routes.get(0).getName() + ": " + routes.get(0).getRoutePlatforms().size() + " stops");
			final Route route = routes.get(0);
			routeId = route.getId();
			originalStops = stops(route);
			expectedStops = new ArrayList<>(originalStops.subList(1, originalStops.size()));
			expectedStops.add(originalStops.get(0));
			screen().selectRouteForTest(route);
			final DragListWidget list = screen().stopListForTest();
			final double x = list.getX() + 30;
			final double y0 = list.getY() + DragListWidget.ROW_HEIGHT / 2.0;
			final double yEnd = list.getY() + DragListWidget.ROW_HEIGHT * originalStops.size() + 2;
			screen().mouseClicked(x, y0, 0);
			for (double y = y0; y <= yEnd; y += 4) {
				screen().mouseDragged(x, y, 0, 0, 4);
			}
		});
		test.pause(3);
		test.screenshot("11_stops_dragging");
		test.act("drop", () -> {
			final DragListWidget list = screen().stopListForTest();
			screen().mouseReleased(list.getX() + 30, list.getY() + DragListWidget.ROW_HEIGHT * originalStops.size() + 2, 0);
			final List<Integer> expectedOrder = new ArrayList<>();
			for (int i = 1; i < originalStops.size(); i++) {
				expectedOrder.add(i);
			}
			expectedOrder.add(0);
			test.check("drag moved first stop to the end", list.currentOriginalIndices().equals(expectedOrder), list.currentOriginalIndices().toString());
		});
		test.pause(3);
		test.screenshot("12_stops_dropped");
		test.act("press Save order", () -> press(test, "Save order"));
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		openStopsScreen(test);
		test.act("verify new order came back from MTR core", () -> {
			final Route route = MtrBridge.dashboardData().routeIdMap.get(routeId);
			test.check("reordered route persisted in MTR", route != null && stops(route).equals(expectedStops), route == null ? "route missing" : stops(route) + " expected " + expectedStops);
			// Restore with the buttons: select the last stop and move it up to the top.
			screen().selectRouteForTest(route);
			final DragListWidget list = screen().stopListForTest();
			list.setSelected(originalStops.size() - 1);
			for (int i = 0; i < originalStops.size() - 1; i++) {
				press(test, "Move up");
			}
			test.check("Move up restored the order on screen", list.getSelected() == 0, "selected " + list.getSelected());
			press(test, "Save order");
		});
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		openStopsScreen(test);
		test.act("verify original order restored", () -> {
			final Route route = MtrBridge.dashboardData().routeIdMap.get(routeId);
			test.check("original route order restored in MTR", route != null && stops(route).equals(originalStops), route == null ? "route missing" : stops(route).toString());
			MinecraftClient.getInstance().setScreen(null);
		});
	}

	private static void openStopsScreen(AtuSelfTest test) {
		test.openDashboard();
		test.act("press ATU button", () -> press(test, "ATU"));
		test.waitFor("ATU tools open", () -> MinecraftClient.getInstance().currentScreen instanceof AtuToolsScreen);
		test.act("press Route stop order", () -> press(test, "Route stop order"));
		test.waitFor("stops screen open", () -> MinecraftClient.getInstance().currentScreen instanceof RouteStopsScreen);
		test.pause(3);
	}

	private static RouteStopsScreen screen() {
		return (RouteStopsScreen) MinecraftClient.getInstance().currentScreen;
	}

	private static void press(AtuSelfTest test, String label) {
		final ClickableWidget button = PresetSelfTest.findButton(label);
		if (button == null || !button.active) {
			test.check("button '" + label + "' usable", false, button == null ? "missing" : "inactive");
			return;
		}
		button.onClick(button.getX() + 1, button.getY() + 1);
	}

	/** Each stop as "platformId:customDestination", in route order. */
	private static List<String> stops(Route route) {
		final List<String> result = new ArrayList<>();
		for (final RoutePlatformData data : route.getRoutePlatforms()) {
			result.add(data.getPlatform() == null ? "null" : data.getPlatform().getId() + ":" + data.getCustomDestination());
		}
		return result;
	}
}
