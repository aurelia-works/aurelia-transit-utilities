package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.TrainView;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every train MTR has sent to this client (those within render distance), the one the player rides first: route,
 * next stop, depot and siding, speed against the rail's limit, and the delay MTR predicts. Read-only; refreshes once a
 * second while open.
 */
public final class TrainInspectorScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int REFRESH_TICKS = 20;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_WARN = 0xFFAA00;

	private final Screen parent;
	private SimpleListWidget<Long> list;
	private ButtonWidget jumpButton;
	private List<TrainView> trains = List.of();
	private Long selectedId;
	private int ticks;

	public TrainInspectorScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.trains.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		final int listBottom = height - 58;
		list = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP, half, listBottom - TOP, id -> {
			selectedId = id;
			updateButtons();
		}));
		jumpButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.jump.go"), button -> jump()).dimensions(rightX, listBottom - 20, half, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());
		refresh();
	}

	@Override
	public void tick() {
		if (++ticks % REFRESH_TICKS == 0) {
			refresh();
		}
	}

	private void refresh() {
		if (client.player == null) {
			return;
		}
		trains = MtrBridge.loadedTrains(client.player.getX(), client.player.getY(), client.player.getZ());
		final List<SimpleListWidget.Item<Long>> items = new ArrayList<>();
		boolean keep = false;
		for (final TrainView train : trains) {
			items.add(new SimpleListWidget.Item<>(Text.literal(label(train)), train.riding() ? COLOR_OK : train.onRoute() ? COLOR_TEXT : COLOR_DIM, train.id()));
			keep |= selectedId != null && selectedId == train.id();
		}
		if (!keep) {
			selectedId = trains.isEmpty() ? null : trains.get(0).id();
		}
		list.setItems(items, selectedId);
		updateButtons();
	}

	private static String label(TrainView train) {
		final String route = name(train.route());
		final String destination = name(train.destination());
		final String text = route.isEmpty() ? Text.translatable("gui.aurelia_transit_utilities.trains.not_in_service").getString() : route + (destination.isEmpty() ? "" : " → " + destination);
		return (train.riding() ? "★ " : "") + text + String.format(Locale.ROOT, " (%d m)", Math.round(train.distance()));
	}

	private static String name(String raw) {
		return MtrBridge.displayName(raw == null ? "" : raw.split("\\|\\|")[0]).strip();
	}

	private TrainView selected() {
		for (final TrainView train : trains) {
			if (selectedId != null && train.id() == selectedId) {
				return train;
			}
		}
		return null;
	}

	private boolean canTeleport() {
		return client.player != null && client.player.hasPermissionLevel(2);
	}

	private void updateButtons() {
		final TrainView train = selected();
		jumpButton.active = train != null && !train.riding() && canTeleport();
	}

	private void jump() {
		final TrainView train = selected();
		if (train == null || !canTeleport()) {
			return;
		}
		client.player.networkHandler.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f", train.x(), train.y() + 1, train.z()));
		client.setScreen(null);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, COLOR_TEXT);
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		final TrainView train = selected();
		if (train == null) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.trains.none"), MARGIN + half / 2, height / 2, COLOR_DIM);
			return;
		}
		int y = TOP + 2;
		y = line(context, Text.literal(label(train)), rightX, y, train.riding() ? COLOR_OK : COLOR_TEXT);
		y += 4;
		if (!name(train.thisStation()).isEmpty()) {
			y = line(context, Text.translatable("gui.aurelia_transit_utilities.trains.this_station", name(train.thisStation())), rightX, y, COLOR_DIM);
		}
		if (!name(train.nextStation()).isEmpty()) {
			y = line(context, Text.translatable("gui.aurelia_transit_utilities.trains.next_station", name(train.nextStation())), rightX, y, COLOR_TEXT);
		}
		y = line(context, Text.translatable("gui.aurelia_transit_utilities.trains.depot", train.depot().isEmpty() ? "?" : MtrBridge.displayName(train.depot()), train.siding().isEmpty() ? "?" : train.siding()), rightX, y, COLOR_DIM);
		y = line(context, Text.translatable("gui.aurelia_transit_utilities.trains.cars", train.cars(), Math.round(train.length())), rightX, y, COLOR_DIM);
		final boolean atLimit = train.limitKmh() > 0 && train.speedKmh() >= train.limitKmh() - 2;
		y = line(context, Text.translatable("gui.aurelia_transit_utilities.trains.speed", train.speedKmh(), train.limitKmh()), rightX, y, atLimit ? COLOR_WARN : COLOR_TEXT);
		final String state = train.doorsOpen() ? "doors" : train.manual() ? "manual" : train.onRoute() ? "running" : "depot";
		y = line(context, Text.translatable("gui.aurelia_transit_utilities.trains.state." + state), rightX, y, COLOR_DIM);
		if (train.delaySeconds() != null) {
			final long delay = train.delaySeconds();
			final Text text = Math.abs(delay) < 30 ? Text.translatable("gui.aurelia_transit_utilities.trains.on_time")
					: delay > 0 ? Text.translatable("gui.aurelia_transit_utilities.trains.late", formatDuration(delay)) : Text.translatable("gui.aurelia_transit_utilities.trains.early", formatDuration(-delay));
			line(context, text, rightX, y, Math.abs(delay) < 30 ? COLOR_OK : COLOR_WARN);
		}
	}

	private int line(DrawContext context, Text text, int x, int y, int color) {
		final int half = (width - MARGIN * 3) / 2;
		if (y < height - 90) {
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(text, half).getString(), x, y, color);
		}
		return y + 11;
	}

	private static String formatDuration(long seconds) {
		return seconds >= 60 ? String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60) : seconds + " s";
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** For the dev self-test. */
	public List<TrainView> trainsForTest() {
		return trains;
	}
}
