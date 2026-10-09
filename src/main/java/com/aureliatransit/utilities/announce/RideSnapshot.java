package com.aureliatransit.utilities.announce;

import java.util.List;

/**
 * What the Announcer needs from the vehicle the player rides, copied out of MTR's client data by {@code MtrBridge}.
 * MTR's naming: {@code route} is the route of the next stop, {@code nextRoute} the route the train runs as after it.
 *
 * @param thisPlatformId      the platform the train last entered; MTR moves it on when the head enters a platform
 * @param nextPlatformId      the platform after that (MTR does not send its stop index to the client, these it does)
 * @param nextStation         raw MTR name of the next stop, empty when there is none
 * @param terminating         the next stop is the last one of {@code route}
 * @param interchanges        every route MTR lists at the next station and its connected stations
 * @param distanceToNextStop  blocks from the train's head to the next platform's middle, NaN when MTR has not sent it
 * @param announcementsDisabled MTR's per-route "no announcements" flag, as far as the client has learnt it
 */
public record RideSnapshot(long vehicleId, long thisPlatformId, long nextPlatformId, String nextStation, boolean terminating,
						   String route, int routeColor, String nextRoute, int nextRouteColor, String nextRouteDestination,
						   List<Interchange> interchanges, double speedMetresPerSecond, double doorValue, double distanceToNextStop,
						   boolean announcementsDisabled) {

	public record Interchange(String stationName, int color, String routeName) {
	}
}
