package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.jump.JumpTarget;
import com.aureliatransit.utilities.overlap.Point;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Position;
import org.mtr.core.data.SavedRailBase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Jump to any station, platform, depot or siding (MTR #1112). Sends a normal /tp command, so the server decides who may
 * use it (operators / cheats on), exactly as if the player typed it.
 */
public final class JumpScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_ERROR = 0xFF5555;

	/** A list row: what to show and the command that gets there. */
	private record Target(String label, JumpTarget.Kind kind, String command) {
	}

	private final Screen parent;
	private TextFieldWidget search;
	private SimpleListWidget<Target> list;
	private ButtonWidget jumpButton;
	private final List<Target> targets = new ArrayList<>();
	private Target selected;

	public JumpScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.jump.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int listWidth = Math.min(320, width - MARGIN * 2);
		final int x = (width - listWidth) / 2;
		search = addDrawableChild(new TextFieldWidget(textRenderer, x, TOP, listWidth, 16, Text.translatable("gui.aurelia_transit_utilities.jump.search")));
		search.setPlaceholder(Text.translatable("gui.aurelia_transit_utilities.jump.search"));
		search.setChangedListener(text -> refresh());
		list = addDrawableChild(new SimpleListWidget<>(client, x, TOP + 20, listWidth, height - 84 - TOP, target -> {
			selected = target;
			jumpButton.active = target != null && canTeleport();
		}));
		jumpButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.jump.go"), button -> jump()).dimensions(x, height - 56, listWidth, 20).build());
		jumpButton.active = false;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());
		collect();
		refresh();
		setInitialFocus(search);
	}

	private boolean canTeleport() {
		return client.player != null && client.player.hasPermissionLevel(2);
	}

	/** Built once per open. Stations and depots jump onto their first platform or siding. */
	private void collect() {
		targets.clear();
		final var data = MtrBridge.dashboardData();
		addAreas(new ArrayList<>(data.stations), JumpTarget.Kind.STATION, "■ ");
		addAreas(new ArrayList<>(data.depots), JumpTarget.Kind.DEPOT, "⌂ ");
		data.platforms.forEach(platform -> addRail(platform, JumpTarget.Kind.PLATFORM, Text.translatable("gui.aurelia_transit_utilities.stops.platform", platform.getName()).getString() + (platform.area == null ? "" : " — " + MtrBridge.displayName(platform.area.getName()))));
		data.sidings.forEach(siding -> addRail(siding, JumpTarget.Kind.SIDING, Text.translatable("gui.aurelia_transit_utilities.height.siding", siding.getName()).getString() + (siding.area == null ? "" : " — " + MtrBridge.displayName(siding.area.getName()))));
		targets.sort(Comparator.comparing((Target target) -> target.kind().ordinal()).thenComparing(target -> target.label().toLowerCase(Locale.ROOT)));
	}

	private void addAreas(List<? extends AreaBase<?, ?>> areas, JumpTarget.Kind kind, String prefix) {
		for (final AreaBase<?, ?> area : areas) {
			final List<Point> mids = new ArrayList<>();
			area.savedRails.forEach(rail -> mids.add(point(rail.getMidPosition())));
			final Optional<Point> standOn = JumpTarget.standOn(mids);
			final String command;
			if (standOn.isPresent()) {
				command = JumpTarget.command(standOn.get());
			} else if (MtrBridge.validCorners(area)) {
				final Position center = area.getCenter();
				command = JumpTarget.commandKeepingHeight(center.getX(), center.getZ());
			} else {
				continue;
			}
			targets.add(new Target(prefix + MtrBridge.displayName(area.getName()), kind, command));
		}
	}

	private void addRail(SavedRailBase<?, ?> rail, JumpTarget.Kind kind, String label) {
		JumpTarget.standOn(List.of(point(rail.getMidPosition()))).ifPresent(point -> targets.add(new Target("   " + label, kind, JumpTarget.command(point))));
	}

	private static Point point(Position position) {
		return new Point(position.getX(), position.getY(), position.getZ());
	}

	private void refresh() {
		final List<SimpleListWidget.Item<Target>> items = new ArrayList<>();
		for (final Target target : targets) {
			if (JumpTarget.matches(target.label(), search.getText())) {
				final boolean area = target.kind() == JumpTarget.Kind.STATION || target.kind() == JumpTarget.Kind.DEPOT;
				items.add(new SimpleListWidget.Item<>(Text.literal(target.label()), area ? COLOR_TEXT : COLOR_DIM, target));
			}
		}
		list.setItems(items, selected);
	}

	private void jump() {
		if (selected == null || client.player == null || !canTeleport()) {
			return;
		}
		client.player.networkHandler.sendCommand(selected.command());
		close();
		client.setScreen(null);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, COLOR_TEXT);
		if (!canTeleport()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.jump.no_permission"), width / 2, height - 34, COLOR_ERROR);
		} else if (targets.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.jump.none"), width / 2, height / 2, COLOR_DIM);
		}
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** For the dev self-test: select the first target whose label contains {@code text}. */
	public boolean selectForTest(String text) {
		for (final SimpleListWidget.Row<Target> row : list.children()) {
			if (row.value().label().contains(text)) {
				list.setSelected(row);
				return true;
			}
		}
		return false;
	}

	/** For the dev self-test. */
	public String selectedCommandForTest() {
		return selected == null ? null : selected.command();
	}
}
