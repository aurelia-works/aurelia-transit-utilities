package com.aureliatransit.utilities.client;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Tells Joban Client Mod's vehicle scripts (mod id {@code jsblock}) that ATU is installed and whether its Announcer is
 * on. Scripts read it with {@code Resources.getAddonVersion("aurelia_transit_utilities:announcer")} ("on" / "off"),
 * so the AureliaEX {@code onboard.js} can leave the station announcements to ATU and keep only its door warning.
 * JCM only knows addons that register themselves; ATU does not depend on JCM, so the call goes through reflection.
 */
final class ScriptingHandshake {

	static final String ANNOUNCER_KEY = AureliaTransitUtilitiesClient.MOD_ID + ":announcer";
	private static final String JCM_MOD_ID = "jsblock";
	private static final String API_CLASS = "com.lx862.mtrscripting.core.api.MTRScriptingAPI";

	private static boolean failed;

	private ScriptingHandshake() {
	}

	static void publish(boolean announcerOn) {
		if (failed || !FabricLoader.getInstance().isModLoaded(JCM_MOD_ID)) {
			return;
		}
		final String version = FabricLoader.getInstance().getModContainer(AureliaTransitUtilitiesClient.MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
		try {
			final Class<?> api = Class.forName(API_CLASS);
			final Method register = api.getMethod("registerAddonVersion", String.class, String.class);
			register.invoke(null, AureliaTransitUtilitiesClient.MOD_ID, version);
			register.invoke(null, ANNOUNCER_KEY, announcerOn ? "on" : "off");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			failed = true;
			AureliaTransitUtilitiesClient.LOGGER.warn("[ATU] Could not register with Joban Client Mod's scripting; AureliaEX trains may announce twice", e);
		}
	}
}
