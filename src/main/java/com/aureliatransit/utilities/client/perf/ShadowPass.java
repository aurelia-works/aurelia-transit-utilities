package com.aureliatransit.utilities.client.perf;

/**
 * Whether MTR's current render pass is a shader pack's shadow pass. MTR's own {@code OptimizedRenderer.renderingShadows()}
 * walks the whole thread stack on every call, far too slow per rail piece, so ATU works it out once at the start of
 * each {@code MainRenderer.render} the same way (an Iris or OptiFine class on the stack) and keeps it for the pass.
 */
public final class ShadowPass {

	private static final StackWalker WALKER = StackWalker.getInstance();
	private static boolean active;

	private ShadowPass() {
	}

	/** Start of MTR's {@code MainRenderer.render}, render thread. */
	public static void begin() {
		active = WALKER.walk(frames -> frames.anyMatch(frame -> {
			final String className = frame.getClassName();
			return className.startsWith("net.irisshaders") || className.startsWith("net.optifine");
		}));
	}

	public static boolean active() {
		return active;
	}
}
