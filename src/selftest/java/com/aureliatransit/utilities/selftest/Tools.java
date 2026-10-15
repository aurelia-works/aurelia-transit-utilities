package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;

/** Shared steps: open an ATU tool from MTR's dashboard the way a player does, press buttons by label. */
final class Tools {

	private Tools() {
	}

	static void open(AtuSelfTest test, String toolLabel, Class<? extends Screen> screenClass) {
		test.openDashboard();
		test.act("press ATU button", () -> press(test, "ATU"));
		test.waitFor("ATU tools open", () -> MinecraftClient.getInstance().currentScreen instanceof AtuToolsScreen);
		test.act("press " + toolLabel, () -> press(test, toolLabel));
		test.waitFor(toolLabel + " open", () -> screenClass.isInstance(MinecraftClient.getInstance().currentScreen));
		test.pause(3);
	}

	static void press(AtuSelfTest test, String label) {
		final ClickableWidget button = PresetSelfTest.findButton(label);
		if (button == null || !button.active) {
			test.check("button '" + label + "' usable", false, button == null ? "missing" : "inactive");
			return;
		}
		button.onClick(button.getX() + 1, button.getY() + 1);
	}
}
