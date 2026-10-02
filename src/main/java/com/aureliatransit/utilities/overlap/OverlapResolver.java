package com.aureliatransit.utilities.overlap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plans how to give a contested platform to the station the player chose, without touching MTR's rule: every other
 * zone that contains the platform's midpoint is cut back on one face so it no longer does. For each zone the cut that
 * moves the fewest other platforms wins; ties prefer a map (X/Z) cut over a height cut, since players draw zones on the
 * map and zones are usually full height, then the least volume lost. Height cuts win only when they move fewer
 * platforms, which is the stacked-station case (subway under an overground station). The plan is only a proposal; the caller shows
 * {@link Plan#sideEffects()} before anything is sent.
 */
public final class OverlapResolver {

	private OverlapResolver() {
	}

	public record Cut(long stationId, Box before, Box after, Box.Side side) {
	}

	/**
	 * @param cuts        zones to change, in MTR station order
	 * @param sideEffects other platforms whose station changes: platform id to (before, after), null = no station
	 * @param reachesGoal whether the chosen platform ends up in the chosen station
	 */
	public record Plan(List<Cut> cuts, Map<Long, Long[]> sideEffects, boolean reachesGoal) {
	}

	/** Returns null when some zone cannot be cut without disappearing (the midpoint sits on every face). */
	public static Plan plan(Zones zones, Zones.Conflict conflict, long targetStationId) {
		if (!conflict.stationIds().contains(targetStationId)) {
			throw new IllegalArgumentException("Station " + targetStationId + " does not contain platform " + conflict.platformId());
		}
		Zones working = zones;
		final List<Cut> cuts = new ArrayList<>();
		for (final long stationId : conflict.stationIds()) {
			if (stationId == targetStationId) {
				continue;
			}
			final Box before = working.box(stationId);
			Cut best = null;
			int bestMoved = Integer.MAX_VALUE;
			double bestLost = Double.MAX_VALUE;
			boolean bestVertical = false;
			for (final Box.Side side : Box.Side.values()) {
				final Box after = before.cutAway(conflict.midpoint(), side);
				if (after.isEmpty()) {
					continue;
				}
				final Map<Long, Long[]> changes = working.changesTo(working.withBox(stationId, after));
				changes.remove(conflict.platformId());
				final int moved = changes.size();
				final double lost = before.volume() - after.volume();
				final boolean vertical = side == Box.Side.BELOW || side == Box.Side.ABOVE;
				final boolean better = best == null
						|| moved < bestMoved
						|| moved == bestMoved && !vertical && bestVertical
						|| moved == bestMoved && vertical == bestVertical && lost < bestLost;
				if (better) {
					best = new Cut(stationId, before, after, side);
					bestMoved = moved;
					bestLost = lost;
					bestVertical = vertical;
				}
			}
			if (best == null) {
				return null;
			}
			cuts.add(best);
			working = working.withBox(stationId, best.after());
		}
		final Map<Long, Long[]> sideEffects = new LinkedHashMap<>(zones.changesTo(working));
		sideEffects.remove(conflict.platformId());
		final Long finalStation = working.assignment().get(conflict.platformId());
		return new Plan(cuts, sideEffects, finalStation != null && finalStation == targetStationId);
	}
}
