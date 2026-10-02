package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Hub opened from the ATU button on MTR's dashboard. */
public final class AtuToolsScreen extends Screen {

	private static final int BUTTON_WIDTH = 200;

	private final Screen parent;

	public AtuToolsScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.tools.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int x = (width - BUTTON_WIDTH) / 2;
		int y = height / 4 + 8;
		final ButtonWidget presets = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.tools.presets"), button -> client.setScreen(new TrainPresetScreen(this))).dimensions(x, y, BUTTON_WIDTH, 20).build());
		presets.active = MtrBridge.canEdit();
		y += 24;
		final ButtonWidget stops = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.tools.stops"), button -> client.setScreen(new RouteStopsScreen(this))).dimensions(x, y, BUTTON_WIDTH, 20).build());
		stops.active = MtrBridge.canEdit();
		y += 24;
		final ButtonWidget dwell = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.tools.dwell"), button -> client.setScreen(new PlatformDwellScreen(this))).dimensions(x, y, BUTTON_WIDTH, 20).build());
		dwell.active = MtrBridge.canEdit();
		y += 24;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(x, height - 28, BUTTON_WIDTH, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
		if (!MtrBridge.canEdit()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.no_permission"), width / 2, 32, 0xFF5555);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
