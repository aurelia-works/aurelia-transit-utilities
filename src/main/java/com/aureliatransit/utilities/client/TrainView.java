package com.aureliatransit.utilities.client;

/**
 * What the train inspector shows about one train, read from MTR's live client data (trains within render distance).
 * {@code delaySeconds} is null when MTR's arrival list has no entry for the train's next stop.
 */
public record TrainView(
		long id,
		boolean riding,
		double distance,
		String route,
		int routeColor,
		String destination,
		String thisStation,
		String nextStation,
		String depot,
		String siding,
		int cars,
		double length,
		long speedKmh,
		long limitKmh,
		boolean manual,
		boolean onRoute,
		boolean doorsOpen,
		Long delaySeconds,
		double x,
		double y,
		double z
) {
}
