package com.aureliatransit.utilities.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

/** A plain selectable list of labelled values. */
public final class SimpleListWidget<T> extends AlwaysSelectedEntryListWidget<SimpleListWidget.Row<T>> {

	private final Consumer<T> onSelect;

	public SimpleListWidget(MinecraftClient client, int x, int y, int width, int height, Consumer<T> onSelect) {
		super(client, width, height, y, y + height, 12);
		setLeftPos(x);
		setRenderBackground(false);
		setRenderHorizontalShadows(false);
		this.onSelect = onSelect;
	}

	public record Item<T>(Text label, int color, T value) {
	}

	public void setItems(List<Item<T>> items, T selected) {
		clearEntries();
		Row<T> selectedRow = null;
		for (final Item<T> item : items) {
			final Row<T> row = new Row<>(this, item);
			addEntry(row);
			if (selected != null && selected.equals(item.value())) {
				selectedRow = row;
			}
		}
		super.setSelected(selectedRow);
		setScrollAmount(getScrollAmount());
	}

	public void scrollToSelected() {
		final Row<T> row = getSelectedOrNull();
		if (row != null) {
			ensureVisible(row);
		}
	}

	public T selectedValue() {
		final Row<T> row = getSelectedOrNull();
		return row == null ? null : row.item.value();
	}

	@Override
	public void setSelected(Row<T> row) {
		super.setSelected(row);
		onSelect.accept(row == null ? null : row.item.value());
	}

	@Override
	public int getRowWidth() {
		return width - 12;
	}

	@Override
	protected int getScrollbarPositionX() {
		return left + width - 6;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(left, top, left + width, bottom, 0x80000000);
		super.render(context, mouseX, mouseY, delta);
	}

	public static final class Row<T> extends AlwaysSelectedEntryListWidget.Entry<Row<T>> {

		private final SimpleListWidget<T> list;
		private final Item<T> item;

		private Row(SimpleListWidget<T> list, Item<T> item) {
			this.list = list;
			this.item = item;
		}

		@Override
		public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			final MinecraftClient client = MinecraftClient.getInstance();
			context.drawText(client.textRenderer, client.textRenderer.trimToWidth(item.label(), entryWidth - 4).getString(), x + 2, y + 1, item.color(), false);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 0) {
				list.setSelected(this);
				return true;
			}
			return false;
		}

		public T value() {
			return item.value();
		}

		@Override
		public Text getNarration() {
			return item.label();
		}
	}
}
