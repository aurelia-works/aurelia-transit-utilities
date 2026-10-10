package com.aureliatransit.utilities.client;

import com.aureliatransit.utilities.announce.Announcement;
import com.aureliatransit.utilities.announce.AnnouncerLogic;
import com.aureliatransit.utilities.announce.RideSnapshot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Properties;

/**
 * Station announcements on the MTR train the player rides: "next station" after departure, "arriving" before the
 * platform, "change here for" other routes and the last stop. Clips are {@code mtr:aureliaex_ann_<key>} sound events
 * from a resource pack (the AureliaEX pack ships them); a missing station clip falls back to {@code chime}, and without
 * any clips the announcement is text only. The text goes to chat, in place of MTR's own chat announcement: while the
 * player rides with the Announcer on, MTR's "chat announcements" setting is switched off in memory (MTR only writes its
 * config from its own settings screen) and switched back when the ride ends, the Announcer is turned off or the game closes.
 */
public final class Announcer {

	private static final String CLIP_PREFIX = "aureliaex_ann_";
	/** Five checks a second; when not riding a check is one riding test per loaded vehicle. */
	private static final int CHECK_EVERY_TICKS = 4;
	/** MTR can report "not riding" for a moment (e.g. between cars); the ride is only over after this many checks. */
	private static final int LEFT_AFTER_CHECKS = 10;
	/** A sound reports "not playing" until the sound system has started it. */
	private static final int START_GRACE_TICKS = 10;

	private static final AnnouncerLogic LOGIC = new AnnouncerLogic();
	private static final ArrayDeque<String> CLIPS = new ArrayDeque<>();
	/** The last announcements and clips, for the dev self-test. */
	private static final ArrayDeque<String> HISTORY = new ArrayDeque<>();
	private static boolean enabled = true;
	private static int ticks;
	private static int missedChecks;
	private static SoundInstance playing;
	private static int playingTicks;
	/** MTR's chat announcements were on and the Announcer switched them off for this ride. */
	private static boolean mtrChatMuted;
	private static boolean warnedNoClips;

	private Announcer() {
	}

	public static void register() {
		load();
		ScriptingHandshake.register(enabled);
		ClientTickEvents.END_CLIENT_TICK.register(Announcer::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> restoreMtrChat());
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
		save();
		if (!enabled) {
			restoreMtrChat();
		}
		ScriptingHandshake.publish(enabled);
	}

	public static List<String> historyForTest() {
		return List.copyOf(HISTORY);
	}

	private static void remember(String entry) {
		if (HISTORY.size() >= 30) {
			HISTORY.poll();
		}
		HISTORY.add(entry);
	}

	private static void tick(MinecraftClient client) {
		if (++ticks % CHECK_EVERY_TICKS != 0) {
			return;
		}
		if (!enabled || client.world == null || client.player == null) {
			missedChecks = LEFT_AFTER_CHECKS;
			LOGIC.reset();
			stop(client);
			restoreMtrChat();
			return;
		}
		final RideSnapshot ride = MtrBridge.ridingSnapshot();
		if (ride == null) {
			if (++missedChecks >= LEFT_AFTER_CHECKS) {
				LOGIC.reset();
				stop(client);
				restoreMtrChat();
			} else {
				pump(client);
			}
			return;
		}
		missedChecks = 0;
		muteMtrChat();
		final Announcement announcement = LOGIC.update(ride, Util.getMeasuringTimeMs(), key -> hasClip(client, key));
		if (announcement != null) {
			start(client, announcement);
		}
		pump(client);
	}

	private static void start(MinecraftClient client, Announcement announcement) {
		stop(client);
		CLIPS.addAll(announcement.clips());
		// local chat only (nothing is sent to the server), like MTR's own announcement
		announcement.lines().forEach(line -> client.player.sendMessage(Text.literal(line), false));
		remember(announcement.kind() + " " + announcement.lines() + " " + announcement.clips());
	}

	/** Plays the queued clips one after another. */
	private static void pump(MinecraftClient client) {
		if (playing != null) {
			playingTicks += CHECK_EVERY_TICKS;
			if (playingTicks < START_GRACE_TICKS || client.getSoundManager().isPlaying(playing)) {
				return;
			}
			playing = null;
		}
		while (!CLIPS.isEmpty()) {
			final Identifier id = new Identifier("mtr", CLIP_PREFIX + CLIPS.poll());
			if (client.getSoundManager().get(id) == null) {
				remember("missing " + id);
				if (!warnedNoClips) {
					warnedNoClips = true;
					AureliaTransitUtilitiesClient.LOGGER.info("[ATU] Announcer: sound {} is not in any loaded resource pack; announcements without it are text only", id);
				}
				continue;
			}
			playing = new PositionedSoundInstance(id, SoundCategory.VOICE, 1, 1, SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true);
			playingTicks = 0;
			client.getSoundManager().play(playing);
			remember("play " + id);
			break;
		}
	}

	private static void muteMtrChat() {
		try {
			final Client mtr = Config.getClient();
			if (!mtrChatMuted && mtr.getChatAnnouncements()) {
				mtr.toggleChatAnnouncements();
				mtrChatMuted = true;
			}
		} catch (RuntimeException | LinkageError e) {
			AureliaTransitUtilitiesClient.LOGGER.warn("[ATU] Announcer: could not switch MTR's chat announcements off", e);
		}
	}

	private static void restoreMtrChat() {
		if (!mtrChatMuted) {
			return;
		}
		mtrChatMuted = false;
		try {
			final Client mtr = Config.getClient();
			if (!mtr.getChatAnnouncements()) {
				mtr.toggleChatAnnouncements();
			}
		} catch (RuntimeException | LinkageError e) {
			AureliaTransitUtilitiesClient.LOGGER.warn("[ATU] Announcer: could not switch MTR's chat announcements back on", e);
		}
	}

	private static void stop(MinecraftClient client) {
		CLIPS.clear();
		if (playing != null) {
			client.getSoundManager().stop(playing);
			playing = null;
		}
	}

	private static boolean hasClip(MinecraftClient client, String key) {
		return client.getSoundManager().get(new Identifier("mtr", CLIP_PREFIX + key)) != null;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(AureliaTransitUtilitiesClient.MOD_ID).resolve("announcer.properties");
	}

	private static void load() {
		final Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			final Properties properties = new Properties();
			properties.load(reader);
			enabled = !"false".equals(properties.getProperty("enabled"));
		} catch (IOException e) {
			AureliaTransitUtilitiesClient.LOGGER.error("[ATU] Could not read {}; the Announcer stays on", file, e);
		}
	}

	private static void save() {
		final Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				final Properties properties = new Properties();
				properties.setProperty("enabled", Boolean.toString(enabled));
				properties.store(writer, "Aurelia Transit Utilities announcer");
			}
		} catch (IOException e) {
			AureliaTransitUtilitiesClient.LOGGER.error("[ATU] Could not save {}", file, e);
		}
	}
}
