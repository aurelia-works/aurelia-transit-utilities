package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.announce.RideSnapshot;
import com.aureliatransit.utilities.client.Announcer;
import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.timetable.Timetable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.mtr.core.data.Depot;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.mod.config.Config;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.VehicleExtension;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Announcer: schedule one train 20 s ahead, board it, ride the line and check what the Announcer said and played.
 * Restores the depot's schedule afterwards.
 * <p>
 * MTR boards a player only through an open doorway (doors open on the side with platform blocks, which the test
 * world does not have) or, with a driver key in hand, through any car floor. So the player holds MTR's creative
 * driver key while teleporting into the first car, then puts it away once riding.
 */
final class AnnouncerSelfTest {

	private static final int SECONDS = 20;

	private static long depotId;
	private static boolean originalRealTime;
	private static LongArrayList originalDepartures;
	private static final int[] ORIGINAL_FREQUENCIES = new int[24];
	private static Vec3d start;
	private static int boardTicks;
	private static int lastHistorySize;
	private static int shots;
	private static int shotIn;
	private static int notRidingTicks;
	private static boolean changed;
	private static int depotTicks;
	private static boolean mtrChatBefore;
	private static boolean mtrChatMutedWhileRiding;
	private static String legKey = "";

	private AnnouncerSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		test.onFinish(AnnouncerSelfTest::restore);
		test.openDashboard();
		test.act("schedule a train in 60 s", () -> {
			final MinecraftClient client = MinecraftClient.getInstance();
			start = client.player.getPos();
			test.check("announcer is on", Announcer.enabled(), "");
			final Depot depot = MtrBridge.dashboardData().depots.iterator().next();
			depotId = depot.getId();
			originalRealTime = depot.getUseRealTime();
			originalDepartures = new LongArrayList(depot.getRealTimeDepartures());
			for (int hour = 0; hour < 24; hour++) {
				ORIGINAL_FREQUENCIES[hour] = (int) depot.getFrequency(hour);
			}
			final long now = Math.floorMod(System.currentTimeMillis() + TimeZone.getDefault().getOffset(System.currentTimeMillis()), Timetable.DAY);
			changed = true;
			MtrBridge.saveDepartures(depot, List.of(now + 60_000));
		});
		// Sidings only pick up new departures when the depot is generated (Depot.finishGeneratingPath).
		test.pause(40);
		test.act("regenerate the depot", () -> {
			MtrBridge.regenerateDepots(List.of(MtrBridge.dashboardData().depotIdMap.get(depotId)));
			MinecraftClient.getInstance().setScreen(null);
		});
		test.act("hold a driver key", () -> {
			mtrChatBefore = Config.getClient().getChatAnnouncements();
			final MinecraftClient client = MinecraftClient.getInstance();
			client.player.networkHandler.sendCommand("item replace entity @s hotbar.8 with mtr:creative_driver_key");
			client.player.getInventory().selectedSlot = 8;
		});
		test.waitFor("a train is in the siding", () -> !MinecraftClientData.getInstance().vehicles.isEmpty(), 90 * SECONDS);
		test.waitFor("board it", () -> {
			if (MtrBridge.ridingSnapshot() != null) {
				return true;
			}
			// Teleport into the first car every 2 s until MTR starts the ride.
			final var vehicles = MinecraftClientData.getInstance().vehicles;
			if (boardTicks++ % 40 == 0 && !vehicles.isEmpty()) {
				final VehicleExtension vehicle = vehicles.iterator().next();
				final var bogies = vehicle.getVehicleCarsAndPositions().get(0).right();
				double x = 0, y = 0, z = 0;
				for (final var pair : bogies) {
					for (final Vector v : List.of(pair.left(), pair.right())) {
						x += v.x;
						y += v.y;
						z += v.z;
					}
				}
				final int n = bogies.size() * 2;
				MinecraftClient.getInstance().player.networkHandler.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", x / n, y / n + 1, z / n));
			}
			return false;
		}, 60 * SECONDS);
		test.act("riding", () -> {
			// Put the key away: the Announcer should see an ordinary passenger.
			MinecraftClient.getInstance().player.networkHandler.sendCommand("item replace entity @s hotbar.8 with air");
			MinecraftClient.getInstance().player.getInventory().selectedSlot = 0;
			final RideSnapshot ride = MtrBridge.ridingSnapshot();
			test.check("riding an MTR train", ride != null, ride == null ? "" : "next " + ride.nextStation() + ", route " + ride.route() + ", interchanges " + ride.interchanges().size());
			final var player = MinecraftClient.getInstance().player;
			final var trains = MtrBridge.loadedTrains(player.getX(), player.getY(), player.getZ());
			test.check("train inspector lists the ridden train first", !trains.isEmpty() && trains.get(0).riding(), trains.isEmpty() ? "no trains" : trains.get(0).toString());
			lastHistorySize = Announcer.historyForTest().size();
		});
		// Ride until the train leaves service or we are thrown off; screenshot each new announcement.
		test.waitFor("ride the line", () -> {
			final List<String> history = Announcer.historyForTest();
			if (history.size() > lastHistorySize) {
				lastHistorySize = history.size();
				// A second later, so the chat lines are in the frame.
				shotIn = 20;
			}
			if (shotIn > 0 && --shotIn == 0 && shots < 6) {
				final MinecraftClient client = MinecraftClient.getInstance();
				ScreenshotRecorder.saveScreenshot(client.runDirectory, "atu_90_announcer_" + shots++ + ".png", client.getFramebuffer(), text -> {
				});
			}
			final RideSnapshot ride = MtrBridge.ridingSnapshot();
			notRidingTicks = ride == null ? notRidingTicks + 1 : 0;
			if (ride != null) {
				mtrChatMutedWhileRiding |= !Config.getClient().getChatAnnouncements();
				logLegChange(ride);
				final boolean inDepot = ride.nextStation().isEmpty() && ride.speedMetresPerSecond() < 0.1;
				depotTicks = inDepot && history.stream().anyMatch(entry -> entry.startsWith("NEXT")) ? depotTicks + 1 : 0;
			}
			if (depotTicks > 10 * SECONDS) {
				return true;
			}
			return notRidingTicks > 5 * SECONDS || history.stream().filter(entry -> entry.startsWith("NEXT") || entry.startsWith("ARRIVING")).count() >= 6;
		}, 360 * SECONDS);
		test.act("check announcements", () -> {
			final List<String> history = Announcer.historyForTest();
			history.forEach(entry -> test.check("announcer said", true, entry));
			test.check("a next-station announcement", history.stream().anyMatch(entry -> entry.startsWith("NEXT [The next station is ")), "");
			// Both routes in the test world end at Alpha; Route 1's last leg has no "continues as" (MTR sends it a leg later).
			test.check("last stop announced", history.stream().anyMatch(entry -> entry.startsWith("NEXT") && entry.contains("This is the last stop.")), "");
			// Clips need the AureliaEX pack in run/selftest/resourcepacks, enabled in options.txt (both gitignored).
			if (MinecraftClient.getInstance().getSoundManager().get(new Identifier("mtr", "aureliaex_ann_chime")) == null) {
				test.skip("clips", "AureliaEX pack not loaded");
			} else {
				test.check("a clip was played", history.stream().anyMatch(entry -> entry.startsWith("play mtr:aureliaex_ann_")), "");
				test.check("last stop announced with the terminus clip", history.stream().anyMatch(entry -> entry.contains("This is the last stop.") && entry.contains("terminus")), "");
				test.check("terminus clip played", history.contains("play mtr:aureliaex_ann_terminus"), "");
			}
			test.check("no announcement repeated back to back", noRepeats(history), "");
			// The Announcer's lines go to chat instead of MTR's own chat announcement.
			test.check("MTR chat announcements off while riding", !mtrChatBefore || mtrChatMutedWhileRiding, "MTR setting was " + mtrChatBefore);
		});
		test.openDashboard();
		test.act("restore the depot", () -> {
			restore();
			MinecraftClient.getInstance().setScreen(null);
			MinecraftClient.getInstance().player.networkHandler.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", start.x, start.y, start.z));
		});
		test.pause(20);
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
		// Off the train for longer than the Announcer's 2 s grace: MTR's setting is back as it was.
		test.pause(60);
		test.act("MTR chat setting restored", () -> test.check("MTR chat announcements restored after the ride", Config.getClient().getChatAnnouncements() == mtrChatBefore, "now " + Config.getClient().getChatAnnouncements()));
	}

	/** MTR's raw fields at each leg change, next to the last stop derived from MTR's simplified route list. */
	private static void logLegChange(RideSnapshot ride) {
		final String key = ride.thisPlatformId() + "|" + ride.nextPlatformId();
		if (key.equals(legKey)) {
			return;
		}
		legKey = key;
		final MinecraftClientData data = MinecraftClientData.getInstance();
		for (final VehicleExtension vehicle : data.vehicles) {
			if (vehicle.getId() != ride.vehicleId()) {
				continue;
			}
			final var extra = vehicle.vehicleExtraData;
			final var route = data.simplifiedRouteIdMap.get(extra.getThisRouteId());
			final String derived = route == null ? "no simplified route" : "nextIsLastOfThisRoute=" + (!route.getPlatforms().isEmpty() && route.getPlatforms().get(route.getPlatforms().size() - 1).getPlatformId() == extra.getNextPlatformId());
			AtuSelfTest.LOGGER.info("[ATU-SELFTEST] leg: this {} '{}' -> next {} '{}'; thisRoute {} '{}' dest '{}'; nextRoute {} '{}'; isTerminating {}; {}",
					extra.getThisPlatformId(), extra.getThisStationName(), extra.getNextPlatformId(), extra.getNextStationName(),
					extra.getThisRouteId(), extra.getThisRouteName(), extra.getThisRouteDestination(), extra.getNextRouteId(), extra.getNextRouteName(),
					extra.getIsTerminating(), derived);
		}
	}

	/** Also runs when the script aborts, so the test world never keeps the temporary timetable. */
	private static void restore() {
		if (!changed) {
			return;
		}
		changed = false;
		MinecraftClient.getInstance().player.networkHandler.sendCommand("clear @s mtr:creative_driver_key");
		final Depot depot = MtrBridge.dashboardData().depotIdMap.get(depotId);
		depot.getRealTimeDepartures().clear();
		depot.getRealTimeDepartures().addAll(originalDepartures);
		for (int hour = 0; hour < 24; hour++) {
			depot.setFrequency(hour, ORIGINAL_FREQUENCIES[hour]);
		}
		depot.setUseRealTime(originalRealTime);
		MtrBridge.send(request -> request.addDepot(depot));
		MtrBridge.regenerateDepots(List.of(depot));
		AtuSelfTest.LOGGER.info("[ATU-SELFTEST] depot schedule restored (realTime={}, {} departures)", originalRealTime, originalDepartures.size());
	}

	private static boolean noRepeats(List<String> history) {
		String last = null;
		for (final String entry : history) {
			if (entry.startsWith("NEXT") || entry.startsWith("ARRIVING")) {
				if (entry.equals(last)) {
					return false;
				}
				last = entry;
			}
		}
		return true;
	}
}
