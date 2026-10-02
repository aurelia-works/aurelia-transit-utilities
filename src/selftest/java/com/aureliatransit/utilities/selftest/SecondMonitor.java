package com.aureliatransit.utilities.selftest;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;

/**
 * Dev only: moves the self-test window to a non-primary monitor so it does not cover the owner's work.
 * {@code -Datu.selftest.monitor=N} picks monitor N (0-based, GLFW order); default is the first non-primary one.
 * With a single monitor nothing moves.
 */
final class SecondMonitor {

	private SecondMonitor() {
	}

	static void move(MinecraftClient client) {
		// Keep running normally while the owner works in other windows.
		client.options.pauseOnLostFocus = false;
		final PointerBuffer monitors = GLFW.glfwGetMonitors();
		if (monitors == null || monitors.limit() < 2) {
			AtuSelfTest.LOGGER.info("[ATU-SELFTEST] one monitor only, window not moved");
			return;
		}
		final long primary = GLFW.glfwGetPrimaryMonitor();
		long target = 0;
		final int requested = Integer.getInteger("atu.selftest.monitor", -1);
		if (requested >= 0 && requested < monitors.limit()) {
			target = monitors.get(requested);
		} else {
			for (int i = 0; i < monitors.limit(); i++) {
				if (monitors.get(i) != primary) {
					target = monitors.get(i);
					break;
				}
			}
		}
		if (target == 0) {
			return;
		}
		final int[] x = new int[1];
		final int[] y = new int[1];
		final int[] width = new int[1];
		final int[] height = new int[1];
		GLFW.glfwGetMonitorWorkarea(target, x, y, width, height);
		final long window = client.getWindow().getHandle();
		final int[] windowWidth = new int[1];
		final int[] windowHeight = new int[1];
		GLFW.glfwGetWindowSize(window, windowWidth, windowHeight);
		GLFW.glfwSetWindowPos(window, x[0] + Math.max(0, (width[0] - windowWidth[0]) / 2), y[0] + Math.max(0, (height[0] - windowHeight[0]) / 2));
		AtuSelfTest.LOGGER.info("[ATU-SELFTEST] window moved to monitor '{}'", GLFW.glfwGetMonitorName(target));
	}
}
