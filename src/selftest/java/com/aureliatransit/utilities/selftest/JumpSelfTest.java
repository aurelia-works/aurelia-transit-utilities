package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import com.aureliatransit.utilities.client.screen.JumpScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.util.math.Vec3d;

/** Jump to: pick a platform, press Jump, check the player arrived, go back. */
final class JumpSelfTest {

	private static Vec3d start;
	private static double[] target;

	private JumpSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		test.openDashboard();
		test.act("press ATU button", () -> press(test, "ATU"));
		test.waitFor("ATU tools open", () -> MinecraftClient.getInstance().currentScreen instanceof AtuToolsScreen);
		test.pause(2);
		test.screenshot("80_tools_all");
		test.act("press Jump to", () -> press(test, "Jump to…"));
		test.waitFor("jump screen open", () -> MinecraftClient.getInstance().currentScreen instanceof JumpScreen);
		test.pause(3);
		test.screenshot("81_jump");
		test.act("jump to a platform", () -> {
			final MinecraftClient client = MinecraftClient.getInstance();
			start = client.player.getPos();
			test.check("player may use /tp here", client.player.hasPermissionLevel(2), "");
			final JumpScreen screen = (JumpScreen) client.currentScreen;
			test.check("platforms listed", screen.selectForTest("Platform"), "");
			final String[] parts = screen.selectedCommandForTest().split(" ");
			target = new double[]{Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Double.parseDouble(parts[4])};
			press(test, "Jump");
		});
		test.waitFor("player arrived", () -> {
			final Vec3d pos = MinecraftClient.getInstance().player.getPos();
			return Math.abs(pos.x - target[0]) < 1.5 && Math.abs(pos.z - target[2]) < 1.5 && Math.abs(pos.y - target[1]) < 3;
		});
		test.act("check and go back", () -> {
			final Vec3d pos = MinecraftClient.getInstance().player.getPos();
			test.check("player teleported to the platform", true, String.format("at %.1f %.1f %.1f, target %s %s %s", pos.x, pos.y, pos.z, target[0], target[1], target[2]));
			test.check("jump closed the screens", MinecraftClient.getInstance().currentScreen == null, String.valueOf(MinecraftClient.getInstance().currentScreen));
			MinecraftClient.getInstance().player.networkHandler.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", start.x, start.y, start.z));
		});
		test.pause(10);
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
