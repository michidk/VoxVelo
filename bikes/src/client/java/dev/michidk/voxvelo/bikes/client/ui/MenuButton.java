package dev.michidk.voxvelo.bikes.client.ui;

import dev.michidk.voxvelo.bikes.VoxVelo;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A "VoxVelo" button on the pause menu and the options menu that opens the settings hub.
 *
 * <p>To live next to other mods it never uses fixed coordinates for the normal case: it is placed against vanilla
	 * buttons found by their label or position, above "Save and Quit to Title" on the pause menu and beside "Done" in
	 * the options footer. The listener runs after the default phase, so buttons added by other mods already exist.
 */
public final class MenuButton {
	private static final Identifier PHASE = VoxVelo.id("menu_button");
	private static final int HEIGHT = 20;
	private static final int GAP = 4;
	private static final int MAX_SHIFTS = 6;
	/** Buttons this class added, held weakly so closed screens can be collected. */
	private static final Set<Button> ADDED = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<AbstractWidget, MovedButton> MOVED = new WeakHashMap<>();
	private static final Map<AbstractWidget, Footer> FOOTERS = new WeakHashMap<>();
	private record Footer(Slot original, Slot resized) {}

	private MenuButton() {
	}

	/** Remembers the quit button's position across repeated layout passes. */
	private record MovedButton(int originalY, int movedY) {
	}

	/** Where the button goes: left edge, top edge and width. */
	private record Slot(int x, int y, int width) {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
		ScreenEvents.AFTER_INIT.register(PHASE, (client, screen, width, height) -> {
			// AFTER_INIT also fires when a screen is only repositioned (e.g. on resize), which keeps its widgets, so
			// drop the button from the previous pass before placing it again.
			if (screen instanceof PauseScreen || screen instanceof OptionsScreen) {
				for (AbstractWidget widget : Screens.getWidgets(screen)) {
					Footer footer = FOOTERS.remove(widget);
					if (footer != null && widget.getX() == footer.resized().x() && widget.getY() == footer.resized().y()
						&& widget.getWidth() == footer.resized().width()) {
						widget.setWidth(footer.original().width());
					}
					MovedButton moved = MOVED.remove(widget);
					if (moved != null && widget.getY() == moved.movedY()) {
						widget.setY(moved.originalY());
					}
				}
				Screens.getWidgets(screen).removeIf(ADDED::contains);
			}
			if (screen instanceof PauseScreen) {
				addBeforeQuit(client, screen, CommonComponents.disconnectButtonLabel(client.isLocalServer()));
			} else if (screen instanceof OptionsScreen) {
				addToOptions(client, screen);
			}
		});
	}

	/** Takes the quit button's row and moves quitting to the next free row below it. */
	private static void addBeforeQuit(Minecraft client, Screen screen, Component anchorLabel) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		AbstractWidget anchor = find(widgets, anchorLabel);
		Slot slot = anchor == null ? null : findSlot(screen, widgets, anchor.getX(), anchor.getY() + anchor.getHeight() + GAP, anchor.getWidth(), 1);
		if (slot != null) {
			int originalY = anchor.getY();
			anchor.setY(slot.y());
			MOVED.put(anchor, new MovedButton(originalY, slot.y()));
			slot = new Slot(anchor.getX(), originalY, anchor.getWidth());
		}
		place(client, screen, widgets, slot);
	}

	/** Uses a full grid row when it fits, otherwise shares the footer with Done in short windows. */
	private static void addToOptions(Minecraft client, Screen screen) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		AbstractWidget done = find(widgets, CommonComponents.GUI_DONE);
		int bottom = widgets.stream().filter(widget -> widget != done && widget.visible)
			.mapToInt(widget -> widget.getY() + widget.getHeight()).max().orElse(0);
		List<AbstractWidget> lastRow = widgets.stream().filter(widget -> widget != done && widget.visible
			&& widget.getY() + widget.getHeight() == bottom).toList();
		if (!lastRow.isEmpty()) {
			int left = lastRow.stream().mapToInt(AbstractWidget::getX).min().orElse(0);
			int right = lastRow.stream().mapToInt(widget -> widget.getX() + widget.getWidth()).max().orElse(0);
			int y = bottom + GAP;
			if (y + HEIGHT + GAP <= (done == null ? screen.height : done.getY()) && isFree(widgets, left, y, right - left)) {
				place(client, screen, widgets, new Slot(left, y, right - left));
				return;
			}
		}
		if (done == null) {
			place(client, screen, widgets, null);
			return;
		}
		Slot original = new Slot(done.getX(), done.getY(), done.getWidth());
		int half = (original.width() - GAP) / 2;
		Slot resized = new Slot(original.x(), original.y(), half);
		done.setWidth(half);
		FOOTERS.put(done, new Footer(original, resized));
		place(client, screen, widgets, new Slot(original.x() + half + GAP, original.y(), original.width() - half - GAP));
	}
	private static AbstractWidget find(List<AbstractWidget> widgets, Component label) {
		for (AbstractWidget widget : widgets) {
			if (widget instanceof Button && widget.getMessage().getString().equals(label.getString())) {
				return widget;
			}
		}
		return null;
	}

	/** Starts at {@code startY} and moves one row at a time in {@code direction} (1 down, -1 up) until the spot is free. */
	private static Slot findSlot(Screen screen, List<AbstractWidget> widgets, int x, int startY, int width, int direction) {
		int step = direction * (HEIGHT + GAP);
		int y = startY;
		for (int shift = 0; shift <= MAX_SHIFTS; shift++, y += step) {
			if (y >= GAP && y + HEIGHT <= screen.height - GAP && isFree(widgets, x, y, width)) {
				return new Slot(x, y, width);
			}
		}
		return null;
	}

	private static void place(Minecraft client, Screen screen, List<AbstractWidget> widgets, Slot slot) {
		if (slot == null) {
			slot = new Slot(6, 6, 90);
		}
		Button button = Button.builder(Component.translatable("voxvelo_bikes.menu.button"), pressed -> client.gui.setScreen(new ConfigScreen(screen)))
			.tooltip(Tips.of("voxvelo_bikes.menu.button"))
			.bounds(slot.x(), slot.y(), slot.width(), HEIGHT)
			.build();
		ADDED.add(button);
		widgets.add(button);
	}

	private static boolean isFree(List<AbstractWidget> widgets, int x, int y, int width) {
		for (AbstractWidget other : widgets) {
			if (other.visible && x < other.getX() + other.getWidth() && other.getX() < x + width
				&& y < other.getY() + other.getHeight() && other.getY() < y + HEIGHT) {
				return false;
			}
		}
		return true;
	}
}
