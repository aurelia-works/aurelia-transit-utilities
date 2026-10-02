package com.aureliatransit.utilities.client.screen;

import com.aureliatransit.utilities.route.StopOrder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.function.IntFunction;

/** A scrollable list whose rows can be dragged into a new order. Rows are labelled by their original index. */
public final class DragListWidget extends ClickableWidget {

	public static final int ROW_HEIGHT = 14;
	private static final int DRAG_THRESHOLD = 3;

	private StopOrder order = new StopOrder(0);
	private IntFunction<Text> labelForOriginalIndex = index -> Text.empty();
	private Runnable onChange = () -> {
	};
	private int selected = -1;
	private double scroll;
	private int pressedRow = -1;
	private double pressedY;
	private boolean dragging;
	private double dragY;

	public DragListWidget(int x, int y, int width, int height) {
		super(x, y, width, height, Text.empty());
	}

	public void setOrder(StopOrder order, IntFunction<Text> labelForOriginalIndex, Runnable onChange) {
		this.order = order;
		this.labelForOriginalIndex = labelForOriginalIndex;
		this.onChange = onChange;
		selected = -1;
		scroll = 0;
		dragging = false;
		pressedRow = -1;
	}

	public int getSelected() {
		return selected;
	}

	public void setSelected(int position) {
		selected = position;
		if (position >= 0) {
			final double rowTop = position * ROW_HEIGHT;
			if (rowTop < scroll) {
				scroll = rowTop;
			} else if (rowTop + ROW_HEIGHT > scroll + height) {
				scroll = rowTop + ROW_HEIGHT - height;
			}
		}
	}

	private double maxScroll() {
		return Math.max(0, order.size() * ROW_HEIGHT - height);
	}

	private int rowAt(double mouseY) {
		final int row = (int) Math.floor((mouseY - getY() + scroll) / ROW_HEIGHT);
		return row >= 0 && row < order.size() ? row : -1;
	}

	@Override
	protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final int left = getX();
		final int top = getY();
		context.fill(left, top, left + width, top + height, 0x80000000);
		context.enableScissor(left, top, left + width, top + height);
		for (int position = 0; position < order.size(); position++) {
			final int rowY = (int) (top + position * ROW_HEIGHT - scroll);
			if (rowY + ROW_HEIGHT < top || rowY > top + height) {
				continue;
			}
			final boolean isDragged = dragging && position == pressedRow;
			if (position == selected) {
				context.fill(left + 1, rowY, left + width - 1, rowY + ROW_HEIGHT, isDragged ? 0x40FFFFFF : 0x60FFFFFF);
			} else if (isMouseOver(mouseX, mouseY) && rowAt(mouseY) == position && !dragging) {
				context.fill(left + 1, rowY, left + width - 1, rowY + ROW_HEIGHT, 0x20FFFFFF);
			}
			drawRow(context, client, position, rowY, isDragged ? 0x808080 : 0xFFFFFF);
		}
		if (dragging) {
			final int insertion = StopOrder.insertionIndex(dragY, top - scroll, ROW_HEIGHT, order.size());
			final int lineY = (int) (top + insertion * ROW_HEIGHT - scroll);
			context.fill(left + 1, lineY - 1, left + width - 1, lineY + 1, 0xFF55FF55);
			final int ghostY = MathHelper.clamp((int) dragY - ROW_HEIGHT / 2, top, top + height - ROW_HEIGHT);
			context.fill(left + 1, ghostY, left + width - 1, ghostY + ROW_HEIGHT, 0xC0303030);
			drawRow(context, client, pressedRow, ghostY, 0x55FF55);
		}
		context.disableScissor();
		if (maxScroll() > 0) {
			final int barHeight = Math.max(8, height * height / (order.size() * ROW_HEIGHT));
			final int barY = top + (int) ((height - barHeight) * (scroll / maxScroll()));
			context.fill(left + width - 3, barY, left + width - 1, barY + barHeight, 0xFFA0A0A0);
		}
	}

	private void drawRow(DrawContext context, MinecraftClient client, int position, int rowY, int color) {
		final String number = (position + 1) + ".";
		context.drawText(client.textRenderer, number, getX() + 4, rowY + 3, 0xA0A0A0, false);
		final Text label = labelForOriginalIndex.apply(order.originalIndexAt(position));
		context.drawText(client.textRenderer, client.textRenderer.trimToWidth(label, width - 34).getString(), getX() + 26, rowY + 3, color, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) {
			return false;
		}
		final int row = rowAt(mouseY);
		selected = row;
		pressedRow = row;
		pressedY = mouseY;
		dragging = false;
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (pressedRow < 0 || button != 0) {
			return false;
		}
		if (!dragging && Math.abs(mouseY - pressedY) >= DRAG_THRESHOLD) {
			dragging = true;
		}
		if (dragging) {
			dragY = mouseY;
			// Auto-scroll when dragging past the edges.
			if (mouseY < getY()) {
				scroll = MathHelper.clamp(scroll - 4, 0, maxScroll());
			} else if (mouseY > getY() + height) {
				scroll = MathHelper.clamp(scroll + 4, 0, maxScroll());
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (pressedRow < 0 || button != 0) {
			return false;
		}
		if (dragging) {
			final int newPosition = order.move(pressedRow, StopOrder.insertionIndex(mouseY, getY() - scroll, ROW_HEIGHT, order.size()));
			if (newPosition >= 0) {
				selected = newPosition;
				onChange.run();
			}
		}
		dragging = false;
		pressedRow = -1;
		return true;
	}

	/** Moves the selected row by {@code delta} positions (buttons and arrow keys). */
	public void moveSelected(int delta) {
		if (selected < 0) {
			return;
		}
		final int newPosition = order.move(selected, delta < 0 ? selected + delta : selected + delta + 1);
		if (newPosition >= 0) {
			setSelected(newPosition);
			onChange.run();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!isMouseOver(mouseX, mouseY)) {
			return false;
		}
		scroll = MathHelper.clamp(scroll - amount * ROW_HEIGHT * 2, 0, maxScroll());
		return true;
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
	}

	/** For tests and keyboard use: the labels in the current order. */
	public List<Integer> currentOriginalIndices() {
		final java.util.ArrayList<Integer> result = new java.util.ArrayList<>();
		for (int i = 0; i < order.size(); i++) {
			result.add(order.originalIndexAt(i));
		}
		return result;
	}
}
