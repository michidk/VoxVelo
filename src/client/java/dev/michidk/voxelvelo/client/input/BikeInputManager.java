package dev.michidk.voxelvelo.client.input;

import dev.michidk.voxelvelo.bike.BikeControlState;
import dev.michidk.voxelvelo.client.config.VoxelVeloConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Composes the final BikeControlState from independently selected sources. Each logical control
 * channel names its own source in the config, so e.g. trainer propulsion and keyboard steering mix.
 *
 * <p>If the selected source for a channel is disconnected: propulsion falls to zero (the bike coasts),
 * every other channel falls back to the keyboard so the bike stays steerable and brakeable.
 *
 * <p>The keyboard also coexists with a non-keyboard source instead of being switched off: it still brakes
 * (the stronger of the two brakes wins), steers whenever the other source is idle, and its shift keys still
 * shift gears. Propulsion is the exception: the selected source alone provides it.
 */
public final class BikeInputManager {
	/** Below this a source counts as idle and the keyboard may steer instead. */
	private static final double STEERING_IDLE = 0.05;

	private final VoxelVeloConfig config;
	private final List<BikeInputSource> sources = new ArrayList<>();
	private final BikeInputSource keyboard;
	private int gear = BikeControlState.DEFAULT_GEAR;

	public BikeInputManager(VoxelVeloConfig config, BikeInputSource keyboard) {
		this.config = config;
		this.keyboard = keyboard;
		this.sources.add(keyboard);
	}

	public void register(BikeInputSource source) {
		this.sources.add(source);
	}

	public List<BikeInputSource> sources() {
		return this.sources;
	}

	/** The source currently chosen for a channel, whether or not it is connected. Never null. */
	public BikeInputSource selected(ControlChannel channel) {
		String id = this.config.sourceFor(channel);
		for (BikeInputSource source : this.sources) {
			if (source.id().equals(id) && source.supports(channel)) {
				return source;
			}
		}
		return this.keyboard;
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
		for (BikeInputSource source : this.sources) {
			if (source.isActive()) {
				source.tick(client);
			}
		}

		BikeInputSource propulsionSource = this.selected(ControlChannel.PROPULSION);
		double propulsion = propulsionSource.isActive() ? propulsionSource.propulsion().orElse(0.0) : 0.0;
		double steering = this.active(ControlChannel.STEERING).steering().orElse(0.0);
		if (Math.abs(steering) < STEERING_IDLE) {
			steering = this.keyboard.steering().orElse(0.0);
		}
		double brake = Math.max(this.active(ControlChannel.BRAKE).brake().orElse(0.0), this.keyboard.brake().orElse(0.0));
		BikeInputSource gearSource = this.active(ControlChannel.GEAR);
		gearSource.consumeGearSet().ifPresent(set -> this.gear = clampGear(set));
		int shifts = gearSource.consumeGearShift();
		if (gearSource != this.keyboard) {
			shifts += this.keyboard.consumeGearShift();
		}
		this.gear = clampGear(this.gear + shifts);

		return new BikeControlState(propulsion, steering, brake, this.gear).sanitized();
	}

	private BikeInputSource active(ControlChannel channel) {
		BikeInputSource source = this.selected(channel);
		return source.isActive() ? source : this.keyboard;
	}

	private static int clampGear(int gear) {
		return Math.max(BikeControlState.MIN_GEAR, Math.min(BikeControlState.MAX_GEAR, gear));
	}
}
