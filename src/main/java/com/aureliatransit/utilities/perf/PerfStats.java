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
	private long sharedArrays;
	private long phaseMaxLoadNanos;
	private long lastLoadAt;
	private long arraysCreated;
	/** OBJ reading may run on worker threads (background loading). */
	private final java.util.concurrent.atomic.LongAdder parseNanos = new java.util.concurrent.atomic.LongAdder();
	private final java.util.concurrent.atomic.LongAdder parseFallbacks = new java.util.concurrent.atomic.LongAdder();

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
		phaseMaxLoadNanos = Math.max(phaseMaxLoadNanos, nanos);
		lastLoadAt = System.nanoTime();
	}

	/** Starts a measuring phase for {@link #phaseMaxLoadMillis} (benchmarks). */
	public void startPhase() {
		phaseMaxLoadNanos = 0;
	}

	/** Longest single model build since {@link #startPhase}. */
	public double phaseMaxLoadMillis() {
		return phaseMaxLoadNanos / 1e6;
	}

	/** {@link System#nanoTime} of the end of the last model build. */
	public long lastLoadAt() {
		return lastLoadAt;
	}

	/** MTR created a GPU vertex array (any model). */
	public void arrayCreated() {
		arraysCreated++;
	}

	public long arraysCreated() {
		return arraysCreated;
	}

	/** The merge and upload part of a model build (ATU's merged upload only). */
	public void merge(long nanos) {
		mergeNanos += nanos;
	}

	/** One OBJ file read (ATU's reader, or the time it took to hand a file back to MTR's). Any thread. */
	public void parse(long nanos) {
		parseNanos.add(nanos);
	}

	/** A file ATU's reader left to MTR's javagl reader. Any thread. */
	public void parseFallback() {
		parseFallbacks.increment();
	}

	public double parseMillis() {
		return parseNanos.sum() / 1e6;
	}

	public long parseFallbacks() {
		return parseFallbacks.sum();
	}

	/** A model reused the other door state's buffers instead of uploading them again. */
	public void shared(int vertexArrays) {
		sharedArrays += vertexArrays;
	}

	public long sharedArrays() {
		return sharedArrays;
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
