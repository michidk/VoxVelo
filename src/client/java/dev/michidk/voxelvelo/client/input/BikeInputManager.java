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
 */
public final class BikeInputManager {
	private final VoxelVeloConfig config;
	private final List<BikeInputSource> sources = new ArrayList<>();
	private final KeyboardBikeInput keyboard;
	private int gear = BikeControlState.DEFAULT_GEAR;

	public BikeInputManager(VoxelVeloConfig config, KeyboardBikeInput keyboard) {
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
		double brake = this.active(ControlChannel.BRAKE).brake().orElse(0.0);
		this.gear = clampGear(this.gear + this.active(ControlChannel.GEAR).consumeGearShift());

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
