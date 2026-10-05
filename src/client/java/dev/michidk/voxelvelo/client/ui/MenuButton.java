package dev.michidk.voxelvelo.client.ui;

import dev.michidk.voxelvelo.VoxelVelo;
import java.util.List;
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
 * A "VoxelVelo" button on the pause menu and the options menu that opens the settings hub.
 *
 * <p>To live next to other mods it never uses fixed coordinates for the normal case: it is placed against the vanilla
 * button it belongs to (found by its label), below "Save and Quit to Title" on the pause menu and above "Done" on the
 * options menu, in the same width. The listener runs after the default phase, so buttons added by other mods already
 * exist; if the spot is taken it moves one row further away, and as a last resort falls back to the top-left corner.
 */
public final class MenuButton {
	private static final Identifier PHASE = VoxelVelo.id("menu_button");
	private static final int HEIGHT = 20;
	private static final int GAP = 4;
	private static final int MAX_SHIFTS = 6;

	private MenuButton() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
		ScreenEvents.AFTER_INIT.register(PHASE, (client, screen, width, height) -> {
			if (screen instanceof PauseScreen) {
				add(client, screen, CommonComponents.disconnectButtonLabel(client.isLocalServer()), 1);
			} else if (screen instanceof OptionsScreen) {
				add(client, screen, CommonComponents.GUI_DONE, -1);
			}
		});
	}

	/** Adds the button next to the widget labelled {@code anchorLabel}: below it for direction 1, above it for -1. */
	private static void add(Minecraft client, Screen screen, Component anchorLabel, int direction) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		AbstractWidget anchor = null;
		for (AbstractWidget widget : widgets) {
			if (widget instanceof Button && widget.getMessage().getString().equals(anchorLabel.getString())) {
				anchor = widget;
				break;
			}
		}

		int x = 6;
		int y = 6;
		int width = 90;
		if (anchor != null) {
			int step = direction * (HEIGHT + GAP);
			int candidateY = direction > 0 ? anchor.getY() + anchor.getHeight() + GAP : anchor.getY() - HEIGHT - GAP;
			for (int shift = 0; shift <= MAX_SHIFTS; shift++, candidateY += step) {
				if (candidateY >= GAP && candidateY + HEIGHT <= screen.height - GAP && isFree(widgets, anchor.getX(), candidateY, anchor.getWidth())) {
					x = anchor.getX();
					y = candidateY;
					width = anchor.getWidth();
					break;
				}
			}
		}

		Button button = Button.builder(Component.translatable("voxelvelo.menu.button"), pressed -> client.gui.setScreen(new ConfigScreen(screen)))
			.tooltip(Tips.of("voxelvelo.menu.button"))
			.bounds(x, y, width, HEIGHT)
			.build();
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
