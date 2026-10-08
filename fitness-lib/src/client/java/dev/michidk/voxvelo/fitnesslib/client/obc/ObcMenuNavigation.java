package dev.michidk.voxvelo.fitnesslib.client.obc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.michidk.voxvelo.fitnesslib.client.FitnessSettingsScreen;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/** Uses the screen's own keyboard handling for controller navigation, on the game thread. */
public final class ObcMenuNavigation {
	private ObcMenuNavigation() {}

	public static void tick(Minecraft client, ObcClient controller) {
		var presses = controller.controls().consumeMenuPresses();
		// Always drain, so background/disconnected input cannot act on a menu opened later.
		if (!controller.isConnected() || !client.isWindowActive() || client.gui.overlay() != null) return;
		Screen screen = client.gui.screen();
		for (int action : presses) {
			if (screen == null) {
				if (action == Obc.BUTTON_MENU && client.level != null) {
					client.setLastInputType(InputType.KEYBOARD_ARROW);
					client.gui.setScreen(new FitnessSettingsScreen(null));
				}
			} else {
				client.setLastInputType(InputType.KEYBOARD_ARROW);
				dispatch(screen, action);
			}
			// A selection/back press may open another screen. Do not carry the batch into it.
			if (client.gui.screen() != screen) break;
		}
	}

	public static void dispatch(Screen screen, int action) {
		int key = switch (action) {
			case Obc.BUTTON_UP -> InputConstants.KEY_UP;
			case Obc.BUTTON_DOWN -> InputConstants.KEY_DOWN;
			case Obc.BUTTON_LEFT -> InputConstants.KEY_LEFT;
			case Obc.BUTTON_RIGHT -> InputConstants.KEY_RIGHT;
			case Obc.BUTTON_SELECT -> InputConstants.KEY_RETURN;
			case Obc.BUTTON_BACK -> InputConstants.KEY_ESCAPE;
			default -> 0;
		};
		if (key == 0) return;
		// Mouse-opened screens may have no focus yet. Tab establishes a navigable widget.
		if (screen.getFocused() == null && key != InputConstants.KEY_ESCAPE) {
			press(screen, InputConstants.KEY_TAB);
			if (action != Obc.BUTTON_SELECT) return;
		}
		press(screen, key);
	}

	private static void press(Screen screen, int key) {
		// Screens use logical keycodes for focus navigation and physical keys for activation.
		int keycode = switch (key) {
			case InputConstants.KEY_UP -> InputConstants.KEYCODE_UP;
			case InputConstants.KEY_DOWN -> InputConstants.KEYCODE_DOWN;
			case InputConstants.KEY_LEFT -> InputConstants.KEYCODE_LEFT;
			case InputConstants.KEY_RIGHT -> InputConstants.KEYCODE_RIGHT;
			case InputConstants.KEY_RETURN -> InputConstants.KEYCODE_RETURN;
			case InputConstants.KEY_TAB -> InputConstants.KEYCODE_TAB;
			default -> 27; // Escape's logical keycode.
		};
		KeyEvent event = new KeyEvent(key, keycode, 0);
		screen.keyPressed(event);
		screen.keyReleased(event);
		screen.afterKeyboardAction();
	}
}
