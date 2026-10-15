package com.aureliatransit.utilities.client;

import com.aureliatransit.utilities.announce.PlatformCalls;
import com.aureliatransit.utilities.announce.RideSnapshot;
import com.aureliatransit.utilities.depot.DepotHealth;
import com.aureliatransit.utilities.overlap.Box;
import com.aureliatransit.utilities.overlap.HeightRange;
import com.aureliatransit.utilities.overlap.OverlapResolver;
import com.aureliatransit.utilities.overlap.Point;
import com.aureliatransit.utilities.overlap.Zones;
import com.aureliatransit.utilities.preset.PresetCar;
import com.aureliatransit.utilities.preset.SidingSettings;
import com.aureliatransit.utilities.preset.TrainPreset;
import com.aureliatransit.utilities.route.ReturnRoute;
import com.aureliatransit.utilities.speed.SpeedAudit;
import net.minecraft.text.Text;
import com.aureliatransit.utilities.timetable.Timetable;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.data.SimplifiedRoute;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.core.operation.DepotOperationByIds;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.data.ArrivalsCacheClient;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.packet.PacketDepotGenerate;
import org.mtr.mod.packet.PacketUpdateData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;

/**
 * The only place ATU touches MTR. Public MTR API only: reads the dashboard copy of the data (filled by MTR when its
 * dashboard opens) and writes with the same packet MTR's own screens send.
 */
public final class MtrBridge {

	private MtrBridge() {
	}

	/** MTR's own client-side edit gate. ATU never offers an edit MTR's own screens would refuse. */
	public static boolean canEdit() {
		return MinecraftClientData.hasPermission();
	}

	public static MinecraftClientData dashboardData() {
		return MinecraftClientData.getDashboardInstance();
	}

	public static void send(Consumer<UpdateDataRequest> fill) {
		final UpdateDataRequest request = new UpdateDataRequest(dashboardData());
		fill.accept(request);
		InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));
	}

	public static Set<String> knownVehicleIds(TransportMode transportMode) {
		final Set<String> ids = new HashSet<>();
		CustomResourceLoader.iterateVehicles(transportMode, vehicleResource -> ids.add(vehicleResource.getId()));
		return ids;
	}

	public static TrainPreset toPreset(String name, Siding siding) {
		final List<PresetCar> cars = new ArrayList<>();
		siding.getVehicleCars().forEach(car -> {
			// VehicleCar exposes its coupling paddings only through getTotalLength(isFirst, isLast).
			final double couplingPadding1 = car.getTotalLength(false, true) - car.getLength();
			final double couplingPadding2 = car.getTotalLength(true, false) - car.getLength();
			cars.add(new PresetCar(car.getVehicleId(), car.getLength(), car.getWidth(), car.getBogie1Position(), car.getBogie2Position(), couplingPadding1, couplingPadding2));
		});
		return new TrainPreset(name, siding.getTransportMode().name(), cars, settingsOf(siding));
	}

	public static SidingSettings settingsOf(Siding siding) {
		return new SidingSettings(siding.getAcceleration(), siding.getDeceleration(), siding.getIsUnlimited() ? 0 : (int) siding.getMaxVehicles(), siding.getIsManual(),
				siding.getMaxManualSpeed(), siding.getManualToAutomaticTime(), siding.getDelayedVehicleSpeedIncreasePercentage(), siding.getDelayedVehicleReduceDwellTimePercentage(),
				siding.getEarlyVehicleIncreaseDwellTime());
	}

	/** Caller has already run {@code PresetCheck}. Settings are applied too when the preset has them. */
	public static void applyPreset(TrainPreset preset, Siding siding) {
		applyPreset(preset, List.of(siding));
	}

	/** One update for several sidings (e.g. a whole depot). Caller has already run {@code PresetCheck} on each. */
	public static void applyPreset(TrainPreset preset, List<Siding> sidings) {
		sidings.forEach(siding -> {
			final ObjectArrayList<VehicleCar> cars = new ObjectArrayList<>();
			preset.cars().forEach(car -> cars.add(new VehicleCar(car.vehicleId(), car.length(), car.width(), car.bogie1Position(), car.bogie2Position(), car.couplingPadding1(), car.couplingPadding2())));
			siding.setVehicleCars(cars);
			if (preset.settings() != null) {
				applySettings(preset.settings(), siding);
			}
		});
		send(request -> sidings.forEach(request::addSiding));
	}

	/** Same order of setters as MTR's {@code SidingScreen.onClose2}. */
	private static void applySettings(SidingSettings settings, Siding siding) {
		if (settings.manual()) {
			siding.setIsManual(true);
		} else if (settings.unlimited()) {
			siding.setUnlimitedVehicles(true);
		} else {
			siding.setMaxVehicles(settings.maxVehicles());
		}
		siding.setAcceleration(settings.acceleration());
		siding.setDeceleration(settings.deceleration());
		siding.setDelayedVehicleSpeedIncreasePercentage(settings.delayedSpeedIncreasePercentage());
		siding.setDelayedVehicleReduceDwellTimePercentage(settings.delayedReduceDwellTimePercentage());
		siding.setEarlyVehicleIncreaseDwellTime(settings.earlyIncreaseDwellTime());
		siding.setMaxManualSpeed(settings.maxManualSpeed());
		siding.setManualToAutomaticTime(settings.manualToAutomaticTime());
	}

	/** The return-route plan for a route, or null when one of its stops is a deleted platform. */
	public static List<ReturnRoute.Choice> returnRoutePlan(Route route) {
		final List<ReturnRoute.Stop> stops = new ArrayList<>();
		for (final RoutePlatformData data : route.getRoutePlatforms()) {
			if (data.platform == null) {
				return null;
			}
			stops.add(stop(data.platform));
		}
		final Map<Long, List<ReturnRoute.Stop>> byStation = new HashMap<>();
		dashboardData().platforms.forEach(platform -> {
			if (platform.area != null && platform.isTransportMode(route.getTransportMode())) {
				byStation.computeIfAbsent(platform.area.getId(), id -> new ArrayList<>()).add(stop(platform));
			}
		});
		return ReturnRoute.plan(stops, byStation, route.getCircularState() != Route.CircularState.NONE);
	}

	private static ReturnRoute.Stop stop(Platform platform) {
		final Position mid = platform.getMidPosition();
		return new ReturnRoute.Stop(platform.getId(), platform.area == null ? 0 : platform.area.getId(), mid.getX(), mid.getZ());
	}

	/**
	 * Creates the return route: same colour, number, type and visibility, the given platforms in order, the opposite
	 * circular direction. With {@code addToDepots} it also joins every depot of the original route (they need
	 * regenerating before trains run it, like any depot edit in MTR). Caller checked {@link #canEdit()}.
	 */
	public static Route createReturnRoute(Route original, String name, List<Long> platformIds, boolean addToDepots) {
		final Route route = new Route(original.getTransportMode(), dashboardData());
		route.setName(name);
		route.setColor(original.getColor());
		route.setRouteNumber(original.getRouteNumber());
		route.setRouteType(original.getRouteType());
		route.setHidden(original.getHidden());
		route.setCircularState(switch (original.getCircularState()) {
			case CLOCKWISE -> Route.CircularState.ANTICLOCKWISE;
			case ANTICLOCKWISE -> Route.CircularState.CLOCKWISE;
			default -> Route.CircularState.NONE;
		});
		platformIds.forEach(id -> route.getRoutePlatforms().add(new RoutePlatformData(id)));
		final List<Depot> depots = addToDepots ? new ArrayList<>(original.depots) : List.of();
		depots.forEach(depot -> depot.getRouteIds().add(route.getId()));
		send(request -> {
			request.addRoute(route);
			depots.forEach(request::addDepot);
		});
		return route;
	}

	/** Same request as the Generate button in MTR's depot screen. Only ever sent on an explicit click. */
	public static int regenerateDepots(Route route) {
		return regenerateDepots(route.depots);
	}

	public static int regenerateDepots(Collection<Depot> depots) {
		final DepotOperationByIds operation = new DepotOperationByIds();
		depots.forEach(depot -> operation.addDepotId(depot.getId()));
		if (!depots.isEmpty()) {
			InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketDepotGenerate(operation));
		}
		return depots.size();
	}

	/**
	 * Station zones and platforms of one transport mode, in MTR's station order. Stations without valid corners are
	 * left out, as MTR's {@code AreaBase.inArea} never matches them.
	 */
	public static Zones zones(TransportMode transportMode) {
		return zones(dashboardData().stations, dashboardData().platforms, transportMode);
	}

	/** Depot zones and their sidings, same rule as stations and platforms. */
	public static Zones depotZones(TransportMode transportMode) {
		return zones(dashboardData().depots, dashboardData().sidings, transportMode);
	}

	private static Zones zones(Collection<? extends AreaBase<?, ?>> areas, Collection<? extends SavedRailBase<?, ?>> savedRails, TransportMode transportMode) {
		final List<Zones.Station> stations = new ArrayList<>();
		areas.forEach(area -> {
			if (area.isTransportMode(transportMode) && validCorners(area)) {
				stations.add(new Zones.Station(area.getId(), box(area)));
			}
		});
		final List<Zones.Platform> platforms = new ArrayList<>();
		savedRails.forEach(savedRail -> {
			if (savedRail.isTransportMode(transportMode)) {
				final Position mid = savedRail.getMidPosition();
				platforms.add(new Zones.Platform(savedRail.getId(), new Point(mid.getX(), mid.getY(), mid.getZ())));
			}
		});
		return new Zones(stations, platforms);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static boolean validCorners(AreaBase<?, ?> area) {
		return AreaBase.validCorners((AreaBase) area);
	}

	public static Box box(AreaBase<?, ?> area) {
		return new Box(area.getMinX(), area.getMinY(), area.getMinZ(), area.getMaxX(), area.getMaxY(), area.getMaxZ());
	}

	/** Sets a station's or depot's height range and sends it. Caller checked {@link #canEdit()}. */
	public static void applyHeight(AreaBase<?, ?> area, HeightRange range) {
		final Box box = range.applyTo(box(area));
		area.setCorners(new Position(box.minX(), box.minY(), box.minZ()), new Position(box.maxX(), box.maxY(), box.maxZ()));
		send(request -> {
			if (area instanceof Station station) {
				request.addStation(station);
			} else if (area instanceof Depot depot) {
				request.addDepot(depot);
			}
		});
	}

	/** Sends the planned zone cuts. Caller has shown the plan and checked {@link #canEdit()}. */
	public static void applyCuts(List<OverlapResolver.Cut> cuts) {
		final List<Station> changed = new ArrayList<>();
		for (final OverlapResolver.Cut cut : cuts) {
			final Station station = dashboardData().stationIdMap.get(cut.stationId());
			if (station != null) {
				final Box box = cut.after();
				station.setCorners(new Position(box.minX(), box.minY(), box.minZ()), new Position(box.maxX(), box.maxY(), box.maxZ()));
				changed.add(station);
			}
		}
		send(request -> changed.forEach(request::addStation));
	}

	/** Reads one depot's generation result and setup for {@link DepotHealth}. */
	public static DepotHealth.DepotInfo depotInfo(Depot depot) {
		final List<String> tooShort = new ArrayList<>();
		final List<String> deletedPlatforms = new ArrayList<>();
		depot.routes.forEach(route -> {
			if (route.getRoutePlatforms().size() < 2) {
				tooShort.add(displayName(route.getName()));
			}
			if (route.getRoutePlatforms().stream().anyMatch(data -> data.platform == null)) {
				deletedPlatforms.add(displayName(route.getName()));
			}
		});
		final long[] failed = new long[3];
		depot.getFailedPlatformIds((start, end) -> {
			failed[0] = start;
			failed[1] = end;
		}, count -> failed[2] = count);
		final int sidingsWithoutTrain = (int) depot.savedRails.stream().filter(siding -> siding.getVehicleCars().isEmpty()).count();
		final Depot.GeneratedStatus status = depot.getLastGeneratedStatus();
		final List<String> withoutStation = new ArrayList<>();
		depot.routes.forEach(route -> route.getRoutePlatforms().forEach(data -> {
			if (data.platform != null && data.platform.area == null) {
				final String label = Text.translatable("gui.aurelia_transit_utilities.depots.platform_on_route", data.platform.getName(), displayName(route.getName())).getString();
				if (!withoutStation.contains(label)) {
					withoutStation.add(label);
				}
			}
		}));
		final List<String> isolated = new ArrayList<>();
		depot.savedRails.forEach(siding -> {
			if (sidingTrackLinked(siding) == Boolean.FALSE) {
				isolated.add(siding.getName());
			}
		});
		return new DepotHealth.DepotInfo(depot.getId(), displayName(depot.getName()), DepotHealth.status(status == null ? null : status.name()), failed[0], failed[1], failed[2], depot.routes.size(), depot.savedRails.size(), sidingsWithoutTrain, tooShort, deletedPlatforms,
				hasNoDepartures(depot), withoutStation, isolated);
	}

	/** MTR builds no departures from an empty timed list, or from trains-per-hour all at zero. */
	public static boolean hasNoDepartures(Depot depot) {
		if (depot.getTransportMode().continuousMovement) {
			return false;
		}
		if (depot.getUseRealTime()) {
			return depot.getRealTimeDepartures().isEmpty();
		}
		for (int hour = 0; hour < 24; hour++) {
			if (depot.getFrequency(hour) > 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether any other rail meets the siding's rail at either end, from the rails MTR has sent to this client (those
	 * within render distance). Null when the siding's rail is not loaded here, so nothing can be said.
	 */
	public static Boolean sidingTrackLinked(Siding siding) {
		final MinecraftClientData live = MinecraftClientData.getInstance();
		final Position end1 = siding.getRandomPosition();
		final Position end2 = siding.getOtherPosition(end1);
		final Map<Position, Rail> from1 = live.positionsToRail.get(end1);
		final Map<Position, Rail> from2 = live.positionsToRail.get(end2);
		if (from1 == null || from2 == null) {
			return null;
		}
		return from1.keySet().stream().anyMatch(position -> !position.equals(end2)) || from2.keySet().stream().anyMatch(position -> !position.equals(end1));
	}

	/** Every loaded rail (within render distance), for {@link SpeedAudit}. Airplane runways are left out. */
	public static List<SpeedAudit.Rail> loadedRails() {
		final MinecraftClientData live = MinecraftClientData.getInstance();
		final List<SpeedAudit.Rail> rails = new ArrayList<>();
		final Set<String> seen = new HashSet<>();
		live.positionsToRail.forEach((start, ends) -> ends.forEach((end, rail) -> {
			if (rail.getTransportMode() == TransportMode.AIRPLANE || !seen.add(rail.getHexId())) {
				return;
			}
			final var radii = rail.railMath.getHorizontalRadii();
			final double radius = radii.leftDouble() > 0 && radii.rightDouble() > 0 ? Math.min(radii.leftDouble(), radii.rightDouble()) : Math.max(radii.leftDouble(), radii.rightDouble());
			final Vector middle = rail.railMath.getPosition(rail.railMath.getLength() / 2, false);
			rails.add(new SpeedAudit.Rail(rail.getHexId(), nodeKey(start), nodeKey(end), kilometresPerHour(rail.getSpeedLimitMetersPerMillisecond(start)), kilometresPerHour(rail.getSpeedLimitMetersPerMillisecond(end)),
					radius, rail.isPlatform() || rail.isSiding() || rail.canTurnBack(), rail.getTransportMode() == TransportMode.TRAIN, middle.x, middle.y, middle.z));
		}));
		return rails;
	}

	private static String nodeKey(Position position) {
		return position.getX() + "," + position.getY() + "," + position.getZ();
	}

	/** MTR speeds are blocks per millisecond. */
	private static long kilometresPerHour(double metresPerMillisecond) {
		return Math.round(metresPerMillisecond * 3600);
	}

	/** "Platform 2 (Central)" for messages; works for deleted platforms too. */
	public static String platformLabel(long platformId) {
		final Platform platform = dashboardData().platformIdMap.get(platformId);
		if (platform == null) {
			return "#" + Long.toHexString(platformId);
		}
		return platform.getName() + (platform.area == null ? "" : " (" + displayName(platform.area.getName()) + ")");
	}

	/**
	 * MTR stores timed departures as UTC milliseconds of the day; its depot screen parses and shows them in the local
	 * time zone ({@code EditDepotScreen.checkDeparture} / {@code updateList}). ATU works in local time like MTR's screen.
	 */
	private static long zoneOffset() {
		return TimeZone.getDefault().getOffset(System.currentTimeMillis());
	}

	public static List<Long> localDepartures(Depot depot) {
		final List<Long> local = new ArrayList<>();
		final long offset = zoneOffset();
		depot.getRealTimeDepartures().forEach(stored -> local.add(Math.floorMod(stored + offset, Timetable.DAY)));
		return Timetable.normalise(local);
	}

	/** Replaces the depot's timed departures (and switches it to timed mode) and sends it. Caller checked canEdit. */
	public static void saveDepartures(Depot depot, List<Long> localTimes) {
		final long offset = zoneOffset();
		final var stored = depot.getRealTimeDepartures();
		stored.clear();
		Timetable.normalise(localTimes).forEach(local -> stored.add(Math.floorMod(local - offset, Timetable.DAY)));
		depot.setUseRealTime(true);
		send(request -> request.addDepot(depot));
	}

	/** Sets MTR's per-hour frequency values (0..20 quarter trains per hour) and switches to that mode. */
	public static void saveFrequencies(Depot depot, int[] frequencies) {
		for (int hour = 0; hour < 24; hour++) {
			depot.setFrequency(hour, Math.max(0, Math.min(Timetable.MAX_FREQUENCY, frequencies[hour])));
		}
		depot.setUseRealTime(false);
		send(request -> request.addDepot(depot));
	}

	/**
	 * The vehicle the player rides, from MTR's live client data (not the dashboard copy), or null when not riding.
	 * Rail progress and dwell time are not public in MTR, so the distance to the next stop is measured from the train's
	 * head to the middle of the next platform, and only when MTR has sent that platform to the client.
	 */
	public static RideSnapshot ridingSnapshot() {
		final MinecraftClientData data = MinecraftClientData.getInstance();
		for (final VehicleExtension vehicle : data.vehicles) {
			if (VehicleRidingMovement.isRiding(vehicle.getId())) {
				return snapshot(data, vehicle);
			}
		}
		return null;
	}

	private static RideSnapshot snapshot(MinecraftClientData data, VehicleExtension vehicle) {
		final VehicleExtraData extra = vehicle.vehicleExtraData;
		final List<RideSnapshot.Interchange> interchanges = new ArrayList<>();
		extra.iterateInterchanges((stationName, colors) -> colors.forEach((color, routeNames) -> routeNames.forEach(routeName -> interchanges.add(new RideSnapshot.Interchange(stationName, color, routeName)))));
		double distance = Double.NaN;
		final Platform nextPlatform = data.platformIdMap.get(extra.getNextPlatformId());
		final Vector head = vehicle.getHeadPosition();
		if (nextPlatform != null && head != null) {
			final Position mid = nextPlatform.getMidPosition();
			distance = head.distanceTo(new Vector(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5));
		}
		// MTR speeds are blocks per millisecond.
		return new RideSnapshot(vehicle.getId(), extra.getThisPlatformId(), extra.getNextPlatformId(), extra.getNextStationName(), extra.getIsTerminating() || nextIsLastStop(data, extra),
				extra.getThisRouteName(), extra.getThisRouteColor(), extra.getNextRouteName(), extra.getNextRouteColor(), extra.getNextRouteDestination(),
				interchanges, vehicle.getSpeed() * 1000, vehicle.persistentVehicleData.getDoorValue(), distance,
				data.getRouteIdHasDisabledAnnouncements(extra.getThisRouteId()));
	}

	/**
	 * The next platform is the last one of the route the train is on. MTR's own {@code isTerminating} reached the client
	 * as false on every leg in the self-test, so this reads the route's platform list instead. Circular routes never end.
	 */
	private static boolean nextIsLastStop(MinecraftClientData data, VehicleExtraData extra) {
		final SimplifiedRoute route = data.simplifiedRouteIdMap.get(extra.getThisRouteId());
		if (route == null || route.getCircularState() != Route.CircularState.NONE || route.getPlatforms().isEmpty()) {
			return false;
		}
		return route.getPlatforms().get(route.getPlatforms().size() - 1).getPlatformId() == extra.getNextPlatformId();
	}

	/**
	 * Every train MTR has sent to this client, the ridden one first, then nearest first. Delays come from MTR's shared
	 * arrival cache (the one its departure screens use); asking for them adds the trains' next platforms to MTR's next
	 * arrival request, at most every 3 seconds.
	 */
	public static List<TrainView> loadedTrains(double playerX, double playerY, double playerZ) {
		final MinecraftClientData live = MinecraftClientData.getInstance();
		final LongArrayList nextPlatforms = new LongArrayList();
		live.vehicles.forEach(vehicle -> {
			final long next = vehicle.vehicleExtraData.getNextPlatformId();
			if (next != 0 && !nextPlatforms.contains(next)) {
				nextPlatforms.add(next);
			}
		});
		final List<ArrivalResponse> arrivals = nextPlatforms.isEmpty() ? List.of() : ArrivalsCacheClient.INSTANCE.requestArrivals(nextPlatforms);
		final List<TrainView> trains = new ArrayList<>();
		for (final VehicleExtension vehicle : live.vehicles) {
			final VehicleExtraData extra = vehicle.vehicleExtraData;
			final Vector head = vehicle.getHeadPosition();
			final double x = head == null ? playerX : head.x;
			final double y = head == null ? playerY : head.y;
			final double z = head == null ? playerZ : head.z;
			Long delay = null;
			for (final ArrivalResponse arrival : arrivals) {
				if (arrival.getPlatformId() == extra.getNextPlatformId() && arrival.getRouteId() == extra.getThisRouteId() && arrival.getDepartureIndex() == vehicle.getDepartureIndex()) {
					delay = arrival.getDeviation() / 1000;
					break;
				}
			}
			final Depot depot = dashboardData().depotIdMap.get(extra.getDepotId());
			final Siding siding = dashboardData().sidingIdMap.get(extra.getSidingId());
			trains.add(new TrainView(vehicle.getId(), VehicleRidingMovement.isRiding(vehicle.getId()), Math.sqrt((x - playerX) * (x - playerX) + (y - playerY) * (y - playerY) + (z - playerZ) * (z - playerZ)),
					extra.getThisRouteName(), extra.getThisRouteColor(), extra.getThisRouteDestination(), extra.getThisStationName(), extra.getNextStationName(),
					depot == null ? "" : depot.getName(), siding == null ? "" : siding.getName(), extra.immutableVehicleCars.size(), extra.getTotalVehicleLength(),
					kilometresPerHour(vehicle.getSpeed()), vehicle.getSpeedLimitKilometersPerHour(), extra.getIsCurrentlyManual(), vehicle.getIsOnRoute(),
					vehicle.persistentVehicleData.getDoorValue() > 0, delay, x, y, z));
		}
		trains.sort(Comparator.comparing((TrainView train) -> !train.riding()).thenComparingDouble(TrainView::distance));
		return trains;
	}

	/** The live platform nearest to the player's feet within {@code radius} blocks, or null. */
	public static Platform platformNear(double x, double y, double z, double radius) {
		final MinecraftClientData live = MinecraftClientData.getInstance();
		final Position position = new Position((long) Math.floor(x), (long) Math.floor(y), (long) Math.floor(z));
		Platform best = null;
		double bestDistance = Double.MAX_VALUE;
		for (final Platform platform : live.platforms) {
			if (platform.closeTo(position, radius)) {
				final double distance = platform.getApproximateClosestDistance(position, live);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = platform;
				}
			}
		}
		return best;
	}

	/**
	 * Upcoming trains at one platform from MTR's shared arrival cache, with times moved onto this computer's clock
	 * ({@code System.currentTimeMillis()}), the way MTR's PIDS do it.
	 */
	public static List<PlatformCalls.Arrival> platformArrivals(Platform platform) {
		final long offset = ArrivalsCacheClient.INSTANCE.getMillisOffset();
		final List<PlatformCalls.Arrival> arrivals = new ArrayList<>();
		for (final ArrivalResponse arrival : ArrivalsCacheClient.INSTANCE.requestArrivals(LongArrayList.of(platform.getId()))) {
			final String destination = arrival.getDestination() == null || arrival.getDestination().isBlank() ? "" : arrival.getDestination();
			arrivals.add(new PlatformCalls.Arrival(arrival.getRouteId() + ":" + arrival.getDepartureIndex() + ":" + arrival.getPlatformId(), arrival.getPlatformName(), arrival.getRouteName(), destination,
					arrival.getArrival() - offset, arrival.getDeparture() - offset, arrival.getDeviation(), arrival.getIsTerminating(), arrival.getCarCount()));
		}
		return arrivals;
	}

	/** MTR stores alternative-language names separated by '|'. */
	public static String displayName(String name) {
		return name == null ? "" : name.replace('|', ' ');
	}
}
