package com.aureliatransit.utilities.handshake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class HandshakeTest {

	@Test
	void valuesHaveEveryKey() {
		final var map = Handshake.values("1.2.0", true, false);
		assertEquals("1.2.0", map.get("aurelia_transit_utilities"));
		assertEquals("2", map.get("aurelia_transit_utilities:handshake"));
		assertEquals("on", map.get("aurelia_transit_utilities:announcer"));
		assertEquals("en", map.get("aurelia_transit_utilities:announcer_lang"));
		assertEquals("off", map.get("aurelia_transit_utilities:melodies"));
		assertEquals(6, map.size());
	}

	@Test
	void capsListNamesEveryFeatureKey() {
		final var caps = List.of(Handshake.CAPS.split(","));
		for (final String key : Handshake.values("x", true, true).keySet()) {
			if (key.contains(":") && !key.endsWith(":handshake") && !key.endsWith(":caps")) {
				assertTrue(caps.contains(key.substring(key.indexOf(':') + 1)), key);
			}
		}
	}

	@Test
	void unknownVersionFallsBack() {
		assertEquals("unknown", Handshake.values(null, true, true).get(Handshake.ID));
	}

	@Test
	void melodiesDefaultOnAndRoundTrip() throws IOException {
		assertTrue(Handshake.parseMelodies(new Properties()));
		assertTrue(Handshake.parseMelodies(null));
		for (final boolean value : new boolean[]{true, false}) {
			final StringWriter out = new StringWriter();
			Handshake.toProperties(value).store(out, null);
			final Properties back = new Properties();
			back.load(new StringReader(out.toString()));
			assertEquals(value, Handshake.parseMelodies(back));
		}
		final Properties junk = new Properties();
		junk.setProperty("melodies", "banana");
		assertTrue(Handshake.parseMelodies(junk));
		assertFalse(Handshake.parseMelodies(Handshake.toProperties(false)));
	}
}
