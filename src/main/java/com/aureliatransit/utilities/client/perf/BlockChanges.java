package com.aureliatransit.utilities.client.perf;

/**
 * A number that changes whenever any block the client can see may have changed: a block set in a loaded chunk, a chunk
 * loaded, unloaded or moved out of reach. Caches of block lookups compare it to know they are still exact.
 */
public final class BlockChanges {

	private static volatile long version;

	private BlockChanges() {
	}

	public static void changed() {
		version++;
	}

	public static long version() {
		return version;
	}
}
