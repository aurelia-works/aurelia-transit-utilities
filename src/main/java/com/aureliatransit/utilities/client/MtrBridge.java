package com.aureliatransit.utilities.client;

import com.aureliatransit.utilities.overlap.Box;
import com.aureliatransit.utilities.overlap.HeightRange;
import com.aureliatransit.utilities.overlap.OverlapResolver;
import com.aureliatransit.utilities.overlap.Point;
import com.aureliatransit.utilities.overlap.Zones;
import com.aureliatransit.utilities.preset.PresetCar;
import com.aureliatransit.utilities.preset.TrainPreset;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Position;
import org.mtr.core.data.Route;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.operation.DepotOperationByIds;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketDepotGenerate;
import org.mtr.mod.packet.PacketUpdateData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
		return new TrainPreset(name, siding.getTransportMode().name(), cars);
	}

	/** Caller has already run {@code PresetCheck}. */
	public static void applyPreset(TrainPreset preset, Siding siding) {
		final ObjectArrayList<VehicleCar> cars = new ObjectArrayList<>();
		preset.cars().forEach(car -> cars.add(new VehicleCar(car.vehicleId(), car.length(), car.width(), car.bogie1Position(), car.bogie2Position(), car.couplingPadding1(), car.couplingPadding2())));
		siding.setVehicleCars(cars);
		send(request -> request.addSiding(siding));
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

	/** MTR stores alternative-language names separated by '|'. */
	public static String displayName(String name) {
		return name == null ? "" : name.replace('|', ' ');
	}
}
