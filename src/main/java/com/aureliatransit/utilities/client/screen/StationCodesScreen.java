package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.codes.StationCodes;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Number the stations of a route (JR East style "JY17"). Codes are added as the last '|' parts of the station name, so
 * MTR's own signs and maps show them.
 */
public final class StationCodesScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_ERROR = 0xFF5555;

	private final Screen parent;
	private SimpleListWidget<Route> routeList;
	private TextFieldWidget prefixField;
	private TextFieldWidget startField;
	private TextFieldWidget stepField;
	private TextFieldWidget digitsField;
	private ButtonWidget applyButton;
	private ButtonWidget removeButton;

	private Route selectedRoute;
	private final List<Station> stations = new ArrayList<>();
	private final List<String> newNames = new ArrayList<>();
	private Text problem = Text.empty();
	private Text status = Text.empty();
	private int statusColor = COLOR_DIM;

	public StationCodesScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.codes.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int leftWidth = Math.min(180, (width - MARGIN * 3) / 3);
		final int rightX = MARGIN * 2 + leftWidth;
		final int rightWidth = width - rightX - MARGIN;
		final int listBottom = height - 58;
		final int fieldWidth = (rightWidth - 18) / 4;
		routeList = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP, leftWidth, listBottom - TOP, this::selectRoute));
		prefixField = field(rightX, fieldWidth, "JY", 4, 0);
		startField = field(rightX, fieldWidth, "1", 5, 1);
		stepField = field(rightX, fieldWidth, "1", 5, 2);
		digitsField = field(rightX, fieldWidth, "2", 1, 3);
		applyButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.codes.apply"), button -> apply()).dimensions(rightX, listBottom - 20, rightWidth / 2 - 3, 20).build());
		removeButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.codes.remove"), button -> remove()).dimensions(rightX + rightWidth / 2 + 3, listBottom - 20, rightWidth / 2 - 3, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());

		final List<Route> routes = new ArrayList<>(MtrBridge.dashboardData().routes);
		routes.sort(Comparator.comparing(route -> MtrBridge.displayName(route.getName()).toLowerCase(Locale.ROOT)));
		final List<SimpleListWidget.Item<Route>> items = new ArrayList<>();
		routes.forEach(route -> items.add(new SimpleListWidget.Item<>(Text.literal(MtrBridge.displayName(route.getName())), COLOR_TEXT, route)));
		routeList.setItems(items, selectedRoute);
		preview();
	}

	private TextFieldWidget field(int rightX, int fieldWidth, String value, int maxLength, int column) {
		final TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, rightX + 1 + column * (fieldWidth + 6), TOP + 12, fieldWidth - 2, 16, Text.empty()));
		field.setMaxLength(maxLength);
		field.setText(value);
		field.setChangedListener(text -> preview());
		return field;
	}

	private void selectRoute(Route route) {
		selectedRoute = route;
		stations.clear();
		if (route != null) {
			// Each station once, in route order (a loop's return visit is not numbered twice).
			final Set<Station> ordered = new LinkedHashSet<>();
			for (final RoutePlatformData data : route.getRoutePlatforms()) {
				if (data.platform != null && data.platform.area != null) {
					ordered.add(data.platform.area);
				}
			}
			stations.addAll(ordered);
		}
		status = Text.empty();
		preview();
	}

	/** On field edits and route choice only. */
	private void preview() {
		newNames.clear();
		problem = Text.empty();
		if (prefixField == null) {
			return;
		}
		final String prefix = StationCodes.normalisePrefix(prefixField.getText());
		final Integer start = parseInt(startField.getText());
		final Integer step = parseInt(stepField.getText());
		final Integer digits = parseInt(digitsField.getText());
		if (prefix == null) {
			problem = Text.translatable("gui.aurelia_transit_utilities.codes.bad_prefix");
		} else if (start == null || step == null || digits == null || digits < 1 || digits > 4 || step == 0) {
			problem = Text.translatable("gui.aurelia_transit_utilities.codes.bad_numbers");
		} else if (!stations.isEmpty()) {
			final List<String> codes = StationCodes.number(prefix, stations.size(), start, step, digits);
			if (codes.isEmpty()) {
				problem = Text.translatable("gui.aurelia_transit_utilities.codes.out_of_range");
			} else {
				for (int i = 0; i < stations.size(); i++) {
					newNames.add(StationCodes.withCode(stations.get(i).getName(), codes.get(i)));
				}
			}
		}
		final boolean canEdit = MtrBridge.canEdit() && !stations.isEmpty();
		applyButton.active = canEdit && !newNames.isEmpty();
		removeButton.active = canEdit && prefix != null && stations.stream().anyMatch(station -> StationCodes.codesOf(station.getName()).stream().anyMatch(code -> prefix.equals(StationCodes.prefixOf(code))));
	}

	private static Integer parseInt(String text) {
		try {
			return Integer.parseInt(text.strip());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void apply() {
		if (!MtrBridge.canEdit() || newNames.size() != stations.size()) {
			return;
		}
		final List<Station> changed = new ArrayList<>();
		for (int i = 0; i < stations.size(); i++) {
			if (!stations.get(i).getName().equals(newNames.get(i))) {
				stations.get(i).setName(newNames.get(i));
				changed.add(stations.get(i));
			}
		}
		send(changed, "gui.aurelia_transit_utilities.codes.applied");
	}

	private void remove() {
		final String prefix = StationCodes.normalisePrefix(prefixField.getText());
		if (!MtrBridge.canEdit() || prefix == null) {
			return;
		}
		final List<Station> changed = new ArrayList<>();
		for (final Station station : stations) {
			final String renamed = StationCodes.withoutCodes(station.getName(), prefix);
			if (!renamed.equals(station.getName())) {
				station.setName(renamed);
				changed.add(station);
			}
		}
		send(changed, "gui.aurelia_transit_utilities.codes.removed");
	}

	private void send(List<Station> changed, String key) {
		if (!changed.isEmpty()) {
			MtrBridge.send(request -> changed.forEach(request::addStation));
		}
		status = Text.translatable(key, changed.size());
		statusColor = COLOR_OK;
		preview();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, COLOR_TEXT);
		final int leftWidth = Math.min(180, (width - MARGIN * 3) / 3);
		final int rightX = MARGIN * 2 + leftWidth;
		final int rightWidth = width - rightX - MARGIN;
		final int fieldWidth = (rightWidth - 18) / 4;
		final String[] labels = {"prefix", "start", "step", "digits"};
		for (int i = 0; i < labels.length; i++) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.codes." + labels[i]), rightX + i * (fieldWidth + 6), TOP + 1, COLOR_DIM);
		}
		int y = TOP + 36;
		if (selectedRoute == null) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.codes.pick"), rightX, y, COLOR_DIM);
		} else if (!problem.getString().isEmpty()) {
			context.drawTextWithShadow(textRenderer, problem, rightX, y, COLOR_ERROR);
		} else {
			for (int i = 0; i < newNames.size() && y < height - 90; i++, y += 11) {
				final boolean same = newNames.get(i).equals(stations.get(i).getName());
				final String line = (i + 1) + ". " + MtrBridge.displayName(stations.get(i).getName()) + "  →  " + newNames.get(i);
				context.drawText(textRenderer, textRenderer.trimToWidth(Text.literal(line), rightWidth).getString(), rightX, y, same ? COLOR_DIM : COLOR_TEXT, true);
			}
		}
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 40, statusColor);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** For the dev self-test: same as clicking the route row. */
	public void selectRouteForTest(Route route) {
		for (final SimpleListWidget.Row<Route> row : routeList.children()) {
			if (row.value() == route) {
				routeList.setSelected(row);
				return;
			}
		}
	}

	/** For the dev self-test. */
	public TextFieldWidget prefixFieldForTest() {
		return prefixField;
	}

	/** For the dev self-test. */
	public List<String> previewForTest() {
		return List.copyOf(newNames);
	}
}
