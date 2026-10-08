package dev.michidk.voxvelo.bikes.client.input;

import dev.michidk.voxvelo.bikes.bike.BikeControlState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Composes the final BikeControlState from every connected source at once. The keyboard is always there; a
 * trainer, a power meter or an OpenBikeControl device works as well while it is connected, and a source that is not
 * connected simply contributes nothing.
 *
 * <ul>
 * <li>Propulsion: the strongest power of all sources, so pedalling the trainer or holding the pedal key both move the bike.
 * <li>Brake: the strongest brake of all sources.
 * <li>Gears: every source's shifts count, and an absolute gear a source asks for is applied.
 * <li>Steering: the latest registered source that is steering wins, so road follow takes over from an
 *     OpenBikeControl device, which takes over from the keyboard; the keyboard steers when the others are idle.
 * </ul>
 */
public final class BikeInputManager {
	/** Below this a source counts as idle and an earlier registered one may steer instead. */
	private static final double STEERING_IDLE = 0.05;

	private final List<BikeInputSource> sources = new ArrayList<>();
	private final BikeInputSource keyboard;
	private int gear = BikeControlState.DEFAULT_GEAR;

	public BikeInputManager(BikeInputSource keyboard) {
		this.keyboard = keyboard;
		this.sources.add(keyboard);
	}

	/** Adds a source. Sources registered later win over earlier ones where only one can steer. */
	public void register(BikeInputSource source) {
		this.sources.add(source);
	}

	public List<BikeInputSource> sources() {
		return this.sources;
	}

	/** Called when the local player gets on a bike. */
	public void onMount(int bikeGear) {
		this.reset();
		this.gear = clampGear(bikeGear);
	}

	/** Called when the local player is not riding. */
	public void reset() {
		for (BikeInputSource source : this.sources) {
			source.reset();
		}
	}

	public BikeControlState compose(Minecraft client) {
		// Every source is ticked, connected or not: some (road follow) only learn that they are active by ticking.
		for (BikeInputSource source : this.sources) {
			source.tick(client);
		}

		double propulsion = 0.0;
		double brake = 0.0;
		int shifts = 0;
		for (BikeInputSource source : this.sources) {
			if (source.isActive() && source.supports(ControlChannel.PROPULSION)) {
				propulsion = Math.max(propulsion, source.propulsion().orElse(0.0));
			}
			if (source.isActive() && source.supports(ControlChannel.BRAKE)) {
				brake = Math.max(brake, source.brake().orElse(0.0));
			}
			if (source.isActive() && source.supports(ControlChannel.GEAR)) {
				source.consumeGearSet().ifPresent(set -> this.gear = clampGear(set));
				shifts += source.consumeGearShift();
			}
		}
		this.gear = clampGear(this.gear + shifts);

		return new BikeControlState(propulsion, this.steering(), brake, this.gear).sanitized();
	}

	/** The latest registered active source that is steering; otherwise whatever the keyboard has (it may be easing back to centre). */
	private double steering() {
		for (int i = this.sources.size() - 1; i >= 0; i--) {
			BikeInputSource source = this.sources.get(i);
			if (source.isActive() && source.supports(ControlChannel.STEERING)) {
				double value = source.steering().orElse(0.0);
				if (Math.abs(value) >= STEERING_IDLE) {
					return value;
				}
			}
		}
		return this.keyboard.steering().orElse(0.0);
	}

	private static int clampGear(int gear) {
		return Math.max(BikeControlState.MIN_GEAR, Math.min(BikeControlState.MAX_GEAR, gear));
	}
}
