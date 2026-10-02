package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.overlap.HeightRange;
import com.aureliatransit.utilities.overlap.Zones;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Edit the height (Y range) of a station or depot zone. MTR stores it but its dashboard always draws zones with no
 * height limit (MTR issue #815). Limiting heights separates stations stacked above each other.
 */
public final class ZoneHeightScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int BUTTON_WIDTH = 110;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_WARN = 0xFFAA00;
	private static final int COLOR_ERROR = 0xFF5555;

	private final Screen parent;
	private SimpleListWidget<AreaBase<?, ?>> areaList;
	private TextFieldWidget bottomField;
	private TextFieldWidget topField;
	private ButtonWidget saveButton;

	private AreaBase<?, ?> selectedArea;
	private HeightRange pending;
	private final List<Text> previewLines = new ArrayList<>();
	private final List<Integer> previewColors = new ArrayList<>();
	private Text status = Text.empty();
	private int statusColor = COLOR_DIM;

	public ZoneHeightScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.height.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		final int listBottom = height - 58;
		final int fieldWidth = (half - 8) / 2;
		areaList = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP, half, listBottom - TOP, this::selectArea));
		bottomField = addDrawableChild(new TextFieldWidget(textRenderer, rightX + 1, TOP + 36, fieldWidth - 2, 16, Text.translatable("gui.aurelia_transit_utilities.height.bottom")));
		topField = addDrawableChild(new TextFieldWidget(textRenderer, rightX + fieldWidth + 9, TOP + 36, fieldWidth - 2, 16, Text.translatable("gui.aurelia_transit_utilities.height.top")));
		bottomField.setPlaceholder(Text.translatable("gui.aurelia_transit_utilities.height.no_limit"));
		topField.setPlaceholder(Text.translatable("gui.aurelia_transit_utilities.height.no_limit"));
		bottomField.setMaxLength(9);
		topField.setMaxLength(9);
		bottomField.setChangedListener(text -> preview());
		topField.setChangedListener(text -> preview());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.height.my_y"), button -> bottomField.setText(Integer.toString(playerY()))).dimensions(rightX, TOP + 56, fieldWidth, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.height.my_y"), button -> topField.setText(Integer.toString(playerY()))).dimensions(rightX + fieldWidth + 8, TOP + 56, fieldWidth, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.height.clear"), button -> {
			bottomField.setText("");
			topField.setText("");
		}).dimensions(rightX, TOP + 80, fieldWidth, 20).build());
		saveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.height.save"), button -> save()).dimensions(rightX + fieldWidth + 8, TOP + 80, fieldWidth, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());
		refreshAreas();
		if (selectedArea != null) {
			fillFields();
		}
		preview();
	}

	private int playerY() {
		return client.player == null ? 0 : client.player.getBlockPos().getY();
	}

	private void refreshAreas() {
		final List<SimpleListWidget.Item<AreaBase<?, ?>>> items = new ArrayList<>();
		final List<Station> stations = new ArrayList<>(MtrBridge.dashboardData().stations);
		stations.sort(Comparator.comparing(station -> MtrBridge.displayName(station.getName()).toLowerCase(Locale.ROOT)));
		stations.forEach(station -> addItem(items, station, "■ "));
		final List<Depot> depots = new ArrayList<>(MtrBridge.dashboardData().depots);
		depots.sort(Comparator.comparing(depot -> MtrBridge.displayName(depot.getName()).toLowerCase(Locale.ROOT)));
		depots.forEach(depot -> addItem(items, depot, "⌂ "));
		areaList.setItems(items, selectedArea);
	}

	private static void addItem(List<SimpleListWidget.Item<AreaBase<?, ?>>> items, AreaBase<?, ?> area, String prefix) {
		if (!MtrBridge.validCorners(area)) {
			return;
		}
		final HeightRange range = HeightRange.of(MtrBridge.box(area));
		items.add(new SimpleListWidget.Item<>(Text.literal(prefix + MtrBridge.displayName(area.getName()) + "  (" + range.describe() + ")"), range.hasBottom() || range.hasTop() ? COLOR_TEXT : COLOR_DIM, area));
	}

	private void selectArea(AreaBase<?, ?> area) {
		selectedArea = area;
		fillFields();
		setStatus(Text.empty(), COLOR_DIM);
	}

	private void fillFields() {
		if (selectedArea == null) {
			return;
		}
		final HeightRange range = HeightRange.of(MtrBridge.box(selectedArea));
		bottomField.setText(range.bottomField());
		topField.setText(range.topField());
		preview();
	}

	/** Runs on field edits only. Shows which platforms (or sidings) would change station (or depot). */
	private void preview() {
		previewLines.clear();
		previewColors.clear();
		pending = null;
		if (selectedArea != null) {
			final Optional<HeightRange> range = HeightRange.parse(bottomField.getText(), topField.getText());
			if (range.isEmpty()) {
				addLine(Text.translatable("gui.aurelia_transit_utilities.height.invalid"), COLOR_ERROR);
			} else {
				pending = range.get();
				final boolean isStation = selectedArea instanceof Station;
				final Zones zones = isStation ? MtrBridge.zones(selectedArea.getTransportMode()) : MtrBridge.depotZones(selectedArea.getTransportMode());
				final Zones after = zones.withBox(selectedArea.getId(), pending.applyTo(MtrBridge.box(selectedArea)));
				final Map<Long, Long[]> changes = zones.changesTo(after);
				addLine(Text.translatable("gui.aurelia_transit_utilities.height.new_range", pending.describe()), COLOR_TEXT);
				if (changes.isEmpty()) {
					addLine(Text.translatable(isStation ? "gui.aurelia_transit_utilities.height.no_changes" : "gui.aurelia_transit_utilities.height.no_changes_depot"), COLOR_OK);
				} else {
					addLine(Text.translatable(isStation ? "gui.aurelia_transit_utilities.height.changes" : "gui.aurelia_transit_utilities.height.changes_depot", changes.size()), COLOR_WARN);
					changes.forEach((id, stations) -> addLine(Text.literal("  " + savedRailName(id, isStation) + ": " + areaName(stations[0], isStation) + " → " + areaName(stations[1], isStation)), COLOR_WARN));
				}
			}
		}
		if (saveButton != null) {
			saveButton.active = MtrBridge.canEdit() && pending != null && !pending.equals(HeightRange.of(MtrBridge.box(selectedArea)));
		}
	}

	private void save() {
		if (!MtrBridge.canEdit() || selectedArea == null || pending == null) {
			return;
		}
		MtrBridge.applyHeight(selectedArea, pending);
		setStatus(Text.translatable("gui.aurelia_transit_utilities.height.saved", MtrBridge.displayName(selectedArea.getName()), pending.describe()), COLOR_OK);
		refreshAreas();
		preview();
	}

	private static String savedRailName(long id, boolean isStation) {
		final var savedRail = isStation ? MtrBridge.dashboardData().platformIdMap.get(id) : MtrBridge.dashboardData().sidingIdMap.get(id);
		final String name = savedRail == null ? "?" : savedRail.getName();
		return Text.translatable(isStation ? "gui.aurelia_transit_utilities.stops.platform" : "gui.aurelia_transit_utilities.height.siding", name).getString();
	}

	private static String areaName(Long id, boolean isStation) {
		if (id == null) {
			return Text.translatable("gui.aurelia_transit_utilities.height.none").getString();
		}
		final AreaBase<?, ?> area = isStation ? MtrBridge.dashboardData().stationIdMap.get(id) : MtrBridge.dashboardData().depotIdMap.get(id);
		return area == null ? "?" : MtrBridge.displayName(area.getName());
	}

	private void addLine(Text text, int color) {
		previewLines.add(text);
		previewColors.add(color);
	}

	private void setStatus(Text text, int color) {
		status = text;
		statusColor = color;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, COLOR_TEXT);
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		final int fieldWidth = (half - 8) / 2;
		if (selectedArea == null) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.height.pick"), rightX, TOP + 2, COLOR_DIM);
		} else {
			context.drawTextWithShadow(textRenderer, Text.literal(MtrBridge.displayName(selectedArea.getName())), rightX, TOP + 2, COLOR_TEXT);
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.height.bottom"), rightX, TOP + 24, COLOR_DIM);
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.height.top"), rightX + fieldWidth + 8, TOP + 24, COLOR_DIM);
		}
		int y = TOP + 108;
		for (int i = 0; i < previewLines.size() && y < height - 50; i++, y += 10) {
			context.drawText(textRenderer, textRenderer.trimToWidth(previewLines.get(i), half).getString(), rightX, y, previewColors.get(i), true);
		}
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 40, statusColor);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** For the dev self-test: same as clicking the zone in the list. */
	public void selectAreaForTest(AreaBase<?, ?> area) {
		for (final SimpleListWidget.Row<AreaBase<?, ?>> row : areaList.children()) {
			if (row.value() == area) {
				areaList.setSelected(row);
				return;
			}
		}
	}

	/** For the dev self-test. */
	public TextFieldWidget bottomFieldForTest() {
		return bottomField;
	}

	/** For the dev self-test. */
	public TextFieldWidget topFieldForTest() {
		return topField;
	}

	/** For the dev self-test: the preview lines as plain text. */
	public List<String> previewForTest() {
		return previewLines.stream().map(Text::getString).toList();
	}
}
