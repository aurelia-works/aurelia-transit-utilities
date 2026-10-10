package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.Announcer;
import com.aureliatransit.utilities.client.ScriptingHandshake;
import com.aureliatransit.utilities.handshake.Handshake;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Handshake v2. The dev client has no JCM, so nothing reaches JCM's addon map here: these checks cover the published
 * map (recorded by ScriptingHandshake), persistence and republish on toggle. Delivery into JCM is only provable in Noriega.
 */
final class HandshakeSelfTest {

	private static boolean melodiesBefore;
	private static boolean announcerBefore;
	private static int published;

	private HandshakeSelfTest() {
	}

	private static Map<String, String> last() {
		final var all = ScriptingHandshake.publishedForTest();
		return all.get(all.size() - 1);
	}

	static void addSteps(AtuSelfTest test) {
		test.onFinish(() -> {
			ScriptingHandshake.setMelodies(melodiesBefore);
			Announcer.setEnabled(announcerBefore);
		});
		test.act("handshake: published map has every key", () -> {
			melodiesBefore = ScriptingHandshake.melodiesEnabled();
			announcerBefore = Announcer.enabled();
			test.check("handshake: published at startup", !ScriptingHandshake.publishedForTest().isEmpty(), "");
			final Map<String, String> map = last();
			final String version = FabricLoader.getInstance().getModContainer("aurelia_transit_utilities").get().getMetadata().getVersion().getFriendlyString();
			test.check("handshake: version and protocol keys", version.equals(map.get(Handshake.ID)) && "2".equals(map.get(Handshake.KEY_HANDSHAKE)), map.toString());
			test.check("handshake: announcer, language and caps keys", Handshake.onOff(Announcer.enabled()).equals(map.get(Handshake.KEY_ANNOUNCER))
					&& "en".equals(map.get(Handshake.KEY_ANNOUNCER_LANG)) && "announcer,announcer_lang,melodies".equals(map.get(Handshake.KEY_CAPS)), map.toString());
			test.check("handshake: melodies key matches the setting", Handshake.onOff(melodiesBefore).equals(map.get(Handshake.KEY_MELODIES)), "");
		});
		test.act("handshake: melodies off republishes and persists", () -> {
			published = ScriptingHandshake.publishedForTest().size();
			ScriptingHandshake.setMelodies(false);
			test.check("handshake: melodies off republished", ScriptingHandshake.publishedForTest().size() == published + 1 && "off".equals(last().get(Handshake.KEY_MELODIES)), "");
			test.check("handshake: melodies off written to handshake.properties", fileText().contains("melodies=off"), fileText());
		});
		test.act("handshake: melodies on republishes and persists", () -> {
			published = ScriptingHandshake.publishedForTest().size();
			ScriptingHandshake.setMelodies(true);
			test.check("handshake: melodies on republished", ScriptingHandshake.publishedForTest().size() == published + 1 && "on".equals(last().get(Handshake.KEY_MELODIES)), "");
			test.check("handshake: melodies on written to handshake.properties", fileText().contains("melodies=on"), fileText());
		});
		test.act("handshake: announcer toggle republishes both keys", () -> {
			published = ScriptingHandshake.publishedForTest().size();
			Announcer.setEnabled(false);
			test.check("handshake: announcer off republished with melodies kept", ScriptingHandshake.publishedForTest().size() == published + 1
					&& "off".equals(last().get(Handshake.KEY_ANNOUNCER)) && "on".equals(last().get(Handshake.KEY_MELODIES)), last().toString());
			Announcer.setEnabled(announcerBefore);
			test.check("handshake: announcer restored republished", Handshake.onOff(announcerBefore).equals(last().get(Handshake.KEY_ANNOUNCER)), "");
		});
	}

	private static String fileText() {
		try {
			final Path file = FabricLoader.getInstance().getConfigDir().resolve("aurelia_transit_utilities").resolve("handshake.properties");
			return Files.exists(file) ? Files.readString(file) : "";
		} catch (java.io.IOException e) {
			return "";
		}
	}
}
