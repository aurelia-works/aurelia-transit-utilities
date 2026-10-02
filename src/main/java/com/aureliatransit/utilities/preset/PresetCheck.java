package com.aureliatransit.utilities.preset;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Checks a preset against a target siding before it is applied. Nothing is applied when any problem is found. */
public final class PresetCheck {

	private PresetCheck() {
	}

	public sealed interface Problem permits WrongMode, MissingVehicles, TooLong {
	}

	public record WrongMode(String presetMode, String sidingMode) implements Problem {
	}

	/** Vehicle ids that the loaded resource packs do not provide. */
	public record MissingVehicles(Set<String> vehicleIds) implements Problem {
	}

	public record TooLong(double trainLength, double railLength) implements Problem {
	}

	public static List<Problem> check(TrainPreset preset, String sidingMode, double sidingRailLength, Set<String> knownVehicleIds) {
		final List<Problem> problems = new ArrayList<>();
		if (!preset.transportMode().equals(sidingMode)) {
			problems.add(new WrongMode(preset.transportMode(), sidingMode));
		}
		final Set<String> missing = new TreeSet<>();
		preset.cars().forEach(car -> {
			if (!knownVehicleIds.contains(car.vehicleId())) {
				missing.add(car.vehicleId());
			}
		});
		if (!missing.isEmpty()) {
			problems.add(new MissingVehicles(missing));
		}
		final double length = preset.totalLength();
		if (length > sidingRailLength + 1e-6) {
			problems.add(new TooLong(length, sidingRailLength));
		}
		return problems;
	}
}
