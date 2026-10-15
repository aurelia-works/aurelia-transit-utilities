package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.Announcer;
import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.timetable.Timetable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Platform calls: schedule one train, stand on the first platform of its route and wait for "approaching" and
 * "now arriving" from MTR's arrival data. Restores the depot afterwards.
 */
final class PlatformCallsSelfTest {

	private static final int SECONDS = 20;

	private static long depotId;
	private static boolean originalRealTime;
	private static LongArrayList originalDepartures;
	private static final int[] ORIGINAL_FREQUENCIES = new int[24];
	private static boolean changed;
	private static Vec3d start;
	private static int historyBefore;

	private PlatformCallsSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		test.onFinish(PlatformCallsSelfTest::restore);
		test.openDashboard();
		test.act("schedule a train in 75 s", () -> {
			test.check("platform calls are on", Announcer.platformEnabled(), "");
			start = MinecraftClient.getInstance().player.getPos();
			final Depot depot = MtrBridge.dashboardData().depots.iterator().next();
			depotId = depot.getId();
			originalRealTime = depot.getUseRealTime();
			originalDepartures = new LongArrayList(depot.getRealTimeDepartures());
			for (int hour = 0; hour < 24; hour++) {
				ORIGINAL_FREQUENCIES[hour] = (int) depot.getFrequency(hour);
			}
			final long now = Math.floorMod(System.currentTimeMillis() + TimeZone.getDefault().getOffset(System.currentTimeMillis()), Timetable.DAY);
			changed = true;
			MtrBridge.saveDepartures(depot, List.of(now + 75_000));
		});
		test.pause(40);
		test.act("regenerate and stand on the first platform", () -> {
			final Depot depot = MtrBridge.dashboardData().depotIdMap.get(depotId);
			MtrBridge.regenerateDepots(List.of(depot));
			final Platform platform = depot.routes.get(0).getRoutePlatforms().get(0).getPlatform();
			final Position mid = platform.getMidPosition();
			AtuSelfTest.LOGGER.info("[ATU-SELFTEST] waiting on platform {} at {}", platform.getName(), mid);
			MinecraftClient.getInstance().setScreen(null);
			MinecraftClient.getInstance().player.networkHandler.sendCommand(String.format(Locale.ROOT, "tp @s %d %d %d", mid.getX(), mid.getY() + 1, mid.getZ()));
			historyBefore = Announcer.historyForTest().size();
		});
		test.waitFor("approaching call", () -> said("PLATFORM_APPROACHING"), 150 * SECONDS);
		test.pause(20);
		test.screenshot("95_platform_approaching");
		test.waitFor("arriving call", () -> said("PLATFORM_ARRIVING"), 120 * SECONDS);
		test.pause(20);
		test.screenshot("96_platform_arriving");
		test.act("check platform calls", () -> {
			final List<String> history = Announcer.historyForTest();
			history.subList(Math.min(historyBefore, history.size()), history.size()).forEach(entry -> test.check("platform call", true, entry));
			test.check("approaching names the service", history.stream().anyMatch(entry -> entry.startsWith("PLATFORM_APPROACHING [") && entry.contains("service")), "");
			test.check("arriving call said once", history.stream().filter(entry -> entry.startsWith("PLATFORM_ARRIVING")).count() == 1, "");
		});
		test.openDashboard();
		test.act("restore the depot", () -> {
			restore();
			MinecraftClient.getInstance().setScreen(null);
			MinecraftClient.getInstance().player.networkHandler.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", start.x, start.y, start.z));
		});
		test.pause(20);
	}

	private static boolean said(String kind) {
		final List<String> history = Announcer.historyForTest();
		return history.subList(Math.min(historyBefore, history.size()), history.size()).stream().anyMatch(entry -> entry.startsWith(kind));
	}

	private static void restore() {
		if (!changed) {
			return;
		}
		changed = false;
		final Depot depot = MtrBridge.dashboardData().depotIdMap.get(depotId);
		depot.getRealTimeDepartures().clear();
		depot.getRealTimeDepartures().addAll(originalDepartures);
		for (int hour = 0; hour < 24; hour++) {
			depot.setFrequency(hour, ORIGINAL_FREQUENCIES[hour]);
		}
		depot.setUseRealTime(originalRealTime);
		MtrBridge.send(request -> request.addDepot(depot));
		MtrBridge.regenerateDepots(List.of(depot));
	}
}
