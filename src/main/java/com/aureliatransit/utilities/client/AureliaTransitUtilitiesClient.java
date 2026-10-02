package com.aureliatransit.utilities.client;

import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.DashboardScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AureliaTransitUtilitiesClient implements ClientModInitializer {

	public static final String MOD_ID = "aurelia_transit_utilities";
	public static final Logger LOGGER = LoggerFactory.getLogger("Aurelia Transit Utilities");

	private static final int DASHBOARD_BUTTON_WIDTH = 40;

	@Override
	public void onInitializeClient() {
		// Fabric API screen event, no mixin: adds an "ATU" button to MTR's dashboard. MTR fills its dashboard data
		// when that screen opens, so ATU's tools always start from fresh data. Top-left corner of the map: MTR draws the
		// cursor coordinates top-right and its own buttons along the bottom.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof DashboardScreen) {
				Screens.getButtons(screen).add(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.dashboard_button"), button -> client.setScreen(new AtuToolsScreen(screen)))
						.dimensions(IGui.PANEL_WIDTH + 4, 4, DASHBOARD_BUTTON_WIDTH, 20)
						.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("gui.aurelia_transit_utilities.tools.title")))
						.build());
			}
		});
		LOGGER.info("[ATU] Client initialised");
	}
}
