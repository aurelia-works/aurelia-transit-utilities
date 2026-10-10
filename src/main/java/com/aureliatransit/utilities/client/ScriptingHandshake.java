package com.aureliatransit.utilities.client;

import com.aureliatransit.utilities.handshake.Handshake;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Tells Joban Client Mod's vehicle scripts (mod id {@code jsblock}) about ATU: version, protocol, Announcer state and
 * the "melodies" preference (docs/HANDSHAKE.md). Scripts read it with {@code Resources.getAddonVersion(key)}. JCM only
 * knows addons that register themselves; ATU does not depend on JCM, so the call goes through reflection.
 * {@code registerAddonVersion} is a plain {@code HashMap.put}, so republishing overwrites the value and scripts see it live.
 */
public final class ScriptingHandshake {

	private static final String JCM_MOD_ID = "jsblock";
	private static final String API_CLASS = "com.lx862.mtrscripting.core.api.MTRScriptingAPI";

	private static boolean failed;
	private static boolean melodies = true;
	private static boolean announcerOn = true;
	private static final List<Map<String, String>> PUBLISHED = new ArrayList<>();

	private ScriptingHandshake() {
	}

	public static void register(boolean announcer) {
		loadMelodies();
		publish(announcer);
	}

	public static boolean melodiesEnabled() {
		return melodies;
	}

	public static void setMelodies(boolean value) {
		melodies = value;
		saveMelodies();
		publish(announcerOn);
	}

	/** Every map handed to JCM (or that would have been, without JCM), oldest first; for the self-test. */
	public static List<Map<String, String>> publishedForTest() {
		return List.copyOf(PUBLISHED);
	}

	public static void publish(boolean announcer) {
		announcerOn = announcer;
		final String version = FabricLoader.getInstance().getModContainer(AureliaTransitUtilitiesClient.MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
		final Map<String, String> values = Handshake.values(version, announcer, melodies);
		if (PUBLISHED.size() >= 50) {
			PUBLISHED.remove(0);
		}
		PUBLISHED.add(values);
		if (failed || !FabricLoader.getInstance().isModLoaded(JCM_MOD_ID)) {
			return;
		}
		try {
			final Class<?> api = Class.forName(API_CLASS);
			final Method register = api.getMethod("registerAddonVersion", String.class, String.class);
			for (final Map.Entry<String, String> entry : values.entrySet()) {
				register.invoke(null, entry.getKey(), entry.getValue());
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			failed = true;
			AureliaTransitUtilitiesClient.LOGGER.warn("[ATU] Could not register with Joban Client Mod's scripting; vehicle scripts will not see ATU settings", e);
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(AureliaTransitUtilitiesClient.MOD_ID).resolve("handshake.properties");
	}

	private static void loadMelodies() {
		final Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			final Properties properties = new Properties();
			properties.load(reader);
			melodies = Handshake.parseMelodies(properties);
		} catch (IOException e) {
			AureliaTransitUtilitiesClient.LOGGER.error("[ATU] Could not read {}; melodies stay on", file, e);
		}
	}

	private static void saveMelodies() {
		final Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				Handshake.toProperties(melodies).store(writer, "Aurelia Transit Utilities, settings published to vehicle scripts");
			}
		} catch (IOException e) {
			AureliaTransitUtilitiesClient.LOGGER.error("[ATU] Could not save {}", file, e);
		}
	}
}
