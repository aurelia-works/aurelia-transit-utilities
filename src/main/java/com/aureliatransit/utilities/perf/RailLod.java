package com.aureliatransit.utilities.perf;

/**
 * Detail level of one piece of a 3D rail by its distance from the camera. MTR draws a 3D rail as one model instance
 * (one draw call) every {@code repeatInterval} blocks, at any distance.
 * <ul>
 *     <li>{@link Tier#FULL}: MTR's own model.</li>
 *     <li>{@link Tier#SIMPLE}: a flat textured strip through MTR's batched 2D path, or, for a tall model (catenary,
 *     walls), still the model.</li>
 *     <li>{@link Tier#FAR}: one strip or one stretched model per {@link #FAR_SPACING} / {@link #TALL_SPACING} blocks
 *     instead of one per piece.</li>
 * </ul>
 * A shader pack's shadow pass uses {@link Tier#FAR} everywhere when cheap shadows are on.
 */
public final class RailLod {

	public enum Tier {FULL, SIMPLE, FAR}

	/** Models taller than this (blocks) are not replaced by a flat strip. */
	public static final double TALL_HEIGHT = 0.5;
	/** Far tier: blocks per flat strip. */
	public static final double FAR_SPACING = 4;
	/** Far tier: blocks per stretched tall model, shorter so masts and walls keep their shape. */
	public static final double TALL_SPACING = 2;

	private RailLod() {
	}

	public static Tier tier(double distanceBlocks, int simpleChunks, int farChunks, boolean cheapShadowPass) {
		if (cheapShadowPass) {
			return Tier.FAR;
		}
		if (farChunks > 0 && distanceBlocks > farChunks * 16.0) {
			return Tier.FAR;
		}
		if (simpleChunks > 0 && distanceBlocks > simpleChunks * 16.0) {
			return Tier.SIMPLE;
		}
		return Tier.FULL;
	}

	public static boolean isTall(double modelHeight) {
		return modelHeight > TALL_HEIGHT;
	}

	/** How many consecutive pieces one far-tier piece stands for; at least 1. */
	public static int farStep(double repeatInterval, boolean tall) {
		if (!(repeatInterval > 0)) {
			return 1;
		}
		return Math.max(1, (int) Math.round((tall ? TALL_SPACING : FAR_SPACING) / repeatInterval));
	}

	/**
	 * Index of a piece along its rail, from the distance of the piece's start to the rail's first node. Stable from
	 * frame to frame (unlike a running count, which shifts when pieces behind the camera are skipped), so the same
	 * pieces stay drawn as the camera turns.
	 */
	public static long pieceIndex(double distanceFromRailStart, double repeatInterval) {
		if (!(repeatInterval > 0)) {
			return 0;
		}
		return (long) Math.floor(distanceFromRailStart / repeatInterval + 1E-3);
	}

	/** In the far tier only every {@code step}-th piece is drawn, covering the next {@code step} pieces. */
	public static boolean drawsFarPiece(long pieceIndex, int step) {
		return step <= 1 || Math.floorMod(pieceIndex, step) == 0;
	}
}
