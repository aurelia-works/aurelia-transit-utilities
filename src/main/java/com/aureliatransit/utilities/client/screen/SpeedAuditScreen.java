package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.speed.SpeedAudit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only rail speed check of the rails around the player (MTR only sends rails within render distance to the
 * client): slow spots between faster rails, and curves set faster than is comfortable. Changes nothing; Jump takes
 * the player to the rail to fix it with MTR's own tools.
 */
public final class SpeedAuditScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_SLOW = 0xFF5555;
	private static final int COLOR_CURVE = 0xFFAA00;

	private final Screen parent;
	private SimpleListWidget<SpeedAudit.Finding> list;
	private ButtonWidget jumpButton;
	private List<SpeedAudit.Finding> findings = List.of();
	private int railCount;
	private SpeedAudit.Finding selected;

	public SpeedAuditScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.speed.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		final int listBottom = height - 58;
		list = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP, half, listBottom - TOP, finding -> {
			selected = finding;
			updateButtons();
		}));
		jumpButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.jump.go"), button -> jump()).dimensions(rightX, listBottom - 20, half / 2 - 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.depots.refresh"), button -> refresh()).dimensions(rightX + half / 2 + 2, listBottom - 20, half - half / 2 - 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());
		refresh();
	}

	private void refresh() {
		final List<SpeedAudit.Rail> rails = MtrBridge.loadedRails();
		railCount = rails.size();
		findings = SpeedAudit.audit(rails);
		final List<SimpleListWidget.Item<SpeedAudit.Finding>> items = new ArrayList<>();
		findings.forEach(finding -> items.add(new SimpleListWidget.Item<>(Text.literal(label(finding)), finding.kind() == SpeedAudit.Kind.SLOW_SPOT ? COLOR_SLOW : COLOR_CURVE, finding)));
		if (selected != null && !findings.contains(selected)) {
			selected = null;
		}
		list.setItems(items, selected);
		updateButtons();
	}

	private String label(SpeedAudit.Finding finding) {
		final SpeedAudit.Rail rail = finding.rail();
		final String where = String.format(Locale.ROOT, " @ %d, %d, %d", Math.round(rail.x()), Math.round(rail.y()), Math.round(rail.z()));
		if (finding.kind() == SpeedAudit.Kind.SLOW_SPOT) {
			return "▼ " + Text.translatable("gui.aurelia_transit_utilities.speed.slow_row", finding.limit(), finding.expected()).getString() + where;
		}
		return "↷ " + Text.translatable("gui.aurelia_transit_utilities.speed.curve_row", finding.limit(), Math.round(rail.radius())).getString() + where;
	}

	private boolean canTeleport() {
		return client.player != null && client.player.hasPermissionLevel(2);
	}

	private void updateButtons() {
		jumpButton.active = selected != null && canTeleport();
	}

	private void jump() {
		if (selected == null || !canTeleport()) {
			return;
		}
		final SpeedAudit.Rail rail = selected.rail();
		client.player.networkHandler.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f", rail.x(), rail.y() + 1, rail.z()));
		client.setScreen(null);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, COLOR_TEXT);
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		final long slow = findings.stream().filter(finding -> finding.kind() == SpeedAudit.Kind.SLOW_SPOT).count();
		int y = TOP + 2;
		y = paragraph(context, Text.translatable("gui.aurelia_transit_utilities.speed.summary", railCount, slow, findings.size() - slow), rightX, y, half, COLOR_DIM);
		y += 6;
		if (selected != null) {
			final SpeedAudit.Rail rail = selected.rail();
			final Text detail = selected.kind() == SpeedAudit.Kind.SLOW_SPOT
					? Text.translatable("gui.aurelia_transit_utilities.speed.slow_detail", selected.limit(), selected.expected())
					: Text.translatable("gui.aurelia_transit_utilities.speed.curve_detail", Math.round(rail.radius()), selected.limit(), selected.expected());
			paragraph(context, detail, rightX, y, half, selected.kind() == SpeedAudit.Kind.SLOW_SPOT ? COLOR_SLOW : COLOR_CURVE);
		} else if (findings.isEmpty()) {
			paragraph(context, Text.translatable(railCount == 0 ? "gui.aurelia_transit_utilities.speed.no_rails" : "gui.aurelia_transit_utilities.speed.clean"), rightX, y, half, railCount == 0 ? COLOR_DIM : COLOR_OK);
		} else {
			paragraph(context, Text.translatable("gui.aurelia_transit_utilities.speed.help"), rightX, y, half, COLOR_DIM);
		}
		if (!canTeleport()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.jump.no_permission"), width / 2, height - 40, COLOR_DIM);
		}
	}

	private int paragraph(DrawContext context, Text text, int x, int y, int width, int color) {
		for (final var line : textRenderer.wrapLines(text, width)) {
			if (y > height - 90) {
				break;
			}
			context.drawTextWithShadow(textRenderer, line, x, y, color);
			y += 10;
		}
		return y;
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** For the dev self-test. */
	public int railCountForTest() {
		return railCount;
	}

	/** For the dev self-test. */
	public List<SpeedAudit.Finding> findingsForTest() {
		return findings;
	}
}
