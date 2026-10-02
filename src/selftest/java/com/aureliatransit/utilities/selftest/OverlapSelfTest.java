package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import com.aureliatransit.utilities.client.screen.StationOverlapScreen;
import com.aureliatransit.utilities.overlap.OverlapResolver;
import com.aureliatransit.utilities.overlap.Zones;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;

import java.util.ArrayList;
import java.util.List;

/**
 * U1: stretch station B's zone over a platform of station A (making an overlap), check ATU reads MTR's choice right,
 * give the platform to the other station through the screen, check MTR's own assignment, restore both zones.
 */
final class OverlapSelfTest {

	private static long platformId;
	private static long stationAId;
	private static long stationBId;
	private static Position[] cornersA;
	private static Position[] cornersB;
	private static long chosenStationId;

	private OverlapSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		test.openDashboard();
		test.act("create an overlap", () -> {
			Platform platform = null;
			Station other = null;
			for (final Platform candidate : MtrBridge.dashboardData().platforms) {
				if (candidate.area instanceof Station) {
					for (final Station station : MtrBridge.dashboardData().stations) {
						if (station != candidate.area && station.isTransportMode(candidate)) {
							platform = candidate;
							other = station;
							break;
						}
					}
				}
				if (platform != null) {
					break;
				}
			}
			test.check("world has a platform and a second station", platform != null, "");
			final Station a = (Station) platform.area;
			platformId = platform.getId();
			stationAId = a.getId();
			stationBId = other.getId();
			cornersA = corners(a);
			cornersB = corners(other);
			final Position mid = platform.getMidPosition();
			other.setCorners(
					new Position(Math.min(other.getMinX(), mid.getX() - 1), Math.min(other.getMinY(), mid.getY() - 1), Math.min(other.getMinZ(), mid.getZ() - 1)),
					new Position(Math.max(other.getMaxX(), mid.getX() + 1), Math.max(other.getMaxY(), mid.getY() + 1), Math.max(other.getMaxZ(), mid.getZ() + 1))
			);
			final Station b = other;
			MtrBridge.send(request -> request.addStation(b));
		});
		test.pause(20);
		openScreen(test);
		test.act("find the overlap and resolve it", () -> {
			final StationOverlapScreen screen = (StationOverlapScreen) MinecraftClient.getInstance().currentScreen;
			test.check("ATU's reading of MTR's rule matches MTR (all platforms)", screen.mismatchesForTest() == 0, screen.mismatchesForTest() + " mismatch(es)");
			Zones.Conflict conflict = null;
			for (final Zones.Conflict candidate : screen.conflictsForTest()) {
				if (candidate.platformId() == platformId) {
					conflict = candidate;
				}
			}
			test.check("overlap is listed", conflict != null && conflict.stationIds().contains(stationAId) && conflict.stationIds().contains(stationBId), String.valueOf(conflict));
			final Platform platform = MtrBridge.dashboardData().platformIdMap.get(platformId);
			test.check("MTR's pick is the first listed station", platform.area != null && platform.area.getId() == conflict.currentStationId(), "MTR " + (platform.area == null ? "none" : platform.area.getId()) + ", ATU " + conflict.currentStationId());
			// Choose the station MTR did NOT pick.
			chosenStationId = conflict.stationIds().get(1);
			screen.selectConflictForTest(platformId);
			screen.selectStationForTest(chosenStationId);
			final OverlapResolver.Plan plan = screen.planForTest();
			test.check("plan reaches the chosen station", plan != null && plan.reachesGoal(), plan == null ? "no plan" : plan.cuts().size() + " cut(s), " + plan.sideEffects().size() + " side effect(s)");
		});
		test.pause(3);
		test.screenshot("30_overlap");
		test.act("press Apply", () -> press(test, "Apply"));
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify MTR now assigns the chosen station", () -> {
			final Platform platform = MtrBridge.dashboardData().platformIdMap.get(platformId);
			test.check("MTR assigns platform to chosen station", platform.area != null && platform.area.getId() == chosenStationId, "area " + (platform.area == null ? "none" : platform.area.getId()) + ", chosen " + chosenStationId);
			restore(stationAId, cornersA);
			restore(stationBId, cornersB);
			MtrBridge.send(request -> {
				request.addStation(MtrBridge.dashboardData().stationIdMap.get(stationAId));
				request.addStation(MtrBridge.dashboardData().stationIdMap.get(stationBId));
			});
		});
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify zones restored", () -> {
			final Station a = MtrBridge.dashboardData().stationIdMap.get(stationAId);
			final Station b = MtrBridge.dashboardData().stationIdMap.get(stationBId);
			final Platform platform = MtrBridge.dashboardData().platformIdMap.get(platformId);
			test.check("original zones restored", same(corners(a), cornersA) && same(corners(b), cornersB) && platform.area != null && platform.area.getId() == stationAId, "");
			MinecraftClient.getInstance().setScreen(null);
		});
	}

	private static Position[] corners(Station station) {
		return new Position[]{new Position(station.getMinX(), station.getMinY(), station.getMinZ()), new Position(station.getMaxX(), station.getMaxY(), station.getMaxZ())};
	}

	private static void restore(long stationId, Position[] corners) {
		MtrBridge.dashboardData().stationIdMap.get(stationId).setCorners(corners[0], corners[1]);
	}

	private static boolean same(Position[] a, Position[] b) {
		return a[0].equals(b[0]) && a[1].equals(b[1]);
	}

	private static void openScreen(AtuSelfTest test) {
		test.openDashboard();
		test.act("press ATU button", () -> press(test, "ATU"));
		test.waitFor("ATU tools open", () -> MinecraftClient.getInstance().currentScreen instanceof AtuToolsScreen);
		test.act("press Station overlaps", () -> press(test, "Station overlaps"));
		test.waitFor("overlap screen open", () -> MinecraftClient.getInstance().currentScreen instanceof StationOverlapScreen);
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
