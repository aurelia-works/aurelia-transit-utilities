package com.aureliatransit.utilities.codes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Station codes (JR East style "JY17") kept as extra '|'-separated parts at the end of an MTR station name, the
 * owner's choice so MTR's own signs and maps show them without any new data field.
 */
public final class StationCodes {

	/** 1-4 capital letters, optional '-', 1-4 digits. "JY17", "A01", "BL-5". */
	private static final Pattern CODE = Pattern.compile("^([A-Z]{1,4})-?(\\d{1,4})$");
	public static final int MAX_PREFIX = 4;
	public static final int MAX_NUMBER = 9999;

	private StationCodes() {
	}

	public static boolean isCode(String part) {
		return CODE.matcher(part.strip()).matches();
	}

	/** The letters of a code part, or null when the part is not a code. */
	public static String prefixOf(String part) {
		final var matcher = CODE.matcher(part.strip());
		return matcher.matches() ? matcher.group(1) : null;
	}

	/** Valid prefix: 1-4 letters A-Z (upper-cased). Returns null when invalid. */
	public static String normalisePrefix(String prefix) {
		final String upper = prefix == null ? "" : prefix.strip().toUpperCase(Locale.ROOT);
		return upper.matches("[A-Z]{1," + MAX_PREFIX + "}") ? upper : null;
	}

	public static String format(String prefix, int number, int digits) {
		return prefix + String.format(Locale.ROOT, "%0" + Math.max(1, digits) + "d", number);
	}

	/**
	 * Codes for {@code count} stations in order: start, start+step, ... Empty list when any number would leave
	 * 0..{@link #MAX_NUMBER} or the prefix is invalid.
	 */
	public static List<String> number(String prefix, int count, int start, int step, int digits) {
		final String normalised = normalisePrefix(prefix);
		final List<String> codes = new ArrayList<>();
		if (normalised == null || digits < 1 || digits > 4) {
			return codes;
		}
		for (int i = 0; i < count; i++) {
			final long number = (long) start + (long) i * step;
			if (number < 0 || number > MAX_NUMBER) {
				return new ArrayList<>();
			}
			codes.add(format(normalised, (int) number, digits));
		}
		return codes;
	}

	/** Adds {@code code} to the name; a code with the same prefix is replaced, other lines' codes are kept. */
	public static String withCode(String name, String code) {
		final String prefix = prefixOf(code);
		if (prefix == null) {
			throw new IllegalArgumentException("Not a station code: " + code);
		}
		final List<String> parts = parts(name);
		parts.removeIf(part -> prefix.equals(prefixOf(part)));
		parts.add(code);
		return String.join("|", parts);
	}

	/** Removes codes with this prefix, or every code when {@code prefix} is null. Never removes the last name part. */
	public static String withoutCodes(String name, String prefix) {
		final List<String> parts = parts(name);
		final List<String> kept = new ArrayList<>();
		for (final String part : parts) {
			final String partPrefix = prefixOf(part);
			if (partPrefix == null || prefix != null && !prefix.equals(partPrefix)) {
				kept.add(part);
			}
		}
		return kept.isEmpty() ? name : String.join("|", kept);
	}

	/** The code parts of a name, in order. */
	public static List<String> codesOf(String name) {
		final List<String> codes = new ArrayList<>();
		parts(name).forEach(part -> {
			if (isCode(part)) {
				codes.add(part.strip());
			}
		});
		return codes;
	}

	private static List<String> parts(String name) {
		final List<String> parts = new ArrayList<>();
		for (final String part : (name == null ? "" : name).split("\\|", -1)) {
			if (!part.isBlank()) {
				parts.add(part);
			}
		}
		return parts;
	}
}
