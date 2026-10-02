package com.aureliatransit.utilities.preset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** File format: {@code {"version":1,"presets":[{"name":..,"mode":..,"cars":[{..}]}]}}. Bad entries are skipped and counted. */
public final class PresetJson {

	public static final int VERSION = 1;

	private PresetJson() {
	}

	public record LoadResult(int loaded, int skipped) {
	}

	public static String write(PresetLibrary library) {
		final JsonArray presets = new JsonArray();
		library.all().forEach(preset -> {
			final JsonObject object = new JsonObject();
			object.addProperty("name", preset.name());
			object.addProperty("mode", preset.transportMode());
			final JsonArray cars = new JsonArray();
			preset.cars().forEach(car -> {
				final JsonObject carObject = new JsonObject();
				carObject.addProperty("id", car.vehicleId());
				carObject.addProperty("length", car.length());
				carObject.addProperty("width", car.width());
				carObject.addProperty("bogie1", car.bogie1Position());
				carObject.addProperty("bogie2", car.bogie2Position());
				carObject.addProperty("coupling1", car.couplingPadding1());
				carObject.addProperty("coupling2", car.couplingPadding2());
				cars.add(carObject);
			});
			object.add("cars", cars);
			presets.add(object);
		});
		final JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.add("presets", presets);
		return root.toString();
	}

	/** Replaces the library's contents. Throws {@link IllegalArgumentException} when the file is not a preset file at all. */
	public static LoadResult read(String json, PresetLibrary library) {
		final JsonObject root;
		try {
			root = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Not a JSON object", e);
		}
		if (!root.has("presets") || !root.get("presets").isJsonArray()) {
			throw new IllegalArgumentException("Missing \"presets\" array");
		}
		library.clear();
		int loaded = 0;
		int skipped = 0;
		for (final JsonElement element : root.getAsJsonArray("presets")) {
			try {
				final JsonObject object = element.getAsJsonObject();
				final List<PresetCar> cars = new ArrayList<>();
				for (final JsonElement carElement : object.getAsJsonArray("cars")) {
					final JsonObject car = carElement.getAsJsonObject();
					cars.add(new PresetCar(
							car.get("id").getAsString(),
							finite(car, "length"), finite(car, "width"),
							finite(car, "bogie1"), finite(car, "bogie2"),
							finite(car, "coupling1"), finite(car, "coupling2")
					));
				}
				final PresetLibrary.PutResult result = library.put(new TrainPreset(object.get("name").getAsString(), object.get("mode").getAsString(), cars));
				if (result == PresetLibrary.PutResult.ADDED || result == PresetLibrary.PutResult.REPLACED) {
					loaded++;
				} else {
					skipped++;
				}
			} catch (RuntimeException e) {
				skipped++;
			}
		}
		return new LoadResult(loaded, skipped);
	}

	private static double finite(JsonObject object, String key) {
		final double value = object.get(key).getAsDouble();
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(key + " is not finite");
		}
		return value;
	}
}
