package com.aureliatransit.utilities.speed;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpeedAuditTest {

	private static SpeedAudit.Rail rail(String id, String a, String b, long speed) {
		return new SpeedAudit.Rail(id, a, b, speed, speed, 0, false, true, 0, 0, 0);
	}

	private static SpeedAudit.Rail curve(String id, String a, String b, long speed, double radius) {
		return new SpeedAudit.Rail(id, a, b, speed, speed, radius, false, true, 0, 0, 0);
	}

	private static SpeedAudit.Rail platform(String id, String a, String b, long speed) {
		return new SpeedAudit.Rail(id, a, b, speed, speed, 0, true, true, 0, 0, 0);
	}

	@Test
	void slowRailBetweenFastRailsIsFound() {
		final List<SpeedAudit.Finding> findings = SpeedAudit.audit(List.of(rail("a", "1", "2", 160), rail("b", "2", "3", 60), rail("c", "3", "4", 200)));
		assertEquals(1, findings.size());
		assertEquals(SpeedAudit.Kind.SLOW_SPOT, findings.get(0).kind());
		assertEquals("b", findings.get(0).rail().id());
		assertEquals(60, findings.get(0).limit());
		assertEquals(160, findings.get(0).expected(), "the slower side decides");
	}

	@Test
	void speedChangeAtOneEndIsNotASlowSpot() {
		assertTrue(SpeedAudit.audit(List.of(rail("a", "1", "2", 160), rail("b", "2", "3", 80), rail("c", "3", "4", 80))).isEmpty());
	}

	@Test
	void deadEndsAndPlatformsDoNotCount() {
		// "b" ends at a buffer; "d" sits between platform rails; platform rails themselves are never reported.
		assertTrue(SpeedAudit.audit(List.of(rail("a", "1", "2", 160), rail("b", "2", "3", 40))).isEmpty());
		assertTrue(SpeedAudit.audit(List.of(platform("p1", "1", "2", 80), rail("d", "2", "3", 40), platform("p2", "3", "4", 80))).isEmpty());
		assertTrue(SpeedAudit.audit(List.of(rail("a", "1", "2", 160), platform("p", "2", "3", 40), rail("c", "3", "4", 160))).isEmpty());
	}

	@Test
	void oneWayRailUsesItsDrivableDirection() {
		final SpeedAudit.Rail oneWay = new SpeedAudit.Rail("b", "2", "3", 60, 0, 0, false, true, 0, 0, 0);
		final List<SpeedAudit.Finding> findings = SpeedAudit.audit(List.of(rail("a", "1", "2", 120), oneWay, rail("c", "3", "4", 120)));
		assertEquals(60, findings.get(0).limit());
	}

	@Test
	void tightCurveIsReportedWithComfortableSpeed() {
		// 100 m radius: sqrt(1.5 * 100) * 3.6 = 44.1 km/h, rounded down to 40.
		assertEquals(40, SpeedAudit.comfortableSpeed(100));
		assertEquals(135, SpeedAudit.comfortableSpeed(1000));
		final List<SpeedAudit.Finding> findings = SpeedAudit.audit(List.of(curve("k", "1", "2", 120, 100), curve("ok", "3", "4", 50, 100)));
		assertEquals(1, findings.size());
		assertEquals(SpeedAudit.Kind.TIGHT_CURVE, findings.get(0).kind());
		assertEquals(40, findings.get(0).expected());
	}

	@Test
	void curvesAreOnlyCheckedForTrains() {
		final SpeedAudit.Rail boat = new SpeedAudit.Rail("w", "1", "2", 120, 120, 50, false, false, 0, 0, 0);
		assertTrue(SpeedAudit.audit(List.of(boat)).isEmpty());
	}

	@Test
	void slowSpotsComeFirstBiggestGapFirst() {
		final List<SpeedAudit.Finding> findings = SpeedAudit.audit(List.of(
				rail("a", "1", "2", 200), rail("small", "2", "3", 160), rail("c", "3", "4", 200),
				rail("d", "5", "6", 200), rail("big", "6", "7", 40), rail("f", "7", "8", 200),
				curve("k", "9", "10", 200, 100)
		));
		assertEquals(List.of("big", "small", "k"), findings.stream().map(finding -> finding.rail().id()).toList());
	}
}
