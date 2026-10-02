package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.overlap.Box;
import com.aureliatransit.utilities.overlap.OverlapResolver;
import com.aureliatransit.utilities.overlap.Zones;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Platforms inside two or more station zones. MTR gives such a platform to the first station in its list; this screen
 * lets the player pick the station instead, by cutting the other zones back so only the chosen one contains it.
 */
public final class StationOverlapScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_WARN = 0xFFAA00;
	private static final int COLOR_ERROR = 0xFF5555;

	/** A conflict with the transport mode it was found in. */
	private record Entry(TransportMode transportMode, Zones zones, Zones.Conflict conflict) {
	}

	private final Screen parent;
	private SimpleListWidget<Entry> conflictList;
	private SimpleListWidget<Long> stationList;
	private ButtonWidget applyButton;

	private final List<Entry> entries = new ArrayList<>();
	private Entry selectedEntry;
	private Long selectedStationId;
	private OverlapResolver.Plan plan;
	private final List<Text> planLines = new ArrayList<>();
	private final List<Integer> planColors = new ArrayList<>();
	private int mismatches;
	private Text status = Text.empty();
	private int statusColor = COLOR_DIM;

	public StationOverlapScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.overlap.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		final int listBottom = height - 58;
		conflictList = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP, half, listBottom - TOP, this::selectEntry));
		stationList = addDrawableChild(new SimpleListWidget<>(client, rightX, TOP + 14, half, 12 * 5 + 4, this::selectStation));
		applyButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.overlap.apply"), button -> apply()).dimensions(rightX, listBottom - 20, half, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());
		final Entry keep = selectedEntry;
		final Long keepStation = selectedStationId;
		rebuild();
		if (keep != null) {
			reselect(keep.conflict().platformId(), keepStation);
		}
		updateButtons();
	}

	/** Re-reads zones from MTR's dashboard data. Only on open, resize and after Apply; never per frame. */
	private void rebuild() {
		entries.clear();
		mismatches = 0;
		for (final TransportMode transportMode : TransportMode.values()) {
			final Zones zones = MtrBridge.zones(transportMode);
			zones.conflicts().forEach(conflict -> entries.add(new Entry(transportMode, zones, conflict)));
			// Honesty check: ATU's copy of MTR's rule must agree with what MTR itself assigned.
			zones.assignment().forEach((platformId, stationId) -> {
				final Platform platform = MtrBridge.dashboardData().platformIdMap.get(platformId);
				if (platform != null && (platform.area == null || platform.area.getId() != stationId)) {
					mismatches++;
				}
			});
		}
		final List<SimpleListWidget.Item<Entry>> items = new ArrayList<>();
		for (final Entry entry : entries) {
			items.add(new SimpleListWidget.Item<>(Text.literal(conflictLabel(entry)), COLOR_TEXT, entry));
		}
		conflictList.setItems(items, null);
		selectedEntry = null;
		selectedStationId = null;
		plan = null;
		stationList.setItems(List.of(), null);
		planLines.clear();
		planColors.clear();
	}

	private void reselect(long platformId, Long stationId) {
		for (final SimpleListWidget.Row<Entry> row : conflictList.children()) {
			if (row.value().conflict().platformId() == platformId) {
				conflictList.setSelected(row);
				if (stationId != null) {
					selectStationForTest(stationId);
				}
				return;
			}
		}
	}

	private void selectEntry(Entry entry) {
		selectedEntry = entry;
		selectedStationId = null;
		plan = null;
		planLines.clear();
		planColors.clear();
		final List<SimpleListWidget.Item<Long>> items = new ArrayList<>();
		if (entry != null) {
			final List<Long> stationIds = entry.conflict().stationIds();
			for (int i = 0; i < stationIds.size(); i++) {
				final String name = stationName(stationIds.get(i));
				items.add(new SimpleListWidget.Item<>(i == 0 ? Text.translatable("gui.aurelia_transit_utilities.overlap.current", name) : Text.literal(name), i == 0 ? COLOR_OK : COLOR_TEXT, stationIds.get(i)));
			}
		}
		stationList.setItems(items, null);
		updateButtons();
	}

	private void selectStation(Long stationId) {
		selectedStationId = stationId;
		plan = null;
		planLines.clear();
		planColors.clear();
		if (selectedEntry != null && stationId != null) {
			plan = OverlapResolver.plan(selectedEntry.zones(), selectedEntry.conflict(), stationId);
			describePlan();
		}
		updateButtons();
	}

	private void describePlan() {
		if (plan == null) {
			addLine(Text.translatable("gui.aurelia_transit_utilities.overlap.impossible"), COLOR_ERROR);
			return;
		}
		addLine(Text.translatable("gui.aurelia_transit_utilities.overlap.will_change"), COLOR_DIM);
		for (final OverlapResolver.Cut cut : plan.cuts()) {
			addLine(Text.literal("  " + stationName(cut.stationId()) + ": " + describeCut(cut)), COLOR_TEXT);
		}
		if (plan.sideEffects().isEmpty()) {
			addLine(Text.translatable("gui.aurelia_transit_utilities.overlap.no_side_effects"), COLOR_OK);
		} else {
			addLine(Text.translatable("gui.aurelia_transit_utilities.overlap.side_effects", plan.sideEffects().size()), COLOR_WARN);
			for (final Map.Entry<Long, Long[]> change : plan.sideEffects().entrySet()) {
				addLine(Text.literal("  " + platformName(change.getKey()) + ": " + stationName(change.getValue()[0]) + " → " + stationName(change.getValue()[1])), COLOR_WARN);
			}
		}
		if (!plan.reachesGoal()) {
			addLine(Text.translatable("gui.aurelia_transit_utilities.overlap.not_reached"), COLOR_ERROR);
		}
	}

	private static String describeCut(OverlapResolver.Cut cut) {
		final Box before = cut.before();
		final Box after = cut.after();
		return switch (cut.side()) {
			case WEST -> "west edge x " + before.minX() + " → " + after.minX();
			case EAST -> "east edge x " + before.maxX() + " → " + after.maxX();
			case NORTH -> "north edge z " + before.minZ() + " → " + after.minZ();
			case SOUTH -> "south edge z " + before.maxZ() + " → " + after.maxZ();
			case BELOW -> "bottom y " + before.minY() + " → " + after.minY();
			case ABOVE -> "top y " + before.maxY() + " → " + after.maxY();
		};
	}

	private void addLine(Text text, int color) {
		planLines.add(text);
		planColors.add(color);
	}

	private void updateButtons() {
		// Picking the station MTR already uses is valid too: it removes the overlap so the choice stays stable.
		applyButton.active = MtrBridge.canEdit() && plan != null && plan.reachesGoal() && !plan.cuts().isEmpty();
	}

	private void apply() {
		if (!MtrBridge.canEdit() || plan == null || !plan.reachesGoal() || selectedEntry == null) {
			return;
		}
		final String platform = platformName(selectedEntry.conflict().platformId());
		final String station = stationName(selectedStationId);
		MtrBridge.applyCuts(plan.cuts());
		rebuild();
		setStatus(Text.translatable("gui.aurelia_transit_utilities.overlap.applied", platform, station), COLOR_OK);
		updateButtons();
	}

	private static String stationName(Long stationId) {
		if (stationId == null) {
			return Text.translatable("gui.aurelia_transit_utilities.stops.no_station").getString();
		}
		final Station station = MtrBridge.dashboardData().stationIdMap.get(stationId);
		return station == null ? "?" : MtrBridge.displayName(station.getName());
	}

	private static String platformName(long platformId) {
		final Platform platform = MtrBridge.dashboardData().platformIdMap.get(platformId);
		return Text.translatable("gui.aurelia_transit_utilities.stops.platform", platform == null ? "?" : platform.getName()).getString();
	}

	private static String conflictLabel(Entry entry) {
		final List<Long> ids = entry.conflict().stationIds();
		final StringBuilder others = new StringBuilder();
		for (int i = 1; i < ids.size(); i++) {
			others.append(i > 1 ? ", " : "").append(stationName(ids.get(i)));
		}
		return platformName(entry.conflict().platformId()) + " @ " + stationName(ids.get(0)) + "  (+ " + others + ")";
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
		if (entries.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.overlap.none"), MARGIN + half / 2, height / 2 - 10, COLOR_OK);
		}
		context.drawTextWithShadow(textRenderer, selectedEntry == null ? Text.translatable("gui.aurelia_transit_utilities.overlap.pick") : Text.translatable("gui.aurelia_transit_utilities.overlap.choose"), rightX, TOP + 2, COLOR_DIM);
		int y = TOP + 14 + 12 * 5 + 10;
		for (int i = 0; i < planLines.size() && y < height - 90; i++, y += 10) {
			context.drawText(textRenderer, textRenderer.trimToWidth(planLines.get(i), half).getString(), rightX, y, planColors.get(i), true);
		}
		if (mismatches > 0) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.overlap.mismatch", mismatches), width / 2, height - 50, COLOR_ERROR);
		}
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 40, statusColor);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** For the dev self-test: the conflicts found, as (platform id, station ids in MTR order). */
	public List<Zones.Conflict> conflictsForTest() {
		return entries.stream().map(Entry::conflict).toList();
	}

	/** For the dev self-test: same as clicking the conflict row. */
	public void selectConflictForTest(long platformId) {
		reselect(platformId, null);
	}

	/** For the dev self-test: same as clicking the station row. */
	public void selectStationForTest(long stationId) {
		for (final SimpleListWidget.Row<Long> row : stationList.children()) {
			if (row.value() == stationId) {
				stationList.setSelected(row);
				return;
			}
		}
	}

	/** For the dev self-test. */
	public OverlapResolver.Plan planForTest() {
		return plan;
	}

	/** For the dev self-test. */
	public int mismatchesForTest() {
		return mismatches;
	}
}
