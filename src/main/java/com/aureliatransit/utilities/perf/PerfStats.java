package com.aureliatransit.utilities.perf;

/**
 * Counts MTR's draw calls and time spent in MTR's batched renderer, and turns them into per-frame averages once per
 * second for the F3 screen. Render thread only.
 */
public final class PerfStats {

	private static final long WINDOW_NANOS = 1_000_000_000L;

	private long draws;
	private long renderNanos;
	private long mtrNanos;
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

	/** Parts of the build in progress on the render thread, and the slowest build of the phase split into them. */
	private long buildCreateNanos;
	private long buildAssembleNanos;
	private long buildParseNanos;
	private long buildMergeNanos;
	private long buildPackNanos;
	private long buildCreateMergeNanos;
	private long buildCreateLoadNanos;
	private String phaseSlowestBuild = "none";

	private double drawsPerFrame;
	private double renderMillisPerFrame;
	private double mtrMillisPerFrame;
	private boolean hasResult;

	public void draw() {
		draws++;
	}

	/** One MTR model build (OBJ parse and upload) on the render thread. */
	public void load(long nanos) {
		loads++;
		loadNanos += nanos;
		maxLoadNanos = Math.max(maxLoadNanos, nanos);
		if (nanos > phaseMaxLoadNanos) {
			phaseMaxLoadNanos = nanos;
			phaseSlowestBuild = String.format(java.util.Locale.ROOT, "%.1f ms: createModel %.1f (model file %.1f, of which OBJ reading %.1f and materials %.1f; merge and upload %.1f; rest %.1f), merge and upload outside it %.1f (mesh build in all %.1f), other %.1f", nanos / 1e6, buildCreateNanos / 1e6, buildCreateLoadNanos / 1e6, buildParseNanos / 1e6, buildAssembleNanos / 1e6, buildCreateMergeNanos / 1e6, (buildCreateNanos - buildCreateLoadNanos - buildCreateMergeNanos) / 1e6, (buildMergeNanos - buildCreateMergeNanos) / 1e6, buildPackNanos / 1e6, (nanos - buildCreateNanos - (buildMergeNanos - buildCreateMergeNanos)) / 1e6);
		}
		buildCreateNanos = 0;
		buildAssembleNanos = 0;
		buildParseNanos = 0;
		buildMergeNanos = 0;
		buildPackNanos = 0;
		buildCreateMergeNanos = 0;
		buildCreateLoadNanos = 0;
		lastLoadAt = System.nanoTime();
	}

	/** MTR's {@code VehicleModel.createModel} inside the current build (render thread). */
	public void buildCreate(long nanos, long mergeNanosInside) {
		buildCreateNanos += nanos;
		buildCreateMergeNanos += mergeNanosInside;
	}

	/** MTR's {@code ModelResourceLoader.loadModel} (model file to parts) inside createModel. */
	public void buildCreateLoad(long nanos) {
		buildCreateLoadNanos += nanos;
	}

	/** Merge and upload time of the current build so far. */
	public long buildMergeNanos() {
		return buildMergeNanos;
	}

	/** Materials and mesh checks of a background-parsed file inside the current build (render thread). */
	public void buildAssemble(long nanos) {
		buildAssembleNanos += nanos;
	}

	/** An OBJ file read on the render thread inside the current build. */
	public void buildParse(long nanos) {
		buildParseNanos += nanos;
	}

	/** {@link FastMeshBuild#pack} inside the current build (render thread). */
	public void buildPack(long nanos) {
		buildPackNanos += nanos;
	}

	/** The slowest build since {@link #startPhase}, split into its parts. */
	public String phaseSlowestBuild() {
		return phaseSlowestBuild;
	}

	/** Starts a measuring phase for {@link #phaseMaxLoadMillis} (benchmarks). */
	public void startPhase() {
		phaseMaxLoadNanos = 0;
		phaseSlowestBuild = "none";
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
		buildMergeNanos += nanos;
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

	/** One whole MTR {@code MainRenderer.render} (CPU: everything MTR does per frame, the batched renderer included). */
	public void mtrRender(long nanos) {
		mtrNanos += nanos;
	}

	/** Called once per rendered frame with the current time. */
	public void frame(long now) {
		if (windowStart < 0) {
			windowStart = now;
			draws = 0;
			renderNanos = 0;
			mtrNanos = 0;
			return;
		}
		frames++;
		if (now - windowStart >= WINDOW_NANOS) {
			drawsPerFrame = (double) draws / frames;
			renderMillisPerFrame = renderNanos / 1e6 / frames;
			mtrMillisPerFrame = mtrNanos / 1e6 / frames;
			hasResult = true;
			draws = 0;
			renderNanos = 0;
			mtrNanos = 0;
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

	public double mtrMillisPerFrame() {
		return mtrMillisPerFrame;
	}
}
