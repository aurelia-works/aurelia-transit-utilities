package com.aureliatransit.utilities.preset;

import java.util.List;

/**
 * A named train (ordered cars) for one MTR transport mode, optionally with the rest of the siding's settings
 * ({@code settings} is null for presets saved without them, including every preset from ATU before 1.3.0).
 */
public record TrainPreset(String name, String transportMode, List<PresetCar> cars, SidingSettings settings) {

	public TrainPreset {
		name = PresetNames.sanitize(name);
		transportMode = transportMode == null ? "" : transportMode;
		cars = cars == null ? List.of() : List.copyOf(cars);
	}

	public TrainPreset(String name, String transportMode, List<PresetCar> cars) {
		this(name, transportMode, cars, null);
	}

	public TrainPreset withoutSettings() {
		return new TrainPreset(name, transportMode, cars, null);
	}

	/** Same rule as MTR's {@code Siding.getTotalVehicleLength}. */
	public double totalLength() {
		double total = 0;
		for (int i = 0; i < cars.size(); i++) {
			total += cars.get(i).totalLength(i == 0, i == cars.size() - 1);
		}
		return total;
	}
}
