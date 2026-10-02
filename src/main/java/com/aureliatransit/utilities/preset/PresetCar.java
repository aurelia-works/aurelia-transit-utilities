package com.aureliatransit.utilities.preset;

/**
 * One car of a saved train, with the same fields as MTR's {@code VehicleCar} so a preset can be applied without the
 * vehicle's resource pack data at hand.
 */
public record PresetCar(String vehicleId, double length, double width, double bogie1Position, double bogie2Position, double couplingPadding1, double couplingPadding2) {

	public PresetCar {
		vehicleId = vehicleId == null ? "" : vehicleId;
	}

	/** Same rule as MTR's {@code VehicleCar.getTotalLength}: outer coupling paddings of the whole train are not counted. */
	public double totalLength(boolean isFirst, boolean isLast) {
		return (isFirst ? 0 : couplingPadding1) + length + (isLast ? 0 : couplingPadding2);
	}
}
