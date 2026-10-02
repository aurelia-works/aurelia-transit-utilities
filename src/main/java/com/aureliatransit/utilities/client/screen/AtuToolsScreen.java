package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
			new Tool("dwell", true, PlatformDwellScreen::new),
			new Tool("departures", true, DepartureEditorScreen::new),
			new Tool("overlap", true, StationOverlapScreen::new),
			new Tool("height", true, ZoneHeightScreen::new),
			new Tool("codes", true, StationCodesScreen::new),
			new Tool("depots", false, DepotHealthScreen::new)
	);

	private final Screen parent;

	public AtuToolsScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.tools.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int top = 44;
		final int rowsThatFit = Math.max(1, (height - 36 - top) / ROW);
		final int columns = TOOLS.size() <= rowsThatFit ? 1 : 2;
		final int buttonWidth = columns == 1 ? BUTTON_WIDTH : Math.min(BUTTON_WIDTH, (width - 24) / 2);
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
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions((width - BUTTON_WIDTH) / 2, height - 28, BUTTON_WIDTH, 20).build());
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
