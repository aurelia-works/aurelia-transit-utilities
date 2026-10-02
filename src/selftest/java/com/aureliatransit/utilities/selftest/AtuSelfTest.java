package com.aureliatransit.utilities.selftest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.packet.PacketOpenDashboardScreen;
import org.mtr.mod.screen.DashboardScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Dev-only. Runs a fixed script of steps on the client tick once the world is loaded, records PASS/FAIL lines and
 * screenshots, then quits. Each step either acts once or waits (bounded) for a condition.
 */
public final class AtuSelfTest implements ClientModInitializer {

	static final Logger LOGGER = LoggerFactory.getLogger("ATU self-test");
	private static final int WAIT_LIMIT_TICKS = 200;

	private final List<Step> steps = new ArrayList<>();
	private final List<String> report = new ArrayList<>();
	private int stepIndex;
	private int waitTicks;
	private int failures;
	private boolean started;

	private record Step(String name, Runnable action, BooleanSupplier until) {
	}

	@Override
	public void onInitializeClient() {
		PresetSelfTest.addSteps(this);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	void act(String name, Runnable action) {
		steps.add(new Step(name, action, null));
	}

	void waitFor(String name, BooleanSupplier until) {
		steps.add(new Step(name, null, until));
	}

	void pause(int ticks) {
		final int[] counter = {0};
		steps.add(new Step("pause " + ticks, null, () -> ++counter[0] >= ticks));
	}

	void screenshot(String name) {
		act("screenshot " + name, () -> ScreenshotRecorder.saveScreenshot(MinecraftClient.getInstance().runDirectory, "atu_" + name + ".png", MinecraftClient.getInstance().getFramebuffer(), text -> {
		}));
	}

	void check(String name, boolean ok, String detail) {
		final String line = (ok ? "PASS " : "FAIL ") + name + (detail == null || detail.isEmpty() ? "" : " - " + detail);
		if (!ok) {
			failures++;
		}
		report.add(line);
		LOGGER.info("[ATU-SELFTEST] {}", line);
	}

	/** Server side, like right-clicking with MTR's dashboard item: MTR sends all data and opens its dashboard. */
	void openDashboard() {
		act("open MTR dashboard", () -> {
			final MinecraftClient client = MinecraftClient.getInstance();
			final var server = client.getServer();
			final var playerId = client.player.getUuid();
			server.execute(() -> {
				final var player = server.getPlayerManager().getPlayer(playerId);
				PacketOpenDashboardScreen.sendDirectlyToServer(new org.mtr.mapping.holder.ServerWorld(player.getServerWorld()), new org.mtr.mapping.holder.ServerPlayerEntity(player), TransportMode.TRAIN);
			});
		});
		waitFor("dashboard open", () -> MinecraftClient.getInstance().currentScreen instanceof DashboardScreen);
		pause(10);
	}

	private void tick(MinecraftClient client) {
		if (!started) {
			if (client.player == null || client.world == null) {
				return;
			}
			started = true;
			pauseStart = 60;
		}
		if (pauseStart > 0) {
			pauseStart--;
			return;
		}
		if (stepIndex >= steps.size()) {
			finish(client);
			return;
		}
		final Step step = steps.get(stepIndex);
		try {
			if (step.action() != null) {
				step.action().run();
				stepIndex++;
			} else if (step.until().getAsBoolean()) {
				stepIndex++;
				waitTicks = 0;
			} else if (++waitTicks > WAIT_LIMIT_TICKS) {
				check(step.name(), false, "timed out");
				stepIndex = steps.size();
			}
		} catch (Throwable e) {
			LOGGER.error("[ATU-SELFTEST] step '{}' threw", step.name(), e);
			check(step.name(), false, "threw " + e);
			stepIndex = steps.size();
		}
	}

	private int pauseStart;
	private boolean finished;

	private void finish(MinecraftClient client) {
		if (finished) {
			return;
		}
		finished = true;
		report.add(failures == 0 ? "RESULT PASS" : "RESULT FAIL (" + failures + ")");
		LOGGER.info("[ATU-SELFTEST] {}", report.get(report.size() - 1));
		try {
			Files.write(Path.of(client.runDirectory.getPath(), "atu_selftest.txt"), report, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("[ATU-SELFTEST] could not write report", e);
		}
		if (!Boolean.getBoolean("atu.selftest.stay")) {
			client.scheduleStop();
		}
	}
}
