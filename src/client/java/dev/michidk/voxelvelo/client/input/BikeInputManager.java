package dev.michidk.voxelvelo.client.input;

import dev.michidk.voxelvelo.bike.BikeControlState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Composes the final {@link BikeControlState} from independently selected sources. Each logical
 * control channel names its own source, so e.g. trainer propulsion and keyboard steering can mix.
 * Today only the keyboard exists; other sources register here and are chosen per channel.
 */
public final class BikeInputManager {
	private final List<BikeInputSource> sources = new ArrayList<>();
	private final Map<ControlChannel, BikeInputSource> selection = new EnumMap<>(ControlChannel.class);
	private final KeyboardBikeInput keyboard = new KeyboardBikeInput();
	private int gear = BikeControlState.DEFAULT_GEAR;

	public BikeInputManager() {
		this.sources.add(this.keyboard);
		for (ControlChannel channel : ControlChannel.values()) {
			this.selection.put(channel, this.keyboard);
		}
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

		double propulsion = this.source(ControlChannel.PROPULSION).propulsion().orElse(0.0);
		double steering = this.source(ControlChannel.STEERING).steering().orElse(0.0);
		double brake = this.source(ControlChannel.BRAKE).brake().orElse(0.0);
		this.gear = clampGear(this.gear + this.source(ControlChannel.GEAR).consumeGearShift());

		return new BikeControlState(propulsion, steering, brake, this.gear).sanitized();
	}

	private BikeInputSource source(ControlChannel channel) {
		BikeInputSource source = this.selection.get(channel);
		return source.isActive() ? source : this.keyboard;
	}

	private static int clampGear(int gear) {
		return Math.max(BikeControlState.MIN_GEAR, Math.min(BikeControlState.MAX_GEAR, gear));
	}
}
