package dev.michidk.voxvelo.fitnesslib.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The scrolling list of a {@link SettingsScreen}: the vanilla options list, plus rows that vanilla has no entry for,
 * lines of live text and rows that mix widgets at free positions with text drawn beside them.
 */
public final class SettingsList extends OptionsList {
	public static final int WIDTH = 310;
	/** Width of one of two widgets sharing a row, as in the vanilla options screens. */
	public static final int HALF = 150;
	private static final int ROW_HEIGHT = 25;
	private static final int TEXT_HEIGHT = 12;
	private static final int LINE_HEIGHT = 10;

	/** Draws text into a row, given the row's left and top edge; widgets are 20 high, so text sits at {@code top + 6}. */
	@FunctionalInterface
	public interface RowText {
		void draw(GuiGraphicsExtractor graphics, Font font, int left, int top);
	}

	public SettingsList(Minecraft minecraft, int width, OptionsSubScreen screen) {
		super(minecraft, width, screen);
	}

	/**
	 * Centred text that is read again every frame, so it can show a live status. It wraps to the list's width; the
	 * row is as high as the text first is, so text that later grows longer is cut off rather than overlapping.
	 */
	public void addText(Supplier<Component> text, int color) {
		Component first = text.get();
		int lines = first == null ? 1 : Math.max(1, this.minecraft.font.split(first, WIDTH).size());
		this.addEntry(new Row(List.of(), (graphics, font, left, top) -> {
			Component current = text.get();
			if (current == null) {
				return;
			}
			List<FormattedCharSequence> parts = font.split(current, WIDTH);
			for (int i = 0; i < Math.min(lines, parts.size()); i++) {
				graphics.centeredText(font, parts.get(i), left + WIDTH / 2, top + 2 + i * LINE_HEIGHT, color);
			}
		}), TEXT_HEIGHT + (lines - 1) * LINE_HEIGHT);
	}

	/** A row of text only, such as a pair of stats. */
	public void addText(RowText text) {
		this.addEntry(new Row(List.of(), text), TEXT_HEIGHT);
	}

	/** Widgets placed at their x as an offset from the row's left edge, with optional text drawn beside them. */
	public void addRow(List<? extends AbstractWidget> widgets, @Nullable RowText text) {
		this.addEntry(new Row(widgets, text), ROW_HEIGHT);
	}

	public void addRow(AbstractWidget... widgets) {
		this.addRow(List.of(widgets), null);
	}

	private static final class Row extends OptionsList.AbstractEntry {
		private final List<AbstractWidget> widgets;
		private final int[] offsets;
		private final @Nullable RowText text;

		Row(List<? extends AbstractWidget> widgets, @Nullable RowText text) {
			this.widgets = new ArrayList<>(widgets);
			this.offsets = widgets.stream().mapToInt(AbstractWidget::getX).toArray();
			this.text = text;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			int left = this.getContentXMiddle() - WIDTH / 2;
			int top = this.getContentY();
			if (this.text != null) {
				this.text.draw(graphics, Minecraft.getInstance().font, left, top);
			}
			for (int i = 0; i < this.widgets.size(); i++) {
				AbstractWidget widget = this.widgets.get(i);
				widget.setPosition(left + this.offsets[i], top);
				widget.extractRenderState(graphics, mouseX, mouseY, a);
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return this.widgets;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return this.widgets;
		}
	}
}
