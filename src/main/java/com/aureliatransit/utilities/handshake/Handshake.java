package com.aureliatransit.utilities.handshake;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The key/value map ATU publishes to Joban Client Mod's vehicle scripts (protocol 2, see docs/HANDSHAKE.md), and the
 * parsing of the stored melodies preference. Pure: no Minecraft, no JCM.
 */
public final class Handshake {

	public static final String ID = "aurelia_transit_utilities";
	public static final String PROTOCOL = "2";
	/** The Announcer speaks English only (text and its clips), so this is the only honest value. */
	public static final String ANNOUNCER_LANG = "en";
	public static final String CAPS = "announcer,announcer_lang,melodies";

	public static final String KEY_HANDSHAKE = ID + ":handshake";
	public static final String KEY_ANNOUNCER = ID + ":announcer";
	public static final String KEY_ANNOUNCER_LANG = ID + ":announcer_lang";
	public static final String KEY_MELODIES = ID + ":melodies";
	public static final String KEY_CAPS = ID + ":caps";

	private Handshake() {
	}

	public static String onOff(boolean value) {
		return value ? "on" : "off";
	}

	/** Everything to register, in registration order. The base key carries the mod version. */
	public static Map<String, String> values(String modVersion, boolean announcerOn, boolean melodiesOn) {
		final Map<String, String> map = new LinkedHashMap<>();
		map.put(ID, modVersion == null || modVersion.isEmpty() ? "unknown" : modVersion);
		map.put(KEY_HANDSHAKE, PROTOCOL);
		map.put(KEY_ANNOUNCER, onOff(announcerOn));
		map.put(KEY_ANNOUNCER_LANG, ANNOUNCER_LANG);
		map.put(KEY_MELODIES, onOff(melodiesOn));
		map.put(KEY_CAPS, CAPS);
		return map;
	}

	/** Missing or unreadable means on: melodies are the default behaviour of the vehicle. */
	public static boolean parseMelodies(Properties properties) {
		return properties == null || !"off".equals(properties.getProperty("melodies"));
	}

	public static Properties toProperties(boolean melodiesOn) {
		final Properties properties = new Properties();
		properties.setProperty("melodies", onOff(melodiesOn));
		return properties;
	}
}
