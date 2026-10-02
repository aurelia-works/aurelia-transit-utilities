package com.aureliatransit.utilities.jump;

import com.aureliatransit.utilities.overlap.Point;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JumpTargetTest {

	@Test
	void standsOneBlockAboveTheFirstRail() {
		assertEquals(new Point(10, 65, -3), JumpTarget.standOn(List.of(new Point(10, 64, -3), new Point(99, 0, 0))).orElseThrow());
		assertTrue(JumpTarget.standOn(List.of()).isEmpty());
	}

	@Test
	void commands() {
		assertEquals("tp @s 10 65 -3", JumpTarget.command(new Point(10, 65, -3)));
		assertEquals("tp @s 5 ~ -7", JumpTarget.commandKeepingHeight(5, -7));
	}

	@Test
	void search() {
		assertTrue(JumpTarget.matches("Platform 1 (Central)", "cent"));
		assertTrue(JumpTarget.matches("東京駅", ""));
		assertFalse(JumpTarget.matches("Alpha", "beta"));
	}
}
