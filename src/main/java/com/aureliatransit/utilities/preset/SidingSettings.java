package com.aureliatransit.utilities.preset;

/**
 * The settings of MTR's siding screen besides the train itself, so a preset can carry a whole siding setup.
 * {@code maxVehicles} is 0 for "unlimited" (MTR's own encoding) and ignored when {@code manual} is set. Values are clamped to the ranges MTR's own siding screen allows.
 */
public record SidingSettings(
		double acceleration,
		double deceleration,
		int maxVehicles,
		boolean manual,
		double maxManualSpeed,
		int manualToAutomaticTime,
		int delayedSpeedIncreasePercentage,
		int delayedReduceDwellTimePercentage,
		boolean earlyIncreaseDwellTime
) {

	/** MTR's {@code Siding.MIN_ACCELERATION} / {@code MAX_ACCELERATION}, in blocks per ms². */
	public static final double MIN_ACCELERATION = 4E-7;
	public static final double MAX_ACCELERATION = 2E-5;
	public static final int MAX_PERCENTAGE = 100;

	public SidingSettings {
		acceleration = clamp(acceleration, MIN_ACCELERATION, MAX_ACCELERATION);
		deceleration = clamp(deceleration, MIN_ACCELERATION, MAX_ACCELERATION);
		maxVehicles = Math.max(0, maxVehicles);
		maxManualSpeed = Double.isFinite(maxManualSpeed) ? Math.max(0, maxManualSpeed) : 0;
		manualToAutomaticTime = Math.max(0, manualToAutomaticTime);
		delayedSpeedIncreasePercentage = Math.max(0, Math.min(MAX_PERCENTAGE, delayedSpeedIncreasePercentage));
		delayedReduceDwellTimePercentage = Math.max(0, Math.min(MAX_PERCENTAGE, delayedReduceDwellTimePercentage));
	}

	public boolean unlimited() {
		return maxVehicles == 0;
	}

	private static double clamp(double value, double min, double max) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
	}
}
