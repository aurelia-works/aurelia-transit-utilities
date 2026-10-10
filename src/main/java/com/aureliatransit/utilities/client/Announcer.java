package com.aureliatransit.utilities.client;

import com.aureliatransit.utilities.announce.Announcement;
import com.aureliatransit.utilities.announce.AnnouncerLogic;
import com.aureliatransit.utilities.announce.RideSnapshot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Station announcements on the MTR train the player rides: "next station" after departure, "arriving" before the
 * platform, "change here for" other routes and the last stop. Clips are {@code mtr:aureliaex_ann_<key>} sound events
 * from a resource pack (the AureliaEX pack ships them); a missing station clip falls back to {@code chime}, and without
 * any clips the announcement is text only. The text is drawn as a caption above the action bar: MTR rewrites the action
 * bar every tick while the player rides, and may already print its own announcement to chat.
 */
public final class Announcer {

	private static final String CLIP_PREFIX = "aureliaex_ann_";
	/** Five checks a second; when not riding a check is one riding test per loaded vehicle. */
	private static final int CHECK_EVERY_TICKS = 4;
	/** MTR can report "not riding" for a moment (e.g. between cars); the ride is only over after this many checks. */
	private static final int LEFT_AFTER_CHECKS = 10;
	/** A sound reports "not playing" until the sound system has started it. */
	private static final int START_GRACE_TICKS = 10;
	private static final int TEXT_MIN_TICKS = 100;

	private static final AnnouncerLogic LOGIC = new AnnouncerLogic();
	private static final ArrayDeque<String> CLIPS = new ArrayDeque<>();
	/** The last announcements and clips, for the dev self-test. */
	private static final ArrayDeque<String> HISTORY = new ArrayDeque<>();
	private static boolean enabled = true;
	private static int ticks;
	private static int missedChecks;
	private static SoundInstance playing;
	private static int playingTicks;
	private static List<String> text;
	private static int textTicks;
	private static boolean warnedNoClips;

	private Announcer() {
	}

	public static void register() {
		load();
		ScriptingHandshake.publish(enabled);
		ClientTickEvents.END_CLIENT_TICK.register(Announcer::tick);
		HudRenderCallback.EVENT.register(Announcer::render);
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
		save();
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
			return;
		}
		final RideSnapshot ride = MtrBridge.ridingSnapshot();
		if (ride == null) {
			if (++missedChecks >= LEFT_AFTER_CHECKS) {
				LOGIC.reset();
				stop(client);
			} else {
				pump(client);
			}
			return;
		}
		missedChecks = 0;
		final Announcement announcement = LOGIC.update(ride, Util.getMeasuringTimeMs(), key -> hasClip(client, key));
		if (announcement != null) {
			start(client, announcement);
		}
		pump(client);
	}

	private static void start(MinecraftClient client, Announcement announcement) {
		stop(client);
		CLIPS.addAll(announcement.clips());
		text = announcement.lines();
		textTicks = 0;
		remember(announcement.kind() + " " + announcement.lines() + " " + announcement.clips());
	}

	/** Plays the queued clips one after another and keeps the text on screen while they play. */
	private static void pump(MinecraftClient client) {
		if (playing != null) {
			playingTicks += CHECK_EVERY_TICKS;
			if (playingTicks < START_GRACE_TICKS || client.getSoundManager().isPlaying(playing)) {
				holdText();
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
		if (playing != null || textTicks < TEXT_MIN_TICKS) {
			holdText();
		} else {
			text = null;
		}
	}

	private static void holdText() {
		if (text != null) {
			textTicks += CHECK_EVERY_TICKS;
		}
	}

	/** One caption line per announcement line, centred just above the action bar. */
	private static void render(DrawContext context, float tickDelta) {
		final List<String> lines = text;
		final MinecraftClient client = MinecraftClient.getInstance();
		if (lines == null || client.options.hudHidden) {
			return;
		}
		final TextRenderer textRenderer = client.textRenderer;
		final int centre = context.getScaledWindowWidth() / 2;
		final int maxWidth = Math.min(context.getScaledWindowWidth() - 20, 360);
		final List<OrderedText> wrapped = new ArrayList<>();
		lines.forEach(line -> wrapped.addAll(textRenderer.wrapLines(Text.literal(line), maxWidth)));
		int y = context.getScaledWindowHeight() - 84 - 11 * wrapped.size();
		for (final OrderedText line : wrapped) {
			final int width = textRenderer.getWidth(line);
			context.fill(centre - width / 2 - 3, y - 2, centre + width / 2 + 3, y + 9, 0x90000000);
			context.drawTextWithShadow(textRenderer, line, centre - width / 2, y, 0xFFFFFF);
			y += 11;
		}
	}

	private static void stop(MinecraftClient client) {
		CLIPS.clear();
		if (playing != null) {
			client.getSoundManager().stop(playing);
			playing = null;
		}
		text = null;
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
