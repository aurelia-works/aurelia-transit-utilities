package com.aureliatransit.utilities.perf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PerfSettingsTest {

	@TempDir
	Path dir;

	@Test
	void missingFileGivesEverythingOn() {
		final PerfSettings settings = PerfSettings.load(dir.resolve("none.properties"));
		assertTrue(settings.mergeTrainParts);
		assertTrue(settings.shaderPackFastPath);
		assertTrue(settings.debugOverlay);
	}

	@Test
	void savedValuesComeBack() throws Exception {
		final Path file = dir.resolve("config").resolve(PerfSettings.FILE_NAME);
		final PerfSettings settings = new PerfSettings();
		settings.mergeTrainParts = false;
		settings.debugOverlay = false;
		settings.save(file);
		final PerfSettings loaded = PerfSettings.load(file);
		assertFalse(loaded.mergeTrainParts);
		assertTrue(loaded.shaderPackFastPath);
		assertFalse(loaded.debugOverlay);
	}

	@Test
	void typoKeepsTheDefaultInsteadOfTurningAFixOff() throws Exception {
		final Path file = dir.resolve("typo.properties");
		Files.writeString(file, "mergeTrainParts = flase\nshaderPackFastPath = FALSE \n", StandardCharsets.UTF_8);
		final PerfSettings loaded = PerfSettings.load(file);
		assertTrue(loaded.mergeTrainParts);
		assertFalse(loaded.shaderPackFastPath);
	}

	@Test
	void distancesRoundTripAndBadValuesKeepDefaults() throws Exception {
		final Path file = dir.resolve("distances.properties");
		final PerfSettings settings = new PerfSettings();
		settings.trainInteriorChunks = 0;
		settings.trainBoxChunks = 14;
		settings.save(file);
		final PerfSettings loaded = PerfSettings.load(file);
		assertEquals(0, loaded.trainInteriorChunks);
		assertEquals(14, loaded.trainBoxChunks);
		Files.writeString(file, "trainInteriorChunks = -3\ntrainBoxChunks = ten\n", StandardCharsets.UTF_8);
		final PerfSettings bad = PerfSettings.load(file);
		assertEquals(5, bad.trainInteriorChunks);
		assertEquals(10, bad.trainBoxChunks);
	}

	@Test
	void distanceButtonCyclesThroughChoicesAndBackToOff() {
		assertEquals(2, PerfSettings.nextDistance(0));
		assertEquals(12, PerfSettings.nextDistance(10));
		assertEquals(12, PerfSettings.nextDistance(11));
		assertEquals(0, PerfSettings.nextDistance(32));
	}
}
