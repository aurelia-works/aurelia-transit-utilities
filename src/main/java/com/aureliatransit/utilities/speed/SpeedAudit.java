package com.aureliatransit.utilities.speed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only check of rail speed limits, from the rails MTR has sent to the client. Finds two things:
 * <ul>
 * <li><b>Slow spots:</b> a rail slower than the fastest rail on <em>both</em> of its ends, the usual result of one
 * segment laid with the wrong rail type in the middle of a fast line. Trains brake for it every time.</li>
 * <li><b>Tight curves:</b> a curved train rail whose limit is above a comfortable speed for its radius,
 * {@code v = sqrt(LATERAL_ACCELERATION * r)} (1.5 m/s², about what cant plus cant deficiency allow on real lines).
 * MTR does not slow trains for curves, so this is advice, not an error.</li>
 * </ul>
 * Platform, siding and turn-back rails are left out on both sides: their speed is set on purpose.
 */
public final class SpeedAudit {

	public static final double LATERAL_ACCELERATION = 1.5;
	/** A curve is only reported when its limit is this much above the comfortable speed. */
	public static final long CURVE_TOLERANCE_KMH = 10;
	public static final long MAX_COMFORT_KMH = 400;

	private SpeedAudit() {
	}

	/**
	 * One rail between two nodes. {@code speedForward} is the limit from {@code nodeA} to {@code nodeB}, 0 when trains
	 * cannot go that way. {@code radius} is the tightest horizontal radius in blocks (= metres), 0 for straight.
	 */
	public record Rail(String id, String nodeA, String nodeB, long speedForward, long speedBackward, double radius, boolean special, boolean trainLike, double x, double y, double z) {

		public long speed() {
			return Math.max(speedForward, speedBackward);
		}
	}

	public enum Kind {SLOW_SPOT, TIGHT_CURVE}

	/** {@code limit} is the rail's own limit; {@code expected} the neighbours' speed or the comfortable curve speed. */
	public record Finding(Kind kind, Rail rail, long limit, long expected) {

		public long gap() {
			return Math.abs(expected - limit);
		}
	}

	public static List<Finding> audit(List<Rail> rails) {
		final Map<String, List<Rail>> byNode = new HashMap<>();
		for (final Rail rail : rails) {
			byNode.computeIfAbsent(rail.nodeA(), key -> new ArrayList<>()).add(rail);
			byNode.computeIfAbsent(rail.nodeB(), key -> new ArrayList<>()).add(rail);
		}
		final List<Finding> findings = new ArrayList<>();
		for (final Rail rail : rails) {
			if (rail.special() || rail.speed() <= 0) {
				continue;
			}
			final long fastestA = fastestNeighbour(byNode.get(rail.nodeA()), rail);
			final long fastestB = fastestNeighbour(byNode.get(rail.nodeB()), rail);
			final long around = Math.min(fastestA, fastestB);
			if (around > rail.speed()) {
				findings.add(new Finding(Kind.SLOW_SPOT, rail, rail.speed(), around));
			}
			if (rail.trainLike() && rail.radius() > 0) {
				final long comfort = comfortableSpeed(rail.radius());
				if (rail.speed() > comfort + CURVE_TOLERANCE_KMH) {
					findings.add(new Finding(Kind.TIGHT_CURVE, rail, rail.speed(), comfort));
				}
			}
		}
		findings.sort(Comparator.comparing(Finding::kind).thenComparing(Comparator.comparingLong(Finding::gap).reversed()).thenComparing(finding -> finding.rail().id()));
		return findings;
	}

	/** Fastest ordinary rail touching the node, other than {@code self}; 0 when there is none. */
	private static long fastestNeighbour(List<Rail> atNode, Rail self) {
		long fastest = 0;
		if (atNode != null) {
			for (final Rail other : atNode) {
				if (other != self && !other.special()) {
					fastest = Math.max(fastest, other.speed());
				}
			}
		}
		return fastest;
	}

	/** Comfortable speed for a curve of {@code radius} metres, in km/h, rounded down to 5 km/h. */
	public static long comfortableSpeed(double radius) {
		final double kmh = Math.sqrt(LATERAL_ACCELERATION * radius) * 3.6;
		return Math.min(MAX_COMFORT_KMH, (long) (kmh / 5) * 5);
	}
}
