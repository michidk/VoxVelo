package dev.michidk.voxelvelo.client.input;

import dev.michidk.voxelvelo.client.obc.ObcClient;
import dev.michidk.voxelvelo.client.obc.ObcControls;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;

/**
 * Steering, braking and shifting from an OpenBikeControl device (for example the BikeControl phone app).
 * Steer left/right are digital in the protocol, so the value is ramped for smooth, bicycle-like steering.
 * It is not a propulsion source.
 */
public final class OpenBikeControlInput implements BikeInputSource {
	private static final double TURN_IN_PER_TICK = 0.3;
	private static final double RETURN_PER_TICK = 0.45;

	private final ObcClient obc;
	private double steering;

	public OpenBikeControlInput(ObcClient obc) {
		this.obc = obc;
	}

	@Override
	public String id() {
		return "openbikecontrol";
	}

	@Override
	public boolean supports(ControlChannel channel) {
		return channel != ControlChannel.PROPULSION;
	}

	@Override
	public String displayName() {
		return "OpenBikeControl";
	}

	@Override
	public boolean isActive() {
		return this.obc.isConnected();
	}

	@Override
	public void tick(Minecraft client) {
		double target = this.obc.controls().steering();
		boolean returning = Math.abs(target) < Math.abs(this.steering) || Math.signum(target) != Math.signum(this.steering);
		double step = returning ? RETURN_PER_TICK : TURN_IN_PER_TICK;
		this.steering += Math.max(-step, Math.min(step, target - this.steering));
	}

	@Override
	public void reset() {
		this.steering = 0.0;
	}

	@Override
	public OptionalDouble propulsion() {
		return OptionalDouble.empty();
	}

	@Override
	public OptionalDouble steering() {
		return OptionalDouble.of(this.steering);
	}

	@Override
	public OptionalDouble brake() {
		return OptionalDouble.of(this.obc.controls().brake());
	}

	@Override
	public int consumeGearShift() {
		return this.obc.controls().consumeShifts();
	}

	@Override
	public OptionalInt consumeGearSet() {
		int gear = this.obc.controls().consumeGearSet();
		return gear > 0 ? OptionalInt.of(gear) : OptionalInt.empty();
	}
}
