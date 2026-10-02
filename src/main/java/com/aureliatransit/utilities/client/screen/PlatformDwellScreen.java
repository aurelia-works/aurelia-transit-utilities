package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.dwell.DwellTime;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.LongUnaryOperator;

/** Set the dwell time of many platforms at once: all platforms, one station's, or every stop of one route. */
public final class PlatformDwellScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int BUTTON_WIDTH = 110;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_ERROR = 0xFF5555;
	private static final long NUDGE_MILLIS = 5000;

	/** A left-hand list entry: everything, one station, or one route. */
	private record Scope(String label, Object source) {
	}

	private static final Object ALL = new Object();

	private final Screen parent;
	private SimpleListWidget<Scope> scopeList;
	private SimpleListWidget<Platform> platformList;
	private TextFieldWidget dwellField;
	private ButtonWidget setButton;
	private ButtonWidget minusButton;
	private ButtonWidget plusButton;
	private ButtonWidget regenerateButton;

	private Scope selectedScope;
	private final List<Platform> scopePlatforms = new ArrayList<>();
	private final Set<Platform> ticked = new LinkedHashSet<>();
	private final Set<Depot> depotsToRegenerate = new LinkedHashSet<>();
	private Text status = Text.empty();
	private int statusColor = COLOR_DIM;

	public PlatformDwellScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.dwell.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int leftWidth = Math.min(180, (width - MARGIN * 3) / 3);
		final int rightX = MARGIN * 2 + leftWidth;
		final int rightWidth = width - rightX - MARGIN;
		final int listBottom = height - 58;
		final int buttonsX = rightX + rightWidth - BUTTON_WIDTH;

		scopeList = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP, leftWidth, listBottom - TOP, this::selectScope));
		platformList = addDrawableChild(new SimpleListWidget<>(client, rightX, TOP + 14, rightWidth - BUTTON_WIDTH - 6, listBottom - TOP - 14, platform -> {
			if (platform != null) {
				if (!ticked.remove(platform)) {
					ticked.add(platform);
				}
				refreshPlatforms();
			}
		}));
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.dwell.all"), button -> {
			ticked.addAll(scopePlatforms);
			refreshPlatforms();
		}).dimensions(buttonsX, TOP + 14, BUTTON_WIDTH / 2 - 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.dwell.none"), button -> {
			ticked.clear();
			refreshPlatforms();
		}).dimensions(buttonsX + BUTTON_WIDTH / 2 + 2, TOP + 14, BUTTON_WIDTH / 2 - 2, 20).build());
		dwellField = addDrawableChild(new TextFieldWidget(textRenderer, buttonsX + 1, TOP + 44, BUTTON_WIDTH - 2, 16, Text.translatable("gui.aurelia_transit_utilities.dwell.field")));
		dwellField.setMaxLength(10);
		dwellField.setPlaceholder(Text.translatable("gui.aurelia_transit_utilities.dwell.field"));
		dwellField.setChangedListener(text -> updateButtons());
		setButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.dwell.set"), button -> setDwell()).dimensions(buttonsX, TOP + 64, BUTTON_WIDTH, 20).build());
		minusButton = addDrawableChild(ButtonWidget.builder(Text.literal("-5 s"), button -> apply(millis -> DwellTime.adjust(millis, -NUDGE_MILLIS), "-5 s")).dimensions(buttonsX, TOP + 88, BUTTON_WIDTH / 2 - 2, 20).build());
		plusButton = addDrawableChild(ButtonWidget.builder(Text.literal("+5 s"), button -> apply(millis -> DwellTime.adjust(millis, NUDGE_MILLIS), "+5 s")).dimensions(buttonsX + BUTTON_WIDTH / 2 + 2, TOP + 88, BUTTON_WIDTH / 2 - 2, 20).build());
		regenerateButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.stops.regenerate"), button -> regenerate()).dimensions(buttonsX, TOP + 116, BUTTON_WIDTH, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());

		refreshScopes();
		refreshPlatforms();
	}

	private void refreshScopes() {
		final List<SimpleListWidget.Item<Scope>> items = new ArrayList<>();
		final Scope all = new Scope(Text.translatable("gui.aurelia_transit_utilities.dwell.scope_all").getString(), ALL);
		items.add(new SimpleListWidget.Item<>(Text.literal(all.label()), COLOR_TEXT, all));
		final List<Station> stations = new ArrayList<>(MtrBridge.dashboardData().stations);
		stations.sort(Comparator.comparing(station -> MtrBridge.displayName(station.getName()).toLowerCase(Locale.ROOT)));
		stations.forEach(station -> items.add(new SimpleListWidget.Item<>(Text.literal("■ " + MtrBridge.displayName(station.getName())), COLOR_TEXT, new Scope(MtrBridge.displayName(station.getName()), station))));
		final List<Route> routes = new ArrayList<>(MtrBridge.dashboardData().routes);
		routes.sort(Comparator.comparing(route -> MtrBridge.displayName(route.getName()).toLowerCase(Locale.ROOT)));
		routes.forEach(route -> items.add(new SimpleListWidget.Item<>(Text.literal("→ " + MtrBridge.displayName(route.getName())), COLOR_DIM, new Scope(MtrBridge.displayName(route.getName()), route))));
		scopeList.setItems(items, selectedScope);
	}

	private void selectScope(Scope scope) {
		selectedScope = scope;
		scopePlatforms.clear();
		ticked.clear();
		if (scope != null) {
			final Set<Platform> platforms = new LinkedHashSet<>();
			if (scope.source() == ALL) {
				platforms.addAll(MtrBridge.dashboardData().platforms);
			} else if (scope.source() instanceof Station station) {
				platforms.addAll(station.savedRails);
			} else if (scope.source() instanceof Route route) {
				// Route order, each platform once.
				for (final RoutePlatformData data : route.getRoutePlatforms()) {
					if (data.platform != null) {
						platforms.add(data.platform);
					}
				}
			}
			// Cable cars never stop, so MTR ignores their dwell time (MTR issue #1375).
			platforms.removeIf(platform -> platform.getTransportMode().continuousMovement);
			scopePlatforms.addAll(platforms);
			if (!(scope.source() instanceof Route)) {
				scopePlatforms.sort(Comparator.comparing(PlatformDwellScreen::stationName).thenComparing(platform -> platform));
			}
			ticked.addAll(scopePlatforms);
		}
		refreshPlatforms();
	}

	private void refreshPlatforms() {
		final List<SimpleListWidget.Item<Platform>> items = new ArrayList<>();
		for (final Platform platform : scopePlatforms) {
			final boolean isTicked = ticked.contains(platform);
			// Dwell first: long station names are trimmed at the right edge.
			final String label = (isTicked ? "[x] " : "[ ] ") + DwellTime.format(platform.getDwellTime()) + "   " + stationName(platform) + "  " + Text.translatable("gui.aurelia_transit_utilities.stops.platform", platform.getName()).getString();
			items.add(new SimpleListWidget.Item<>(Text.literal(label), isTicked ? COLOR_TEXT : COLOR_DIM, platform));
		}
		final double scroll = platformList.getScrollAmount();
		platformList.setItems(items, null);
		platformList.setScrollAmount(scroll);
		updateButtons();
	}

	private void updateButtons() {
		final boolean canEdit = MtrBridge.canEdit() && !ticked.isEmpty();
		setButton.active = canEdit && DwellTime.parseMillis(dwellField.getText()).isPresent();
		minusButton.active = canEdit;
		plusButton.active = canEdit;
		regenerateButton.active = MtrBridge.canEdit() && !depotsToRegenerate.isEmpty();
	}

	private void setDwell() {
		final OptionalLong millis = DwellTime.parseMillis(dwellField.getText());
		if (millis.isEmpty()) {
			setStatus(Text.translatable("gui.aurelia_transit_utilities.dwell.invalid"), COLOR_ERROR);
			return;
		}
		apply(current -> millis.getAsLong(), DwellTime.format(millis.getAsLong()));
	}

	private void apply(LongUnaryOperator change, String description) {
		if (!MtrBridge.canEdit() || ticked.isEmpty()) {
			return;
		}
		final List<Platform> changed = new ArrayList<>();
		for (final Platform platform : ticked) {
			final long newMillis = change.applyAsLong(platform.getDwellTime());
			if (newMillis != platform.getDwellTime()) {
				platform.setDwellTime(newMillis);
				changed.add(platform);
			}
		}
		if (changed.isEmpty()) {
			setStatus(Text.translatable("gui.aurelia_transit_utilities.dwell.unchanged"), COLOR_DIM);
			return;
		}
		// One request for all platforms, like MTR's own screens send one per edit.
		MtrBridge.send(request -> changed.forEach(request::addPlatform));
		changed.forEach(platform -> platform.routes.forEach(route -> depotsToRegenerate.addAll(route.depots)));
		setStatus(Text.translatable(depotsToRegenerate.isEmpty() ? "gui.aurelia_transit_utilities.dwell.applied_no_depot" : "gui.aurelia_transit_utilities.dwell.applied", changed.size(), description), COLOR_OK);
		refreshPlatforms();
	}

	private void regenerate() {
		if (!MtrBridge.canEdit() || depotsToRegenerate.isEmpty()) {
			return;
		}
		final int count = MtrBridge.regenerateDepots(depotsToRegenerate);
		depotsToRegenerate.clear();
		setStatus(Text.translatable("gui.aurelia_transit_utilities.stops.regenerating", count), COLOR_OK);
		updateButtons();
	}

	private static String stationName(Platform platform) {
		return platform.area == null ? Text.translatable("gui.aurelia_transit_utilities.stops.no_station").getString() : MtrBridge.displayName(platform.area.getName());
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
		final int leftWidth = Math.min(180, (width - MARGIN * 3) / 3);
		final int rightX = MARGIN * 2 + leftWidth;
		context.drawTextWithShadow(textRenderer, selectedScope == null
				? Text.translatable("gui.aurelia_transit_utilities.dwell.pick_scope")
				: Text.translatable("gui.aurelia_transit_utilities.dwell.ticked", ticked.size(), scopePlatforms.size()), rightX, TOP + 2, COLOR_DIM);
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 40, statusColor);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** For the dev self-test: same as clicking the scope in the list. */
	public void selectScopeForTest(Object source) {
		for (final SimpleListWidget.Row<Scope> row : scopeList.children()) {
			if (row.value().source() == source) {
				scopeList.setSelected(row);
				return;
			}
		}
	}

	/** For the dev self-test. */
	public TextFieldWidget dwellFieldForTest() {
		return dwellField;
	}

	/** For the dev self-test. */
	public Set<Platform> tickedForTest() {
		return ticked;
	}

	/** For the dev self-test: same as clicking that platform row. */
	public void clickPlatformForTest(Platform platform) {
		for (final SimpleListWidget.Row<Platform> row : platformList.children()) {
			if (row.value() == platform) {
				platformList.setSelected(row);
				return;
			}
		}
	}
}
