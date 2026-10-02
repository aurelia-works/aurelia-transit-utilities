package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import com.aureliatransit.utilities.client.screen.DepotHealthScreen;
import com.aureliatransit.utilities.depot.DepotHealth;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Siding;
import org.mtr.core.data.VehicleCar;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Depot health: a siding without a train is reported; Regenerate gives MTR's fresh result; train restored. */
final class DepotHealthSelfTest {

	private static long depotId;
	private static long sidingId;
	private static ObjectArrayList<VehicleCar> originalCars;

	private DepotHealthSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
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
			MinecraftClient.getInstance().setScreen(null);
		});
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
