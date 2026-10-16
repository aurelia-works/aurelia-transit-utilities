package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import com.aureliatransit.utilities.client.screen.DepotHealthScreen;
import com.aureliatransit.utilities.depot.DepotHealth;
import com.aureliatransit.utilities.timetable.Timetable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Siding;
import org.mtr.core.data.VehicleCar;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.List;

/** Depot health: a siding without a train is reported; Regenerate gives MTR's fresh result; train restored. */
final class DepotHealthSelfTest {

	private static long depotId;
	private static long sidingId;
	private static ObjectArrayList<VehicleCar> originalCars;

	private DepotHealthSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		test.onFinish(DepotHealthSelfTest::restoreDepartures);
		test.openDashboard();
		test.act("empty a siding's train", () -> {
			final Siding siding = MtrBridge.dashboardData().sidings.iterator().next();
			test.check("siding is in a depot", siding.area != null, "");
			depotId = siding.area.getId();
			sidingId = siding.getId();
			originalCars = new ObjectArrayList<>(siding.getVehicleCars());
			siding.setVehicleCars(new ObjectArrayList<>());
			MtrBridge.send(request -> request.addSiding(siding));
		});
		test.pause(20);
		openScreen(test);
		test.act("check the empty siding is reported, then regenerate", () -> {
			final DepotHealthScreen screen = (DepotHealthScreen) MinecraftClient.getInstance().currentScreen;
			final List<DepotHealth.Problem> problems = screen.problemsForTest().get(depotId);
			test.check("empty siding reported", problems != null && problems.stream().anyMatch(problem -> problem.key().equals("sidings_without_train")), String.valueOf(problems));
			screen.selectDepotForTest(depotId);
			press(test, "Regenerate this depot");
		});
		test.pause(3);
		test.screenshot("50_depots");
		test.pause(60);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify MTR's generation result, restore train", () -> {
			final Depot depot = MtrBridge.dashboardData().depotIdMap.get(depotId);
			final DepotHealth.DepotInfo info = MtrBridge.depotInfo(depot);
			test.check("Regenerate produced a fresh MTR result", depot.getLastGeneratedMillis() > System.currentTimeMillis() - 120_000, "status " + info.status() + ", " + (System.currentTimeMillis() - depot.getLastGeneratedMillis()) + " ms ago");
			final Siding siding = MtrBridge.dashboardData().sidingIdMap.get(sidingId);
			siding.setVehicleCars(originalCars);
			MtrBridge.send(request -> request.addSiding(siding));
		});
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify train restored", () -> {
			test.check("siding train restored", MtrBridge.dashboardData().sidingIdMap.get(sidingId).getVehicleCars().size() == originalCars.size(), "");
			final Boolean linked = MtrBridge.sidingTrackLinked(MtrBridge.dashboardData().sidingIdMap.get(sidingId));
			if (linked == null) {
				test.skip("siding track linked", "siding rail not within render distance");
			} else {
				test.check("connected siding is not reported as isolated", linked, "");
			}
			// Depot doctor: an empty timed list means no train ever leaves.
			final Depot depot = MtrBridge.dashboardData().depotIdMap.get(depotId);
			if (MtrBridge.hasNoDepartures(depot)) {
				// A run killed between clearing and restoring leaves the test world without departures: seed a timetable.
				test.note("test depot had no departures (left by an interrupted run); seeding one every 15 minutes");
				final List<Long> seeded = new ArrayList<>();
				for (long time = 0; time < Timetable.DAY; time += 15 * 60_000L) {
					seeded.add(time);
				}
				MtrBridge.saveDepartures(depot, seeded);
			}
			originalRealTime = depot.getUseRealTime();
			originalDepartures = new LongArrayList(depot.getRealTimeDepartures());
			test.check("test depot has departures before", !MtrBridge.hasNoDepartures(depot), "");
			changedDepartures = true;
			MtrBridge.saveDepartures(depot, List.of());
		});
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		openScreen(test);
		test.act("check no departures is reported, restore", () -> {
			final DepotHealthScreen screen = (DepotHealthScreen) MinecraftClient.getInstance().currentScreen;
			final List<DepotHealth.Problem> problems = screen.problemsForTest().get(depotId);
			test.check("depot without departures reported", problems != null && problems.stream().anyMatch(problem -> problem.key().equals("no_departures")), String.valueOf(problems));
			screen.selectDepotForTest(depotId);
		});
		test.pause(3);
		test.screenshot("51_depot_doctor");
		test.act("restore departures", DepotHealthSelfTest::restoreDepartures);
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify departures restored", () -> {
			final Depot depot = MtrBridge.dashboardData().depotIdMap.get(depotId);
			test.check("depot departures restored", depot.getUseRealTime() == originalRealTime && depot.getRealTimeDepartures().equals(originalDepartures), "");
			MinecraftClient.getInstance().setScreen(null);
		});
	}

	private static boolean originalRealTime;
	private static LongArrayList originalDepartures;
	private static boolean changedDepartures;

	private static void restoreDepartures() {
		if (!changedDepartures) {
			return;
		}
		changedDepartures = false;
		final Depot depot = MtrBridge.dashboardData().depotIdMap.get(depotId);
		depot.getRealTimeDepartures().clear();
		depot.getRealTimeDepartures().addAll(originalDepartures);
		depot.setUseRealTime(originalRealTime);
		MtrBridge.send(request -> request.addDepot(depot));
	}

	private static void openScreen(AtuSelfTest test) {
		test.openDashboard();
		test.act("press ATU button", () -> press(test, "ATU"));
		test.waitFor("ATU tools open", () -> MinecraftClient.getInstance().currentScreen instanceof AtuToolsScreen);
		test.act("press Depot health", () -> press(test, "Depot health"));
		test.waitFor("depot screen open", () -> MinecraftClient.getInstance().currentScreen instanceof DepotHealthScreen);
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
