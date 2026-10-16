package com.aureliatransit.utilities.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerfStatsTest {

	@Test
	void averagesPerFrameOverOneSecond() {
		final PerfStats stats = new PerfStats();
		stats.frame(0);
		assertFalse(stats.hasResult());
		for (int frame = 1; frame <= 4; frame++) {
			for (int i = 0; i < 100; i++) {
				stats.draw();
			}
			stats.render(2_000_000);
			stats.frame(frame * 250_000_000L);
		}
		assertTrue(stats.hasResult());
		assertEquals(100, stats.drawsPerFrame(), 1e-9);
		assertEquals(2.0, stats.renderMillisPerFrame(), 1e-9);
	}

	@Test
	void drawsBeforeTheFirstFrameAreNotCounted() {
		final PerfStats stats = new PerfStats();
		stats.draw();
		stats.frame(0);
		stats.draw();
		stats.frame(1_000_000_000L);
		assertEquals(1, stats.drawsPerFrame(), 1e-9);
	}
}
