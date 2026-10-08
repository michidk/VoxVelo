package dev.michidk.voxvelo.bikes.client.ui;

import dev.michidk.voxvelo.bikes.VoxVelo;
import java.util.Collections;
import java.util.List;
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
 * buttons found by their label or position, below "Save and Quit to Title" on the pause menu and as a row under the
 * settings grid (spanning both columns) on the options menu. The listener runs after the default phase, so buttons
 * added by other mods already exist; if the spot is taken it moves one row further away. On the options menu it
 * then tries above "Done", and as a last resort every screen falls back to the top-left corner.
 */
public final class MenuButton {
	private static final Identifier PHASE = VoxVelo.id("menu_button");
	private static final int HEIGHT = 20;
	private static final int GAP = 4;
	private static final int MAX_SHIFTS = 6;
	/** Buttons this class added, held weakly so closed screens can be collected. */
	private static final Set<Button> ADDED = Collections.newSetFromMap(new WeakHashMap<>());

	private MenuButton() {
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
				Screens.getWidgets(screen).removeIf(ADDED::contains);
			}
			if (screen instanceof PauseScreen) {
				addBelow(client, screen, CommonComponents.disconnectButtonLabel(client.isLocalServer()));
			} else if (screen instanceof OptionsScreen) {
				addToOptions(client, screen);
			}
		});
	}

	/** Adds the button below the widget labelled {@code anchorLabel}, in the same column and width. */
	private static void addBelow(Minecraft client, Screen screen, Component anchorLabel) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		AbstractWidget anchor = find(widgets, anchorLabel);
		Slot slot = anchor == null ? null : findSlot(screen, widgets, anchor.getX(), anchor.getY() + anchor.getHeight() + GAP, anchor.getWidth(), 1);
		place(client, screen, widgets, slot);
	}

	/** Adds the button as a row under the settings grid, or above "Done" when there is no room below the grid. */
	private static void addToOptions(Minecraft client, Screen screen) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		AbstractWidget done = find(widgets, CommonComponents.GUI_DONE);

		int gridBottom = Integer.MIN_VALUE;
		for (AbstractWidget widget : widgets) {
			if (widget != done && widget.visible) {
				gridBottom = Math.max(gridBottom, widget.getY());
			}
		}

		Slot slot = null;
		if (gridBottom != Integer.MIN_VALUE) {
			int left = Integer.MAX_VALUE;
			int right = Integer.MIN_VALUE;
			for (AbstractWidget widget : widgets) {
				if (widget != done && widget.visible && widget.getY() == gridBottom) {
					left = Math.min(left, widget.getX());
					right = Math.max(right, widget.getX() + widget.getWidth());
				}
			}
			slot = findSlot(screen, widgets, left, gridBottom + HEIGHT + GAP, right - left, 1);
		}
		if (slot == null && done != null) {
			slot = findSlot(screen, widgets, done.getX(), done.getY() - HEIGHT - GAP, done.getWidth(), -1);
		}
		place(client, screen, widgets, slot);
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
