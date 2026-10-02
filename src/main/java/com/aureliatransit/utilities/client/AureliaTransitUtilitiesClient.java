package com.aureliatransit.utilities.client;

import com.aureliatransit.utilities.client.screen.AtuToolsScreen;
import java.lang.reflect.Method;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.DashboardScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AureliaTransitUtilitiesClient implements ClientModInitializer {

	public static final String MOD_ID = "aurelia_transit_utilities";
	public static final Logger LOGGER = LoggerFactory.getLogger("Aurelia Transit Utilities");

	// 20x20 logo button; the texture holds the normal state on top and the hovered state below it.
	private static final Identifier DASHBOARD_BUTTON_TEXTURE = new Identifier(MOD_ID, "textures/gui/dashboard_button.png");
	private static final int DASHBOARD_BUTTON_SIZE = 20;
	// MAGIC (mod id "jme") puts its own 20x20 menu button 6px inside the map's top-left corner, so ATU's goes under it.
	private static final boolean MAGIC_LOADED = FabricLoader.getInstance().isModLoaded("jme");
	private static final int DASHBOARD_BUTTON_Y = MAGIC_LOADED ? 6 + 20 + 4 : 6;

	@Override
	public void onInitializeClient() {
		// Fabric API screen events, no mixin: adds ATU's logo button to MTR's dashboard. MTR fills its dashboard data
		// when that screen opens, so ATU's tools always start from fresh data. Top-left corner of the map: MTR draws the
		// cursor coordinates top-right and its own buttons along the bottom.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof DashboardScreen) {
				final TexturedButtonWidget button = new TexturedButtonWidget(IGui.PANEL_WIDTH + 6, DASHBOARD_BUTTON_Y, DASHBOARD_BUTTON_SIZE, DASHBOARD_BUTTON_SIZE, 0, 0, DASHBOARD_BUTTON_SIZE, DASHBOARD_BUTTON_TEXTURE, DASHBOARD_BUTTON_SIZE, DASHBOARD_BUTTON_SIZE * 2, pressed -> client.setScreen(new AtuToolsScreen(screen)), Text.translatable("gui.aurelia_transit_utilities.dashboard_button"));
				button.setTooltip(Tooltip.of(Text.translatable("gui.aurelia_transit_utilities.tools.title")));
				Screens.getButtons(screen).add(button);
				// MTR's map widget is registered before ATU's button and takes every click over the map, so the button
				// handles its own clicks before the dashboard sees them. Skipped while MAGIC's menu is open over it.
				ScreenMouseEvents.allowMouseClick(screen).register((clickedScreen, mouseX, mouseY, mouseButton) -> magicMenuOpen(clickedScreen) || !button.mouseClicked(mouseX, mouseY, mouseButton));
			}
		});
		LOGGER.info("[ATU] Client initialised");
	}

	private static Method magicMenuOpenMethod;
	private static boolean magicMenuOpenLookedUp;

	/** MAGIC adds a public {@code jme$isOverlayMenuOpen()} to MTR's dashboard; its dropdown opens where ATU's button is. */
	private static boolean magicMenuOpen(Screen screen) {
		if (!MAGIC_LOADED) {
			return false;
		}
		try {
			if (!magicMenuOpenLookedUp) {
				magicMenuOpenLookedUp = true;
				magicMenuOpenMethod = screen.getClass().getMethod("jme$isOverlayMenuOpen");
			}
			return magicMenuOpenMethod != null && (boolean) magicMenuOpenMethod.invoke(screen);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}
}
