package com.aureliatransit.utilities.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AureliaTransitUtilitiesClient implements ClientModInitializer {

	public static final String MOD_ID = "aurelia_transit_utilities";
	public static final Logger LOGGER = LoggerFactory.getLogger("Aurelia Transit Utilities");

	@Override
	public void onInitializeClient() {
		LOGGER.info("[ATU] Client initialised");
	}
}
