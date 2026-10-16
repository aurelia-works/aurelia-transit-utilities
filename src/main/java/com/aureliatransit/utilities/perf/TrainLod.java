package com.aureliatransit.utilities.perf;

/**
 * Detail level of one train car by its distance from the camera. The car you ride is always drawn in full.
 * A car drawn as a box never asks MTR for its model, so its OBJ is not loaded until it comes closer.
 */
public final class TrainLod {

	public enum Tier {FULL, NO_INTERIOR, BOX}

	private TrainLod() {
	}

	public static Tier tier(double distanceBlocks, boolean riding, int interiorChunks, int boxChunks) {
		if (riding) {
			return Tier.FULL;
		}
		if (boxChunks > 0 && distanceBlocks > boxChunks * 16.0) {
			return Tier.BOX;
		}
		if (interiorChunks > 0 && distanceBlocks > interiorChunks * 16.0) {
			return Tier.NO_INTERIOR;
		}
		return Tier.FULL;
	}
}
