package dev.michidk.voxvelo.client.input;

import dev.michidk.voxvelo.bike.BikePhysics;
import dev.michidk.voxvelo.client.config.VoxVeloConfig;
import java.util.OptionalDouble;
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

	private final VoxVeloConfig config;

	private double propulsion;
	private double steering;
	private double brake;

	public KeyboardBikeInput(VoxVeloConfig config) {
		this.config = config;
	}

	@Override
	public String id() {
		return "keyboard";
	}

	@Override
	public boolean supports(ControlChannel channel) {
		// The raw bike has one fixed gear; shifting comes with the fitness integration.
		return channel != ControlChannel.GEAR;
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

		boolean pedal = options.keyUp.isDown() || VoxVeloKeys.PEDAL.isDown();
		this.propulsion = pedal ? this.config.keyboardVirtualPower : 0.0;

		boolean left = options.keyLeft.isDown() || VoxVeloKeys.STEER_LEFT.isDown();
		boolean right = options.keyRight.isDown() || VoxVeloKeys.STEER_RIGHT.isDown();
		double target = ((right ? 1.0 : 0.0) - (left ? 1.0 : 0.0)) * this.config.keyboardSteeringSensitivity;
		target = Math.max(-1.0, Math.min(1.0, target));
		// The keys only say left or right, so they ramp a little faster than the physics smooths the fork.
		this.steering = BikePhysics.smoothSteering(this.steering, target, STEER_TURN_IN_PER_TICK, STEER_RETURN_PER_TICK);

		if (VoxVeloKeys.HARD_BRAKE.isDown()) {
			this.brake = 1.0;
		} else if (options.keyDown.isDown() || VoxVeloKeys.BRAKE.isDown()) {
			this.brake = BRAKE_STRENGTH;
		} else {
			this.brake = 0.0;
		}
	}

	@Override
	public void reset() {
		this.propulsion = 0.0;
		this.steering = 0.0;
		this.brake = 0.0;
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
		return 0;
	}
}
