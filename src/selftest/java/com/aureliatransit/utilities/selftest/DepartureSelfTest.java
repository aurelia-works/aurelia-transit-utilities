package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import com.aureliatransit.utilities.client.screen.DepartureEditorScreen;
import com.aureliatransit.utilities.timetable.Timetable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Route;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.List;

/** Departure editor: timed service with rest period and first-station helper, then hourly mode; verify; restore. */
final class DepartureSelfTest {

	private static long depotId;
	private static boolean originalRealTime;
	private static LongArrayList originalDepartures;
	private static final int[] ORIGINAL_FREQUENCIES = new int[24];
	private static List<Long> expected;

	private DepartureSelfTest() {
	}

	private static long t(String text) {
		return Timetable.parseTime(text).orElseThrow();
	}

	static void addSteps(AtuSelfTest test) {
		openScreen(test);
		test.act("build a timed service", () -> {
			final Depot depot = MtrBridge.dashboardData().depots.iterator().next();
			depotId = depot.getId();
			originalRealTime = depot.getUseRealTime();
			originalDepartures = new LongArrayList(depot.getRealTimeDepartures());
			for (int hour = 0; hour < 24; hour++) {
				ORIGINAL_FREQUENCIES[hour] = (int) depot.getFrequency(hour);
			}
			// What the client knows about the depot's path, for the first-station helper (owner idea 22).
			test.check("depot path on client (info only)", true, depot.getPath().size() + " path segments; route durations " + depot.routes.stream().map(route -> route.durations.size()).toList());
			final DepartureEditorScreen screen = screen();
			screen.selectDepotForTest(depot);
			screen.showTabForTest("TIMED");
			final List<TextFieldWidget> fields = screen.fieldsForTest();
			fields.get(0).setText("06:00");
			fields.get(1).setText("07:00");
			fields.get(2).setText("15");
			fields.get(3).setText("0");
			press(test, "Replace");
			test.check("Replace makes 06:00-07:00 every 15", screen.pendingForTest().equals(List.of(t("06:00"), t("06:15"), t("06:30"), t("06:45"), t("07:00"))), times(screen.pendingForTest()));
			// First-station helper: trains must be at the first station at 08:00, the depot is 3 minutes away.
			fields.get(0).setText("08:00");
			fields.get(1).setText("08:00");
			fields.get(3).setText("3");
			press(test, "Add");
			fields.get(4).setText("06:20");
			fields.get(5).setText("06:40");
			press(test, "No trains");
			expected = List.of(t("06:00"), t("06:15"), t("06:45"), t("07:00"), t("07:57"));
			test.check("rest period and first-station helper", screen.pendingForTest().equals(expected), times(screen.pendingForTest()));
			press(test, "Save as timed departures");
		});
		test.pause(3);
		test.screenshot("70_departures");
		test.pause(17);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		openScreen(test);
		test.act("verify timed departures, then set hourly", () -> {
			final Depot depot = MtrBridge.dashboardData().depotIdMap.get(depotId);
			test.check("timed departures persisted in MTR", depot.getUseRealTime() && MtrBridge.localDepartures(depot).equals(expected), depot.getUseRealTime() + " " + times(MtrBridge.localDepartures(depot)));
			final DepartureEditorScreen screen = screen();
			screen.selectDepotForTest(depot);
			screen.showTabForTest("HOURLY");
			final List<TextFieldWidget> fields = screen.fieldsForTest();
			fields.get(7).setText("06:00");
			fields.get(8).setText("08:00");
			fields.get(9).setText("15");
			press(test, "Set those hours");
		});
		test.pause(3);
		test.screenshot("71_departures_hourly");
		test.act("save hourly", () -> press(test, "Save as trains per hour"));
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify hourly, then restore", () -> {
			final Depot depot = MtrBridge.dashboardData().depotIdMap.get(depotId);
			test.check("trains per hour persisted in MTR", !depot.getUseRealTime() && depot.getFrequency(6) == 16 && depot.getFrequency(7) == 16 && depot.getFrequency(8) == 16 && depot.getFrequency(9) == ORIGINAL_FREQUENCIES[9],
					"realTime=" + depot.getUseRealTime() + " 06=" + depot.getFrequency(6) + " 08=" + depot.getFrequency(8) + " 09=" + depot.getFrequency(9));
			depot.getRealTimeDepartures().clear();
			depot.getRealTimeDepartures().addAll(originalDepartures);
			for (int hour = 0; hour < 24; hour++) {
				depot.setFrequency(hour, ORIGINAL_FREQUENCIES[hour]);
			}
			depot.setUseRealTime(originalRealTime);
			MtrBridge.send(request -> request.addDepot(depot));
		});
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify depot restored", () -> {
			final Depot depot = MtrBridge.dashboardData().depotIdMap.get(depotId);
			boolean ok = depot.getUseRealTime() == originalRealTime && depot.getRealTimeDepartures().equals(originalDepartures);
			for (int hour = 0; hour < 24; hour++) {
				ok &= depot.getFrequency(hour) == ORIGINAL_FREQUENCIES[hour];
			}
			test.check("depot schedule restored", ok, "");
			MinecraftClient.getInstance().setScreen(null);
		});
	}

	private static String times(List<Long> times) {
		return times.stream().map(Timetable::format).toList().toString();
	}

	private static DepartureEditorScreen screen() {
		return (DepartureEditorScreen) MinecraftClient.getInstance().currentScreen;
	}

	private static void openScreen(AtuSelfTest test) {
		test.openDashboard();
		test.act("press ATU button", () -> press(test, "ATU"));
		test.waitFor("ATU tools open", () -> MinecraftClient.getInstance().currentScreen instanceof AtuToolsScreen);
		test.act("press Departure times", () -> press(test, "Departure times"));
		test.waitFor("departures screen open", () -> MinecraftClient.getInstance().currentScreen instanceof DepartureEditorScreen);
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
