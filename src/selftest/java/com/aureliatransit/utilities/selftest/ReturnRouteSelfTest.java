package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.screen.ReturnRouteScreen;
import com.aureliatransit.utilities.route.ReturnRoute;
import net.minecraft.client.MinecraftClient;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.operation.DeleteDataRequest;
import org.mtr.mod.InitClient;
import org.mtr.mod.packet.PacketDeleteData;

import java.util.ArrayList;
import java.util.List;

/** Return route: plan the opposite direction of a route, create it in MTR, check it came back, delete it again. */
final class ReturnRouteSelfTest {

	private static final String NAME = "ATU selftest return";
	private static long originalId;
	private static List<Long> plannedPlatforms = List.of();
	private static List<Long> originalDepots = List.of();
	private static long createdId;

	private ReturnRouteSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		test.onFinish(ReturnRouteSelfTest::deleteCreated);
		Tools.open(test, "Return route", ReturnRouteScreen.class);
		test.act("plan the return of a route", () -> {
			final ReturnRouteScreen screen = (ReturnRouteScreen) MinecraftClient.getInstance().currentScreen;
			final Route route = MtrBridge.dashboardData().routes.stream().filter(r -> r.getRoutePlatforms().size() >= 2).findFirst().orElse(null);
			test.check("a route with two or more stops", route != null, "");
			if (route == null) {
				return;
			}
			originalId = route.getId();
			originalDepots = route.depots.stream().map(Depot::getId).toList();
			screen.selectRouteForTest(route);
			final List<ReturnRoute.Choice> plan = screen.planForTest();
			final List<Long> originalStations = new ArrayList<>();
			route.getRoutePlatforms().forEach(data -> originalStations.add(data.platform.area == null ? 0 : data.platform.area.getId()));
			final List<Long> plannedStations = new ArrayList<>();
			plan.forEach(choice -> {
				final Platform platform = MtrBridge.dashboardData().platformIdMap.get(choice.platformId());
				plannedStations.add(platform == null || platform.area == null ? 0 : platform.area.getId());
			});
			final List<Long> reversed = new ArrayList<>(originalStations);
			java.util.Collections.reverse(reversed);
			test.check("return visits the stations in reverse", plannedStations.equals(reversed), plannedStations + " vs " + originalStations);
			test.check("ends keep their platform", plan.get(0).platformId() == route.getRoutePlatforms().get(route.getRoutePlatforms().size() - 1).getPlatform().getId() && !plan.get(plan.size() - 1).moved(), "");
			AtuSelfTest.LOGGER.info("[ATU-SELFTEST] return plan: {}", plan);
			plannedPlatforms = plan.stream().map(ReturnRoute.Choice::platformId).toList();
			screen.setNameForTest(NAME);
			Tools.press(test, "Create return route");
		});
		test.pause(3);
		test.screenshot("15_return_route");
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify the new route in MTR", () -> {
			final Route created = MtrBridge.dashboardData().routes.stream().filter(route -> route.getName().equals(NAME)).findFirst().orElse(null);
			test.check("return route exists in MTR", created != null, "");
			if (created == null) {
				return;
			}
			createdId = created.getId();
			final Route original = MtrBridge.dashboardData().routeIdMap.get(originalId);
			test.check("same colour as the original", original != null && original.getColor() == created.getColor(), "");
			final List<Long> stops = created.getRoutePlatforms().stream().map(data -> data.getPlatform() == null ? 0L : data.getPlatform().getId()).toList();
			test.check("stops as planned", stops.equals(plannedPlatforms), stops + " vs " + plannedPlatforms);
			final List<Long> depots = created.depots.stream().map(Depot::getId).toList();
			test.check("joined the original's depots", depots.containsAll(originalDepots) && depots.size() == originalDepots.size(), depots + " vs " + originalDepots);
			deleteCreated();
		});
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify it was deleted", () -> {
			test.check("return route deleted again", MtrBridge.dashboardData().routes.stream().noneMatch(route -> route.getName().equals(NAME)), "");
			test.check("depots no longer list it", MtrBridge.dashboardData().depots.stream().noneMatch(depot -> depot.getRouteIds().contains(createdId)), "");
			MinecraftClient.getInstance().setScreen(null);
		});
	}

	/** Also on abort: never leave the test route in the world. */
	private static void deleteCreated() {
		final List<Long> ids = new ArrayList<>();
		MtrBridge.dashboardData().routes.forEach(route -> {
			if (route.getName().equals(NAME)) {
				ids.add(route.getId());
			}
		});
		if (ids.isEmpty()) {
			return;
		}
		final DeleteDataRequest request = new DeleteDataRequest();
		ids.forEach(request::addRouteId);
		InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketDeleteData(request));
		// Should MTR's delete leave the id in a depot's route list, take it out the way MTR's depot screen would.
		final List<Depot> changed = new ArrayList<>();
		MtrBridge.dashboardData().depots.forEach(depot -> {
			if (depot.getRouteIds().removeIf(ids::contains)) {
				changed.add(depot);
			}
		});
		if (!changed.isEmpty()) {
			MtrBridge.send(update -> changed.forEach(update::addDepot));
		}
	}
}
