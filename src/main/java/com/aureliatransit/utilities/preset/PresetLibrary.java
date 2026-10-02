package com.aureliatransit.utilities.preset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The player's saved trains. Bounded; names are unique ignoring case. Not thread-safe (render thread only). */
public final class PresetLibrary {

	public static final int MAX_PRESETS = 256;
	public static final int MAX_CARS = 128;

	private final Map<String, TrainPreset> presets = new LinkedHashMap<>();

	public enum PutResult {ADDED, REPLACED, EMPTY_NAME, NO_CARS, TOO_MANY_CARS, LIBRARY_FULL}

	public PutResult put(TrainPreset preset) {
		if (preset.name().isEmpty()) {
			return PutResult.EMPTY_NAME;
		}
		if (preset.cars().isEmpty()) {
			return PutResult.NO_CARS;
		}
		if (preset.cars().size() > MAX_CARS) {
			return PutResult.TOO_MANY_CARS;
		}
		final String key = PresetNames.key(preset.name());
		if (presets.containsKey(key)) {
			presets.put(key, preset);
			return PutResult.REPLACED;
		}
		if (presets.size() >= MAX_PRESETS) {
			return PutResult.LIBRARY_FULL;
		}
		presets.put(key, preset);
		return PutResult.ADDED;
	}

	public boolean remove(String name) {
		return presets.remove(PresetNames.key(name)) != null;
	}

	public TrainPreset get(String name) {
		return presets.get(PresetNames.key(name));
	}

	public int size() {
		return presets.size();
	}

	/** Presets of one transport mode, sorted by name. */
	public List<TrainPreset> forMode(String transportMode) {
		final List<TrainPreset> result = new ArrayList<>();
		presets.values().forEach(preset -> {
			if (preset.transportMode().equals(transportMode)) {
				result.add(preset);
			}
		});
		result.sort(Comparator.comparing(preset -> PresetNames.key(preset.name())));
		return result;
	}

	public List<TrainPreset> all() {
		return new ArrayList<>(presets.values());
	}

	public void clear() {
		presets.clear();
	}
}
