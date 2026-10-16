package com.aureliatransit.utilities.perf;

import com.aureliatransit.utilities.mixin.mtr.ObjModelAccess;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.RawModel;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Background mesh builds for MTR 4.0.5 train models. After the OBJ files of a car are read, MTR builds its four models
 * (doors open, doors closed, two bogies) one per tick, each in one go on the render thread; for a heavy car that is a
 * 50-80 ms stall, nearly all of it {@link FastMeshBuild#pack}. With background loading on, the first try hands the
 * packing of every part list of that model to a worker and tells MTR "not ready"; a later try uploads the packed
 * buffers. The GPU uploads stay on the render thread.
 * <p>
 * Packing only reads MTR's meshes, which nothing changes once the model files are built (MTR's own path, which does
 * change them, is not used while this runs). Render thread except {@link Job#run}.
 */
public final class AsyncPacks {

	private static final long UNCLAIMED_MILLIS = 20_000;
	/** At most this many part lists wait at once (each is one material-merged buffer set of a car). */
	private static final int MAX_OUTSTANDING = 24;

	private static final Map<Key, Job> JOBS = new HashMap<>();

	/** MTR's {@code ObjModel.rawModel}. */
	public static final Function<OptimizedModel.ObjModel, RawModel> RAW_MODEL_OF = objModel -> ((ObjModelAccess) (Object) objModel).atu$rawModel();

	private static volatile boolean disabled;

	private static long started;
	private static long built;

	private AsyncPacks() {
	}

	/** One part list being packed on a worker. */
	private static final class Job implements Runnable {

		private final List<OptimizedModel.ObjModel> objModels;
		private final Function<OptimizedModel.ObjModel, RawModel> rawModelOf;
		private final VertexAttributeMapping mapping;
		private final long fingerprint;
		private volatile boolean done;
		private volatile List<FastMeshBuild.Packed> result;
		private long doneAt;

		private Job(List<OptimizedModel.ObjModel> objModels, Function<OptimizedModel.ObjModel, RawModel> rawModelOf, VertexAttributeMapping mapping) {
			this.objModels = objModels;
			this.rawModelOf = rawModelOf;
			this.mapping = mapping;
			fingerprint = fingerprint(objModels, rawModelOf);
		}

		@Override
		public void run() {
			try {
				result = FastMeshBuild.pack(objModels, rawModelOf, mapping);
			} catch (RuntimeException e) {
				// The render thread packs this list itself (and logs if it fails there too).
				Perf.LOGGER.debug("Background mesh build failed; MTR's model is built on the render thread", e);
			} finally {
				done = true;
			}
		}
	}

	/** A part list by the identity of its parts, in order (the same objects MTR passes again on the next try). */
	private static final class Key {

		private final OptimizedModel.ObjModel[] objModels;
		private final int hash;

		private Key(List<OptimizedModel.ObjModel> objModels) {
			this.objModels = objModels.toArray(new OptimizedModel.ObjModel[0]);
			int hash = 1;
			for (final OptimizedModel.ObjModel objModel : this.objModels) {
				hash = hash * 31 + System.identityHashCode(objModel);
			}
			this.hash = hash;
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Key key) || key.hash != hash || key.objModels.length != objModels.length) {
				return false;
			}
			for (int i = 0; i < objModels.length; i++) {
				if (key.objModels[i] != objModels[i]) {
					return false;
				}
			}
			return true;
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}

	/**
	 * Before a model build that may wait: makes sure each of these part lists is packed or being packed. True when all
	 * are packed (build now), false when the build should wait. Lists that cannot be started (too many waiting) are
	 * packed by the build itself, so they do not hold it back.
	 */
	public static boolean prepare(Collection<OptimizedModel.ObjModel> objModels, VertexAttributeMapping mapping) {
		if (disabled || !Perf.settings().mergeTrainParts || !Perf.settings().fasterModelLoading) {
			return true;
		}
		return prepareLists(DoorShare.split(objModels).toBuild(), RAW_MODEL_OF, mapping);
	}

	/** ATU's model upload failed and MTR's own runs from now on: nothing waits for background builds any more. */
	public static void disable() {
		disabled = true;
		JOBS.clear();
	}

	private static boolean prepareLists(List<List<OptimizedModel.ObjModel>> partLists, Function<OptimizedModel.ObjModel, RawModel> rawModelOf, VertexAttributeMapping mapping) {
		boolean ready = true;
		for (final List<OptimizedModel.ObjModel> objModels : partLists) {
			if (objModels.isEmpty()) {
				continue;
			}
			final Key key = new Key(objModels);
			Job job = JOBS.get(key);
			if (job == null) {
				if (JOBS.size() >= MAX_OUTSTANDING) {
					continue;
				}
				job = new Job(new ArrayList<>(objModels), rawModelOf, mapping);
				JOBS.put(key, job);
				AsyncModels.execute(job);
				started++;
			}
			if (!job.done) {
				ready = false;
			}
		}
		return ready;
	}

	/** The packed buffers of this part list, once; null when it was not packed in the background. */
	public static List<FastMeshBuild.Packed> take(List<OptimizedModel.ObjModel> objModels, VertexAttributeMapping mapping) {
		if (JOBS.isEmpty()) {
			return null;
		}
		final Key key = new Key(objModels);
		final Job job = JOBS.get(key);
		if (job == null || !job.done || job.mapping != mapping) {
			return null;
		}
		JOBS.remove(key);
		// MTR adds part placements straight into the parts' meshes; if any were added since, build from the new data.
		if (job.fingerprint != fingerprint(objModels, RAW_MODEL_OF)) {
			return null;
		}
		final List<FastMeshBuild.Packed> packed = job.result;
		if (packed != null) {
			built++;
		}
		return packed;
	}

	/** Mesh, vertex and face counts of the parts: changes when MTR adds a placement. Render thread. */
	private static long fingerprint(List<OptimizedModel.ObjModel> objModels, Function<OptimizedModel.ObjModel, RawModel> rawModelOf) {
		final long[] counts = new long[1];
		for (final OptimizedModel.ObjModel objModel : objModels) {
			rawModelOf.apply(objModel).iterateRawMeshList(rawMesh -> counts[0] = counts[0] * 1_000_003 + rawMesh.vertices.size() * 31L + rawMesh.faces.size());
		}
		return counts[0];
	}

	/** Once per client tick: drop results whose train left view before they were used. */
	static void tick(long now) {
		final Iterator<Job> iterator = JOBS.values().iterator();
		while (iterator.hasNext()) {
			final Job job = iterator.next();
			if (!job.done) {
				continue;
			}
			if (job.doneAt == 0) {
				job.doneAt = now;
			} else if (now - job.doneAt > UNCLAIMED_MILLIS) {
				iterator.remove();
			}
		}
	}

	public static long started() {
		return started;
	}

	/** Part lists uploaded from a background pack. */
	public static long built() {
		return built;
	}
}
