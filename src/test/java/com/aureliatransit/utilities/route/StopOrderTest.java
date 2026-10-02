package com.aureliatransit.utilities.route;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StopOrderTest {

	private static final List<String> STOPS = List.of("A", "B", "C", "D");

	@Test
	void moveDownAndUp() {
		final StopOrder order = new StopOrder(4);
		assertEquals(2, order.move(0, 3));
		assertEquals(List.of("B", "C", "A", "D"), order.apply(STOPS));
		assertEquals(0, order.move(3, 0));
		assertEquals(List.of("D", "B", "C", "A"), order.apply(STOPS));
		assertEquals(3, order.move(0, 4));
		assertEquals(List.of("B", "C", "A", "D"), order.apply(STOPS));
	}

	@Test
	void noOpMovesReportNothing() {
		final StopOrder order = new StopOrder(4);
		assertEquals(-1, order.move(1, 1));
		assertEquals(-1, order.move(1, 2));
		assertEquals(-1, order.move(-1, 0));
		assertEquals(-1, order.move(0, 5));
		assertFalse(order.isChanged());
	}

	@Test
	void reverseResetAndChanged() {
		final StopOrder order = new StopOrder(4);
		order.reverse();
		assertTrue(order.isChanged());
		assertEquals(List.of("D", "C", "B", "A"), order.apply(STOPS));
		order.reset();
		assertFalse(order.isChanged());
		assertEquals(STOPS, order.apply(STOPS));
	}

	@Test
	void repeatedStopsKeepTheirIdentity() {
		// A loop route visits the same platform twice; each visit carries its own data.
		final List<String> loop = List.of("A1", "B", "A2");
		final StopOrder order = new StopOrder(3);
		order.move(2, 0);
		assertEquals(List.of("A2", "A1", "B"), order.apply(loop));
		assertEquals(2, order.originalIndexAt(0));
	}

	@Test
	void applyRefusesAChangedRoute() {
		assertThrows(IllegalArgumentException.class, () -> new StopOrder(3).apply(STOPS));
	}

	@Test
	void insertionIndexFromMouse() {
		// Rows of 12 starting at y=100.
		assertEquals(0, StopOrder.insertionIndex(90, 100, 12, 4));
		assertEquals(0, StopOrder.insertionIndex(105, 100, 12, 4));
		assertEquals(1, StopOrder.insertionIndex(107, 100, 12, 4));
		assertEquals(1, StopOrder.insertionIndex(117, 100, 12, 4));
		assertEquals(4, StopOrder.insertionIndex(145, 100, 12, 4));
		assertEquals(4, StopOrder.insertionIndex(500, 100, 12, 4));
	}
}
