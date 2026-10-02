package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import com.aureliatransit.utilities.client.screen.ZoneHeightScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;

/** #815 zone heights: preview a range that drops the station's platform, save one that keeps it, verify, restore. */
final class ZoneHeightSelfTest {

	private static long stationId;
	private static long platformId;
	private static long platformY;
	private static Position[] original;

	private ZoneHeightSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		openScreen(test);
		test.act("limit a station's height", () -> {
			Platform platform = null;
			for (final Platform candidate : MtrBridge.dashboardData().platforms) {
				if (candidate.area instanceof Station) {
					platform = candidate;
					break;
				}
			}
			final Station station = (Station) platform.area;
			stationId = station.getId();
			platformId = platform.getId();
			platformY = platform.getMidPosition().getY();
			original = new Position[]{new Position(station.getMinX(), station.getMinY(), station.getMinZ()), new Position(station.getMaxX(), station.getMaxY(), station.getMaxZ())};
			final ZoneHeightScreen screen = (ZoneHeightScreen) MinecraftClient.getInstance().currentScreen;
			screen.selectAreaForTest(station);
			screen.bottomFieldForTest().setText(Long.toString(platformY + 5));
			screen.topFieldForTest().setText("");
			test.check("preview warns when the platform would leave", screen.previewForTest().stream().anyMatch(line -> line.contains("change station")), screen.previewForTest().toString());
			screen.bottomFieldForTest().setText("abc");
			test.check("bad input keeps Save disabled", !PresetSelfTest.findButton("Save").active, "");
			screen.bottomFieldForTest().setText(Long.toString(platformY - 3));
			screen.topFieldForTest().setText(Long.toString(platformY + 6));
			test.check("preview shows no change for a range that keeps it", screen.previewForTest().stream().anyMatch(line -> line.contains("No platform changes")), screen.previewForTest().toString());
			press(test, "Save");
		});
		test.pause(3);
		test.screenshot("40_heights");
		test.pause(17);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify height came back from MTR core, then restore", () -> {
			final Station station = MtrBridge.dashboardData().stationIdMap.get(stationId);
			final Platform platform = MtrBridge.dashboardData().platformIdMap.get(platformId);
			test.check("height persisted in MTR", station.getMinY() == platformY - 3 && station.getMaxY() == platformY + 6, "Y " + station.getMinY() + " to " + station.getMaxY());
			test.check("platform still belongs to the station (MTR's own assignment)", platform.area != null && platform.area.getId() == stationId, "");
			station.setCorners(original[0], original[1]);
			MtrBridge.send(request -> request.addStation(station));
		});
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify height restored", () -> {
			final Station station = MtrBridge.dashboardData().stationIdMap.get(stationId);
			test.check("original height restored", station.getMinY() == original[0].getY() && station.getMaxY() == original[1].getY(), "Y " + station.getMinY() + " to " + station.getMaxY());
			MinecraftClient.getInstance().setScreen(null);
		});
	}

	private static void openScreen(AtuSelfTest test) {
		test.openDashboard();
		test.act("press ATU button", () -> press(test, "ATU"));
		test.waitFor("ATU tools open", () -> MinecraftClient.getInstance().currentScreen instanceof AtuToolsScreen);
		test.pause(2);
		test.screenshot("39_tools");
		test.act("press Zone heights", () -> press(test, "Zone heights"));
		test.waitFor("height screen open", () -> MinecraftClient.getInstance().currentScreen instanceof ZoneHeightScreen);
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
