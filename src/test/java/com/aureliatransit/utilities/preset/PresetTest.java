package com.aureliatransit.utilities.preset;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PresetTest {

	private static PresetCar car(String id, double length) {
		return new PresetCar(id, length, 3, -5, 5, 1, 2);
	}

	private static TrainPreset train(String name, String mode, PresetCar... cars) {
		return new TrainPreset(name, mode, List.of(cars));
	}

	@Test
	void namesAreSanitised() {
		assertEquals("Class 700 12-car", PresetNames.sanitize("  Class\t700   12-car \n"));
		assertEquals("", PresetNames.sanitize(null));
		assertEquals("", PresetNames.sanitize(" \u0000 "));
		assertEquals(PresetNames.MAX_LENGTH, PresetNames.sanitize("x".repeat(100)).length());
		assertEquals(PresetNames.key("ABC"), PresetNames.key(" abc "));
	}

	@Test
	void totalLengthMatchesMtrRule() {
		// MTR: sum of lengths plus inner coupling paddings; the first car's padding1 and the last car's padding2 are dropped.
		assertEquals(20, train("a", "TRAIN", car("x", 20)).totalLength(), 1e-9);
		assertEquals(20 + 2 + 1 + 20, train("a", "TRAIN", car("x", 20), car("y", 20)).totalLength(), 1e-9);
		assertEquals(20 + 2 + 1 + 20 + 2 + 1 + 20, train("a", "TRAIN", car("x", 20), car("y", 20), car("z", 20)).totalLength(), 1e-9);
		assertEquals(0, train("a", "TRAIN").totalLength(), 1e-9);
	}

	@Test
	void libraryReplacesByNameIgnoringCase() {
		final PresetLibrary library = new PresetLibrary();
		assertEquals(PresetLibrary.PutResult.ADDED, library.put(train("Metro", "TRAIN", car("a", 10))));
		assertEquals(PresetLibrary.PutResult.REPLACED, library.put(train("METRO ", "TRAIN", car("b", 10), car("b", 10))));
		assertEquals(1, library.size());
		assertEquals(2, library.get("metro").cars().size());
		assertTrue(library.remove("Metro"));
		assertFalse(library.remove("Metro"));
	}

	@Test
	void libraryRejectsInvalidAndIsBounded() {
		final PresetLibrary library = new PresetLibrary();
		assertEquals(PresetLibrary.PutResult.EMPTY_NAME, library.put(train("  ", "TRAIN", car("a", 1))));
		assertEquals(PresetLibrary.PutResult.NO_CARS, library.put(train("x", "TRAIN")));
		assertEquals(PresetLibrary.PutResult.TOO_MANY_CARS, library.put(new TrainPreset("x", "TRAIN", Collections.nCopies(PresetLibrary.MAX_CARS + 1, car("a", 1)))));
		for (int i = 0; i < PresetLibrary.MAX_PRESETS; i++) {
			assertEquals(PresetLibrary.PutResult.ADDED, library.put(train("p" + i, "TRAIN", car("a", 1))));
		}
		assertEquals(PresetLibrary.PutResult.LIBRARY_FULL, library.put(train("one more", "TRAIN", car("a", 1))));
		assertEquals(PresetLibrary.PutResult.REPLACED, library.put(train("p0", "TRAIN", car("b", 1))));
		assertEquals(PresetLibrary.MAX_PRESETS, library.size());
	}

	@Test
	void forModeFiltersAndSorts() {
		final PresetLibrary library = new PresetLibrary();
		library.put(train("b", "TRAIN", car("a", 1)));
		library.put(train("A", "TRAIN", car("a", 1)));
		library.put(train("boat", "BOAT", car("a", 1)));
		assertEquals(List.of("A", "b"), library.forMode("TRAIN").stream().map(TrainPreset::name).toList());
		assertEquals(1, library.forMode("BOAT").size());
		assertTrue(library.forMode("CABLE_CAR").isEmpty());
	}

	@Test
	void checkReportsEveryProblem() {
		final TrainPreset preset = train("a", "TRAIN", car("known", 20), car("gone", 20));
		assertTrue(PresetCheck.check(preset, "TRAIN", 100, Set.of("known", "gone")).isEmpty());
		final List<PresetCheck.Problem> problems = PresetCheck.check(preset, "BOAT", 30, Set.of("known"));
		assertEquals(3, problems.size());
		assertEquals(new PresetCheck.WrongMode("TRAIN", "BOAT"), problems.get(0));
		assertEquals(new PresetCheck.MissingVehicles(Set.of("gone")), problems.get(1));
		assertInstanceOf(PresetCheck.TooLong.class, problems.get(2));
		// Exactly fitting is fine.
		assertTrue(PresetCheck.check(preset, "TRAIN", preset.totalLength(), Set.of("known", "gone")).isEmpty());
	}

	@Test
	void jsonRoundTrip() {
		final PresetLibrary library = new PresetLibrary();
		library.put(train("Metro 6", "TRAIN", car("sp1900", 24.5), car("sp1900_trailer", 24.5)));
		library.put(train("Ferry", "BOAT", car("ferry", 30)));
		final PresetLibrary copy = new PresetLibrary();
		assertEquals(new PresetJson.LoadResult(2, 0), PresetJson.read(PresetJson.write(library), copy));
		assertEquals(library.get("Metro 6"), copy.get("metro 6"));
		assertEquals(library.get("Ferry"), copy.get("ferry"));
	}

	@Test
	void jsonSkipsBadEntriesAndRejectsGarbage() {
		final String json = """
				{"version":1,"presets":[
				 {"name":"ok","mode":"TRAIN","cars":[{"id":"a","length":1,"width":1,"bogie1":0,"bogie2":0,"coupling1":0,"coupling2":0}]},
				 {"name":"no cars","mode":"TRAIN","cars":[]},
				 {"name":"broken","mode":"TRAIN","cars":[{"id":"a"}]},
				 {"name":"nan","mode":"TRAIN","cars":[{"id":"a","length":"NaN","width":1,"bogie1":0,"bogie2":0,"coupling1":0,"coupling2":0}]},
				 42
				]}""";
		final PresetLibrary library = new PresetLibrary();
		assertEquals(new PresetJson.LoadResult(1, 4), PresetJson.read(json, library));
		assertNotNull(library.get("ok"));
		assertThrows(IllegalArgumentException.class, () -> PresetJson.read("[]", library));
		assertThrows(IllegalArgumentException.class, () -> PresetJson.read("{}", library));
		assertThrows(IllegalArgumentException.class, () -> PresetJson.read("not json {", library));
		assertEquals(1, library.size(), "a rejected file must not wipe the library");
	}
}
