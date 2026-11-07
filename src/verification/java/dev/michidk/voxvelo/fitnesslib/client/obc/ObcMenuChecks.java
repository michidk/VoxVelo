package dev.michidk.voxvelo.fitnesslib.client.obc;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Menu input regression checks without a window or a Bluetooth device. */
public final class ObcMenuChecks {
	public static void main(String[] args) throws Exception {
		for (var visibility : dev.michidk.voxvelo.fitnesslib.client.fitness.HudVisibility.values()) {
require(visibility.visible(true) == (visibility != dev.michidk.voxvelo.fitnesslib.client.fitness.HudVisibility.NEVER), "mounted HUD visibility");
require(visibility.visible(false) == (visibility == dev.michidk.voxvelo.fitnesslib.client.fitness.HudVisibility.ALWAYS), "dismounted HUD visibility");
}
java.util.concurrent.CountDownLatch secondClosed = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicBoolean closedConcurrently = new java.util.concurrent.atomic.AtomicBoolean();
		MdnsDiscovery.closeBrowsers(List.of(
			() -> closedConcurrently.set(secondClosed.await(1, java.util.concurrent.TimeUnit.SECONDS)),
			() -> secondClosed.countDown()));
		require(closedConcurrently.get(), "slow interface must not block closing the next one");
		require(secondClosed.getCount() == 0, "every network interface is closed");
		MdnsDiscovery discovery = new MdnsDiscovery();
		discovery.shutdown();
		discovery.shutdown();
		discovery.start();
		discovery.stop();
		require(ObcControls.smooth(0, 1, 1) == 1, "default steering response has no smoothing");
require(ObcControls.smooth(0, 1, 0.25) == 0.25, "smoothing blends steering toward target");
require(ObcControls.smooth(0.5, -0.5, 0.5) == 0, "smoothing handles changes of direction");
require(new dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig().steeringDeadzoneDegrees == 5, "default dead zone is five degrees");
require(ObcControls.deadzone(4.99, 5) == 0 && ObcControls.deadzone(-4.99, 5) == 0, "dead zone centers both directions");
require(ObcControls.deadzone(5, 5) == 5 && ObcControls.deadzone(-6, 5) == -6, "angles outside dead zone retain magnitude");
require(ObcControls.deadzone(1, 0) == 1, "zero disables dead zone");
require(ObcControls.deadzone(Double.NaN, 5) == 0, "invalid input steers straight");
ObcControls custom = new ObcControls();
custom.apply(0x83, 1); custom.apply(0x83, 2);
require(custom.consumeActionPresses().equals(List.of(0x83)), "held custom action fires only once");
custom.apply(0x83, 0); custom.apply(0x83, 1); custom.apply(0x83, 0); custom.apply(0x81, 1);
require(custom.consumeActionPresses().equals(List.of(0x83, 0x81)), "custom actions preserve press order");
custom.apply(0x82, 1); custom.releaseAll();
require(custom.consumeActionPresses().isEmpty(), "disconnect discards pending custom actions");
custom.apply(0x24, 1); custom.apply(0x40, 1); custom.apply(0x44, 1);
require(custom.consumeActionPresses().equals(List.of(0x24, 0x40, 0x44)), "standard photo camera and HUD actions are supported");
ObcControls controls = new ObcControls();
		controls.apply(ObcMessages.parseButtonState(new byte[] {1, 0x11, 1, 0x11, 0, 0x14, 1}));
		require(controls.consumeMenuPresses().equals(List.of(Obc.BUTTON_DOWN, Obc.BUTTON_SELECT)),
			"quick press/release survives until the tick and actions retain arrival order");
		controls.apply(Obc.BUTTON_SELECT, 1);
		controls.apply(Obc.BUTTON_SELECT, 2);
		require(controls.consumeMenuPresses().isEmpty(), "held button updates must not activate another screen");
		controls.apply(Obc.BUTTON_SELECT, 0);
		controls.apply(Obc.BUTTON_SELECT, 1);
		controls.apply(Obc.BUTTON_SELECT, 0);
		controls.apply(Obc.BUTTON_SELECT, 1);
		require(controls.consumeMenuPresses().equals(List.of(Obc.BUTTON_SELECT, Obc.BUTTON_SELECT)),
			"distinct rapid presses are not coalesced");
		controls.apply(Obc.BUTTON_SHIFT_UP, 1);
		controls.apply(Obc.BUTTON_STEER_LEFT, 1);
		controls.apply(Obc.BUTTON_BRAKE, 1);
		require(controls.consumeMenuPresses().isEmpty() && controls.consumeShifts() == 1
			&& controls.steering() == -1 && controls.brake() == 1, "riding controls stay separate from navigation");
		controls.apply(Obc.BUTTON_MENU, 1);
		controls.releaseAll();
		require(controls.consumeMenuPresses().isEmpty(), "disconnect discards queued menu actions");
		controls.apply(Obc.BUTTON_MENU, 1);
		require(controls.consumeMenuPresses().equals(List.of(Obc.BUTTON_MENU)), "reconnect allows a fresh press");

		RecordingScreen screen = new RecordingScreen();
		ObcMenuNavigation.dispatch(screen, Obc.BUTTON_DOWN);
		require(screen.pressed.equals(List.of(InputConstants.KEY_TAB)), "first direction establishes focus without skipping a widget");
		screen.pressed.clear();
		for (int action : List.of(Obc.BUTTON_UP, Obc.BUTTON_DOWN, Obc.BUTTON_LEFT, Obc.BUTTON_RIGHT,
			Obc.BUTTON_SELECT, Obc.BUTTON_BACK)) ObcMenuNavigation.dispatch(screen, action);
		require(screen.pressed.equals(List.of(InputConstants.KEY_UP, InputConstants.KEY_DOWN, InputConstants.KEY_LEFT,
			InputConstants.KEY_RIGHT, InputConstants.KEY_RETURN, InputConstants.KEY_ESCAPE)), "navigation uses the screen's keyboard handling");
		require(screen.released.equals(List.of(InputConstants.KEY_TAB, InputConstants.KEY_UP, InputConstants.KEY_DOWN,
			InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT, InputConstants.KEY_RETURN, InputConstants.KEY_ESCAPE)), "every key is released");
		RecordingScreen unfocused = new RecordingScreen();
		ObcMenuNavigation.dispatch(unfocused, Obc.BUTTON_SELECT);
		require(unfocused.pressed.equals(List.of(InputConstants.KEY_TAB, InputConstants.KEY_RETURN)), "select can activate the first widget");
		RecordingScreen back = new RecordingScreen();
		ObcMenuNavigation.dispatch(back, Obc.BUTTON_BACK);
		require(back.pressed.equals(List.of(InputConstants.KEY_ESCAPE)), "back does not require widget focus");
		System.out.println("OpenBikeControl menu checks passed");
	}

	private static final class RecordingScreen extends Screen {
		final List<Integer> pressed = new ArrayList<>();
		final List<Integer> released = new ArrayList<>();

		RecordingScreen() { super(null, null, Component.literal("Menu test")); }

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.isCycleFocus()) require(event.shortcutKey() == InputConstants.KEYCODE_TAB,
				"screen focus navigation needs a logical Tab keycode");
			if (event.isUp()) require(event.shortcutKey() == InputConstants.KEYCODE_UP, "logical Up keycode");
			if (event.isDown()) require(event.shortcutKey() == InputConstants.KEYCODE_DOWN, "logical Down keycode");
			if (event.isLeft()) require(event.shortcutKey() == InputConstants.KEYCODE_LEFT, "logical Left keycode");
			if (event.isRight()) require(event.shortcutKey() == InputConstants.KEYCODE_RIGHT, "logical Right keycode");
			pressed.add(event.key());
			if (event.key() == InputConstants.KEY_TAB) setFocused(new GuiEventListener() {
				private boolean focused;
				@Override
				public void setFocused(boolean value) { focused = value; }
				@Override
				public boolean isFocused() { return focused; }
			});
			return true;
		}

		@Override
		public boolean keyReleased(KeyEvent event) {
			released.add(event.key());
			return true;
		}

		@Override
		public void afterKeyboardAction() {}
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
