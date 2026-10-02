package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.timetable.Timetable;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.mtr.core.data.Depot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Builds a depot's departures in bulk, in three tabs: timed departures (even service, rest periods, shifting, a
 * "times at the first station" helper), MTR's trains-per-hour mode (a gap for a range of hours), and loops that
 * "repeat infinitely" (trains spread evenly round the loop). Nothing is sent until Save.
 */
public final class DepartureEditorScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 30;
	private static final int ROW = 22;
	private static final int GAP = 4;
	private static final int TIME_FIELD = 38;
	private static final int NUMBER_FIELD = 26;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_WARN = 0xFFAA00;
	private static final int COLOR_ERROR = 0xFF5555;

	private enum Tab {TIMED, HOURLY, LOOP}

	private record Label(Tab tab, Text text, int x, int y) {
	}

	private final Screen parent;
	private SimpleListWidget<Depot> depotList;
	private final Map<Tab, List<ClickableWidget>> tabWidgets = new EnumMap<>(Tab.class);
	private final List<Label> labels = new ArrayList<>();
	private final List<ButtonWidget> editButtons = new ArrayList<>();
	private final Map<Tab, ButtonWidget> tabButtons = new EnumMap<>(Tab.class);
	private TextFieldWidget firstField;
	private TextFieldWidget lastField;
	private TextFieldWidget gapField;
	private TextFieldWidget travelField;
	private TextFieldWidget restFromField;
	private TextFieldWidget restToField;
	private TextFieldWidget shiftField;
	private TextFieldWidget hourFromField;
	private TextFieldWidget hourToField;
	private TextFieldWidget hourGapField;
	private TextFieldWidget loopFirstField;
	private TextFieldWidget trainsField;
	private TextFieldWidget roundTripField;
	private ButtonWidget saveTimedButton;
	private ButtonWidget saveHourlyButton;

	private Tab tab = Tab.TIMED;
	private Depot selected;
	private List<Long> pending = new ArrayList<>();
	private int[] pendingFrequencies = new int[24];
	private boolean dirty;
	private Text status = Text.empty();
	private int statusColor = COLOR_DIM;

	/** Lays widgets left to right on one row and records the labels drawn between them. */
	private final class RowLayout {

		private final Tab rowTab;
		private final int y;
		private int x;

		private RowLayout(Tab rowTab, int x, int y) {
			this.rowTab = rowTab;
			this.x = x;
			this.y = y;
		}

		private RowLayout label(String key) {
			final Text text = Text.translatable("gui.aurelia_transit_utilities.departures.label." + key);
			labels.add(new Label(rowTab, text, x, y + 6));
			x += textRenderer.getWidth(text) + GAP;
			return this;
		}

		private TextFieldWidget field(int width, String value) {
			final TextFieldWidget field = new TextFieldWidget(textRenderer, x, y + 2, width, 16, Text.empty());
			field.setMaxLength(8);
			field.setText(value);
			add(field);
			x += width + GAP;
			return field;
		}

		private ButtonWidget button(String key, Runnable action) {
			final Text text = Text.translatable("gui.aurelia_transit_utilities.departures." + key);
			final int width = textRenderer.getWidth(text) + 10;
			final ButtonWidget button = ButtonWidget.builder(text, b -> action.run()).dimensions(x, y, width, 20).build();
			add(button);
			editButtons.add(button);
			x += width + GAP;
			return button;
		}

		private void add(ClickableWidget widget) {
			addDrawableChild(widget);
			tabWidgets.get(rowTab).add(widget);
		}
	}

	public DepartureEditorScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.departures.title"));
		this.parent = parent;
	}

	private int leftWidth() {
		return Math.min(140, (width - MARGIN * 3) / 4);
	}

	private int contentX() {
		return MARGIN * 2 + leftWidth();
	}

	@Override
	protected void init() {
		labels.clear();
		editButtons.clear();
		tabButtons.clear();
		for (final Tab each : Tab.values()) {
			tabWidgets.put(each, new ArrayList<>());
		}
		final int x = contentX();
		final int rightWidth = width - x - MARGIN;
		final int listBottom = height - 52;
		depotList = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP, leftWidth(), listBottom - TOP, this::selectDepot));

		// Tabs.
		final int tabWidth = (rightWidth - 2 * GAP) / 3;
		for (final Tab each : Tab.values()) {
			final ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.departures.tab." + each.name().toLowerCase(Locale.ROOT)), b -> showTab(each))
					.dimensions(x + each.ordinal() * (tabWidth + GAP), TOP + 12, tabWidth, 20).build());
			tabButtons.put(each, button);
		}

		final int firstRow = TOP + 38;
		// Timed tab.
		RowLayout row = new RowLayout(Tab.TIMED, x, firstRow).label("from");
		firstField = row.field(TIME_FIELD, "06:00");
		row.label("to");
		lastField = row.field(TIME_FIELD, "23:30");
		row.label("every");
		gapField = row.field(NUMBER_FIELD, "10");
		row.label("min");
		row = new RowLayout(Tab.TIMED, x, firstRow + ROW).label("travel");
		travelField = row.field(NUMBER_FIELD, "0");
		row.label("min");
		row.button("add", () -> generate(false));
		row.button("replace", () -> generate(true));
		row = new RowLayout(Tab.TIMED, x, firstRow + ROW * 2).label("rest");
		restFromField = row.field(TIME_FIELD, "01:00");
		row.label("to");
		restToField = row.field(TIME_FIELD, "05:00");
		row.button("rest", this::rest);
		row = new RowLayout(Tab.TIMED, x, firstRow + ROW * 3).label("shift");
		shiftField = row.field(NUMBER_FIELD, "5");
		row.label("min");
		row.button("later", () -> shift(1));
		row.button("earlier", () -> shift(-1));

		// Trains-per-hour tab.
		row = new RowLayout(Tab.HOURLY, x, firstRow).label("hours");
		hourFromField = row.field(TIME_FIELD, "06:00");
		row.label("to");
		hourToField = row.field(TIME_FIELD, "23:00");
		row.label("every");
		hourGapField = row.field(NUMBER_FIELD, "15");
		row.label("min");
		row = new RowLayout(Tab.HOURLY, x, firstRow + ROW);
		row.button("hourly", this::setHours);
		row.button("no_trains", this::clearHours);

		// Loop tab.
		row = new RowLayout(Tab.LOOP, x, firstRow).label("from");
		loopFirstField = row.field(TIME_FIELD, "06:00");
		row.label("trains");
		trainsField = row.field(NUMBER_FIELD, "3");
		row = new RowLayout(Tab.LOOP, x, firstRow + ROW).label("round_trip");
		roundTripField = row.field(NUMBER_FIELD, "24");
		row.label("min");
		row.button("space", this::spaceEvenly);

		saveTimedButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.departures.save_timed"), b -> saveTimed()).dimensions(x, listBottom - 20, rightWidth, 20).build());
		saveHourlyButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.departures.save_hourly"), b -> saveHourly()).dimensions(x, listBottom - 20, rightWidth, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close()).dimensions(width / 2 - 75, height - 24, 150, 20).build());

		final List<Depot> depots = new ArrayList<>(MtrBridge.dashboardData().depots);
		depots.removeIf(depot -> depot.getTransportMode().continuousMovement);
		depots.sort(Comparator.comparing(depot -> MtrBridge.displayName(depot.getName()).toLowerCase(Locale.ROOT)));
		final List<SimpleListWidget.Item<Depot>> items = new ArrayList<>();
		depots.forEach(depot -> items.add(new SimpleListWidget.Item<>(Text.literal(MtrBridge.displayName(depot.getName())), COLOR_TEXT, depot)));
		depotList.setItems(items, selected);
		showTab(tab);
	}

	private void showTab(Tab newTab) {
		tab = newTab;
		tabWidgets.forEach((each, widgets) -> widgets.forEach(widget -> widget.visible = each == tab && selected != null));
		tabButtons.forEach((each, button) -> button.active = each != tab);
		updateButtons();
	}

	private void selectDepot(Depot depot) {
		selected = depot;
		dirty = false;
		pending = depot == null ? new ArrayList<>() : MtrBridge.localDepartures(depot);
		pendingFrequencies = new int[24];
		if (depot != null) {
			for (int hour = 0; hour < 24; hour++) {
				pendingFrequencies[hour] = (int) depot.getFrequency(hour);
			}
			// Open on the tab that matches how the depot runs now.
			tab = depot.getRepeatInfinitely() ? Tab.LOOP : depot.getUseRealTime() ? Tab.TIMED : Tab.HOURLY;
		}
		setStatus(Text.empty(), COLOR_DIM);
		showTab(tab);
	}

	private void updateButtons() {
		final boolean ok = MtrBridge.canEdit() && selected != null;
		editButtons.forEach(button -> button.active = ok);
		saveTimedButton.visible = selected != null && tab != Tab.HOURLY;
		saveHourlyButton.visible = selected != null && tab == Tab.HOURLY;
		saveTimedButton.active = ok && (dirty || !selected.getUseRealTime());
		saveHourlyButton.active = ok && (dirty || selected.getUseRealTime());
	}

	private boolean invalid() {
		setStatus(Text.translatable("gui.aurelia_transit_utilities.departures.invalid"), COLOR_ERROR);
		return false;
	}

	private void generate(boolean replace) {
		final OptionalLong first = Timetable.parseTime(firstField.getText());
		final OptionalLong last = Timetable.parseTime(lastField.getText());
		final OptionalLong gap = Timetable.parseMinutes(gapField.getText());
		final long travel = parseTravel();
		if (first.isEmpty() || last.isEmpty() || gap.isEmpty() || travel < 0) {
			invalid();
			return;
		}
		// Times typed are for the first station when a travel time is set; the depot departs that much earlier.
		final List<Long> generated = Timetable.departuresForFirstStation(Timetable.generate(first.getAsLong(), last.getAsLong(), gap.getAsLong()), travel);
		pending = replace ? Timetable.normalise(generated) : Timetable.merge(pending, generated);
		changed(Text.translatable("gui.aurelia_transit_utilities.departures.generated", generated.size()));
	}

	private long parseTravel() {
		final String text = travelField.getText().strip();
		if (text.isEmpty() || text.equals("0")) {
			return 0;
		}
		final OptionalLong travel = Timetable.parseMinutes(text);
		return travel.isPresent() ? travel.getAsLong() : -1;
	}

	private void rest() {
		final OptionalLong from = Timetable.parseTime(restFromField.getText());
		final OptionalLong to = Timetable.parseTime(restToField.getText());
		if (from.isEmpty() || to.isEmpty()) {
			invalid();
			return;
		}
		final int before = pending.size();
		pending = Timetable.removeBetween(pending, from.getAsLong(), to.getAsLong());
		changed(Text.translatable("gui.aurelia_transit_utilities.departures.rested", before - pending.size()));
	}

	private void shift(int direction) {
		final OptionalLong delta = Timetable.parseMinutes(shiftField.getText());
		if (delta.isEmpty()) {
			invalid();
			return;
		}
		pending = Timetable.shift(pending, direction * delta.getAsLong());
		changed(Text.translatable("gui.aurelia_transit_utilities.departures.shifted"));
	}

	private void spaceEvenly() {
		final OptionalLong first = Timetable.parseTime(loopFirstField.getText());
		final OptionalLong roundTrip = Timetable.parseMinutes(roundTripField.getText());
		int trains;
		try {
			trains = Integer.parseInt(trainsField.getText().strip());
		} catch (NumberFormatException e) {
			trains = 0;
		}
		if (first.isEmpty() || roundTrip.isEmpty() || trains < 1 || trains > 100) {
			invalid();
			return;
		}
		pending = Timetable.evenlySpaced(first.getAsLong(), roundTrip.getAsLong(), trains);
		changed(Text.translatable("gui.aurelia_transit_utilities.departures.spaced", trains, minutesText(roundTrip.getAsLong() / trains)));
	}

	/** Hours from the first to the last (whole hours, may pass midnight) get the gap; the others keep their value. */
	private void setHours() {
		final OptionalLong from = Timetable.parseTime(hourFromField.getText());
		final OptionalLong to = Timetable.parseTime(hourToField.getText());
		final OptionalLong gap = Timetable.parseMinutes(hourGapField.getText());
		if (from.isEmpty() || to.isEmpty() || gap.isEmpty()) {
			invalid();
			return;
		}
		final int frequency = Timetable.frequencyForGap(gap.getAsLong());
		final int[] range = fillHours(from.getAsLong(), to.getAsLong(), frequency);
		changed(Text.translatable("gui.aurelia_transit_utilities.departures.hours_set", range[0], range[1], minutesText(Timetable.gapForFrequency(frequency))));
	}

	private void clearHours() {
		final OptionalLong from = Timetable.parseTime(hourFromField.getText());
		final OptionalLong to = Timetable.parseTime(hourToField.getText());
		if (from.isEmpty() || to.isEmpty()) {
			invalid();
			return;
		}
		final int[] range = fillHours(from.getAsLong(), to.getAsLong(), 0);
		changed(Text.translatable("gui.aurelia_transit_utilities.departures.hours_cleared", range[0], range[1]));
	}

	private int[] fillHours(long from, long to, int frequency) {
		final int firstHour = (int) (from / 3_600_000);
		final int lastHour = (int) (to / 3_600_000);
		for (int i = 0; i < 24; i++) {
			final int hour = (firstHour + i) % 24;
			pendingFrequencies[hour] = frequency;
			if (hour == lastHour) {
				break;
			}
		}
		return new int[]{firstHour, lastHour};
	}

	/** "15 min", "7.5 min", "22 h 3 min". */
	private static String minutesText(long millis) {
		final long seconds = Math.round(millis / 1000.0);
		if (seconds >= 3600) {
			final long minutes = seconds / 60;
			return minutes / 60 + " h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + " min");
		}
		return seconds % 60 == 0 ? seconds / 60 + " min" : String.format(Locale.ROOT, "%.1f min", seconds / 60.0);
	}

	private void changed(Text message) {
		dirty = true;
		setStatus(message, COLOR_OK);
		updateButtons();
	}

	private void saveTimed() {
		if (!MtrBridge.canEdit() || selected == null) {
			return;
		}
		MtrBridge.saveDepartures(selected, pending);
		dirty = false;
		setStatus(Text.translatable("gui.aurelia_transit_utilities.departures.saved_timed", pending.size()), COLOR_OK);
		updateButtons();
	}

	private void saveHourly() {
		if (!MtrBridge.canEdit() || selected == null) {
			return;
		}
		MtrBridge.saveFrequencies(selected, pendingFrequencies);
		dirty = false;
		setStatus(Text.translatable("gui.aurelia_transit_utilities.departures.saved_hourly"), COLOR_OK);
		updateButtons();
	}

	private void setStatus(Text text, int color) {
		status = text;
		statusColor = color;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, COLOR_TEXT);
		final int x = contentX();
		final int rightWidth = width - x - MARGIN;
		final int listBottom = height - 52;
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 34, statusColor);
		if (selected == null) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.departures.pick"), x, TOP, COLOR_DIM);
			return;
		}
		final String mode = Text.translatable(selected.getRepeatInfinitely() ? "gui.aurelia_transit_utilities.departures.mode_loop" : selected.getUseRealTime() ? "gui.aurelia_transit_utilities.departures.mode_timed" : "gui.aurelia_transit_utilities.departures.mode_hourly").getString();
		final Text heading = Text.literal(MtrBridge.displayName(selected.getName()) + " — " + mode + (dirty ? " — " + Text.translatable("gui.aurelia_transit_utilities.departures.unsaved").getString() : ""));
		context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(heading, rightWidth).getString(), x, TOP, dirty ? COLOR_WARN : COLOR_TEXT);
		labels.forEach(label -> {
			if (label.tab() == tab) {
				context.drawTextWithShadow(textRenderer, label.text(), label.x(), label.y(), COLOR_DIM);
			}
		});

		// What Save would send, under the tab's rows and above the Save button.
		final int rows = tab == Tab.TIMED ? 4 : 2;
		int y = TOP + 38 + ROW * rows + 2;
		final int bottom = listBottom - 24;
		if (tab == Tab.HOURLY) {
			for (int line = 0; line < 4 && y + 9 <= bottom; line++, y += 10) {
				final StringBuilder text = new StringBuilder();
				for (int hour = line * 6; hour < line * 6 + 6; hour++) {
					final long gap = Timetable.gapForFrequency(pendingFrequencies[hour]);
					text.append(String.format(Locale.ROOT, "%02dh %-5s", hour, gap == 0 ? "—" : Math.round(gap / 60000.0) + "m"));
				}
				context.drawText(textRenderer, textRenderer.trimToWidth(Text.literal(text.toString()), rightWidth).getString(), x, y, COLOR_DIM, true);
			}
			return;
		}
		if (pending.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.departures.none"), x, y, COLOR_DIM);
			return;
		}
		final Text summary = Text.translatable("gui.aurelia_transit_utilities.departures.summary", pending.size(), Timetable.format(pending.get(0)), Timetable.format(pending.get(pending.size() - 1)), minutesText(Timetable.longestGap(pending)));
		context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(summary, rightWidth).getString(), x, y, COLOR_TEXT);
		y += 11;
		final StringBuilder line = new StringBuilder();
		for (int i = 0; i < pending.size() && i < 120; i++) {
			line.append(Timetable.format(pending.get(i))).append("  ");
		}
		for (final var wrapped : textRenderer.wrapLines(Text.literal(line.toString()), rightWidth)) {
			if (y + 9 > bottom) {
				break;
			}
			context.drawTextWithShadow(textRenderer, wrapped, x, y, COLOR_DIM);
			y += 10;
		}
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** For the dev self-test: same as clicking the depot row. */
	public void selectDepotForTest(Depot depot) {
		for (final SimpleListWidget.Row<Depot> row : depotList.children()) {
			if (row.value() == depot) {
				depotList.setSelected(row);
				return;
			}
		}
	}

	/** For the dev self-test: same as clicking the tab. */
	public void showTabForTest(String name) {
		showTab(Tab.valueOf(name));
	}

	/** For the dev self-test. */
	public List<Long> pendingForTest() {
		return pending;
	}

	/** For the dev self-test: timed tab fields, then hourly, then loop. */
	public List<TextFieldWidget> fieldsForTest() {
		return List.of(firstField, lastField, gapField, travelField, restFromField, restToField, shiftField, hourFromField, hourToField, hourGapField, loopFirstField, trainsField, roundTripField);
	}
}
