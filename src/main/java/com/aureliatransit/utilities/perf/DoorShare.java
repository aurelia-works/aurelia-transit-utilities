package com.aureliatransit.utilities.perf;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.object.VertexArray;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MTR 4.0.5 builds each train car twice, once with the doors open and once with them closed, and uploads every part
 * that is not a door into both. ATU links the two lists of each part condition when MTR creates them: the parts both
 * share are uploaded once and both models draw the same buffers; only the door parts (closed model) are extra.
 * <p>
 * The link lives on the {@code ObjModel}s ({@link Holder}). The shared buffers are held weakly here: while either model
 * draws them they stay, and once both are dropped they are freed like any other unused model.
 */
public final class DoorShare {

	/** Added to MTR's {@code OptimizedModel.ObjModel} by a mixin. */
	public interface Holder {

		DoorShare atu$doorShare();

		void atu$setDoorShare(DoorShare share);
	}

	/** Marks a part in more than one list of one build; such parts are never shared. */
	private static final DoorShare CONFLICT = new DoorShare(Collections.emptySet());

	private final Set<OptimizedModel.ObjModel> common;
	/** The common parts in the order the first upload listed them; both door states use it (one background build). */
	private List<OptimizedModel.ObjModel> commonOrder;
	private List<WeakReference<VertexArray>> uploaded;

	private DoorShare(Set<OptimizedModel.ObjModel> common) {
		this.common = common;
	}

	/**
	 * One build's lists, per part condition: doors open and doors closed (MTR's {@code objModelsModel} and
	 * {@code objModelsModelDoorsClosed}). Render thread.
	 */
	public static void link(Map<?, ? extends Collection<?>> open, Map<?, ? extends Collection<?>> closed, java.util.function.Function<Object, OptimizedModel.ObjModel> unwrap) {
		final Map<OptimizedModel.ObjModel, DoorShare> assigned = new IdentityHashMap<>();
		open.forEach((condition, openList) -> {
			final Collection<?> closedList = closed.get(condition);
			if (openList == null || closedList == null) {
				return;
			}
			final Set<OptimizedModel.ObjModel> openSet = identitySet(openList, unwrap);
			if (openSet.isEmpty() || !identitySet(closedList, unwrap).containsAll(openSet)) {
				return;
			}
			final DoorShare share = new DoorShare(openSet);
			for (final OptimizedModel.ObjModel objModel : openSet) {
				assigned.merge(objModel, share, (a, b) -> CONFLICT);
			}
		});
		assigned.forEach((objModel, share) -> ((Holder) (Object) objModel).atu$setDoorShare(share));
	}

	/**
	 * The share every linked part of this upload belongs to, when all of its common parts are in the upload; null when
	 * the upload is not one of a linked pair.
	 */
	public static DoorShare of(List<OptimizedModel.ObjModel> uniqueObjModels) {
		DoorShare found = null;
		for (final OptimizedModel.ObjModel objModel : uniqueObjModels) {
			final DoorShare share = ((Holder) (Object) objModel).atu$doorShare();
			if (share == null) {
				continue;
			}
			if (share == CONFLICT || found != null && found != share) {
				return null;
			}
			found = share;
		}
		if (found == null) {
			return null;
		}
		final Set<OptimizedModel.ObjModel> present = Collections.newSetFromMap(new IdentityHashMap<>());
		present.addAll(uniqueObjModels);
		return present.containsAll(found.common) ? found : null;
	}

	/**
	 * How one model upload is split: each part once, in order; with a share, the parts both door states use
	 * ({@code common}, uploaded once for both) and the rest.
	 */
	public static final class Split {

		public final List<OptimizedModel.ObjModel> unique;
		/** Null when the upload is not one of a linked pair. */
		public final DoorShare share;
		public final List<OptimizedModel.ObjModel> common;
		public final List<OptimizedModel.ObjModel> rest;

		private Split(List<OptimizedModel.ObjModel> unique, DoorShare share, List<OptimizedModel.ObjModel> common, List<OptimizedModel.ObjModel> rest) {
			this.unique = unique;
			this.share = share;
			this.common = common;
			this.rest = rest;
		}

		/** The part lists this upload would build now: the common parts only if no earlier upload's buffers are alive. */
		public List<List<OptimizedModel.ObjModel>> toBuild() {
			if (share == null) {
				return List.of(unique);
			}
			final List<List<OptimizedModel.ObjModel>> lists = new ArrayList<>(2);
			if (share.uploaded() == null) {
				lists.add(common);
			}
			if (!rest.isEmpty()) {
				lists.add(rest);
			}
			return lists;
		}
	}

	public static Split split(Collection<OptimizedModel.ObjModel> objModels) {
		final Set<OptimizedModel.ObjModel> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		final List<OptimizedModel.ObjModel> unique = new ArrayList<>(objModels.size());
		for (final OptimizedModel.ObjModel objModel : objModels) {
			if (seen.add(objModel)) {
				unique.add(objModel);
			}
		}
		final DoorShare share = of(unique);
		if (share == null) {
			return new Split(unique, null, List.of(), List.of());
		}
		final List<OptimizedModel.ObjModel> common = new ArrayList<>(unique.size());
		final List<OptimizedModel.ObjModel> rest = new ArrayList<>();
		for (final OptimizedModel.ObjModel objModel : unique) {
			(share.isCommon(objModel) ? common : rest).add(objModel);
		}
		// Exactly the share's parts (of() checked they are all here), so the first order fits every later upload.
		if (share.commonOrder == null) {
			share.commonOrder = common;
		}
		return new Split(unique, share, share.commonOrder, rest);
	}

	public boolean isCommon(OptimizedModel.ObjModel objModel) {
		return common.contains(objModel);
	}

	/** The common parts' buffers if an earlier upload made them and they are still in use, else null. */
	public List<VertexArray> uploaded() {
		if (uploaded == null) {
			return null;
		}
		final List<VertexArray> vertexArrays = new ArrayList<>(uploaded.size());
		for (final WeakReference<VertexArray> reference : uploaded) {
			final VertexArray vertexArray = reference.get();
			if (vertexArray == null) {
				return null;
			}
			vertexArrays.add(vertexArray);
		}
		return vertexArrays;
	}

	public void store(List<VertexArray> vertexArrays) {
		final List<WeakReference<VertexArray>> references = new ArrayList<>(vertexArrays.size());
		for (final VertexArray vertexArray : vertexArrays) {
			references.add(new WeakReference<>(vertexArray));
		}
		uploaded = references;
	}

	private static Set<OptimizedModel.ObjModel> identitySet(Collection<?> wrappers, java.util.function.Function<Object, OptimizedModel.ObjModel> unwrap) {
		final Set<OptimizedModel.ObjModel> set = Collections.newSetFromMap(new IdentityHashMap<>());
		for (final Object wrapper : wrappers) {
			final OptimizedModel.ObjModel objModel = unwrap.apply(wrapper);
			if (objModel != null) {
				set.add(objModel);
			}
		}
		return set;
	}
}
