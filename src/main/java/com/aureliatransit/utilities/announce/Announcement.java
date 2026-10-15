package com.aureliatransit.utilities.announce;

import java.util.List;

/**
 * One announcement: the English lines shown on screen and the clips played in order. A clip is the key after
 * {@code mtr:aureliaex_ann_}, e.g. {@code next_alpha}, {@code terminus} or {@code chime}.
 */
public record Announcement(Kind kind, List<String> lines, List<String> clips) {

	public enum Kind {NEXT, ARRIVING, PLATFORM_APPROACHING, PLATFORM_ARRIVING, PLATFORM_DELAYED}
}
