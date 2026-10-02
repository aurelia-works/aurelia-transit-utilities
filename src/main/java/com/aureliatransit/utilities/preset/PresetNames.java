package com.aureliatransit.utilities.preset;

import java.util.Locale;

public final class PresetNames {

	public static final int MAX_LENGTH = 48;

	private PresetNames() {
	}

	/** Trims, collapses whitespace, strips control characters and caps the length. May return an empty string. */
	public static String sanitize(String name) {
		if (name == null) {
			return "";
		}
		final StringBuilder builder = new StringBuilder();
		boolean lastWasSpace = true;
		for (int i = 0; i < name.length() && builder.length() < MAX_LENGTH; i++) {
			final char c = name.charAt(i);
			if (Character.isWhitespace(c)) {
				if (!lastWasSpace) {
					builder.append(' ');
					lastWasSpace = true;
				}
			} else if (!Character.isISOControl(c)) {
				builder.append(c);
				lastWasSpace = false;
			}
		}
		return builder.toString().strip();
	}

	public static String key(String name) {
		return sanitize(name).toLowerCase(Locale.ROOT);
	}
}
