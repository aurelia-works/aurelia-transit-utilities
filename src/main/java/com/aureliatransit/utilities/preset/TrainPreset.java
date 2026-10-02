package com.aureliatransit.utilities.preset;

import java.util.List;

/** A named train (ordered cars) for one MTR transport mode. */
public record TrainPreset(String name, String transportMode, List<PresetCar> cars) {

	public TrainPreset {
		name = PresetNames.sanitize(name);
		transportMode = transportMode == null ? "" : transportMode;
		cars = cars == null ? List.of() : List.copyOf(cars);
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
