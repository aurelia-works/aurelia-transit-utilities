package com.aureliatransit.utilities.client;

import com.aureliatransit.utilities.preset.PresetCar;
import com.aureliatransit.utilities.preset.TrainPreset;
import org.mtr.core.data.Siding;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Route;
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

	/** MTR stores alternative-language names separated by '|'. */
	public static String displayName(String name) {
		return name == null ? "" : name.replace('|', ' ');
	}
}
