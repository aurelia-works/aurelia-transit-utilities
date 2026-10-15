package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.route.ReturnRoute;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds the opposite direction of a route in one go: stops reversed, each moved to the other platform of its
 * station (ends keep theirs). Every stop can be switched to another platform of the same station before saving.
 */
public final class ReturnRouteScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int BUTTON_WIDTH = 120;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_MOVED = 0x55FF55;
	private static final int COLOR_ERROR = 0xFF5555;

	private final Screen parent;
	private TextFieldWidget routeSearch;
	private SimpleListWidget<Route> routeList;
	private TextFieldWidget nameField;
	private SimpleListWidget<Integer> stopList;
	private ButtonWidget cycleButton;
	private ButtonWidget depotsButton;
	private ButtonWidget createButton;
	private ButtonWidget regenerateButton;

	private Route selectedRoute;
	private List<ReturnRoute.Choice> plan = List.of();
	private Integer selectedStop;
	private boolean addToDepots = true;
	private final List<Depot> depotsToRegenerate = new ArrayList<>();
	private Text status = Text.empty();
	private int statusColor = COLOR_DIM;

	public ReturnRouteScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.return.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int leftWidth = Math.min(170, (width - MARGIN * 3) / 3);
		final int rightX = MARGIN * 2 + leftWidth;
		final int rightWidth = width - rightX - MARGIN;
		final int listBottom = height - 58;
		final int buttonsX = rightX + rightWidth - BUTTON_WIDTH;

		routeSearch = addDrawableChild(new TextFieldWidget(textRenderer, MARGIN, TOP, leftWidth, 16, Text.translatable("gui.aurelia_transit_utilities.stops.search")));
		routeSearch.setPlaceholder(Text.translatable("gui.aurelia_transit_utilities.stops.search"));
		routeSearch.setChangedListener(text -> refreshRoutes());
		routeList = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP + 20, leftWidth, listBottom - TOP - 20, this::selectRoute));

		nameField = addDrawableChild(new TextFieldWidget(textRenderer, rightX, TOP, rightWidth - BUTTON_WIDTH - 6, 16, Text.translatable("gui.aurelia_transit_utilities.return.name")));
		nameField.setMaxLength(256);
		nameField.setPlaceholder(Text.translatable("gui.aurelia_transit_utilities.return.name"));
		nameField.setChangedListener(text -> updateButtons());
		stopList = addDrawableChild(new SimpleListWidget<>(client, rightX, TOP + 20, rightWidth - BUTTON_WIDTH - 6, listBottom - TOP - 20, index -> {
			selectedStop = index;
			updateButtons();
		}));
		cycleButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.return.cycle"), button -> cycleSelected()).dimensions(buttonsX, TOP + 20, BUTTON_WIDTH, 20).build());
		depotsButton = addDrawableChild(ButtonWidget.builder(depotsLabel(), button -> {
			addToDepots = !addToDepots;
			button.setMessage(depotsLabel());
		}).dimensions(buttonsX, TOP + 48, BUTTON_WIDTH, 20).build());
		createButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.return.create"), button -> create()).dimensions(buttonsX, TOP + 76, BUTTON_WIDTH, 20).build());
		regenerateButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.stops.regenerate"), button -> regenerate()).dimensions(buttonsX, TOP + 100, BUTTON_WIDTH, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());

		final Route keep = selectedRoute;
		final List<ReturnRoute.Choice> keepPlan = plan;
		refreshRoutes();
		if (keep != null) {
			selectedRoute = keep;
			plan = keepPlan;
			refreshStops();
		}
		updateButtons();
	}

	private Text depotsLabel() {
		return Text.translatable("gui.aurelia_transit_utilities.return.depots", ScreenTexts.onOrOff(addToDepots));
	}

	private void refreshRoutes() {
		final String query = routeSearch.getText().toLowerCase(Locale.ROOT).strip();
		final List<Route> routes = new ArrayList<>(MtrBridge.dashboardData().routes);
		routes.sort(Comparator.comparing(route -> MtrBridge.displayName(route.getName()).toLowerCase(Locale.ROOT)));
		final List<SimpleListWidget.Item<Route>> items = new ArrayList<>();
		for (final Route route : routes) {
			final String label = MtrBridge.displayName(route.getName()) + " (" + route.getRoutePlatforms().size() + ")";
			if (query.isEmpty() || label.toLowerCase(Locale.ROOT).contains(query) || route == selectedRoute) {
				items.add(new SimpleListWidget.Item<>(Text.literal(label), route.getHidden() ? COLOR_DIM : COLOR_TEXT, route));
			}
		}
		routeList.setItems(items, selectedRoute);
	}

	private void selectRoute(Route route) {
		if (route == selectedRoute) {
			return;
		}
		selectedRoute = route;
		selectedStop = null;
		depotsToRegenerate.clear();
		setStatus(Text.empty(), COLOR_DIM);
		if (route == null) {
			plan = List.of();
		} else {
			final List<ReturnRoute.Choice> newPlan = MtrBridge.returnRoutePlan(route);
			plan = newPlan == null ? List.of() : newPlan;
			if (newPlan == null) {
				setStatus(Text.translatable("gui.aurelia_transit_utilities.return.deleted_platform"), COLOR_ERROR);
			}
			nameField.setText(route.getName());
		}
		refreshStops();
		updateButtons();
	}

	private void refreshStops() {
		final List<SimpleListWidget.Item<Integer>> items = new ArrayList<>();
		for (int i = 0; i < plan.size(); i++) {
			final ReturnRoute.Choice choice = plan.get(i);
			items.add(new SimpleListWidget.Item<>(Text.literal(stopLabel(i, choice)), choice.moved() ? COLOR_MOVED : COLOR_TEXT, i));
		}
		stopList.setItems(items, selectedStop);
	}

	private static String stopLabel(int index, ReturnRoute.Choice choice) {
		final Platform platform = MtrBridge.dashboardData().platformIdMap.get(choice.platformId());
		final String station = platform == null || platform.area == null ? "?" : MtrBridge.displayName(platform.area.getName());
		final String name = platform == null ? "?" : platform.getName();
		String label = (index + 1) + ". " + station + " — " + Text.translatable("gui.aurelia_transit_utilities.stops.platform", name).getString();
		if (choice.moved()) {
			final Platform original = MtrBridge.dashboardData().platformIdMap.get(choice.originalPlatformId());
			label += " " + Text.translatable("gui.aurelia_transit_utilities.return.was", original == null ? "?" : original.getName()).getString();
		}
		if (choice.options().size() > 1) {
			label += " (" + (choice.options().indexOf(choice.platformId()) + 1) + "/" + choice.options().size() + ")";
		}
		return label;
	}

	private void cycleSelected() {
		if (selectedStop == null || selectedStop >= plan.size()) {
			return;
		}
		final List<ReturnRoute.Choice> newPlan = new ArrayList<>(plan);
		newPlan.set(selectedStop, plan.get(selectedStop).cycle());
		plan = newPlan;
		refreshStops();
	}

	private void updateButtons() {
		final boolean canEdit = MtrBridge.canEdit();
		cycleButton.active = selectedStop != null && selectedStop < plan.size() && plan.get(selectedStop).options().size() > 1;
		createButton.active = canEdit && selectedRoute != null && plan.size() >= 2 && !nameField.getText().isBlank();
		regenerateButton.active = canEdit && !depotsToRegenerate.isEmpty();
	}

	private void create() {
		if (!MtrBridge.canEdit() || selectedRoute == null || plan.size() < 2) {
			return;
		}
		final List<Long> platformIds = plan.stream().map(ReturnRoute.Choice::platformId).toList();
		final boolean joinDepots = addToDepots && !selectedRoute.depots.isEmpty();
		MtrBridge.createReturnRoute(selectedRoute, nameField.getText().strip(), platformIds, joinDepots);
		depotsToRegenerate.clear();
		if (joinDepots) {
			depotsToRegenerate.addAll(selectedRoute.depots);
			setStatus(Text.translatable("gui.aurelia_transit_utilities.return.created_depots", nameField.getText().strip(), platformIds.size(), depotsToRegenerate.size()), COLOR_MOVED);
		} else {
			setStatus(Text.translatable("gui.aurelia_transit_utilities.return.created", nameField.getText().strip(), platformIds.size()), COLOR_MOVED);
		}
		updateButtons();
	}

	private void regenerate() {
		if (!MtrBridge.canEdit()) {
			return;
		}
		final int count = MtrBridge.regenerateDepots(depotsToRegenerate);
		setStatus(Text.translatable("gui.aurelia_transit_utilities.depots.regenerating", count), COLOR_MOVED);
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
		final int leftWidth = Math.min(170, (width - MARGIN * 3) / 3);
		final int rightX = MARGIN * 2 + leftWidth;
		if (selectedRoute == null) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.return.pick_route"), rightX, TOP + 26, COLOR_DIM);
		}
		final int rightWidth = width - rightX - MARGIN;
		int y = TOP + 128;
		for (final var line : textRenderer.wrapLines(Text.translatable("gui.aurelia_transit_utilities.return.help"), BUTTON_WIDTH)) {
			if (y > height - 70) {
				break;
			}
			context.drawTextWithShadow(textRenderer, line, rightX + rightWidth - BUTTON_WIDTH, y, COLOR_DIM);
			y += 10;
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
	public List<ReturnRoute.Choice> planForTest() {
		return plan;
	}

	/** For the dev self-test. */
	public void setNameForTest(String name) {
		nameField.setText(name);
	}
}
