package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.perf.GlGarbage;
import com.aureliatransit.utilities.perf.AsyncModels;
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
	private static Vec3d along = new Vec3d(0, 0, 1);

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

	static void addSteps(AtuSelfTest test, boolean frameRates) {
		WorldRenderEvents.END.register(context -> {
			if (sampling) {
				frames++;
			}
		});
		test.act("start with every fix off", () -> {
			configure(NONE);
			shaders(false);
			command("gamemode spectator");
		});
		test.openDashboard();
		test.act("close dashboard", () -> MinecraftClient.getInstance().setScreen(null));
		test.act("NS trains in sidings", () -> placeTrains(test));
		test.waitFor("NS trains parked", () -> nsTrains() >= wantedTrains, 120 * SECONDS);
		// The server has them parked; the client may not have received them yet.
		test.waitFor("NS trains on the client", () -> !trains().isEmpty(), 60 * SECONDS);
		test.pause(2 * SECONDS);
		test.act("find trains", () -> locate(test));

		if (frameRates) {
			frameRates(test);
		}
		loading(test);
		test.onFinish(() -> {
			sampling = false;
			configure(ALL);
			Perf.settings().fasterModelLoading = true;
			Perf.settings().backgroundModelLoading = true;
			Perf.settings().freeUnusedModels = true;
		});
	}

	private static void frameRates(AtuSelfTest test) {
		// Models loaded so far used whatever was set before; reload so the first runs are pure MTR.
		reloadResources(test);
		measureViews(test, "shaders off, MTR only", NONE, true);
		test.act("merge", () -> configure(MERGE));
		reloadResources(test);
		measureViews(test, "shaders off, merge", MERGE, false);
		measureViews(test, "shaders off, merge + train LOD", MERGE_TRAIN_LOD, false);
		measureViews(test, "shaders off, all fixes", ALL, true);

		test.act("shaders on", () -> {
			configure(NONE);
			shaders(true);
		});
		reloadResources(test);
		measureViews(test, "shaders on, MTR only", NONE, true);
		test.act("merge", () -> configure(MERGE));
		reloadResources(test);
		measure(test, "shaders on, merge", MERGE, 40);
		measure(test, "shaders on, merge + fast path", MERGE_FAST, 40);
		measure(test, "shaders on, + cheap shadows", MERGE_FAST_SHADOWS, 40);
		measureViews(test, "shaders on, all fixes", ALL, true);

		test.act("shaders off again", () -> shaders(false));
	}

	/**
	 * Model builds after a resource reload with MTR's loading and with ATU's (fast merge, no shader recompile per
	 * model), then the camera far away until MTR drops the unused models: their GPU arrays must be freed.
	 */
	private static void loading(AtuSelfTest test) {
		for (final boolean fast : new boolean[]{false, true, false, true}) {
			final String label = fast ? "ATU loading" : "MTR loading";
			final long[] before = new long[6];
			final double[] beforeMillis = new double[3];
			// Counted from before the reload: MTR rebuilds the visible models one per tick right after it.
			test.act(label, () -> {
				configure(ALL);
				Perf.settings().fasterModelLoading = fast;
				Perf.settings().backgroundModelLoading = fast;
				before[0] = Perf.STATS.loads();
				before[1] = Perf.STATS.arraysCreated();
				before[2] = Perf.STATS.sharedArrays();
				before[3] = AsyncModels.built();
				before[4] = Perf.STATS.parseFallbacks();
				beforeMillis[0] = Perf.STATS.loadMillis();
				beforeMillis[1] = Perf.STATS.mergeMillis();
				beforeMillis[2] = Perf.STATS.parseMillis();
			});
			reloadResources(test);
			test.act("phase " + label, () -> {
				Perf.STATS.startPhase();
				before[5] = System.nanoTime();
			});
			// Close enough for full detail: every car model and rail model gets built.
			measure(test, "shaders off, " + label, ALL, 40);
			test.act("builds " + label, () -> {
				test.note(String.format(Locale.ROOT, "%-38s model builds: %d, %.0f ms total on the render thread, of which %.0f ms merge and upload; longest %.0f ms; last %.2f s after the reload", label, Perf.STATS.loads() - before[0], Perf.STATS.loadMillis() - beforeMillis[0], Perf.STATS.mergeMillis() - beforeMillis[1], Perf.STATS.phaseMaxLoadMillis(), (Perf.STATS.lastLoadAt() - before[5]) / 1e9));
				test.note(String.format(Locale.ROOT, "%-38s GPU vertex arrays created: %d, shared between door states: %d; OBJ reading %.0f ms (any thread), %d background builds, %d files left to MTR's reader", label, Perf.STATS.arraysCreated() - before[1], Perf.STATS.sharedArrays() - before[2], Perf.STATS.parseMillis() - beforeMillis[2], AsyncModels.built() - before[3], Perf.STATS.parseFallbacks() - before[4]));
				if (fast) {
					test.check("door states share GPU buffers", Perf.STATS.sharedArrays() > before[2], (Perf.STATS.sharedArrays() - before[2]) + " vertex arrays reused");
					test.check("ATU reads every NS train OBJ file", Perf.STATS.parseFallbacks() == before[4], (Perf.STATS.parseFallbacks() - before[4]) + " left to MTR");
				}
			});
		}
		// Twice: the camera leaves until MTR drops the models, then comes back and MTR rebuilds them without a reload
		// screen. First with every model built on the render thread, then with background loading.
		for (final boolean background : new boolean[]{false, true}) {
			final String label = background ? "return, background loading" : "return, render-thread loading";
			final long[] freedBefore = new long[1];
			final long[] before = new long[3];
			final double[] beforeMillis = new double[1];
			test.act("camera far away", () -> {
				configure(ALL);
				Perf.settings().backgroundModelLoading = background;
				freedBefore[0] = GlGarbage.freedArrays();
				command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", center.x + 3000, center.y + 20, center.z + 3000));
			});
			// MTR drops a model after 60 s without use (CachedResource), then the Java objects must be collected.
			test.pause(80 * SECONDS);
			test.act("collect", System::gc);
			test.pause(5 * SECONDS);
			test.act("collect again", System::gc);
			test.pause(5 * SECONDS);
			test.act("freed GPU arrays", () -> {
				final long freed = GlGarbage.freedArrays() - freedBefore[0];
				test.check("unused MTR models free their GPU arrays", freed > 0, freed + " vertex arrays freed");
				Perf.STATS.startPhase();
				before[0] = Perf.STATS.loads();
				before[1] = AsyncModels.built();
				before[2] = System.nanoTime();
				beforeMillis[0] = Perf.STATS.loadMillis();
			});
			// Back again: MTR rebuilds the dropped models and the train must look as before.
			measure(test, "shaders off, " + label, ALL, 40);
			test.act("builds " + label, () -> {
				test.note(String.format(Locale.ROOT, "%-38s model builds: %d, %.0f ms total on the render thread, longest %.0f ms, last %.2f s after coming back; %d from a background parse", label, Perf.STATS.loads() - before[0], Perf.STATS.loadMillis() - beforeMillis[0], Perf.STATS.phaseMaxLoadMillis(), (Perf.STATS.lastLoadAt() - before[2]) / 1e9, AsyncModels.built() - before[1]));
				if (background) {
					test.check("train models load in the background", AsyncModels.built() > before[1], (AsyncModels.built() - before[1]) + " models built from a background parse");
				}
			});
			test.screenshot("perf_40_" + (background ? "return_background" : "return_render_thread"));
		}
	}

	/** Which fixes are on. Train LOD is interiors 5 / boxes 10 chunks, rail LOD flat 3 / far 8 chunks. */
	private record Fixes(boolean merge, boolean fastPath, boolean cheapShadows, boolean trainLod, boolean railLod) {
	}

	private static final Fixes NONE = new Fixes(false, false, false, false, false);
	private static final Fixes MERGE = new Fixes(true, false, false, false, false);
	private static final Fixes MERGE_FAST = new Fixes(true, true, false, false, false);
	private static final Fixes MERGE_FAST_SHADOWS = new Fixes(true, true, true, false, false);
	private static final Fixes MERGE_TRAIN_LOD = new Fixes(true, false, false, true, false);
	private static final Fixes ALL = new Fixes(true, true, true, true, true);

	/** Side views at 40, 100 and 170 m, then a view along the track. Merge needs a resource reload to change. */
	private static void measureViews(AtuSelfTest test, String label, Fixes fixes, boolean screenshots) {
		for (final int distance : new int[]{40, 100, 170, -1}) {
			measure(test, label, fixes, distance);
			if (screenshots) {
				test.screenshot("perf_" + (distance < 0 ? "track" : distance) + "_" + slug(label));
			}
		}
	}

	/**
	 * Camera {@code distance} blocks to the side of the trains looking at them, or (distance -1) 3 blocks above the
	 * track 60 m behind the train looking along it; settle, then count frames.
	 */
	private static void measure(AtuSelfTest test, String label, Fixes fixes, int distance) {
		test.act("camera at " + distance, () -> {
			configure(fixes);
			final Vec3d eye;
			final Vec3d target;
			if (distance < 0) {
				eye = center.subtract(along.multiply(60)).add(0, 3, 0);
				target = center.add(along.multiply(100));
			} else {
				eye = center.add(side.multiply(distance)).add(0, Math.max(8, distance * 0.25), 0);
				target = center;
			}
			command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f", eye.x, eye.y, eye.z, target.x, target.y, target.z));
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
			test.note(String.format(Locale.ROOT, "%-38s %5s: %6.1f fps, %6.2f ms/frame, %6.0f MTR draws/frame, %5.2f ms in MTR renderer, %d vehicles", label, distance < 0 ? "track" : distance + " m", fps, 1000 / fps,
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
			PerfBench.along = along.normalize();
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

	private static void configure(Fixes fixes) {
		final PerfSettings settings = Perf.settings();
		settings.mergeTrainParts = fixes.merge();
		settings.shaderPackFastPath = fixes.fastPath();
		settings.cheapShadows = fixes.cheapShadows();
		settings.trainInteriorChunks = fixes.trainLod() ? 5 : 0;
		settings.trainBoxChunks = fixes.trainLod() ? 10 : 0;
		settings.railSimpleChunks = fixes.railLod() ? 3 : 0;
		settings.railFarChunks = fixes.railLod() ? 8 : 0;
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
