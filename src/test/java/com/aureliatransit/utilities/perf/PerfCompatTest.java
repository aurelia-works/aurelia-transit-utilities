package com.aureliatransit.utilities.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerfCompatTest {

	@Test
	void checkedVersionInEveryFormFabricReports() {
		assertTrue(PerfCompat.isChecked("4.0.5"));
		assertTrue(PerfCompat.isChecked("4.0.5+1.20.1"));
		assertTrue(PerfCompat.isChecked("FABRIC-4.0.5+1.20.1"));
	}

	@Test
	void otherVersionsStayOff() {
		assertFalse(PerfCompat.isChecked("4.0.6"));
		assertFalse(PerfCompat.isChecked("4.1.0+1.21.1"));
		assertFalse(PerfCompat.isChecked("4.0.5-beta"));
		assertFalse(PerfCompat.isChecked(null));
	}
}
