package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.route.StopOrder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Reorder a route's stops by dragging them (or with Move up / Move down), then save through MTR's update packet. */
public final class RouteStopsScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int BUTTON_WIDTH = 110;
	private static final int TOP = 32;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_ERROR = 0xFF5555;

	private final Screen parent;
	private TextFieldWidget routeSearch;
	private SimpleListWidget<Route> routeList;
	private DragListWidget stopList;
	private ButtonWidget upButton;
	private ButtonWidget downButton;
	private ButtonWidget reverseButton;
	private ButtonWidget revertButton;
	private ButtonWidget saveButton;
	private ButtonWidget regenerateButton;

	private Route selectedRoute;
	private StopOrder order = new StopOrder(0);
	private Text status = Text.empty();
	private int statusColor = COLOR_DIM;

	public RouteStopsScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.stops.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int leftWidth = Math.min(180, (width - MARGIN * 3) / 3);
		final int rightX = MARGIN * 2 + leftWidth;
		final int rightWidth = width - rightX - MARGIN;
		final int listBottom = height - 58;
		final int buttonsX = rightX + rightWidth - BUTTON_WIDTH;

		routeSearch = addDrawableChild(new TextFieldWidget(textRenderer, MARGIN, TOP, leftWidth, 16, Text.translatable("gui.aurelia_transit_utilities.stops.search")));
		routeSearch.setPlaceholder(Text.translatable("gui.aurelia_transit_utilities.stops.search"));
		routeSearch.setChangedListener(text -> refreshRoutes());
		routeList = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP + 20, leftWidth, listBottom - TOP - 20, this::selectRoute));

		stopList = addDrawableChild(new DragListWidget(rightX, TOP + 14, rightWidth - BUTTON_WIDTH - 6, listBottom - TOP - 14));
		upButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.stops.up"), button -> stopList.moveSelected(-1)).dimensions(buttonsX, TOP + 14, BUTTON_WIDTH, 20).build());
		downButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.stops.down"), button -> stopList.moveSelected(1)).dimensions(buttonsX, TOP + 38, BUTTON_WIDTH, 20).build());
		reverseButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.stops.reverse"), button -> {
			order.reverse();
			stopList.setSelected(-1);
			onOrderChanged();
		}).dimensions(buttonsX, TOP + 66, BUTTON_WIDTH, 20).build());
		revertButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.stops.revert"), button -> {
			order.reset();
			stopList.setSelected(-1);
			onOrderChanged();
		}).dimensions(buttonsX, TOP + 90, BUTTON_WIDTH, 20).build());
		saveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.stops.save"), button -> save()).dimensions(buttonsX, TOP + 118, BUTTON_WIDTH, 20).build());
		regenerateButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.stops.regenerate"), button -> regenerate()).dimensions(buttonsX, TOP + 142, BUTTON_WIDTH, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());

		final Route keep = selectedRoute;
		refreshRoutes();
		if (keep != null) {
			// Re-init (window resize) keeps the route and the unsaved order.
			final StopOrder keepOrder = order;
			selectedRoute = keep;
			order = keepOrder;
			stopList.setOrder(order, this::stopLabel, this::onOrderChanged);
		}
		updateButtons();
	}

	private void refreshRoutes() {
		final String query = routeSearch.getText().toLowerCase(Locale.ROOT).strip();
		final List<Route> routes = new ArrayList<>(MtrBridge.dashboardData().routes);
		routes.sort(Comparator.comparing(route -> MtrBridge.displayName(route.getName()).toLowerCase(Locale.ROOT)));
		final List<SimpleListWidget.Item<Route>> items = new ArrayList<>();
		for (final Route route : routes) {
			final String label = routeLabel(route);
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
		if (selectedRoute != null && order.isChanged()) {
			setStatus(Text.translatable("gui.aurelia_transit_utilities.stops.discarded", MtrBridge.displayName(selectedRoute.getName())), COLOR_DIM);
		} else {
			setStatus(Text.empty(), COLOR_DIM);
		}
		selectedRoute = route;
		order = new StopOrder(route == null ? 0 : route.getRoutePlatforms().size());
		stopList.setOrder(order, this::stopLabel, this::onOrderChanged);
		updateButtons();
	}

	private Text stopLabel(int originalIndex) {
		if (selectedRoute == null || originalIndex >= selectedRoute.getRoutePlatforms().size()) {
			return Text.literal("?");
		}
		final RoutePlatformData routePlatformData = selectedRoute.getRoutePlatforms().get(originalIndex);
		final Platform platform = routePlatformData.platform;
		final StringBuilder label = new StringBuilder();
		if (platform == null) {
			label.append(Text.translatable("gui.aurelia_transit_utilities.stops.missing_platform").getString());
		} else {
			final String stationName = platform.area == null ? "" : MtrBridge.displayName(platform.area.getName());
			label.append(stationName.isEmpty() ? Text.translatable("gui.aurelia_transit_utilities.stops.no_station").getString() : stationName);
			label.append("  ").append(Text.translatable("gui.aurelia_transit_utilities.stops.platform", platform.getName()).getString());
		}
		final String customDestination = routePlatformData.getCustomDestination();
		if (customDestination != null && !customDestination.isEmpty()) {
			label.append("  → ").append(MtrBridge.displayName(customDestination));
		}
		return Text.literal(label.toString());
	}

	private void onOrderChanged() {
		if (order.isChanged()) {
			setStatus(Text.translatable("gui.aurelia_transit_utilities.stops.unsaved"), COLOR_DIM);
		}
		updateButtons();
	}

	private void updateButtons() {
		final boolean hasRoute = selectedRoute != null && order.size() > 1;
		final boolean canEdit = MtrBridge.canEdit();
		upButton.active = hasRoute;
		downButton.active = hasRoute;
		reverseButton.active = hasRoute;
		revertButton.active = order.isChanged();
		saveButton.active = canEdit && order.isChanged();
		regenerateButton.active = canEdit && selectedRoute != null && !selectedRoute.depots.isEmpty();
	}

	private void save() {
		if (selectedRoute == null || !MtrBridge.canEdit() || !order.isChanged()) {
			return;
		}
		final List<RoutePlatformData> live = selectedRoute.getRoutePlatforms();
		final List<RoutePlatformData> reordered;
		try {
			reordered = order.apply(live);
		} catch (IllegalArgumentException e) {
			// The route was edited elsewhere since this screen read it: never write a guess.
			setStatus(Text.translatable("gui.aurelia_transit_utilities.stops.changed_elsewhere"), COLOR_ERROR);
			final Route route = selectedRoute;
			selectedRoute = null;
			selectRoute(route);
			return;
		}
		live.clear();
		live.addAll(reordered);
		MtrBridge.send(request -> request.addRoute(selectedRoute));
		order = new StopOrder(live.size());
		stopList.setOrder(order, this::stopLabel, this::onOrderChanged);
		setStatus(Text.translatable(selectedRoute.depots.isEmpty() ? "gui.aurelia_transit_utilities.stops.saved_no_depot" : "gui.aurelia_transit_utilities.stops.saved"), COLOR_OK);
		updateButtons();
	}

	private void regenerate() {
		if (selectedRoute == null || !MtrBridge.canEdit()) {
			return;
		}
		final int count = MtrBridge.regenerateDepots(selectedRoute);
		setStatus(Text.translatable("gui.aurelia_transit_utilities.stops.regenerating", count), COLOR_OK);
	}

	private static String routeLabel(Route route) {
		final String number = route.getRouteNumber();
		return MtrBridge.displayName(route.getName()) + (number == null || number.isEmpty() ? "" : " [" + number + "]") + " (" + route.getRoutePlatforms().size() + ")";
	}

	private void setStatus(Text text, int color) {
		status = text;
		statusColor = color;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (getFocused() == stopList && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) && Screen.hasShiftDown()) {
			stopList.moveSelected(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, COLOR_TEXT);
		final int leftWidth = Math.min(180, (width - MARGIN * 3) / 3);
		final int rightX = MARGIN * 2 + leftWidth;
		context.drawTextWithShadow(textRenderer, selectedRoute == null ? Text.translatable("gui.aurelia_transit_utilities.stops.pick_route") : Text.translatable("gui.aurelia_transit_utilities.stops.hint"), rightX, TOP + 2, COLOR_DIM);
		if (MtrBridge.dashboardData().routes.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.stops.no_routes"), MARGIN + leftWidth / 2, height / 2, COLOR_DIM);
		}
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 40, statusColor);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** For the dev self-test. */
	public DragListWidget stopListForTest() {
		return stopList;
	}

	/** For the dev self-test: same as clicking the route in the list. */
	public void selectRouteForTest(Route route) {
		selectRoute(route);
		refreshRoutes();
	}
}
