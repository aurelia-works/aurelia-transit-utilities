package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.PresetStore;
import com.aureliatransit.utilities.preset.PresetCheck;
import com.aureliatransit.utilities.preset.PresetLibrary;
import com.aureliatransit.utilities.preset.TrainPreset;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Position;
import org.mtr.core.data.Siding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Save a siding's train as a named preset, or apply a preset to a siding. */
public final class TrainPresetScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int TOP = 32;
	private static final int COLOR_TEXT = 0xFFFFFF;
	private static final int COLOR_DIM = 0xA0A0A0;
	private static final int COLOR_OK = 0x55FF55;
	private static final int COLOR_ERROR = 0xFF5555;
	/** Pre-select the closest siding when the player stands this close to it (blocks). */
	private static final double NEAR_SIDING_DISTANCE = 32;

	private final Screen parent;
	private TextFieldWidget sidingSearch;
	private TextFieldWidget presetName;
	private SimpleListWidget<Siding> sidingList;
	private SimpleListWidget<TrainPreset> presetList;
	private ButtonWidget saveButton;
	private ButtonWidget applyButton;
	private ButtonWidget applyDepotButton;
	private ButtonWidget deleteButton;
	private boolean includeSettings = true;

	private Siding selectedSiding;
	private double selectedSidingTrainLength;
	private TrainPreset selectedPreset;
	private Text status = Text.empty();
	private int statusColor = COLOR_DIM;

	public TrainPresetScreen(Screen parent) {
		super(Text.translatable("gui.aurelia_transit_utilities.presets.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int half = (width - MARGIN * 3) / 2;
		final int leftX = MARGIN;
		final int rightX = MARGIN * 2 + half;
		final int listBottom = height - 84;

		sidingSearch = addDrawableChild(new TextFieldWidget(textRenderer, leftX, TOP, half, 16, Text.translatable("gui.aurelia_transit_utilities.presets.search")));
		sidingSearch.setPlaceholder(Text.translatable("gui.aurelia_transit_utilities.presets.search"));
		sidingSearch.setChangedListener(text -> refreshSidings());
		sidingList = addDrawableChild(new SimpleListWidget<>(client, leftX, TOP + 20, half, listBottom - TOP - 20, siding -> {
			selectedSiding = siding;
			refreshPresets();
		}));

		presetList = addDrawableChild(new SimpleListWidget<>(client, rightX, TOP + 36, half, listBottom - TOP - 36, preset -> {
			selectedPreset = preset;
			if (preset != null) {
				presetName.setText(preset.name());
			}
			updateButtons();
		}));

		presetName = addDrawableChild(new TextFieldWidget(textRenderer, leftX, listBottom + 6, half, 16, Text.translatable("gui.aurelia_transit_utilities.presets.name")));
		presetName.setMaxLength(48);
		presetName.setPlaceholder(Text.translatable("gui.aurelia_transit_utilities.presets.name"));
		presetName.setChangedListener(text -> updateButtons());
		final int halfWidth = (half - 4) / 2;
		saveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.presets.save"), button -> save()).dimensions(rightX, listBottom + 4, halfWidth, 20).build());
		final ButtonWidget settingsButton = addDrawableChild(ButtonWidget.builder(settingsLabel(), button -> {
			includeSettings = !includeSettings;
			button.setMessage(settingsLabel());
		}).dimensions(rightX + halfWidth + 4, listBottom + 4, half - halfWidth - 4, 20).build());
		settingsButton.setTooltip(Tooltip.of(Text.translatable("gui.aurelia_transit_utilities.presets.settings.tooltip")));
		final int thirdWidth = (half - 8) / 3;
		applyButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.presets.apply"), button -> apply()).dimensions(rightX, listBottom + 28, thirdWidth, 20).build());
		applyDepotButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.presets.apply_depot"), button -> applyToDepot()).dimensions(rightX + thirdWidth + 4, listBottom + 28, thirdWidth, 20).build());
		applyDepotButton.setTooltip(Tooltip.of(Text.translatable("gui.aurelia_transit_utilities.presets.apply_depot.tooltip")));
		deleteButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia_transit_utilities.presets.delete"), button -> delete()).dimensions(rightX + (thirdWidth + 4) * 2, listBottom + 28, half - (thirdWidth + 4) * 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(width / 2 - 75, height - 26, 150, 20).build());

		if (selectedSiding == null) {
			selectedSiding = findNearbySiding();
		}
		refreshSidings();
		refreshPresets();
		if (PresetStore.lastError() != null) {
			setStatus(Text.literal(PresetStore.lastError()), COLOR_ERROR);
		}
	}

	private Text settingsLabel() {
		return Text.translatable("gui.aurelia_transit_utilities.presets.settings", ScreenTexts.onOrOff(includeSettings));
	}

	private Siding findNearbySiding() {
		if (client == null || client.player == null) {
			return null;
		}
		final BlockPos blockPos = client.player.getBlockPos();
		final Position position = new Position(blockPos.getX(), blockPos.getY(), blockPos.getZ());
		Siding closest = null;
		double closestDistance = NEAR_SIDING_DISTANCE;
		for (final Siding siding : MtrBridge.dashboardData().sidings) {
			final double distance = siding.getApproximateClosestDistance(position, MtrBridge.dashboardData());
			if (distance < closestDistance) {
				closestDistance = distance;
				closest = siding;
			}
		}
		return closest;
	}

	private void refreshSidings() {
		final String query = sidingSearch.getText().toLowerCase(Locale.ROOT).strip();
		final List<Siding> sidings = new ArrayList<>(MtrBridge.dashboardData().sidings);
		sidings.sort(Comparator.comparing((Siding siding) -> depotName(siding).toLowerCase(Locale.ROOT)).thenComparing(siding -> siding));
		final List<SimpleListWidget.Item<Siding>> items = new ArrayList<>();
		for (final Siding siding : sidings) {
			final String label = sidingLabel(siding);
			if (query.isEmpty() || label.toLowerCase(Locale.ROOT).contains(query) || siding == selectedSiding) {
				items.add(new SimpleListWidget.Item<>(Text.literal(label), siding.getVehicleCars().isEmpty() ? COLOR_DIM : COLOR_TEXT, siding));
			}
		}
		sidingList.setItems(items, selectedSiding);
		if (selectedSiding != null) {
			sidingList.scrollToSelected();
		}
	}

	private void refreshPresets() {
		selectedSidingTrainLength = selectedSiding == null ? 0 : MtrBridge.toPreset("", selectedSiding).totalLength();
		final List<SimpleListWidget.Item<TrainPreset>> items = new ArrayList<>();
		if (selectedSiding != null) {
			for (final TrainPreset preset : PresetStore.library().forMode(selectedSiding.getTransportMode().name())) {
				final boolean fits = preset.totalLength() <= selectedSiding.getRailLength() + 1e-6;
				final Text label = Text.translatable("gui.aurelia_transit_utilities.presets.entry", preset.name(), preset.cars().size(), formatLength(preset.totalLength()));
				items.add(new SimpleListWidget.Item<>(preset.settings() == null ? label : label.copy().append(Text.translatable("gui.aurelia_transit_utilities.presets.with_settings")), fits ? COLOR_TEXT : COLOR_DIM, preset));
			}
		}
		if (selectedPreset != null && items.stream().noneMatch(item -> item.value().equals(selectedPreset))) {
			selectedPreset = null;
		}
		presetList.setItems(items, selectedPreset);
		updateButtons();
	}

	private void updateButtons() {
		final boolean canEdit = MtrBridge.canEdit();
		saveButton.active = selectedSiding != null && !selectedSiding.getVehicleCars().isEmpty() && !presetName.getText().isBlank();
		applyButton.active = canEdit && selectedSiding != null && selectedPreset != null;
		applyDepotButton.active = applyButton.active && selectedSiding.area != null;
		deleteButton.active = selectedPreset != null;
	}

	private void save() {
		if (selectedSiding == null) {
			return;
		}
		final TrainPreset full = MtrBridge.toPreset(presetName.getText(), selectedSiding);
		final TrainPreset preset = includeSettings ? full : full.withoutSettings();
		final PresetLibrary.PutResult result = PresetStore.library().put(preset);
		switch (result) {
			case ADDED, REPLACED -> {
				if (PresetStore.save()) {
					setStatus(Text.translatable(result == PresetLibrary.PutResult.ADDED ? "gui.aurelia_transit_utilities.presets.saved" : "gui.aurelia_transit_utilities.presets.replaced", preset.name()), COLOR_OK);
				} else {
					setStatus(Text.literal(PresetStore.lastError()), COLOR_ERROR);
				}
				selectedPreset = PresetStore.library().get(preset.name());
				refreshPresets();
			}
			default -> setStatus(Text.translatable("gui.aurelia_transit_utilities.presets.put." + result.name().toLowerCase(Locale.ROOT)), COLOR_ERROR);
		}
	}

	private void apply() {
		if (selectedSiding == null || selectedPreset == null || !MtrBridge.canEdit()) {
			return;
		}
		final List<PresetCheck.Problem> problems = PresetCheck.check(selectedPreset, selectedSiding.getTransportMode().name(), selectedSiding.getRailLength(), MtrBridge.knownVehicleIds(selectedSiding.getTransportMode()));
		if (!problems.isEmpty()) {
			setStatus(describe(problems.get(0)), COLOR_ERROR);
			return;
		}
		MtrBridge.applyPreset(selectedPreset, selectedSiding);
		setStatus(Text.translatable("gui.aurelia_transit_utilities.presets.applied", selectedPreset.name(), selectedSiding.getName()), COLOR_OK);
		refreshSidings();
		refreshPresets();
	}

	/** The preset on every siding of the selected siding's depot (same transport mode) that passes the checks. */
	private void applyToDepot() {
		if (selectedSiding == null || selectedPreset == null || selectedSiding.area == null || !MtrBridge.canEdit()) {
			return;
		}
		final Set<String> known = MtrBridge.knownVehicleIds(selectedSiding.getTransportMode());
		final List<Siding> targets = new ArrayList<>();
		int skipped = 0;
		Text firstProblem = null;
		for (final Siding siding : selectedSiding.area.savedRails) {
			final List<PresetCheck.Problem> problems = PresetCheck.check(selectedPreset, siding.getTransportMode().name(), siding.getRailLength(), known);
			if (problems.isEmpty()) {
				targets.add(siding);
			} else {
				skipped++;
				if (firstProblem == null) {
					firstProblem = Text.literal(siding.getName() + ": ").append(describe(problems.get(0)));
				}
			}
		}
		if (targets.isEmpty()) {
			setStatus(firstProblem == null ? Text.translatable("gui.aurelia_transit_utilities.presets.no_sidings") : firstProblem, COLOR_ERROR);
			return;
		}
		MtrBridge.applyPreset(selectedPreset, targets);
		final Text applied = Text.translatable("gui.aurelia_transit_utilities.presets.applied_depot", selectedPreset.name(), targets.size(), MtrBridge.displayName(selectedSiding.area.getName()));
		setStatus(skipped == 0 ? applied : applied.copy().append(" ").append(Text.translatable("gui.aurelia_transit_utilities.presets.skipped", skipped)), skipped == 0 ? COLOR_OK : COLOR_ERROR);
		refreshSidings();
		refreshPresets();
	}

	private void delete() {
		if (selectedPreset == null) {
			return;
		}
		final String name = selectedPreset.name();
		PresetStore.library().remove(name);
		selectedPreset = null;
		if (PresetStore.save()) {
			setStatus(Text.translatable("gui.aurelia_transit_utilities.presets.deleted", name), COLOR_OK);
		} else {
			setStatus(Text.literal(PresetStore.lastError()), COLOR_ERROR);
		}
		refreshPresets();
	}

	private static Text describe(PresetCheck.Problem problem) {
		if (problem instanceof PresetCheck.WrongMode wrongMode) {
			return Text.translatable("gui.aurelia_transit_utilities.presets.problem.mode", wrongMode.presetMode(), wrongMode.sidingMode());
		} else if (problem instanceof PresetCheck.MissingVehicles missingVehicles) {
			return Text.translatable("gui.aurelia_transit_utilities.presets.problem.missing", String.join(", ", missingVehicles.vehicleIds()));
		} else if (problem instanceof PresetCheck.TooLong tooLong) {
			return Text.translatable("gui.aurelia_transit_utilities.presets.problem.length", formatLength(tooLong.trainLength()), formatLength(tooLong.railLength()));
		}
		return Text.literal(problem.toString());
	}

	private void setStatus(Text text, int color) {
		status = text;
		statusColor = color;
	}

	private static String depotName(Siding siding) {
		final String depotName = siding.getDepotName();
		return depotName == null ? "" : depotName;
	}

	private static String sidingLabel(Siding siding) {
		final String depotName = depotName(siding);
		return (depotName.isEmpty() ? "?" : depotName) + " / " + siding.getName() + " (" + siding.getTransportMode().name().toLowerCase(Locale.ROOT) + ", " + siding.getVehicleCars().size() + ")";
	}

	private static String formatLength(double length) {
		return String.format(Locale.ROOT, "%.1f m", length);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, COLOR_TEXT);
		final int half = (width - MARGIN * 3) / 2;
		final int rightX = MARGIN * 2 + half;
		if (selectedSiding == null) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.presets.pick_siding"), rightX, TOP + 4, COLOR_DIM);
		} else {
			final List<org.mtr.core.data.VehicleCar> cars = selectedSiding.getVehicleCars();
			context.drawTextWithShadow(textRenderer, Text.literal(selectedSiding.getName()), rightX, TOP, COLOR_TEXT);
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.presets.current", cars.size(), formatLength(selectedSidingTrainLength), formatLength(selectedSiding.getRailLength())), rightX, TOP + 12, COLOR_DIM);
		}
		if (MtrBridge.dashboardData().sidings.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia_transit_utilities.presets.no_sidings"), MARGIN + half / 2, height / 2, COLOR_DIM);
		}
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 38, statusColor);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
