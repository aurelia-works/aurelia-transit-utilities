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

	private double drawsPerFrame;
	private double renderMillisPerFrame;
	private boolean hasResult;

	public void draw() {
		draws++;
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
