package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.perf.Perf;
import com.aureliatransit.utilities.perf.PerfSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** On/off switches for ATU's MTR performance fixes. Saved to the config folder. */
public final class PerformanceScreen extends Screen {

	private static final int BUTTON_WIDTH = 260;
	private static final int ROW = 24;
	private final Screen parent;

	public PerformanceScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.perf.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int left = (width - BUTTON_WIDTH) / 2;
		int y = 52;
		toggle("merge", settings -> settings.mergeTrainParts, (settings) -> settings.mergeTrainParts = !settings.mergeTrainParts, left, y);
		y += ROW;
		toggle("shader", settings -> settings.shaderPackFastPath, (settings) -> settings.shaderPackFastPath = !settings.shaderPackFastPath, left, y);
		y += ROW;
		distance("interior", settings -> settings.trainInteriorChunks, (settings, value) -> settings.trainInteriorChunks = value, left, y);
		y += ROW;
		distance("box", settings -> settings.trainBoxChunks, (settings, value) -> settings.trainBoxChunks = value, left, y);
		y += ROW;
		toggle("overlay", settings -> settings.debugOverlay, (settings) -> settings.debugOverlay = !settings.debugOverlay, left, y);
		y += ROW;
		// Merging happens when MTR builds a model, so already-loaded trains keep their old buffers until a reload.
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.perf.reload"), button -> client.reloadResources())
				.dimensions(left, y, BUTTON_WIDTH, 20).tooltip(Tooltip.of(Text.translatable("gui.aurelia_transit_utilities.perf.reload.tooltip"))).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions((width - 200) / 2, height - 28, 200, 20).build());
	}

	private void toggle(String key, Predicate<PerfSettings> get, Consumer<PerfSettings> flip, int x, int y) {
		addDrawableChild(ButtonWidget.builder(label(key, get), button -> {
			flip.accept(Perf.settings());
			Perf.save();
			button.setMessage(label(key, get));
		}).dimensions(x, y, BUTTON_WIDTH, 20).tooltip(Tooltip.of(Text.translatable("gui.aurelia_transit_utilities.perf." + key + ".tooltip"))).build());
	}

	private void distance(String key, ToIntFunction<PerfSettings> get, ObjIntConsumer<PerfSettings> set, int x, int y) {
		addDrawableChild(ButtonWidget.builder(distanceLabel(key, get), button -> {
			set.accept(Perf.settings(), PerfSettings.nextDistance(get.applyAsInt(Perf.settings())));
			Perf.save();
			button.setMessage(distanceLabel(key, get));
		}).dimensions(x, y, BUTTON_WIDTH, 20).tooltip(Tooltip.of(Text.translatable("gui.aurelia_transit_utilities.perf." + key + ".tooltip"))).build());
	}

	private static Text distanceLabel(String key, ToIntFunction<PerfSettings> get) {
		final int chunks = get.applyAsInt(Perf.settings());
		return Text.translatable("gui.aurelia_transit_utilities.perf." + key, chunks == 0 ? ScreenTexts.OFF : Text.translatable("gui.aurelia_transit_utilities.perf.chunks", chunks));
	}

	private static Text label(String key, Predicate<PerfSettings> get) {
		return Text.translatable("gui.aurelia_transit_utilities.perf." + key, ScreenTexts.onOrOff(get.test(Perf.settings())));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
		final Text status = Perf.mtrChecked() ? Text.translatable("gui.aurelia_transit_utilities.perf.active") : Text.translatable("gui.aurelia_transit_utilities.perf.unchecked");
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, 30, Perf.mtrChecked() ? 0xAAAAAA : 0xFF5555);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
