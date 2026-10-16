package com.aureliatransit.utilities.client.perf;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL33;

import java.lang.ref.Cleaner;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Frees the GPU buffers of MTR models nobody can draw any more. MTR 4.0.5 drops a model when it has not been used for
 * a minute but never deletes its vertex array, vertex buffer or index buffer (and {@code VertexArray.close} deletes
 * only the vertex array), so VRAM grows every time a train leaves view and comes back.
 * <p>
 * Each uploaded MTR vertex array gets a {@link Buffers} record. When the Java object becomes unreachable (no cache,
 * batch or other model holds it) the {@link Cleaner} hands its ids to the render thread, which deletes them at the
 * end of the frame. An explicit {@code close()} by MTR deletes the vertex array itself; the buffers follow here.
 */
public final class GlGarbage {

	private static final Cleaner CLEANER = Cleaner.create();
	private static final Queue<int[]> PENDING = new ConcurrentLinkedQueue<>();
	private static long freedArrays;

	private GlGarbage() {
	}

	/** GL ids of one uploaded MTR vertex array; deleted once, by whichever comes first. */
	public static final class Buffers implements Runnable {

		private final int vertexArray;
		private final int vertexBuffer;
		private final int indexBuffer;
		private volatile boolean done;

		private Buffers(int vertexArray, int vertexBuffer, int indexBuffer) {
			this.vertexArray = vertexArray;
			this.vertexBuffer = vertexBuffer;
			this.indexBuffer = indexBuffer;
		}

		/** Unreachable: delete everything. Runs on the cleaner thread. */
		@Override
		public void run() {
			if (!done) {
				done = true;
				PENDING.add(new int[]{vertexArray, vertexBuffer, indexBuffer});
			}
		}

		/** MTR closed the vertex array itself: delete only the two buffers it leaves behind. */
		public void closedByMtr() {
			if (!done) {
				done = true;
				PENDING.add(new int[]{0, vertexBuffer, indexBuffer});
			}
		}
	}

	public static Buffers track(Object vertexArrayObject, int vertexArray, int vertexBuffer, int indexBuffer) {
		final Buffers buffers = new Buffers(vertexArray, vertexBuffer, indexBuffer);
		CLEANER.register(vertexArrayObject, buffers);
		return buffers;
	}

	/** Render thread, once per frame. */
	public static void drain() {
		if (PENDING.isEmpty() || !RenderSystem.isOnRenderThread()) {
			return;
		}
		int[] ids;
		while ((ids = PENDING.poll()) != null) {
			if (ids[0] != 0) {
				GL33.glDeleteVertexArrays(ids[0]);
				freedArrays++;
			}
			if (ids[1] != 0) {
				GL33.glDeleteBuffers(ids[1]);
			}
			if (ids[2] != 0) {
				GL33.glDeleteBuffers(ids[2]);
			}
		}
	}

	public static long freedArrays() {
		return freedArrays;
	}
}
