package com.aureliatransit.utilities.announce;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Splits an MTR name such as {@code 東京駅|Alpha|AT03} into its English part ("Alpha"), its CJK part ("東京駅") and the
 * clip slug ("alpha"). Same rules as the AureliaEX {@code onboard.js} and {@code make_announcements.py}, so slugs match
 * the recorded {@code next_<slug>} clips.
 */
public record StationName(String english, String cjk, String slug) {

	private static final Pattern CODE = Pattern.compile("[A-Z]{2,4}\\d+");
	private static final Pattern WIP = Pattern.compile("\\[?WIP]?", Pattern.CASE_INSENSITIVE);
	private static final Pattern CJK = Pattern.compile("[\\u3040-\\u30ff\\u3400-\\u9fff]");
	private static final Pattern NOT_SLUG = Pattern.compile("[^a-z0-9]+");

	public static StationName of(String raw) {
		final String name = raw == null ? "" : raw;
		String english = "";
		String cjk = "";
		for (final String part : name.split("\\|")) {
			final String trimmed = part.trim();
			if (trimmed.isEmpty() || CODE.matcher(trimmed).matches() || WIP.matcher(trimmed).matches()) {
				continue;
			}
			if (CJK.matcher(trimmed).find()) {
				if (cjk.isEmpty()) {
					cjk = trimmed;
				}
			} else if (english.isEmpty()) {
				english = trimmed;
			}
		}
		if (english.isEmpty()) {
			english = cjk.isEmpty() ? name : cjk;
		}
		return new StationName(english, cjk, slug(english));
	}

	/** "Olympia High Speed Rail Terminal 1" → "olympia_high_speed_rail_terminal_1". */
	public static String slug(String name) {
		final String slug = NOT_SLUG.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("_");
		int start = 0;
		int end = slug.length();
		while (start < end && slug.charAt(start) == '_') {
			start++;
		}
		while (end > start && slug.charAt(end - 1) == '_') {
			end--;
		}
		return slug.substring(start, end);
	}

	/** MTR's route name rule ({@code VehicleExtension.formatRouteName}): only the part before "||" is spoken. */
	public static String routeName(String raw) {
		return raw == null ? "" : raw.split("\\|\\|")[0];
	}
}
