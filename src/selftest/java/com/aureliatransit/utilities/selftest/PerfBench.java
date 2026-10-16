package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.perf.Perf;
import com.aureliatransit.utilities.perf.PerfSettings;
import com.aureliatransit.utilities.preset.PresetCar;
import com.aureliatransit.utilities.preset.SidingSettings;
import com.aureliatransit.utilities.preset.TrainPreset;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Siding;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Vector;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.resource.VehicleResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Dev only, run/perf: frame rate and MTR draw calls with ATU's performance fixes on and off, with and without a shader
 * pack. Puts parked NS Civity trains (MTR4_NS_trains pack) in up to three sidings set to manual, so they never leave,
 * then measures from fixed camera positions. Results are INFO lines in run/perf/atu_selftest.txt. Works on a copy of
 * the self-test world, so nothing is restored afterwards.
 */
final class PerfBench {

	private static final int SECONDS = 20;
	private static final int SETTLE_TICKS = 4 * SECONDS;
	private static final int SAMPLE_TICKS = 10 * SECONDS;
	private static final int MAX_TRAINS = 3;
	private static final String CAB = "mtr_custom_train_ns_sng_civity_mc_cab_1";
	private static final String TRAILER = "mtr_custom_train_ns_sng_civity_m_trailer";
	private static final String BACK_CAB = "mtr_custom_train_ns_sng_civity_mc_back_cab_2";

	private static int wantedTrains;
	private static Vec3d center = Vec3d.ZERO;
	private static Vec3d side = new Vec3d(1, 0, 0);

	private static boolean sampling;
	private static long frames;
	private static long sampleStart;
	private static int sampleTicks;
	private static double drawSum;
	private static double renderSum;
	private static int statCount;
	private static CompletableFuture<Void> reload;

	private PerfBench() {
	}

	static void addSteps(AtuSelfTest test) {
		WorldRenderEvents.END.register(context -> {
			if (sampling) {
				frames++;
			}
		});
		test.act("start with every fix off", () -> {
			configure(false, false, 0, 0);
			shaders(false);
			command("gamemode spectator");
		});
		test.openDashboard();
		test.act("close dashboard", () -> MinecraftClient.getInstance().setScreen(null));
		test.act("NS trains in sidings", () -> placeTrains(test));
		test.waitFor("NS trains parked", () -> nsTrains() >= wantedTrains, 120 * SECONDS);
		test.act("find trains", () -> locate(test));

		// Models loaded so far used whatever was set before; reload so the first runs are pure MTR.
		reloadResources(test);
		measureViews(test, "shaders off, MTR only", false);

		test.act("merge on", () -> configure(true, false, 0, 0));
		reloadResources(test);
		measureViews(test, "shaders off, merge", false);

		test.act("merge + LOD", () -> configure(true, false, 5, 10));
		measureViews(test, "shaders off, merge + LOD 5/10", true);

		test.act("shaders on", () -> {
			configure(true, false, 0, 0);
			shaders(true);
		});
		test.pause(10 * SECONDS);
		measure(test, "shaders on, merge", 40);
		test.act("fast path on", () -> configure(true, true, 0, 0));
		measure(test, "shaders on, merge + fast path", 40);
		test.act("fast path only", () -> configure(false, true, 0, 0));
		reloadResources(test);
		measure(test, "shaders on, fast path", 40);
		test.act("every fix off", () -> configure(false, false, 0, 0));
		measure(test, "shaders on, MTR only", 40);
		test.act("all fixes", () -> configure(true, true, 5, 10));
		reloadResources(test);
		measureViews(test, "shaders on, merge + fast path + LOD 5/10", true);

		test.act("shaders off again", () -> shaders(false));
		test.onFinish(() -> {
			sampling = false;
			configure(true, true, 5, 10);
		});
	}

	private static void measureViews(AtuSelfTest test, String label, boolean screenshots) {
		measure(test, label, 40);
		if (screenshots) {
			test.screenshot("perf_near_" + slug(label));
		}
		measure(test, label, 100);
		if (screenshots) {
			test.screenshot("perf_mid_" + slug(label));
		}
		measure(test, label, 170);
		if (screenshots) {
			test.screenshot("perf_far_" + slug(label));
		}
	}

	/** Camera {@code distance} blocks to the side of the trains, looking at them; settle, then count frames. */
	private static void measure(AtuSelfTest test, String label, int distance) {
		test.act("camera at " + distance, () -> {
			final Vec3d eye = center.add(side.multiply(distance)).add(0, Math.max(8, distance * 0.25), 0);
			command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f", eye.x, eye.y, eye.z, center.x, center.y, center.z));
		});
		test.pause(SETTLE_TICKS);
		test.act("sample " + label + " " + distance, () -> {
			sampling = true;
			frames = 0;
			drawSum = 0;
			renderSum = 0;
			statCount = 0;
			sampleTicks = 0;
			sampleStart = System.nanoTime();
		});
		test.waitFor("sampling " + label + " " + distance, () -> {
			sampleTicks++;
			if (sampleTicks % SECONDS == 0 && Perf.STATS.hasResult()) {
				drawSum += Perf.STATS.drawsPerFrame();
				renderSum += Perf.STATS.renderMillisPerFrame();
				statCount++;
			}
			if (sampleTicks < SAMPLE_TICKS) {
				return false;
			}
			sampling = false;
			final double seconds = (System.nanoTime() - sampleStart) / 1e9;
			final double fps = frames / seconds;
			test.note(String.format(Locale.ROOT, "%-45s %4d m: %6.1f fps, %6.2f ms/frame, %6.0f MTR draws/frame, %5.2f ms in MTR renderer, %d vehicles", label, distance, fps, 1000 / fps,
					statCount == 0 ? 0 : drawSum / statCount, statCount == 0 ? 0 : renderSum / statCount, trains().size()));
			return true;
		}, SAMPLE_TICKS + SECONDS);
	}

	private static void reloadResources(AtuSelfTest test) {
		test.act("reload resources", () -> reload = MinecraftClient.getInstance().reloadResources());
		test.waitFor("resources reloaded", () -> reload != null && reload.isDone() && MinecraftClient.getInstance().getOverlay() == null, 180 * SECONDS);
		test.pause(10 * SECONDS);
	}

	private static void placeTrains(AtuSelfTest test) {
		final List<Siding> sidings = new ArrayList<>();
		MtrBridge.dashboardData().sidings.forEach(siding -> {
			if (siding.getTransportMode() == TransportMode.TRAIN) {
				sidings.add(siding);
			}
		});
		sidings.sort(Comparator.comparingDouble((Siding siding) -> siding.getRailLength()).reversed());
		test.note(sidings.size() + " train siding(s), lengths " + sidings.stream().map(siding -> String.format(Locale.ROOT, "%.0f", siding.getRailLength())).toList());
		final List<Siding> used = new ArrayList<>();
		final Set<Depot> depots = new LinkedHashSet<>();
		for (final Siding siding : sidings) {
			if (used.size() == MAX_TRAINS) {
				break;
			}
			final List<PresetCar> cars = civity(siding.getRailLength());
			if (cars.isEmpty()) {
				continue;
			}
			final SidingSettings settings = MtrBridge.settingsOf(siding);
			final SidingSettings manual = new SidingSettings(settings.acceleration(), settings.deceleration(), 1, true, settings.maxManualSpeed(), 3600, settings.delayedSpeedIncreasePercentage(),
					settings.delayedReduceDwellTimePercentage(), settings.earlyIncreaseDwellTime());
			MtrBridge.applyPreset(new TrainPreset("perf bench", TransportMode.TRAIN.name(), cars, manual), siding);
			test.note(String.format(Locale.ROOT, "siding %s (%.0f m): NS Civity, %d cars", siding.getName(), siding.getRailLength(), cars.size()));
			used.add(siding);
			if (siding.area != null) {
				depots.add(siding.area);
			}
		}
		wantedTrains = used.size();
		test.check("NS trains placed", wantedTrains > 0, wantedTrains + " siding(s)");
		// A siding only spawns a train once its depot has generated a path.
		MtrBridge.regenerateDepots(depots);
	}

	/** Cab, as many trailers as fit, back cab; empty when even two cabs do not fit. */
	private static List<PresetCar> civity(double railLength) {
		final PresetCar cab = car(CAB);
		final PresetCar trailer = car(TRAILER);
		final PresetCar back = car(BACK_CAB);
		final List<PresetCar> cars = new ArrayList<>();
		if (cab == null || trailer == null || back == null || span(cab) + span(back) > railLength) {
			return cars;
		}
		cars.add(cab);
		double length = span(cab) + span(back);
		while (cars.size() < 5 && length + span(trailer) <= railLength) {
			cars.add(trailer);
			length += span(trailer);
		}
		cars.add(back);
		return cars;
	}

	/** Car length with both coupling gaps; MTR drops cars that do not fit the siding with their gaps. */
	private static double span(PresetCar car) {
		return car.length() + car.couplingPadding1() + car.couplingPadding2();
	}

	private static PresetCar car(String id) {
		final PresetCar[] found = {null};
		CustomResourceLoader.getVehicleById(TransportMode.TRAIN, id, pair -> {
			final VehicleResource resource = pair.left();
			found[0] = new PresetCar(resource.getId(), resource.getLength(), resource.getWidth(), resource.getBogie1Position(), resource.getBogie2Position(), resource.getCouplingPadding1(),
					resource.getCouplingPadding2());
		});
		return found[0];
	}

	/** Middle of all parked cars, and a horizontal direction across the first train for the camera. */
	private static void locate(AtuSelfTest test) {
		double x = 0, y = 0, z = 0;
		int n = 0;
		Vec3d along = null;
		for (final VehicleExtension vehicle : trains()) {
			for (final var car : vehicle.getVehicleCarsAndPositions()) {
				for (final var bogie : car.right()) {
					for (final Vector v : List.of(bogie.left(), bogie.right())) {
						x += v.x;
						y += v.y;
						z += v.z;
						n++;
					}
					if (along == null) {
						along = new Vec3d(bogie.right().x - bogie.left().x, 0, bogie.right().z - bogie.left().z);
					}
				}
			}
		}
		center = new Vec3d(x / n, y / n + 2, z / n);
		if (along != null && along.lengthSquared() > 1e-6) {
			side = new Vec3d(-along.z, 0, along.x).normalize();
		}
		int cars = 0;
		for (final VehicleExtension vehicle : trains()) {
			cars += vehicle.getVehicleCarsAndPositions().size();
		}
		test.note(String.format(Locale.ROOT, "%d train(s), %d cars, centre %.0f %.0f %.0f", trains().size(), cars, center.x, center.y, center.z));
	}

	private static int nsTrains() {
		int count = 0;
		for (final VehicleExtension vehicle : trains()) {
			if (!vehicle.vehicleExtraData.immutableVehicleCars.isEmpty() && vehicle.vehicleExtraData.immutableVehicleCars.get(0).getVehicleId().equals(CAB)) {
				count++;
			}
		}
		return count;
	}

	private static List<VehicleExtension> trains() {
		return new ArrayList<>(MinecraftClientData.getInstance().vehicles);
	}

	private static void configure(boolean merge, boolean fastPath, int interiorChunks, int boxChunks) {
		final PerfSettings settings = Perf.settings();
		settings.mergeTrainParts = merge;
		settings.shaderPackFastPath = fastPath;
		settings.trainInteriorChunks = interiorChunks;
		settings.trainBoxChunks = boxChunks;
	}

	/** Iris API by reflection: the self-test does not compile against Iris. */
	private static void shaders(boolean on) {
		try {
			final Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			final Object config = api.getMethod("getConfig").invoke(api.getMethod("getInstance").invoke(null));
			Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
		} catch (ReflectiveOperationException e) {
			AtuSelfTest.LOGGER.warn("[ATU-SELFTEST] Iris not available", e);
		}
	}

	private static void command(String command) {
		MinecraftClient.getInstance().player.networkHandler.sendCommand(command);
	}

	private static String slug(String label) {
		return label.replaceAll("[^a-z0-9]+", "_");
	}
}
