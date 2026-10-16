package com.aureliatransit.utilities.perf;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Player switches for ATU's MTR performance fixes, stored in {@code config/aurelia_transit_utilities_performance.properties}.
 * Every fix defaults to on; off means MTR's own code runs unchanged.
 */
public final class PerfSettings {

	public static final String FILE_NAME = "aurelia_transit_utilities_performance.properties";

	/** One GPU buffer per material per car instead of one per OBJ group per material. Applies when models (re)load. */
	public boolean mergeTrainParts = true;
	/** With a shader pack, bind the shader once per batch instead of once per draw call. */
	public boolean shaderPackFastPath = true;
	/** In a shader pack's shadow pass, train cars cast shadows from their exterior only and translucent parts cast none. */
	public boolean cheapShadows = true;
	/**
	 * Faster model loading: ATU's OBJ reader and mesh builder (same output as MTR's), and no shader recompile for every
	 * model MTR loads (Joban Client Mod does that part too; ATU then leaves it to JCM).
	 */
	public boolean fasterModelLoading = true;
	/** Train OBJ files are read on a worker thread instead of stalling a frame (needs fasterModelLoading). */
	public boolean backgroundModelLoading = true;
	/** Models MTR has dropped give their memory and GPU buffers back. */
	public boolean freeUnusedModels = true;
	/** ATU lines on the F3 screen: MTR draw calls and render time per frame. */
	public boolean debugOverlay = true;
	/** Train cars farther than this (chunks) are drawn without interior parts. 0 = never. */
	public int trainInteriorChunks = 5;
	/** Train cars farther than this (chunks) are drawn as a box and their model is not loaded. 0 = never. */
	public int trainBoxChunks = 10;

	/** 3D rail pieces farther than this (chunks) are drawn as flat rail strips; tall rail models keep their model. 0 = never. */
	public int railSimpleChunks = 3;
	/** 3D rail pieces farther than this (chunks) are drawn as one long strip (or stretched model) per few blocks. 0 = never. */
	public int railFarChunks = 8;

	/** Choices offered on the settings screen, in chunks; 0 means off. */
	public static final int[] DISTANCE_CHOICES = {0, 2, 3, 4, 5, 6, 8, 10, 12, 14, 16, 20, 24, 32};

	public static PerfSettings load(Path file) {
		final PerfSettings settings = new PerfSettings();
		if (!Files.isRegularFile(file)) {
			return settings;
		}
		final Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException | IllegalArgumentException e) {
			return settings;
		}
		settings.mergeTrainParts = flag(properties, "mergeTrainParts", settings.mergeTrainParts);
		settings.shaderPackFastPath = flag(properties, "shaderPackFastPath", settings.shaderPackFastPath);
		settings.cheapShadows = flag(properties, "cheapShadows", settings.cheapShadows);
		settings.fasterModelLoading = flag(properties, "fasterModelLoading", settings.fasterModelLoading);
		settings.backgroundModelLoading = flag(properties, "backgroundModelLoading", settings.backgroundModelLoading);
		settings.freeUnusedModels = flag(properties, "freeUnusedModels", settings.freeUnusedModels);
		settings.debugOverlay = flag(properties, "debugOverlay", settings.debugOverlay);
		settings.trainInteriorChunks = chunks(properties, "trainInteriorChunks", settings.trainInteriorChunks);
		settings.trainBoxChunks = chunks(properties, "trainBoxChunks", settings.trainBoxChunks);
		settings.railSimpleChunks = chunks(properties, "railSimpleChunks", settings.railSimpleChunks);
		settings.railFarChunks = chunks(properties, "railFarChunks", settings.railFarChunks);
		return settings;
	}

	public void save(Path file) throws IOException {
		final Properties properties = new Properties();
		properties.setProperty("mergeTrainParts", Boolean.toString(mergeTrainParts));
		properties.setProperty("shaderPackFastPath", Boolean.toString(shaderPackFastPath));
		properties.setProperty("cheapShadows", Boolean.toString(cheapShadows));
		properties.setProperty("fasterModelLoading", Boolean.toString(fasterModelLoading));
		properties.setProperty("backgroundModelLoading", Boolean.toString(backgroundModelLoading));
		properties.setProperty("freeUnusedModels", Boolean.toString(freeUnusedModels));
		properties.setProperty("debugOverlay", Boolean.toString(debugOverlay));
		properties.setProperty("trainInteriorChunks", Integer.toString(trainInteriorChunks));
		properties.setProperty("trainBoxChunks", Integer.toString(trainBoxChunks));
		properties.setProperty("railSimpleChunks", Integer.toString(railSimpleChunks));
		properties.setProperty("railFarChunks", Integer.toString(railFarChunks));
		Files.createDirectories(file.getParent());
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			properties.store(writer, "Aurelia Transit Utilities: MTR performance fixes (true = on, false = MTR's own code)");
		}
	}

	/** The next value of {@link #DISTANCE_CHOICES} after {@code current}, wrapping to 0 (off). */
	public static int nextDistance(int current) {
		for (final int choice : DISTANCE_CHOICES) {
			if (choice > current) {
				return choice;
			}
		}
		return 0;
	}

	private static int chunks(Properties properties, String key, int fallback) {
		final String value = properties.getProperty(key);
		if (value == null) {
			return fallback;
		}
		try {
			final int parsed = Integer.parseInt(value.trim());
			return parsed < 0 || parsed > 64 ? fallback : parsed;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Anything other than an explicit true/false keeps the default, so a typo never turns a fix off silently. */
	private static boolean flag(Properties properties, String key, boolean fallback) {
		final String value = properties.getProperty(key);
		if (value == null) {
			return fallback;
		}
		final String trimmed = value.trim();
		if (trimmed.equalsIgnoreCase("true")) {
			return true;
		}
		if (trimmed.equalsIgnoreCase("false")) {
			return false;
		}
		return fallback;
	}
}
