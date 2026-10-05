package dev.michidk.voxelvelo.client.input;

import dev.michidk.voxelvelo.client.config.VoxelVeloConfig;
import java.util.OptionalDouble;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Keyboard riding is a permanent, fully supported mode. Holding the pedal key produces a configurable
 * virtual rider power that feeds exactly the same physics as real trainer power.
 */
public final class KeyboardBikeInput implements BikeInputSource {
	private static final double BRAKE_STRENGTH = 0.6;
	private static final double STEER_TURN_IN_PER_TICK = 0.3;
	private static final double STEER_RETURN_PER_TICK = 0.45;

	private final VoxelVeloConfig config;

	private double propulsion;
	private double steering;
	private double brake;
	private int gearShift;

	public KeyboardBikeInput(VoxelVeloConfig config) {
		this.config = config;
	}

	@Override
	public String id() {
		return "keyboard";
	}

	@Override
	public boolean supports(ControlChannel channel) {
		return true;
	}

	@Override
	public String displayName() {
		return "Keyboard";
	}

	@Override
	public boolean isActive() {
		return true;
	}

	@Override
	public void tick(Minecraft client) {
		Options options = client.options;

		boolean pedal = options.keyUp.isDown() || VoxelVeloKeys.PEDAL.isDown();
		this.propulsion = pedal ? this.config.keyboardVirtualPower : 0.0;

		boolean left = options.keyLeft.isDown() || VoxelVeloKeys.STEER_LEFT.isDown();
		boolean right = options.keyRight.isDown() || VoxelVeloKeys.STEER_RIGHT.isDown();
		double target = ((right ? 1.0 : 0.0) - (left ? 1.0 : 0.0)) * this.config.keyboardSteeringSensitivity;
		target = Math.max(-1.0, Math.min(1.0, target));
		boolean returning = Math.abs(target) < Math.abs(this.steering) || Math.signum(target) != Math.signum(this.steering);
		double step = returning ? STEER_RETURN_PER_TICK : STEER_TURN_IN_PER_TICK;
		this.steering += Math.max(-step, Math.min(step, target - this.steering));

		if (VoxelVeloKeys.HARD_BRAKE.isDown()) {
			this.brake = 1.0;
		} else if (options.keyDown.isDown() || VoxelVeloKeys.BRAKE.isDown()) {
			this.brake = BRAKE_STRENGTH;
		} else {
			this.brake = 0.0;
		}

		while (VoxelVeloKeys.SHIFT_UP.consumeClick()) {
			this.gearShift++;
		}
		while (VoxelVeloKeys.SHIFT_DOWN.consumeClick()) {
			this.gearShift--;
		}
	}

	@Override
	public void reset() {
		this.propulsion = 0.0;
		this.steering = 0.0;
		this.brake = 0.0;
		this.gearShift = 0;
		drain(VoxelVeloKeys.SHIFT_UP);
		drain(VoxelVeloKeys.SHIFT_DOWN);
	}

	private static void drain(KeyMapping key) {
		while (key.consumeClick()) {
			// discard presses made while not riding
		}
	}

	@Override
	public OptionalDouble propulsion() {
		return OptionalDouble.of(this.propulsion);
	}

	@Override
	public OptionalDouble steering() {
		return OptionalDouble.of(this.steering);
	}

	@Override
	public OptionalDouble brake() {
		return OptionalDouble.of(this.brake);
	}

	@Override
	public int consumeGearShift() {
		int shift = this.gearShift;
		this.gearShift = 0;
		return shift;
	}
}
