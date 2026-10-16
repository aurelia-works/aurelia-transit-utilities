package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.Announcer;
import com.aureliatransit.utilities.client.ScriptingHandshake;
import com.aureliatransit.utilities.client.MtrBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Function;

/** Hub opened from the ATU button on MTR's dashboard. */
public final class AtuToolsScreen extends Screen {

	private static final int BUTTON_WIDTH = 200;
	private static final int ROW = 24;

	/** {@code needsEdit}: greyed out without MTR edit permission. Depot health only reads until Regenerate is pressed. */
	private record Tool(String key, boolean needsEdit, Function<Screen, Screen> open) {
	}

	private static final List<Tool> TOOLS = List.of(
			new Tool("presets", true, TrainPresetScreen::new),
			new Tool("stops", true, RouteStopsScreen::new),
			new Tool("return", true, ReturnRouteScreen::new),
			new Tool("dwell", true, PlatformDwellScreen::new),
			new Tool("departures", true, DepartureEditorScreen::new),
			new Tool("overlap", true, StationOverlapScreen::new),
			new Tool("height", true, ZoneHeightScreen::new),
			new Tool("codes", true, StationCodesScreen::new),
			new Tool("depots", false, DepotHealthScreen::new),
			new Tool("speed", false, SpeedAuditScreen::new),
			new Tool("trains", false, TrainInspectorScreen::new),
			// /tp permission is checked by the server; the screen says so when the player lacks it.
			new Tool("jump", false, JumpScreen::new),
			new Tool("performance", false, PerformanceScreen::new)
	);

	private final Screen parent;

	public AtuToolsScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.tools.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int top = 44;
		// Tools above, a row of three player settings and Done below.
		final int rowsThatFit = Math.max(1, (height - 60 - top) / ROW);
		final int columns = Math.min(3, (TOOLS.size() + rowsThatFit - 1) / rowsThatFit);
		final int buttonWidth = columns == 1 ? BUTTON_WIDTH : Math.min(BUTTON_WIDTH, (width - 16 - (columns - 1) * 8) / columns);
		final int rows = (TOOLS.size() + columns - 1) / columns;
		final int left = (width - columns * buttonWidth - (columns - 1) * 8) / 2;
		final boolean canEdit = MtrBridge.canEdit();
		for (int i = 0; i < TOOLS.size(); i++) {
			final Tool tool = TOOLS.get(i);
			final int column = i / rows;
			final int row = i % rows;
			final ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.tools." + tool.key()), b -> client.setScreen(tool.open().apply(this)))
					.dimensions(left + column * (buttonWidth + 8), top + row * ROW, buttonWidth, 20).build());
			button.active = canEdit || !tool.needsEdit();
		}
		// Player settings, not edits: always available.
		final int settingWidth = Math.min(BUTTON_WIDTH, (width - 16 - 16) / 3);
		final int settingsLeft = (width - settingWidth * 3 - 16) / 2;
		final ButtonWidget announcer = addDrawableChild(ButtonWidget.builder(announcerLabel(), b -> {
			Announcer.setEnabled(!Announcer.enabled());
			b.setMessage(announcerLabel());
		}).dimensions(settingsLeft, height - 52, settingWidth, 20).build());
		announcer.setTooltip(Tooltip.of(Text.translatable("gui.aurelia_transit_utilities.tools.announcer.tooltip")));
		final ButtonWidget platform = addDrawableChild(ButtonWidget.builder(platformLabel(), b -> {
			Announcer.setPlatformEnabled(!Announcer.platformEnabled());
			b.setMessage(platformLabel());
		}).dimensions(settingsLeft + settingWidth + 8, height - 52, settingWidth, 20).build());
		platform.setTooltip(Tooltip.of(Text.translatable("gui.aurelia_transit_utilities.tools.platform.tooltip")));
		final ButtonWidget melodies = addDrawableChild(ButtonWidget.builder(melodiesLabel(), b -> {
			ScriptingHandshake.setMelodies(!ScriptingHandshake.melodiesEnabled());
			b.setMessage(melodiesLabel());
		}).dimensions(settingsLeft + (settingWidth + 8) * 2, height - 52, settingWidth, 20).build());
		melodies.setTooltip(Tooltip.of(Text.translatable("gui.aurelia_transit_utilities.tools.melodies.tooltip")));
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions((width - BUTTON_WIDTH) / 2, height - 28, BUTTON_WIDTH, 20).build());
	}

	private static Text platformLabel() {
		return Text.translatable("gui.aurelia_transit_utilities.tools.platform", ScreenTexts.onOrOff(Announcer.platformEnabled()));
	}

	private static Text melodiesLabel() {
		return Text.translatable("gui.aurelia_transit_utilities.tools.melodies", ScreenTexts.onOrOff(ScriptingHandshake.melodiesEnabled()));
	}

	private static Text announcerLabel() {
		return Text.translatable("gui.aurelia_transit_utilities.tools.announcer", ScreenTexts.onOrOff(Announcer.enabled()));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
		if (!MtrBridge.canEdit()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.no_permission"), width / 2, 30, 0xFF5555);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
