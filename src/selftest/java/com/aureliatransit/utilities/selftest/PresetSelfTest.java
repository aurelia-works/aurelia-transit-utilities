package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.PresetStore;
import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import com.aureliatransit.utilities.client.screen.TrainPresetScreen;
import com.aureliatransit.utilities.preset.PresetCar;
import com.aureliatransit.utilities.preset.PresetCheck;
import com.aureliatransit.utilities.preset.PresetJson;
import com.aureliatransit.utilities.preset.PresetLibrary;
import com.aureliatransit.utilities.preset.TrainPreset;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.mtr.core.data.Siding;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** U2: train presets, end to end through MTR's real update packet and a fresh dashboard fetch. */
final class PresetSelfTest {

	private static long sidingId;
	private static TrainPreset original;
	private static TrainPreset modified;

	private PresetSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		test.openDashboard();
		test.screenshot("01_dashboard");
		test.act("press ATU button", () -> {
			final ClickableWidget button = findButton("ATU");
			test.check("dashboard has ATU button", button != null, button == null ? "" : "at " + button.getX() + "," + button.getY() + " screen " + MinecraftClient.getInstance().getWindow().getScaledWidth());
			if (button != null) {
				button.onClick(button.getX() + 1, button.getY() + 1);
			}
		});
		test.waitFor("ATU tools open", () -> MinecraftClient.getInstance().currentScreen instanceof AtuToolsScreen);
		test.pause(5);
		test.screenshot("02_tools");
		test.act("press Train presets", () -> {
			final ClickableWidget button = findButton("Train presets");
			test.check("tools has Train presets button", button != null && button.active, "");
			if (button != null) {
				button.onClick(button.getX() + 1, button.getY() + 1);
			}
		});
		test.waitFor("presets open", () -> MinecraftClient.getInstance().currentScreen instanceof TrainPresetScreen);
		test.pause(5);
		test.screenshot("03_presets");

		test.act("pick siding and save preset", () -> {
			final List<Siding> sidings = new ArrayList<>(MtrBridge.dashboardData().sidings);
			test.check("dashboard data has sidings", !sidings.isEmpty(), sidings.size() + " siding(s)");
			sidings.sort((a, b) -> Integer.compare(b.getVehicleCars().size(), a.getVehicleCars().size()));
			final Siding siding = sidings.get(0);
			sidingId = siding.getId();
			original = MtrBridge.toPreset("selftest original", siding);
			test.check("siding has a train", !original.cars().isEmpty(), siding.getName() + ": " + original.cars().size() + " cars, " + original.totalLength() + " m of " + siding.getRailLength());
			final PresetLibrary.PutResult result = PresetStore.library().put(original);
			test.check("preset stored", result == PresetLibrary.PutResult.ADDED || result == PresetLibrary.PutResult.REPLACED, result.name());
			test.check("preset file written", PresetStore.save() && Files.exists(PresetStore.file()), PresetStore.file().toString());
			try {
				final PresetLibrary reread = new PresetLibrary();
				PresetJson.read(Files.readString(PresetStore.file()), reread);
				test.check("preset file round trip", original.equals(reread.get("selftest original")), "");
			} catch (Exception e) {
				test.check("preset file round trip", false, e.toString());
			}

			final PresetCar first = original.cars().get(0);
			modified = new TrainPreset("selftest modified", original.transportMode(), original.cars().size() > 1 ? List.of(first) : List.of(first, first));
			final List<PresetCheck.Problem> problems = PresetCheck.check(modified, siding.getTransportMode().name(), siding.getRailLength(), MtrBridge.knownVehicleIds(siding.getTransportMode()));
			test.check("modified preset passes checks", problems.isEmpty(), problems.toString());
			final List<PresetCheck.Problem> missing = PresetCheck.check(new TrainPreset("bad", original.transportMode(), List.of(new PresetCar("atu_no_such_vehicle", 10, 3, -3, 3, 0, 0))), siding.getTransportMode().name(), siding.getRailLength(), MtrBridge.knownVehicleIds(siding.getTransportMode()));
			test.check("unknown vehicle is refused", missing.size() == 1 && missing.get(0) instanceof PresetCheck.MissingVehicles, missing.toString());
			MtrBridge.applyPreset(modified, siding);
		});
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify modified train came back from MTR core", () -> verify(test, "applied preset persisted in MTR", modified));
		test.act("restore original train", () -> MtrBridge.applyPreset(original, MtrBridge.dashboardData().sidingIdMap.get(sidingId)));
		test.pause(20);
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		test.openDashboard();
		test.act("verify original restored", () -> verify(test, "original train restored in MTR", original));
		test.act("clean up presets", () -> {
			PresetStore.library().remove("selftest original");
			PresetStore.save();
			MinecraftClient.getInstance().setScreen(null);
		});
	}

	private static void verify(AtuSelfTest test, String name, TrainPreset expected) {
		final Siding siding = MtrBridge.dashboardData().sidingIdMap.get(sidingId);
		if (siding == null) {
			test.check(name, false, "siding missing after refetch");
			return;
		}
		final TrainPreset actual = MtrBridge.toPreset("actual", siding);
		test.check(name, sameTrain(expected, actual), actual.cars().size() + " cars, expected " + expected.cars().size());
	}

	private static boolean sameTrain(TrainPreset a, TrainPreset b) {
		if (a.cars().size() != b.cars().size()) {
			return false;
		}
		for (int i = 0; i < a.cars().size(); i++) {
			final PresetCar x = a.cars().get(i);
			final PresetCar y = b.cars().get(i);
			if (!x.vehicleId().equals(y.vehicleId()) || Math.abs(x.length() - y.length()) > 1e-6 || Math.abs(x.couplingPadding1() - y.couplingPadding1()) > 1e-6 || Math.abs(x.couplingPadding2() - y.couplingPadding2()) > 1e-6) {
				return false;
			}
		}
		return true;
	}

	static ClickableWidget findButton(String label) {
		final var screen = MinecraftClient.getInstance().currentScreen;
		if (screen == null) {
			return null;
		}
		for (final ClickableWidget widget : Screens.getButtons(screen)) {
			if (widget.getMessage().getString().equals(label)) {
				return widget;
			}
		}
		return null;
	}
}
