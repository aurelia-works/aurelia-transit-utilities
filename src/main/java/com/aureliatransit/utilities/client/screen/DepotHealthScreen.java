package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.depot.DepotHealth;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.mtr.core.data.Depot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every depot's last path generation result plus setup mistakes, worst first. MTR's own depot screen shows this for one
 * depot at a time; here it is the whole network. Reads data only when opened, on Refresh and after Regenerate.
 */
public final class DepotHealthScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int BUTTON_WIDTH = 110;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_WARN = 0xFFAA00;
	private static final int COLOR_ERROR = 0xFF5555;

	private final Screen parent;
	private SimpleListWidget<DepotHealth.DepotInfo> depotList;
	private ButtonWidget regenerateOneButton;
	private ButtonWidget regenerateAllButton;

	private final Map<Long, List<DepotHealth.Problem>> problems = new LinkedHashMap<>();
	private DepotHealth.DepotInfo selected;
	private int errorDepots;
	private Text status = Text.empty();
	private int statusColor = COLOR_DIM;

	public DepotHealthScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.depots.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		final int listBottom = height - 58;
		depotList = addDrawableChild(new SimpleListWidget<>(client, MARGIN, TOP, half, listBottom - TOP, depot -> {
			selected = depot;
			updateButtons();
		}));
		regenerateOneButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.depots.regenerate_one"), button -> regenerate(false)).dimensions(rightX, listBottom - 44, half, 20).build());
		regenerateAllButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.depots.regenerate_all"), button -> regenerate(true)).dimensions(rightX, listBottom - 20, half - BUTTON_WIDTH / 2 - 4, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.depots.refresh"), button -> refresh()).dimensions(rightX + half - BUTTON_WIDTH / 2, listBottom - 20, BUTTON_WIDTH / 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());
		refresh();
	}

	private void refresh() {
		final long keep = selected == null ? 0 : selected.id();
		final List<DepotHealth.DepotInfo> depots = new ArrayList<>();
		MtrBridge.dashboardData().depots.forEach(depot -> depots.add(MtrBridge.depotInfo(depot)));
		depots.sort(DepotHealth.worstFirst());
		problems.clear();
		errorDepots = 0;
		final List<SimpleListWidget.Item<DepotHealth.DepotInfo>> items = new ArrayList<>();
		DepotHealth.DepotInfo reselect = null;
		for (final DepotHealth.DepotInfo depot : depots) {
			final List<DepotHealth.Problem> depotProblems = DepotHealth.check(depot);
			problems.put(depot.id(), depotProblems);
			final int rank = DepotHealth.rank(depotProblems);
			if (rank == 0) {
				errorDepots++;
			}
			final String mark = rank == 0 ? "✖ " : rank == 1 ? "! " : "✔ ";
			items.add(new SimpleListWidget.Item<>(Text.literal(mark + depot.name()), rank == 0 ? COLOR_ERROR : rank == 1 ? COLOR_WARN : COLOR_OK, depot));
			if (depot.id() == keep) {
				reselect = depot;
			}
		}
		selected = reselect;
		depotList.setItems(items, reselect);
		updateButtons();
	}

	private void updateButtons() {
		regenerateOneButton.active = MtrBridge.canEdit() && selected != null;
		regenerateAllButton.active = MtrBridge.canEdit() && errorDepots > 0;
	}

	/** Same request as the Generate button in MTR's depot screen; only on click. */
	private void regenerate(boolean allWithErrors) {
		if (!MtrBridge.canEdit()) {
			return;
		}
		final List<Depot> depots = new ArrayList<>();
		if (allWithErrors) {
			MtrBridge.dashboardData().depots.forEach(depot -> {
				if (DepotHealth.rank(problems.getOrDefault(depot.getId(), List.of())) == 0) {
					depots.add(depot);
				}
			});
		} else if (selected != null) {
			final Depot depot = MtrBridge.dashboardData().depotIdMap.get(selected.id());
			if (depot != null) {
				depots.add(depot);
			}
		}
		final int count = MtrBridge.regenerateDepots(depots);
		status = Text.translatable("gui.aurelia_transit_utilities.depots.regenerating", count);
		statusColor = COLOR_OK;
	}

	private static Text describe(DepotHealth.Problem problem) {
		final String key = "gui.aurelia_transit_utilities.depots.problem." + problem.key();
		if (problem.key().equals("path_between")) {
			return Text.translatable(key, MtrBridge.platformLabel((Long) problem.args().get(0)), MtrBridge.platformLabel((Long) problem.args().get(1)));
		}
		return Text.translatable(key, problem.args().toArray());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, COLOR_TEXT);
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		if (problems.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.depots.none"), MARGIN + half / 2, height / 2, COLOR_DIM);
		}
		if (selected == null) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.depots.summary", problems.size(), errorDepots), rightX, TOP + 2, COLOR_DIM);
		} else {
			context.drawTextWithShadow(textRenderer, Text.literal(selected.name()), rightX, TOP + 2, COLOR_TEXT);
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.depots.counts", selected.routeCount(), selected.sidingCount()), rightX, TOP + 14, COLOR_DIM);
			int y = TOP + 30;
			final List<DepotHealth.Problem> depotProblems = problems.getOrDefault(selected.id(), List.of());
			if (depotProblems.isEmpty()) {
				context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.depots.healthy"), rightX, y, COLOR_OK);
			}
			for (final DepotHealth.Problem problem : depotProblems) {
				if (y > height - 110) {
					break;
				}
				final int color = problem.severity() == DepotHealth.Severity.ERROR ? COLOR_ERROR : COLOR_WARN;
				for (final var line : textRenderer.wrapLines(describe(problem), half)) {
					context.drawTextWithShadow(textRenderer, line, rightX, y, color);
					y += 10;
				}
				y += 3;
			}
		}
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 40, statusColor);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** For the dev self-test. */
	public Map<Long, List<DepotHealth.Problem>> problemsForTest() {
		return problems;
	}

	/** For the dev self-test: same as clicking the depot row. */
	public void selectDepotForTest(long depotId) {
		for (final SimpleListWidget.Row<DepotHealth.DepotInfo> row : depotList.children()) {
			if (row.value().id() == depotId) {
				depotList.setSelected(row);
				return;
			}
		}
	}
}
