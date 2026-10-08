package dev.michidk.voxvelo.bikes.client.input;

import dev.michidk.voxvelo.bikes.bike.BikeControlState;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;

/** Standalone checks for how every connected input source is combined; the keyboard is always one of them. */
public final class InputManagerChecks {
	public static void main(String[] args) {
		// id, then the channels it supports (propulsion, steering, brake, gear), then whether it is connected.
		Source keyboard = new Source("keyboard", true, true, true, true, true);
		Source trainer = new Source("ftms", true, false, false, false, false);
		Source controller = new Source("obc", false, true, true, true, false);
		Source road = new Source("road", false, true, false, false, false);
		BikeInputManager manager = new BikeInputManager(keyboard);
		manager.register(trainer);
		manager.register(controller);
		manager.register(road);

		// Keyboard alone always works.
		keyboard.propulsion = 200;
		keyboard.steering = -0.5;
		keyboard.brake = 0.6;
		BikeControlState state = manager.compose(null);
		require(state.propulsion() == 200 && state.steering() == -0.5 && state.brake() == 0.6, "keyboard works with nothing connected");

		// A disconnected trainer contributes nothing, even if it still holds a value.
		trainer.propulsion = 500;
		require(manager.compose(null).propulsion() == 200, "a disconnected trainer is ignored");

		// A connected trainer works as well; the strongest power moves the bike.
		trainer.active = true;
		trainer.propulsion = 150;
		require(manager.compose(null).propulsion() == 200, "keyboard power wins when it is stronger");
		keyboard.propulsion = 0;
		require(manager.compose(null).propulsion() == 150, "trainer power moves the bike while the keyboard is idle");
		keyboard.propulsion = 100;
		trainer.propulsion = 250;
		require(manager.compose(null).propulsion() == 250, "trainer power wins when it is stronger");

		// Brakes: the strongest of keyboard and controller.
		controller.active = true;
		controller.brake = 0.9;
		require(manager.compose(null).brake() == 0.9, "a controller brake counts");
		controller.brake = 0.2;
		require(manager.compose(null).brake() == 0.6, "the stronger brake wins");

		// Steering: the latest registered steering source wins, the keyboard fills in when the others are idle.
		controller.steering = 0.4;
		require(manager.compose(null).steering() == 0.4, "a controller steers over the keyboard");
		controller.steering = 0.01;
		require(manager.compose(null).steering() == -0.5, "an idle controller leaves the steering to the keyboard");
		controller.steering = 0.4;
		road.active = true;
		road.steering = 0.7;
		require(manager.compose(null).steering() == 0.7, "road follow steers over the controller and keyboard");
		road.steering = 0.0;
		require(manager.compose(null).steering() == 0.4, "centred road follow falls through to the controller");
		road.active = false;
		road.steering = 0.9;
		require(manager.compose(null).steering() == 0.4, "an inactive road follow is ignored");
		controller.active = false;
		require(manager.compose(null).steering() == -0.5, "a disconnected controller is ignored");

		// Gears: every connected source counts, and an absolute gear is applied.
		manager.onMount(6);
		keyboard.shifts = 1;
		controller.active = true;
		controller.shifts = 2;
		require(manager.compose(null).gear() == 9, "shifts from the keyboard and the controller add up");
		controller.shifts = 0;
		controller.setGear = 3;
		require(manager.compose(null).gear() == 3, "a controller can set the gear");
		controller.active = false;
		controller.shifts = 5;
		keyboard.shifts = -1;
		require(manager.compose(null).gear() == 2, "shifts of a disconnected controller are not applied");
		keyboard.shifts = 100;
		require(manager.compose(null).gear() == BikeControlState.MAX_GEAR, "the gear stays within range");
		System.out.println("Input source composition checks passed.");
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	/** A scripted source whose values the checks set directly. */
	private static final class Source implements BikeInputSource {
		private final String id;
		private final boolean propulsionSupported;
		private final boolean steeringSupported;
		private final boolean brakeSupported;
		private final boolean gearSupported;
		boolean active;
		double propulsion;
		double steering;
		double brake;
		int shifts;
		int setGear;

		Source(String id, boolean propulsion, boolean steering, boolean brake, boolean gear, boolean active) {
			this.id = id;
			this.propulsionSupported = propulsion;
			this.steeringSupported = steering;
			this.brakeSupported = brake;
			this.gearSupported = gear;
			this.active = active;
		}

		@Override public String id() { return this.id; }
		@Override public boolean supports(ControlChannel channel) {
			return switch (channel) {
				case PROPULSION -> this.propulsionSupported;
				case STEERING -> this.steeringSupported;
				case BRAKE -> this.brakeSupported;
				case GEAR -> this.gearSupported;
			};
		}
		@Override public String displayName() { return this.id; }
		@Override public boolean isActive() { return this.active; }
		@Override public void tick(Minecraft client) { }
		@Override public void reset() { }
		@Override public OptionalDouble propulsion() { return OptionalDouble.of(this.propulsion); }
		@Override public OptionalDouble steering() { return OptionalDouble.of(this.steering); }
		@Override public OptionalDouble brake() { return OptionalDouble.of(this.brake); }
		@Override public int consumeGearShift() {
			int value = this.shifts;
			this.shifts = 0;
			return value;
		}
		@Override public OptionalInt consumeGearSet() {
			int value = this.setGear;
			this.setGear = 0;
			return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
		}
	}
}
