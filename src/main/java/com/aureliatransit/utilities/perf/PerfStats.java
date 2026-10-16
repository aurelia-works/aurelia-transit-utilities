package com.aureliatransit.utilities.perf;

/**
 * Counts MTR's draw calls and time spent in MTR's batched renderer, and turns them into per-frame averages once per
 * second for the F3 screen. Render thread only.
 */
public final class PerfStats {

	private static final long WINDOW_NANOS = 1_000_000_000L;

	private long draws;
	private long renderNanos;
	private int frames;
	private long windowStart = -1;

	private long loads;
	private long loadNanos;
	private long maxLoadNanos;
	private long mergeNanos;

	private double drawsPerFrame;
	private double renderMillisPerFrame;
	private boolean hasResult;

	public void draw() {
		draws++;
	}

	/** One MTR model build (OBJ parse and upload) on the render thread. */
	public void load(long nanos) {
		loads++;
		loadNanos += nanos;
		maxLoadNanos = Math.max(maxLoadNanos, nanos);
	}

	/** The merge and upload part of a model build (ATU's merged upload only). */
	public void merge(long nanos) {
		mergeNanos += nanos;
	}

	public double mergeMillis() {
		return mergeNanos / 1e6;
	}

	public long loads() {
		return loads;
	}

	public double loadMillis() {
		return loadNanos / 1e6;
	}

	public double maxLoadMillis() {
		return maxLoadNanos / 1e6;
	}

	public void render(long nanos) {
		renderNanos += nanos;
	}

	/** Called once per rendered frame with the current time. */
	public void frame(long now) {
		if (windowStart < 0) {
			windowStart = now;
			draws = 0;
			renderNanos = 0;
			return;
		}
		frames++;
		if (now - windowStart >= WINDOW_NANOS) {
			drawsPerFrame = (double) draws / frames;
			renderMillisPerFrame = renderNanos / 1e6 / frames;
			hasResult = true;
			draws = 0;
			renderNanos = 0;
			frames = 0;
			windowStart = now;
		}
	}

	public boolean hasResult() {
		return hasResult;
	}

	public double drawsPerFrame() {
		return drawsPerFrame;
	}

	public double renderMillisPerFrame() {
		return renderMillisPerFrame;
	}
}
