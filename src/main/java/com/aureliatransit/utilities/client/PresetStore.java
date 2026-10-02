package com.aureliatransit.utilities.client;

import com.aureliatransit.utilities.preset.PresetJson;
import com.aureliatransit.utilities.preset.PresetLibrary;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads the preset file once, on first use; writes it after every change. Errors are reported, never swallowed. */
public final class PresetStore {

	private static final PresetLibrary LIBRARY = new PresetLibrary();
	private static boolean loaded;
	private static String lastError;

	private PresetStore() {
	}

	public static PresetLibrary library() {
		if (!loaded) {
			loaded = true;
			load();
		}
		return LIBRARY;
	}

	/** The last load or save error, or null. Shown in the presets screen. */
	public static String lastError() {
		return lastError;
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(AureliaTransitUtilitiesClient.MOD_ID).resolve("train_presets.json");
	}

	private static void load() {
		final Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try {
			final PresetJson.LoadResult result = PresetJson.read(Files.readString(file, StandardCharsets.UTF_8), LIBRARY);
			AureliaTransitUtilitiesClient.LOGGER.info("[ATU] Loaded {} train presets ({} skipped) from {}", result.loaded(), result.skipped(), file);
			if (result.skipped() > 0) {
				lastError = result.skipped() + " broken preset(s) in train_presets.json were skipped";
			}
		} catch (IOException | IllegalArgumentException e) {
			// Keep the unreadable file so the next save does not destroy it.
			final Path backup = file.resolveSibling("train_presets.json.bad");
			try {
				Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}
			lastError = "train_presets.json could not be read and was moved to train_presets.json.bad";
			AureliaTransitUtilitiesClient.LOGGER.error("[ATU] {}", lastError, e);
		}
	}

	public static boolean save() {
		final Path file = file();
		try {
			Files.createDirectories(file.getParent());
			final Path temp = file.resolveSibling("train_presets.json.tmp");
			Files.writeString(temp, PresetJson.write(LIBRARY), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			lastError = null;
			return true;
		} catch (IOException e) {
			lastError = "Could not save train presets: " + e.getMessage();
			AureliaTransitUtilitiesClient.LOGGER.error("[ATU] {}", lastError, e);
			return false;
		}
	}
}
