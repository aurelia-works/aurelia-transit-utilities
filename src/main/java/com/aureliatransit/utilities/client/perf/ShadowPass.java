package com.aureliatransit.utilities.client.perf;

import com.aureliatransit.utilities.perf.Perf;

/**
 * Whether MTR's current render pass is a shader pack's shadow pass. MTR 4.0.5's {@code ModShaderHandler.renderingShadows()}
 * answers that by walking the whole thread stack for an Iris or OptiFine class, and MTR asks it several times per frame
 * (rails, lifts, every train), with or without a shader pack. ATU works it out once at the start of each
 * {@code MainRenderer.render} the same way and keeps it for the pass, and answers MTR's own calls from that:
 * <ul>
 * <li>Without Iris and OptiFine no such class can be on the stack: always false, exactly MTR's answer.</li>
 * <li>With one, MTR's calls inside the pass get the pass's answer (their stack is the pass's plus MTR's own frames).
 * Every 128th call is still checked the slow way; on a difference ATU stops answering for the rest of the session.</li>
 * </ul>
 */
public final class ShadowPass {

	private static final StackWalker WALKER = StackWalker.getInstance();
	private static final boolean SHADER_MOD = present("net.irisshaders.iris.api.v0.IrisApi") || present("net.optifine.shaders.Shaders");
	private static final int CHECK_EVERY = 128;

	private static boolean active;
	private static boolean inPass;
	private static boolean mismatch;
	private static int calls;
	private static long answered;

	private ShadowPass() {
	}

	/** Start of MTR's {@code MainRenderer.render}, render thread. */
	public static void begin() {
		active = SHADER_MOD && walk();
		inPass = true;
	}

	/** End of MTR's {@code MainRenderer.render}. */
	public static void end() {
		inPass = false;
	}

	public static boolean active() {
		return active;
	}

	/** MTR's {@code renderingShadows()} without the stack walk; null when MTR should walk the stack itself. */
	public static Boolean mtrRenderingShadows() {
		if (!SHADER_MOD) {
			answered++;
			return Boolean.FALSE;
		}
		if (!inPass || mismatch || !Perf.settings().shaderPackFastPath) {
			return null;
		}
		if (++calls % CHECK_EVERY == 0) {
			final boolean walked = walk();
			if (walked != active) {
				mismatch = true;
				Perf.LOGGER.warn("Shadow pass detection differed from MTR's; MTR's own check is used for the rest of this session");
				return walked;
			}
		}
		answered++;
		return active;
	}

	/** MTR calls answered without a stack walk (F3 and benchmarks). */
	public static long answered() {
		return answered;
	}

	public static boolean mismatch() {
		return mismatch;
	}

	private static boolean walk() {
		return WALKER.walk(frames -> frames.anyMatch(frame -> {
			final String className = frame.getClassName();
			return className.startsWith("net.irisshaders") || className.startsWith("net.optifine");
		}));
	}

	private static boolean present(String className) {
		try {
			Class.forName(className, false, ShadowPass.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}
}
