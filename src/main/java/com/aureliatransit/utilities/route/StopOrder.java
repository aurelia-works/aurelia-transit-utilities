package com.aureliatransit.utilities.route;

import java.util.ArrayList;
import java.util.List;

/**
 * An editable order of a route's stops, kept as a permutation of the original indices so that repeated platforms
 * (loops) and per-stop data (custom destinations) travel with their stop.
 */
public final class StopOrder {

	private final List<Integer> order = new ArrayList<>();

	public StopOrder(int size) {
		for (int i = 0; i < size; i++) {
			order.add(i);
		}
	}

	public int size() {
		return order.size();
	}

	/** Original index of the stop now at {@code position}. */
	public int originalIndexAt(int position) {
		return order.get(position);
	}

	/**
	 * Moves the stop at {@code from} so it ends up in front of the stop that is now at {@code insertBefore}
	 * ({@code size()} = to the end). Returns the stop's new position, or -1 when nothing changed.
	 */
	public int move(int from, int insertBefore) {
		if (from < 0 || from >= order.size() || insertBefore < 0 || insertBefore > order.size()) {
			return -1;
		}
		final int target = insertBefore > from ? insertBefore - 1 : insertBefore;
		if (target == from) {
			return -1;
		}
		order.add(target, order.remove(from));
		return target;
	}

	public void reverse() {
		java.util.Collections.reverse(order);
	}

	public void reset() {
		final int size = order.size();
		order.clear();
		for (int i = 0; i < size; i++) {
			order.add(i);
		}
	}

	public boolean isChanged() {
		for (int i = 0; i < order.size(); i++) {
			if (order.get(i) != i) {
				return true;
			}
		}
		return false;
	}

	/** The original list in the edited order. {@code original} must have {@link #size()} elements. */
	public <T> List<T> apply(List<T> original) {
		if (original.size() != order.size()) {
			throw new IllegalArgumentException("Route changed: expected " + order.size() + " stops, found " + original.size());
		}
		final List<T> result = new ArrayList<>(original.size());
		order.forEach(index -> result.add(original.get(index)));
		return result;
	}

	/**
	 * Insertion point for a drag at {@code mouseY} in a list whose first row starts at {@code listTop} (already
	 * scrolled), with rows of {@code rowHeight}. Upper half of a row inserts before it, lower half after it.
	 */
	public static int insertionIndex(double mouseY, double listTop, int rowHeight, int size) {
		final int index = (int) Math.floor((mouseY - listTop) / rowHeight + 0.5);
		return Math.max(0, Math.min(size, index));
	}
}
